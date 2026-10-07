package com.example.capstone_3.Service;

import com.example.capstone_3.Api.ApiException;
import com.example.capstone_3.DtoIn.CreateLearningRequestDtoIn;
import com.example.capstone_3.DtoOut.LearningRequestDtoOut;
import com.example.capstone_3.DtoOut.RequestNegotiationDtoOut;
import com.example.capstone_3.Model.*;
import com.example.capstone_3.Repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LearningRequestService {
    private final AccountAccessService accountAccessService;
    private final LearningRequestRepository learningRequestRepository;
    private final AccountRepository accountRepository;
    private final SkillRepository skillRepository;
    private final SkillOfferRepository skillOfferRepository;
    private final AccountSkillRepository accountSkillRepository;
    private final AccountNameHelper accountNameHelper;
    private final BrevoEmailService brevoEmailService;
    private final WhatsAppService whatsAppService;

    public List<LearningRequest> getAllLearningRequests() {
        return learningRequestRepository.findAll();
    }

    @Transactional
    public void addLearningRequest(Integer accountId, Integer offerId, CreateLearningRequestDtoIn dtoIn) {

        Account account = accountAccessService.requireActive(accountId);

        if (!Boolean.TRUE.equals(account.getEmailVerified())) {
            throw new ApiException("Please verify your email first");
        }

        SkillOffer offer = skillOfferRepository.findSkillOfferById(offerId);

        if (offer == null) {
            throw new ApiException("Skill offer not found");
        }

        if (!"ACTIVE".equals(offer.getStatus())) {
            throw new ApiException("Skill offer is not active");
        }

        Account providerAccount = offer.getProviderAccount();

        if (providerAccount == null) {
            throw new ApiException("Provider account not found");
        }

        if (accountId.equals(providerAccount.getId())) {
            throw new ApiException("You cannot request your own offer");
        }

        if (!"ACTIVE".equals(providerAccount.getStatus())) {
            throw new ApiException("Provider account is not active");
        }

        Skill skill = offer.getSkill();

        if (skill == null) {
            throw new ApiException("Offer skill not found");
        }

        if (accountSkillRepository.findAccountSkillByAccountAndSkill(providerAccount, skill) == null) {
            throw new ApiException("Provider does not have this skill");
        }

        if (!"BOTH".equals(offer.getMode()) && !offer.getMode().equals(dtoIn.getMode())) {
            throw new ApiException("Requested mode is not supported by this offer");
        }

        if (offer.getTokenCost() == null || offer.getTokenCost() <= 0) {
            throw new ApiException("Offer token cost is invalid");
        }

        if (account.getTokenBalance() == null || account.getTokenBalance() < offer.getTokenCost()) {
            throw new ApiException("Not enough tokens");
        }

        LearningRequest learningRequest = new LearningRequest();

        learningRequest.setDescription(dtoIn.getDescription());
        learningRequest.setMode(dtoIn.getMode());
        learningRequest.setBaseTokens(offer.getTokenCost());

        learningRequest.setUrgent(false);
        learningRequest.setUrgentTokens(0);
        learningRequest.setWeekend(false);
        learningRequest.setWeekendTokens(0);
        learningRequest.setNeededBy(null);

        learningRequest.setRequesterAccount(account);
        learningRequest.setProviderAccount(providerAccount);
        learningRequest.setSkill(skill);
        learningRequest.setSkillOffer(offer);
        learningRequest.setStatus("OPEN");
        learningRequest.setCreatedAt(LocalDateTime.now());

        learningRequestRepository.save(learningRequest);

        String subject = "New learning request";

        String text = "Hello " + accountNameHelper.getAccountName(providerAccount)
                + ",\n\n"
                + accountNameHelper.getAccountName(account)
                + " sent you a new learning request."
                + "\n\nRequest ID: " + learningRequest.getId()
                + "\nSkill: " + skill.getName()
                + "\nDescription: " + learningRequest.getDescription()
                + "\nMode: " + learningRequest.getMode()
                + "\nBase cost: " + learningRequest.getBaseTokens() + " tokens";

        brevoEmailService.sendEmail(providerAccount.getEmail(), subject, text);

        try {
            String phone;

            if (providerAccount.getIndividualProfile() != null) {
                phone = providerAccount.getIndividualProfile().getPhone();
            } else {
                phone = providerAccount.getCompanyProfile().getPhone();
            }

            whatsAppService.sendMessage(phone, text);
        } catch (Exception e) {
            System.out.println("WhatsApp message failed: " + e.getMessage());
        }

    }

    public void updateLearningRequest(Integer id, LearningRequest learningRequest){

        LearningRequest oldRequest = learningRequestRepository.findLearningRequestById(id);
        if (oldRequest == null) {
            throw new ApiException("Learning request not found");
        }
        if (!oldRequest.getStatus().equals("OPEN")) {
            throw new ApiException("Only open requests can be updated");
        }
        oldRequest.setDescription(learningRequest.getDescription());
        oldRequest.setMode(learningRequest.getMode());
        oldRequest.setWeekend(learningRequest.getWeekend());
        oldRequest.setBaseTokens(learningRequest.getBaseTokens());
        oldRequest.setNeededBy(learningRequest.getNeededBy());
        calculateExtraTokens(oldRequest);
        if (oldRequest.getRequesterAccount().getTokenBalance() < totalTokens(oldRequest)) {
            throw new ApiException("Not enough tokens");
        }
        learningRequestRepository.save(oldRequest);

    }

    public void deleteLearningRequest(Integer id) {
        LearningRequest learningRequest = learningRequestRepository.findLearningRequestById(id);
        if (learningRequest == null) {
            throw new ApiException("Learning request not found");
        }if (!learningRequest.getStatus().equals("OPEN")) {
            throw new ApiException("Only open requests can be deleted");
        }
        learningRequestRepository.delete(learningRequest);
    }

    @Transactional
    public LearningRequestDtoOut getLearningRequestById(Integer requestId, Integer accountId) {

        Account account = accountAccessService.requireActive(accountId);

        LearningRequest learningRequest = learningRequestRepository.findLearningRequestById(requestId);

        if (learningRequest == null) {
            throw new ApiException("Learning request not found");
        }

        Integer requesterAccountId = learningRequest.getRequesterAccount() == null ? null : learningRequest.getRequesterAccount().getId();
        Integer providerAccountId = learningRequest.getProviderAccount() == null ? null : learningRequest.getProviderAccount().getId();

        if (!accountId.equals(requesterAccountId) && !accountId.equals(providerAccountId)) {
            throw new ApiException("You can only view requests you are involved in");
        }

        List<RequestNegotiationDtoOut> negotiationHistory = new ArrayList<>();

        if (learningRequest.getRequestNegotiations() != null) {

            List<RequestNegotiation> negotiations = new ArrayList<>(learningRequest.getRequestNegotiations());
            negotiations.sort(Comparator.comparing(RequestNegotiation::getCreatedAt).thenComparing(RequestNegotiation::getId));

            for (RequestNegotiation negotiation : negotiations) {
                negotiationHistory.add(new RequestNegotiationDtoOut(negotiation.getId(), negotiation.getSenderAccountId(), accountNameHelper.getAccountName(negotiation.getSenderAccount()), negotiation.getMessage(), negotiation.getProposedDate(), negotiation.getCreatedAt(), negotiation.getUrgentTokens(), negotiation.getWeekendTokens()));
            }
        }

        LearningRequestDtoOut dtoOut = new LearningRequestDtoOut();

        dtoOut.setId(learningRequest.getId());
        dtoOut.setRequesterAccountId(requesterAccountId);
        dtoOut.setProviderAccountId(providerAccountId);
        dtoOut.setSkillId(learningRequest.getSkill() == null ? null : learningRequest.getSkill().getId());
        dtoOut.setSkillName(learningRequest.getSkill() == null ? null : learningRequest.getSkill().getName());
        dtoOut.setOfferId(learningRequest.getSkillOffer() == null ? null : learningRequest.getSkillOffer().getId());
        dtoOut.setDescription(learningRequest.getDescription());
        dtoOut.setMode(learningRequest.getMode());
        dtoOut.setBaseTokens(learningRequest.getBaseTokens());
        dtoOut.setUrgent(learningRequest.getUrgent());
        dtoOut.setUrgentTokens(learningRequest.getUrgentTokens());
        dtoOut.setWeekend(learningRequest.getWeekend());
        dtoOut.setWeekendTokens(learningRequest.getWeekendTokens());
        dtoOut.setTotalTokens(totalTokens(learningRequest));
        dtoOut.setStatus(learningRequest.getStatus());
        dtoOut.setNeededBy(learningRequest.getNeededBy());
        dtoOut.setCreatedAt(learningRequest.getCreatedAt());
        dtoOut.setNegotiationHistory(negotiationHistory);

        return dtoOut;
    }



// نرجع لها
    private Integer totalTokens(LearningRequest learningRequest) {
        return learningRequest.getBaseTokens()+learningRequest.getUrgentTokens()+learningRequest.getWeekendTokens();
    }
    private void calculateExtraTokens(LearningRequest learningRequest) {
        if (learningRequest.getWeekend()!=null&&learningRequest.getWeekend()) {
            learningRequest.setWeekendTokens(1);
        } else {
            learningRequest.setWeekendTokens(0);
        }
    }


    public List<LearningRequest> getOpenLearningRequests() {
        return learningRequestRepository.findByStatus("OPEN");
    }

    public List<LearningRequest> getLearningRequestsBySkill(Integer skillId) {
        if (skillRepository.findSkillById(skillId) == null) {
            throw new ApiException("Skill not found");
        }

        return learningRequestRepository.findBySkill_IdAndStatus(skillId, "OPEN");
    }

    public List<LearningRequest> getRequestsByRequester(Integer loggedInAccountId, Integer accountId) {
        requireOwnAccount(loggedInAccountId, accountId);
        if (accountRepository.findAccountById(accountId) == null) {
            throw new ApiException("Account not found");
        }

        return learningRequestRepository.findByRequesterAccount_Id(accountId);
    }

    public List<LearningRequest> getRequestsByProvider(Integer loggedInAccountId, Integer accountId) {
        requireOwnAccount(loggedInAccountId, accountId);
        if (accountRepository.findAccountById(accountId) == null) {
            throw new ApiException("Account not found");
        }

        return learningRequestRepository.findByProviderAccount_Id(accountId);
    }

    public List<LearningRequest> getUrgentLearningRequests() {
        return learningRequestRepository.findByUrgentTrueAndStatus("OPEN");
    }

    public void cancelLearningRequest(Integer accountId, Integer requestId) {
        accountAccessService.requireActive(accountId);
        LearningRequest request = learningRequestRepository.findLearningRequestById(requestId);

        if (request == null) {
            throw new ApiException("Learning request not found");
        }

        if (request.getRequesterAccount() == null || !accountId.equals(request.getRequesterAccount().getId())) {
            throw new ApiException("Only the requester can cancel this learning request");
        }

        if (!"OPEN".equals(request.getStatus())) {
            throw new ApiException("Only open requests can be cancelled");
        }

        request.setStatus("CANCELLED");
        learningRequestRepository.save(request);
    }

    private void requireOwnAccount(Integer loggedInAccountId, Integer accountId) {
        accountAccessService.requireActive(loggedInAccountId);
        if (!loggedInAccountId.equals(accountId)) {
            throw new ApiException("You can only view your own learning requests");
        }
    }



}
