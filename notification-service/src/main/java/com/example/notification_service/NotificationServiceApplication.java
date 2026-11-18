package com.example.notification_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BUG FIX (see AssetServiceApplication's identical fix for the full
 * explanation): @Scheduled needs @EnableScheduling somewhere in the context
 * to actually run. SseHeartbeatScheduler's 30s keep-alive pings have never
 * fired — which likely explains the Week 3 handoff note that heartbeats
 * "never visibly appeared in curl.exe" being written off as a client-side
 * quirk. It may well have been this instead. Worth re-testing SSE connection
 * longevity now that heartbeats can actually run.
 */
@SpringBootApplication
@EnableScheduling
public class NotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}

}
