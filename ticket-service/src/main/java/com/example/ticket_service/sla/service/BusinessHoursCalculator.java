package com.example.ticket_service.sla.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The 6.1 challenge: add N business-hours to a starting instant, honoring a
 * per-day-of-week working-hours calendar, skipping non-working days
 * (typically weekends — but really "any day with no configured window"),
 * skipping holidays, and correctly handling a shift whose end time is
 * earlier than its start time (a working window that crosses midnight,
 * e.g. 22:00-06:00).
 *
 * Deliberately a plain class with no Spring/repository dependency — takes
 * already-loaded calendar data as plain collections, so BusinessHoursCalculatorTest
 * can exercise every edge case without a Spring context or a database.
 * SlaService is the Spring-aware caller that loads BusinessHours/Holiday
 * rows and hands them to this.
 */
@Component
public class BusinessHoursCalculator {

    /** Generous lookahead bound so a misconfigured tenant (e.g. zero working days) fails loudly instead of looping forever. */
    private static final int MAX_LOOKAHEAD_DAYS = 400;

    private record Window(ZonedDateTime start, ZonedDateTime end) {
    }

    /**
     * @param from          starting instant (e.g. ticket creation time)
     * @param hoursToAdd    business-hours to add (the SLA's resolution/response window)
     * @param workingHours  day-of-week (1=Mon..7=Sun, java.time.DayOfWeek.getValue()) -> [start,end) local time.
     *                      An end time earlier than (or equal to) start means the shift crosses midnight into the next day.
     * @param holidays      calendar dates fully excluded from working time. Applied to the shift's START day.
     * @param zone          the tenant's timezone the working-hours clock times are expressed in
     */
    public Instant addBusinessHours(Instant from, int hoursToAdd, Map<Integer, LocalTime[]> workingHours,
                                     Set<LocalDate> holidays, ZoneId zone) {
        if (hoursToAdd <= 0) {
            return from;
        }

        ZonedDateTime fromZdt = from.atZone(zone);
        List<Window> windows = upcomingWindows(fromZdt, workingHours, holidays);

        long remainingMinutes = hoursToAdd * 60L;
        for (Window window : windows) {
            if (!window.end().isAfter(fromZdt) && !window.end().isAfter(window.start())) {
                continue; // defensive; shouldn't happen given upcomingWindows' own filtering
            }
            ZonedDateTime segmentStart = window.start().isBefore(fromZdt) ? fromZdt : window.start();
            if (!segmentStart.isBefore(window.end())) {
                continue; // this window is entirely in the past relative to `from`
            }
            long availableMinutes = Duration.between(segmentStart, window.end()).toMinutes();
            if (remainingMinutes <= availableMinutes) {
                return segmentStart.plusMinutes(remainingMinutes).toInstant();
            }
            remainingMinutes -= availableMinutes;
        }

        throw new IllegalStateException(
                "Could not compute a business-hours due date within " + MAX_LOOKAHEAD_DAYS
                        + " days — check that business_hours has at least one working day configured");
    }

    /**
     * Generates raw shift windows for every day from (from's calendar day - 1)
     * through +MAX_LOOKAHEAD_DAYS, in ascending start order, already filtered
     * to windows that end after `from` (i.e. drop anything fully in the past).
     * Starting one day early is what lets a midnight-crossing shift that
     * began YESTERDAY and is still open right now be picked up correctly.
     */
    private List<Window> upcomingWindows(ZonedDateTime from, Map<Integer, LocalTime[]> workingHours,
                                          Set<LocalDate> holidays) {
        List<Window> windows = new ArrayList<>();
        LocalDate cursor = from.toLocalDate().minusDays(1);

        for (int i = 0; i < MAX_LOOKAHEAD_DAYS + 1; i++) {
            LocalDate day = cursor.plusDays(i);
            int isoDayOfWeek = day.getDayOfWeek().getValue();
            LocalTime[] range = workingHours.get(isoDayOfWeek);

            if (range == null || holidays.contains(day)) {
                continue; // non-working day (weekend if unconfigured) or holiday
            }

            LocalTime start = range[0];
            LocalTime end = range[1];
            boolean spansMidnight = !end.isAfter(start);

            ZonedDateTime windowStart = ZonedDateTime.of(day, start, from.getZone());
            ZonedDateTime windowEnd = ZonedDateTime.of(spansMidnight ? day.plusDays(1) : day, end, from.getZone());

            if (windowEnd.isAfter(from)) {
                windows.add(new Window(windowStart, windowEnd));
            }
        }

        windows.sort((a, b) -> a.start().compareTo(b.start()));
        return windows;
    }
}
