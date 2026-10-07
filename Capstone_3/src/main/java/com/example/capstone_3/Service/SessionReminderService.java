package com.example.capstone_3.Service;

import com.example.capstone_3.Model.Exchange;
import com.example.capstone_3.Model.Session;
import com.example.capstone_3.Repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionReminderService {

    private final SessionRepository sessionRepository;
    private final BrevoEmailService brevoEmailService;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void sendSessionReminders() {

        List<Session> sessions = sessionRepository.findByStatusAndReminderSentFalse("SCHEDULED");

        LocalDateTime now = LocalDateTime.now();

        for (Session session : sessions) {
            LocalDateTime reminderTime = session.getScheduledAt().minusHours(1);

            if (!now.isBefore(reminderTime) && now.isBefore(session.getScheduledAt())) {
                for (var participant : session.getSessionParticipants()) {

                    Exchange exchange = participant.getExchange();
                    if (exchange != null && exchange.getLearningRequest() != null && exchange.getLearningRequest().getRequesterAccount() != null) {
                        String email = exchange.getLearningRequest().getRequesterAccount().getEmail();

                        brevoEmailService.sendSessionEmail(email, "Session Reminder", "Your session starts in one hour."
                                        + "\nSession: " + session.getTitle()
                                        + "\nDate: " + session.getScheduledAt()
                                        + "\nMeeting Link: " + session.getMeetingLink()
                        );
                    }
                }

                session.setReminderSent(true);
                sessionRepository.save(session);
            }
        }
    }
}
