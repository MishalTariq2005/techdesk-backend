package com.example.ticket_service.assignment.service;

import com.example.ticket_service.assignment.dto.*;
import com.example.ticket_service.assignment.entity.StaffProfile;
import com.example.ticket_service.assignment.entity.StaffSkill;
import com.example.ticket_service.assignment.repository.StaffProfileRepository;
import com.example.ticket_service.assignment.repository.StaffSkillRepository;
import com.example.ticket_service.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StaffProfileService {

    private static final List<String> TERMINAL_STATUSES = List.of("CLOSED", "CANCELLED");

    private final StaffProfileRepository profileRepository;
    private final StaffSkillRepository skillRepository;
    private final TicketRepository ticketRepository;

    public StaffProfileService(StaffProfileRepository profileRepository, StaffSkillRepository skillRepository,
                                TicketRepository ticketRepository) {
        this.profileRepository = profileRepository;
        this.skillRepository = skillRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public StaffProfileResponse upsertProfile(UpsertStaffProfileRequest request) {
        StaffProfile profile = profileRepository.findById(request.staffUserId())
            .orElse(new StaffProfile(request.staffUserId(), request.available(), request.location(), request.maxActiveTickets()));
        profile.setAvailable(request.available());
        profile.setLocation(request.location());
        profile.setMaxActiveTickets(request.maxActiveTickets());
        profile = profileRepository.save(profile);
        return toResponse(profile);
    }

    public StaffProfileResponse getProfile(UUID staffUserId) {
        StaffProfile profile = profileRepository.findById(staffUserId)
            .orElseThrow(() -> new IllegalArgumentException("No staff profile for user: " + staffUserId));
        return toResponse(profile);
    }

    public List<StaffProfileResponse> listAll() {
        return profileRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public StaffSkillResponse upsertSkill(UUID staffUserId, UpsertStaffSkillRequest request) {
        if (!profileRepository.existsById(staffUserId)) {
            throw new IllegalArgumentException("Create a staff profile before assigning skills: " + staffUserId);
        }
        StaffSkill skill = skillRepository.findByStaffUserIdAndCategoryId(staffUserId, request.categoryId())
            .orElse(new StaffSkill(staffUserId, request.categoryId(), request.skillLevel()));
        skill.setSkillLevel(request.skillLevel());
        skill = skillRepository.save(skill);
        return new StaffSkillResponse(skill.getCategoryId(), skill.getSkillLevel());
    }

    public List<StaffSkillResponse> listSkills(UUID staffUserId) {
        return skillRepository.findByStaffUserId(staffUserId).stream()
            .map(s -> new StaffSkillResponse(s.getCategoryId(), s.getSkillLevel()))
            .toList();
    }

    private StaffProfileResponse toResponse(StaffProfile p) {
        long activeCount = ticketRepository.countActiveByAssignedTo(p.getStaffUserId(), TERMINAL_STATUSES);
        return new StaffProfileResponse(p.getStaffUserId(), p.isAvailable(), p.getLocation(), p.getMaxActiveTickets(), activeCount);
    }
}
