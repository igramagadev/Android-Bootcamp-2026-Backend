package ru.sicampus.bootcamp2026.service.impl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.sicampus.bootcamp2026.dto.*;
import ru.sicampus.bootcamp2026.entity.*;
import ru.sicampus.bootcamp2026.util.MeetingMapper;
import ru.sicampus.bootcamp2026.repository.*;
import ru.sicampus.bootcamp2026.service.MeetingService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingServiceImpl implements MeetingService {
    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final MeetingStatusRepository statusRepo;
    private final MeetingTypeRepository typeRepo;
    private final InvitationStatusRepository invStatusRepo;
    private final MeetingMapper meetingMapper;

    @Transactional
    @Override
    public void createMeeting(Long organizerId, MeetingCreateDTO dto) {
        if (dto.getDateTime().getMinute() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Встречи могут начинаться только в начале часа (xx:00)");
        }

        LocalDate date = dto.getDateTime().toLocalDate();
        LocalTime startTime = dto.getDateTime().toLocalTime();
        LocalTime endTime = startTime.plusHours(1);

        if (meetingRepository.existsActiveMeetingForUser(organizerId, date, startTime, endTime)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Вы уже заняты в это время!");
        }

        for (Long pId : dto.getParticipantIds()) {
            if (meetingRepository.existsActiveMeetingForUser(pId, date, startTime, endTime)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Участник с ID " + pId + " уже занят.");
            }
        }

        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Организатор не найден"));

        MeetingStatus scheduledStatus = statusRepo.findByStatusName("SCHEDULED")
                .orElseThrow(() -> new RuntimeException("Статус SCHEDULED не найден в БД"));

        MeetingType onlineType = typeRepo.findByTypeName("ONLINE")
                .orElse(null);

        InvitationStatus pendingStatus = invStatusRepo.findByStatusName("PENDING").orElseThrow();
        InvitationStatus acceptedStatus = invStatusRepo.findByStatusName("ACCEPTED").orElseThrow();

        Meeting meeting = new Meeting();
        meeting.setTopic(dto.getTopic());
        meeting.setCalendarDate(date);
        meeting.setStartTime(startTime);
        meeting.setEndTime(endTime);
        meeting.setOrganizer(organizer);
        meeting.setStatus(scheduledStatus);
        meeting.setType(onlineType);

        Meeting savedMeeting = meetingRepository.save(meeting);

        List<MeetingParticipant> participantsToSave = new ArrayList<>();

        List<User> guests = userRepository.findAllById(dto.getParticipantIds());

        for (User guest : guests) {
            MeetingParticipant mp = new MeetingParticipant();
            mp.setMeeting(savedMeeting);
            mp.setUser(guest);
            mp.setInvitationStatus(pendingStatus);
            participantsToSave.add(mp);
        }

        MeetingParticipant mpOrg = new MeetingParticipant();
        mpOrg.setMeeting(savedMeeting);
        mpOrg.setUser(organizer);
        mpOrg.setInvitationStatus(acceptedStatus);
        participantsToSave.add(mpOrg);

        participantRepository.saveAll(participantsToSave);
    }

    @Override
    public MeetingInfoDTO getMeetingInfo(Long meetingId) {
        Meeting m = meetingRepository.findById(meetingId).orElseThrow(() -> new RuntimeException("Встреча не найдена"));
        return meetingMapper.toInfoDTO(m, participantRepository.findByMeetingId(meetingId));
    }

    @Override
    public Page<InvitationDTO> getInvitations(Long userId, Pageable pageable) {
        return participantRepository.findByUserIdAndInvitationStatus_StatusName(userId, "PENDING", pageable)
                .map(p -> {
                    InvitationDTO dto = new InvitationDTO();
                    dto.setInvitationId(p.getMeeting().getId().toString());
                    dto.setTopic(p.getMeeting().getTopic());
                    dto.setDateTime(LocalDateTime.of(p.getMeeting().getCalendarDate(), p.getMeeting().getStartTime()));
                    dto.setOrganizerName(p.getMeeting().getOrganizer().getFullName());

                    return dto;
                });
    }
    @Override
    public List<InvitationDTO> getInvitations(Long userId) {
        return participantRepository.findByUserIdAndInvitationStatus_StatusName(userId, "PENDING").stream()
                .map(p -> {
                    InvitationDTO dto = new InvitationDTO();
                    dto.setInvitationId(p.getMeeting().getId().toString());
                    dto.setTopic(p.getMeeting().getTopic());
                    dto.setDateTime(LocalDateTime.of(p.getMeeting().getCalendarDate(), p.getMeeting().getStartTime()));
                    dto.setOrganizerName(p.getMeeting().getOrganizer().getFullName());

                    return dto;
                }).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public void respondToInvitation(Long userId, Long invitationId, String status) {
        MeetingParticipant p = participantRepository.findByMeetingIdAndUserId(invitationId, userId)
                .orElseThrow(() -> new RuntimeException("Приглашение не найдено"));

        if ("ACCEPTED".equals(status)) {
            Meeting m = p.getMeeting();
            if (!meetingRepository.findConflictingMeetings(userId, m.getCalendarDate(), m.getStartTime(), m.getEndTime()).isEmpty()) {
                throw new RuntimeException("Слот занят");
            }
        }
        p.setInvitationStatus(invStatusRepo.findByStatusName(status).orElseThrow());
        participantRepository.save(p);
    }

    @Override
    public List<ScheduleEntryDTO> getSchedule(Long userId, LocalDate start, LocalDate end) {
        return meetingRepository.findConfirmedMeetings(userId, start, end).stream()
                .map(m -> {
                    ScheduleEntryDTO dto = new ScheduleEntryDTO();
                    dto.setTopic(m.getTopic());
                    dto.setDateTime(LocalDateTime.of(m.getCalendarDate(), m.getStartTime()));
                    return dto;
                }).collect(Collectors.toList());
    }
}