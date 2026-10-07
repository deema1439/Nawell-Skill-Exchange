package com.example.capstone_3.Service;

import com.example.capstone_3.Api.ApiException;
import com.example.capstone_3.DtoIn.CreateSessionDtoIn;
import com.example.capstone_3.DtoIn.SessionDtoIn;
import com.example.capstone_3.Model.*;
import com.example.capstone_3.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.capstone_3.DtoOut.ZoomMeetingDtoOut;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class SessionService {
    private final AccountAccessService accountAccessService;
    private final BrevoEmailService brevoEmailService;

    private final SessionRepository sessionRepository;
    private final SkillOfferRepository skillOfferRepository;
    private final ExchangeRepository exchangeRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final ZoomService zoomService;
    private final RequestNegotiationRepository requestNegotiationRepository;


    public List<Session> get() {
        return sessionRepository.findAll();
    }

    public void add(SessionDtoIn sessionDtoIn) {
        SkillOffer skillOffer = skillOfferRepository.findSkillOfferById(sessionDtoIn.getSkillOfferId());

        if (skillOffer == null) {
            throw new ApiException("No skill offer found");
        }

        Session session = new Session();
        session.setTitle(sessionDtoIn.getTitle());
        session.setScheduledAt(sessionDtoIn.getScheduledAt());
        session.setDurationMinutes(sessionDtoIn.getDurationMinutes());
        session.setMode(sessionDtoIn.getMode());
        session.setMeetingLink(sessionDtoIn.getMeetingLink());
        session.setLocation(sessionDtoIn.getLocation());
        session.setStatus(sessionDtoIn.getStatus() != null ? sessionDtoIn.getStatus() : "SCHEDULED");
        session.setSkillOffer(skillOffer);

        sessionRepository.save(session);
    }

    public void update(Integer id, SessionDtoIn sessionDtoIn) {
        Session oldSession = sessionRepository.findSessionById(id);

        if (oldSession == null) {
            throw new ApiException("No session found");
        }

        if (oldSession.getZoomMeetingId() != null) {
            throw new ApiException("Sessions linked to Zoom cannot be updated through this endpoint yet");
        }

        SkillOffer skillOffer = skillOfferRepository.findSkillOfferById(sessionDtoIn.getSkillOfferId());

        if (skillOffer == null) {
            throw new ApiException("No skill offer found");
        }

        oldSession.setTitle(sessionDtoIn.getTitle());
        oldSession.setScheduledAt(sessionDtoIn.getScheduledAt());
        oldSession.setDurationMinutes(sessionDtoIn.getDurationMinutes());
        oldSession.setMode(sessionDtoIn.getMode());
        oldSession.setMeetingLink(sessionDtoIn.getMeetingLink());
        oldSession.setLocation(sessionDtoIn.getLocation());
        oldSession.setStatus(sessionDtoIn.getStatus());
        oldSession.setSkillOffer(skillOffer);

        sessionRepository.save(oldSession);
    }

    public void delete(Integer id) {
        Session oldSession = sessionRepository.findSessionById(id);

        if (oldSession == null) {
            throw new ApiException("No session found");
        }

        if (oldSession.getZoomMeetingId() != null) {
            throw new ApiException("Sessions linked to Zoom cannot be deleted through this endpoint yet");
        }

        sessionRepository.delete(oldSession);
    }

    @Transactional
    public Map<String, Object> createSession(Integer accountId, Integer offerId, CreateSessionDtoIn dto) {

        accountAccessService.requireActive(accountId);

        SkillOffer offer = skillOfferRepository.findSkillOfferForUpdate(offerId);

        if (offer == null) {
            throw new ApiException("No skill offer found");
        }

        requireProvider(accountId, offer);

        if (!"ACTIVE".equals(offer.getStatus())) {
            throw new ApiException("Skill offer is not active");
        }

        RequestNegotiation proposal = requestNegotiationRepository.findRequestNegotiationById(dto.getNegotiationId());

        if (proposal == null || proposal.getLearningRequest() == null) {
            throw new ApiException("No proposal found");
        }

        LearningRequest request = proposal.getLearningRequest();

        if (request.getAcceptedNegotiation() == null
                || !proposal.getId().equals(request.getAcceptedNegotiation().getId())) {
            throw new ApiException("The proposal must be accepted");
        }

        Exchange sourceExchange = request.getExchange();

        if (sourceExchange == null
                || sourceExchange.getSkillOffer() == null
                || !offerId.equals(sourceExchange.getSkillOffer().getId())) {
            throw new ApiException("The proposal does not belong to this offer");
        }

        if (!"ACCEPTED".equals(sourceExchange.getStatus())
                && !"IN_PROGRESS".equals(sourceExchange.getStatus())) {
            throw new ApiException("The proposal exchange must be accepted or in progress");
        }

        if (proposal.getProposedDate() == null) {
            throw new ApiException("The accepted proposal has no date");
        }

        LocalDateTime scheduledAt = proposal.getProposedDate()
                .toLocalDate()
                .atTime(dto.getStartTime());

        if (!scheduledAt.isAfter(LocalDateTime.now(ZoneId.of("Asia/Riyadh")))) {
            throw new ApiException("The session date and time must be in the future");
        }

        String mode = offer.getMode();

        if ("BOTH".equals(mode)) {
            mode = dto.getMode();

            if (!"ONLINE".equals(mode) && !"IN_PERSON".equals(mode)) {
                throw new ApiException("Choose ONLINE or IN_PERSON for a BOTH offer");
            }

        } else if (dto.getMode() != null && !dto.getMode().equals(mode)) {
            throw new ApiException("Session mode must match the offer");
        }

        if (!"ONLINE".equals(mode) && !"IN_PERSON".equals(mode)) {
            throw new ApiException("Invalid offer mode");
        }

        if ("IN_PERSON".equals(mode)
                && (dto.getLocation() == null || dto.getLocation().isBlank())) {
            throw new ApiException("Location is required for an in-person session");
        }

        if (offer.getSkill() == null
                || offer.getSkill().getName() == null
                || offer.getSkill().getName().isBlank()) {
            throw new ApiException("The offer has no skill name");
        }

        if (sessionRepository.existsBySkillOffer_IdAndScheduledAtAndStatus(
                offerId, scheduledAt, "SCHEDULED")) {
            throw new ApiException("A session is already scheduled for this offer at this time");
        }

        List<Exchange> exchanges = exchangeRepository.findBySkillOffer_IdAndStatusIn(
                offerId, List.of("ACCEPTED", "IN_PROGRESS"));

        if (exchanges.isEmpty()) {
            throw new ApiException("No registered learners found");
        }

        for (Exchange exchange : exchanges) {

            if (exchange.getLearningRequest() == null
                    || exchange.getLearningRequest().getRequesterAccount() == null) {
                throw new ApiException("A registered exchange has no learner");
            }

            String email = exchange.getLearningRequest().getRequesterAccount().getEmail();

            if (email == null || email.isBlank()) {
                throw new ApiException("A registered learner has no email");
            }
        }

        Session session = new Session();
        session.setTitle(offer.getSkill().getName());
        session.setScheduledAt(scheduledAt);
        session.setDurationMinutes(dto.getDurationMinutes());
        session.setMode(mode);
        session.setLocation("IN_PERSON".equals(mode) ? dto.getLocation().trim() : null);
        session.setStatus("SCHEDULED");
        session.setSkillOffer(offer);

        sessionRepository.saveAndFlush(session);

        for (Exchange exchange : exchanges) {
            SessionParticipant participant = new SessionParticipant();
            participant.setSession(session);
            participant.setExchange(exchange);
            participant.setStatus("JOINED");

            sessionParticipantRepository.save(participant);
        }

        sessionParticipantRepository.flush();

        Long createdMeetingId = null;

        try {

            if ("ONLINE".equals(mode)) {
                ZoomMeetingDtoOut meeting = zoomService.createMeeting(session);

                createdMeetingId = meeting.getMeetingId();

                session.setZoomMeetingId(createdMeetingId);
                session.setMeetingLink(meeting.getMeetingLink());

                sessionRepository.saveAndFlush(session);
            }

            List<String> emails = exchanges.stream()
                    .map(exchange -> exchange.getLearningRequest().getRequesterAccount().getEmail())
                    .distinct()
                    .toList();

            String message = "A new session has been created."
                    + "\nSession: " + session.getTitle()
                    + "\nDate: " + session.getScheduledAt() + " (Asia/Riyadh)"
                    + "\nDuration: " + session.getDurationMinutes() + " minutes"
                    + ("ONLINE".equals(mode)
                    ? "\nMeeting Link: " + session.getMeetingLink()
                    : "\nLocation: " + session.getLocation());

            for (String email : emails) {
                brevoEmailService.sendSessionEmail(email, "New Session Created", message);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "Session created and all email requests accepted");
            result.put("sessionId", session.getId());
            result.put("title", session.getTitle());
            result.put("scheduledAt", session.getScheduledAt());
            result.put("durationMinutes", session.getDurationMinutes());
            result.put("timezone", "Asia/Riyadh");
            result.put("mode", session.getMode());
            result.put("status", session.getStatus());
            result.put("meetingId", session.getZoomMeetingId());
            result.put("meetingLink", session.getMeetingLink());
            result.put("location", session.getLocation());
            result.put("participantCount", exchanges.size());
            result.put("emailRequestCount", emails.size());

            return result;

        } catch (RuntimeException creationError) {

            if (createdMeetingId != null) {

                try {
                    zoomService.deleteMeeting(createdMeetingId);

                } catch (RuntimeException cleanupError) {
                    throw new ApiException(
                            "Session was not saved. Zoom cleanup could not be confirmed for meeting "
                                    + createdMeetingId
                                    + ". Check Zoom before retrying"
                    );
                }
            }

            throw new ApiException(
                    "Session was not saved: " + creationError.getMessage()
            );
        }
    }

    public void joinSession(Integer accountId, Integer sessionId, Integer exchangeId) {
        accountAccessService.requireActive(accountId);
        Session session = sessionRepository.findSessionById(sessionId);

        if (session == null) {
            throw new ApiException("No session found");
        }

        Exchange exchange = exchangeRepository.findExchangeById(exchangeId);

        if (exchange == null) {
            throw new ApiException("No exchange found");
        }

        if (exchange.getLearningRequest() == null || exchange.getLearningRequest().getRequesterAccount() == null || !accountId.equals(exchange.getLearningRequest().getRequesterAccount().getId())) {
            throw new ApiException("Only the exchange learner can join this session");
        }

        if (!"ACCEPTED".equals(exchange.getStatus()) && !"IN_PROGRESS".equals(exchange.getStatus())) {
            throw new ApiException("Only accepted or in-progress exchanges can join a session");
        }

        if (session.getSkillOffer() == null || exchange.getSkillOffer() == null) {
            throw new ApiException("Session or exchange skill offer not found");
        }

        if (!session.getSkillOffer().getId().equals(exchange.getSkillOffer().getId())) {
            throw new ApiException("Exchange does not belong to this skill offer");
        }

        if (!"SCHEDULED".equals(session.getStatus())) {
            throw new ApiException("Session is not scheduled");
        }

        if (sessionParticipantRepository.findSessionParticipantBySession_IdAndExchange_Id(sessionId, exchangeId) != null) {
            throw new ApiException("You have already joined this session");
        }

        SessionParticipant participant = new SessionParticipant();
        participant.setSession(session);
        participant.setExchange(exchange);
        participant.setStatus("JOINED");

        sessionParticipantRepository.save(participant);
        String learnerEmail =
                exchange.getLearningRequest()
                        .getRequesterAccount()
                        .getEmail();

        brevoEmailService.sendSessionEmail(
                learnerEmail,
                "Session Joined",
                "You have successfully joined the session: "
                        + session.getTitle()
                        + "\nDate: "
                        + session.getScheduledAt()
                        + "\nMeeting Link: "
                        + session.getMeetingLink()
        );
    }

    public void updateAttendance(Integer accountId, Integer sessionId, Integer exchangeId, String status) {
        accountAccessService.requireActive(accountId);
        Session session = sessionRepository.findSessionById(sessionId);
        if (session == null) {
            throw new ApiException("No session found");
        }
        requireProvider(accountId, session.getSkillOffer());
        if (status == null || !(status.equals("ATTENDED") || status.equals("ABSENT") || status.equals("CANCELLED"))) {
            throw new ApiException("Status must be ATTENDED, ABSENT, or CANCELLED");
        }

        SessionParticipant participant = sessionParticipantRepository.findSessionParticipantBySession_IdAndExchange_Id(sessionId, exchangeId);

        if (participant == null) {
            throw new ApiException("No participant found for this session and exchange");
        }

        participant.setStatus(status);
        sessionParticipantRepository.save(participant);
    }

    public List<Session> getSessionsByOffer(Integer offerId) {
        SkillOffer skillOffer = skillOfferRepository.findSkillOfferById(offerId);

        if (skillOffer == null) {
            throw new ApiException("No skill offer found");
        }

        return sessionRepository.findBySkillOffer_Id(offerId);
    }

    public List<Session> getSessionsByExchange(Integer accountId, Integer exchangeId) {
        accountAccessService.requireActive(accountId);
        Exchange exchange = exchangeRepository.findExchangeById(exchangeId);

        if (exchange == null) {
            throw new ApiException("No exchange found");
        }

        if (exchange.getLearningRequest() == null || exchange.getLearningRequest().getRequesterAccount() == null || exchange.getLearningRequest().getProviderAccount() == null) {
            throw new ApiException("Exchange participants not found");
        }
        if (!accountId.equals(exchange.getLearningRequest().getRequesterAccount().getId()) && !accountId.equals(exchange.getLearningRequest().getProviderAccount().getId())) {
            throw new ApiException("Only exchange participants can view these sessions");
        }
        return sessionRepository.findDistinctBySessionParticipants_Exchange_Id(exchangeId);
    }



    private void requireProvider(Integer accountId, SkillOffer skillOffer) {
        if (skillOffer == null || skillOffer.getProviderAccount() == null || !accountId.equals(skillOffer.getProviderAccount().getId())) {
            throw new ApiException("Only the offer provider can manage this session");
        }
    }

    @Transactional
    public ZoomMeetingDtoOut createZoomMeeting(Integer accountId, Integer sessionId) {

        accountAccessService.requireActive(accountId);

        Session session = sessionRepository.findSessionForUpdate(sessionId);

        if (session == null) {
            throw new ApiException("No session found");
        }

        requireProvider(accountId, session.getSkillOffer());

        if (!"ONLINE".equals(session.getMode())) {
            throw new ApiException("Zoom meetings are only available for online sessions");
        }

        if (!"SCHEDULED".equals(session.getStatus())) {
            throw new ApiException("The session must be scheduled");
        }

        if (session.getZoomMeetingId() != null) {
            if (session.getMeetingLink() == null || session.getMeetingLink().isBlank()) {
                throw new ApiException("The saved Zoom meeting link is missing");
            }

            ZoomMeetingDtoOut result = new ZoomMeetingDtoOut();
            result.setSessionId(session.getId());
            result.setMeetingId(session.getZoomMeetingId());
            result.setTitle(session.getTitle());
            result.setScheduledAt(session.getScheduledAt());
            result.setDurationMinutes(session.getDurationMinutes());
            result.setTimezone("Asia/Riyadh");
            result.setMeetingLink(session.getMeetingLink());

            return result;
        }

        ZoomMeetingDtoOut result = zoomService.createMeeting(session);

        session.setZoomMeetingId(result.getMeetingId());
        session.setMeetingLink(result.getMeetingLink());
        sessionRepository.saveAndFlush(session);

        return result;
    }


}
