package com.example.capstone_3.Repository;

import com.example.capstone_3.Model.Account;
import com.example.capstone_3.Model.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExchangeRepository extends JpaRepository<Exchange, Integer> {

    Exchange findExchangeById(Integer id);
    @Query("select count(e) from Exchange e where e.skillOffer.providerAccount = ?1 and e.status = 'COMPLETED'")
    Integer countCompletedTeachings(Account provider);

    @Query(value = "SELECT * FROM exchange WHERE id = :id FOR UPDATE", nativeQuery = true)
    Exchange findExchangeForUpdate(@Param("id") Integer id);

    long countBySkillOffer_IdAndStatusIn(Integer offerId, List<String> statuses);

    List<Exchange> findBySkillOffer_IdAndStatusIn(Integer offerId, List<String> statuses);

    @Query("SELECT e FROM Exchange e WHERE e.learningRequest.requesterAccount.id = :accountId OR e.learningRequest.providerAccount.id = :accountId ORDER BY e.createdAt DESC, e.id DESC")
    List<Exchange> findExchangesRelatedToAccount(@Param("accountId") Integer accountId);

    @Query("SELECT COUNT(e) FROM Exchange e WHERE e.learningRequest.requesterAccount.id = :accountId OR e.learningRequest.providerAccount.id = :accountId")
    long countRelatedExchanges(@Param("accountId") Integer accountId);

    @Query("SELECT COUNT(e) FROM Exchange e WHERE (e.learningRequest.requesterAccount.id = :accountId OR e.learningRequest.providerAccount.id = :accountId) AND e.status = :status")
    long countRelatedExchangesByStatus(@Param("accountId") Integer accountId, @Param("status") String status);

    @Query("SELECT COALESCE(SUM(e.tokenAmount), 0) FROM Exchange e WHERE e.learningRequest.requesterAccount.id = :accountId AND e.tokensReserved = true AND e.status IN ('ACCEPTED', 'IN_PROGRESS', 'DISPUTED')")
    Long sumReservedTokens(@Param("accountId") Integer accountId);
}