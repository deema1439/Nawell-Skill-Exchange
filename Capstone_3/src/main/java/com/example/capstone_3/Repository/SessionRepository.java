package com.example.capstone_3.Repository;

import com.example.capstone_3.Model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, Integer> {

    Session findSessionById(Integer id);

    List<Session> findBySkillOffer_Id(Integer offerId);

    List<Session> findDistinctBySessionParticipants_Exchange_Id(Integer exchangeId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.id = :id")
    Session findSessionForUpdate(@Param("id") Integer id);

    List<Session> findByStatusAndReminderSentFalse(String status);

    boolean existsBySkillOffer_IdAndScheduledAtAndStatus(Integer offerId, LocalDateTime scheduledAt, String status);

}
