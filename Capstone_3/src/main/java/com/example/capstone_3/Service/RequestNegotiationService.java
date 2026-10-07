package com.example.capstone_3.Service;

import com.example.capstone_3.Api.ApiException;
import com.example.capstone_3.DtoIn.RequestNegotiationDtoIn;
import com.example.capstone_3.DtoOut.NegotiationProposalDtoOut;
import com.example.capstone_3.DtoOut.RequestNegotiationDtoOut;
import com.example.capstone_3.Model.Account;
import com.example.capstone_3.Model.LearningRequest;
import com.example.capstone_3.Model.RequestNegotiation;
import com.example.capstone_3.Repository.AccountRepository;
import com.example.capstone_3.Repository.LearningRequestRepository;
import com.example.capstone_3.Repository.RequestNegotiationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestNegotiationService {
    private final AccountAccessService accountAccessService;

    private final RequestNegotiationRepository requestNegotiationRepository;
    private final LearningRequestRepository learningRequestRepository;
    private final AccountNameHelper accountNameHelper;
    private final BrevoEmailService brevoEmailService;
    private final WhatsAppService whatsAppService;

    public List<RequestNegotiation> get() {
        return requestNegotiationRepository.findAll();
    }

    @Transactional
    public void respond(Integer accountId, Integer requestId, RequestNegotiationDtoIn dtoIn) {
        Account account = accountAccessService.requireActive(accountId);

        if (!Boolean.TRUE.equals(account.getEmailVerified())) {
            throw new ApiException("Please verify your email first");
        }

        LearningRequest learningRequest = learningRequestRepository.findLearningRequestForUpdate(requestId);
        if (learningRequest == null) {
            throw new ApiException("Learning request not found");
        }

        Account requester = learningRequest.getRequesterAccount();
        Account provider = learningRequest.getProviderAccount();

        if (requester == null || provider == null) {
            throw new ApiException("Learning request accounts not found");
        }

        boolean isRequester = accountId.equals(requester.getId());
        boolean isProvider = accountId.equals(provider.getId());

        if (!isRequester && !isProvider) {
            throw new ApiException("You cannot respond to this learning request");
        }

        if (!"OPEN".equals(learningRequest.getStatus())) {
            throw new ApiException("Only open requests can receive responses");
        }

        if (learningRequest.getAcceptedNegotiation() != null) {
            throw new ApiException("A proposal has already been accepted");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime proposedDate = dtoIn.getProposedDate();
        Integer urgentTokens = 0;
        Integer weekendTokens = 0;

        if (proposedDate != null) {

            if (!proposedDate.isAfter(now)) {
                throw new ApiException("Proposed date must be in the future");
            }

            LocalDateTime teacherDate = getOriginalTeacherDate(learningRequest);

            if (isRequester && teacherDate == null) {
                throw new ApiException("Wait for the teacher to propose a date first");
            }

            if (teacherDate != null) {
                urgentTokens = calculateUrgentTokens(learningRequest.getCreatedAt(), teacherDate, proposedDate);
            }

            DayOfWeek day = proposedDate.getDayOfWeek();

            if (day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY) {
                weekendTokens = 1;
            }

            int totalTokens = learningRequest.getBaseTokens() + urgentTokens + weekendTokens;

            if (requester.getTokenBalance() == null || requester.getTokenBalance() < totalTokens) {
                throw new ApiException("Requester does not have enough tokens for this date");
            }
        }

        RequestNegotiation negotiation = new RequestNegotiation();

        negotiation.setMessage(dtoIn.getMessage());
        negotiation.setProposedDate(proposedDate);
        negotiation.setUrgentTokens(urgentTokens);
        negotiation.setWeekendTokens(weekendTokens);
        negotiation.setCreatedAt(now);
        negotiation.setSenderAccount(account);
        negotiation.setLearningRequest(learningRequest);

        requestNegotiationRepository.save(negotiation);

        Account recipient = isRequester ? provider : requester;

        String subject = proposedDate == null
                ? "New message on your learning request"
                : "New proposed date for your learning request";

        String text = "Hello " + accountNameHelper.getAccountName(recipient)
                + ",\n\n"
                + accountNameHelper.getAccountName(account)
                + " sent a response to learning request #" + learningRequest.getId()
                + ".\n\nMessage: " + dtoIn.getMessage();

        if (proposedDate != null) {
            text += "\nProposed date: " + proposedDate
                    + "\nTotal cost: "
                    + (learningRequest.getBaseTokens() + urgentTokens + weekendTokens)
                    + " tokens";
        }

        brevoEmailService.sendEmail(recipient.getEmail(), subject, text);

        try {
            String phone;

            if (recipient.getIndividualProfile() != null) {
                phone = recipient.getIndividualProfile().getPhone();
            } else {
                phone = recipient.getCompanyProfile().getPhone();
            }

            whatsAppService.sendMessage(phone, text);
        } catch (Exception e) {
            System.out.println("WhatsApp message failed: " + e.getMessage());
        }

    }

    private LocalDateTime getOriginalTeacherDate(LearningRequest learningRequest) {

        if (learningRequest.getRequestNegotiations() == null) {
            return null;
        }

        Integer providerId = learningRequest.getProviderAccount().getId();

        return learningRequest.getRequestNegotiations().stream()
                .filter(negotiation -> providerId.equals(negotiation.getSenderAccountId()) && negotiation.getProposedDate() != null)
                .min(Comparator.comparing(RequestNegotiation::getCreatedAt).thenComparing(RequestNegotiation::getId))
                .map(RequestNegotiation::getProposedDate)
                .orElse(null);
    }

    private Integer calculateUrgentTokens(LocalDateTime requestCreatedAt, LocalDateTime teacherDate, LocalDateTime proposedDate) {

        if (!proposedDate.isBefore(teacherDate)) {
            return 0;
        }

        long fullDuration = Duration.between(requestCreatedAt, teacherDate).toMillis();
        long proposedDuration = Duration.between(requestCreatedAt, proposedDate).toMillis();

        if (fullDuration <= 0) {
            throw new ApiException("Original teacher date is invalid");
        }

        double percentage = (double) proposedDuration / fullDuration;

        if (percentage <= 0.25) {
            return 3;
        }

        if (percentage <= 0.50) {
            return 2;
        }

        return 1;
    }

    public void update(Integer id, RequestNegotiationDtoIn requestNegotiationDtoIn) {

        RequestNegotiation oldRequestNegotiation = requestNegotiationRepository.findRequestNegotiationById(id);

        if (oldRequestNegotiation == null) {
            throw new ApiException("No request negotiation found");
        }

        oldRequestNegotiation.setMessage(requestNegotiationDtoIn.getMessage());
        oldRequestNegotiation.setProposedDate(requestNegotiationDtoIn.getProposedDate());

        requestNegotiationRepository.save(oldRequestNegotiation);
    }

    public void delete(Integer id) {

        RequestNegotiation oldRequestNegotiation = requestNegotiationRepository.findRequestNegotiationById(id);

        if (oldRequestNegotiation == null) {
            throw new ApiException("No request negotiation found");
        }

        requestNegotiationRepository.delete(oldRequestNegotiation);
    }

    @Transactional
    public List<RequestNegotiationDtoOut> getNegotiationHistory(Integer accountId, Integer requestId) {

        LearningRequest learningRequest = checkRequestAccess(accountId, requestId);
        List<RequestNegotiationDtoOut> history = new ArrayList<>();

        if (learningRequest.getRequestNegotiations() == null || learningRequest.getRequestNegotiations().isEmpty()) {
            throw new ApiException("No messages found");
        }

        List<RequestNegotiation> negotiations = new ArrayList<>(learningRequest.getRequestNegotiations());
        negotiations.sort(Comparator.comparing(RequestNegotiation::getCreatedAt).thenComparing(RequestNegotiation::getId));

        for (RequestNegotiation negotiation : negotiations) {
            history.add(toDtoOut(negotiation));
        }

        return history;
    }

    @Transactional
    public RequestNegotiationDtoOut getLatestNegotiation(Integer accountId, Integer requestId) {

        LearningRequest learningRequest = checkRequestAccess(accountId, requestId);

        if (learningRequest.getRequestNegotiations() == null || learningRequest.getRequestNegotiations().isEmpty()) {
            throw new ApiException("No negotiation responses found");
        }

        RequestNegotiation latest = learningRequest.getRequestNegotiations().stream().max(Comparator.comparing(RequestNegotiation::getCreatedAt).thenComparing(RequestNegotiation::getId)).orElseThrow(() -> new ApiException("No negotiation responses found"));

        return toDtoOut(latest);
    }

    private LearningRequest checkRequestAccess(Integer accountId, Integer requestId) {

        Account account = accountAccessService.requireActive(accountId);

        LearningRequest learningRequest = learningRequestRepository.findLearningRequestById(requestId);

        if (learningRequest == null) {
            throw new ApiException("Learning request not found");
        }

        Account requester = learningRequest.getRequesterAccount();
        Account provider = learningRequest.getProviderAccount();

        if (requester == null || provider == null) {
            throw new ApiException("Learning request accounts not found");
        }

        if (!accountId.equals(requester.getId()) && !accountId.equals(provider.getId())) {
            throw new ApiException("You cannot view this request's negotiations");
        }

        return learningRequest;
    }

    private RequestNegotiationDtoOut toDtoOut(RequestNegotiation negotiation) {

        RequestNegotiationDtoOut dtoOut = new RequestNegotiationDtoOut();

        dtoOut.setId(negotiation.getId());
        dtoOut.setSenderAccountId(negotiation.getSenderAccountId());
        dtoOut.setSenderName(accountNameHelper.getAccountName(negotiation.getSenderAccount()));
        dtoOut.setMessage(negotiation.getMessage());
        dtoOut.setProposedDate(negotiation.getProposedDate());
        dtoOut.setCreatedAt(negotiation.getCreatedAt());
        dtoOut.setUrgentTokens(negotiation.getUrgentTokens());
        dtoOut.setWeekendTokens(negotiation.getWeekendTokens());

        return dtoOut;
    }


    @Transactional
    public NegotiationProposalDtoOut getNegotiationProposal(Integer accountId, Integer requestId, Integer negotiationId) {

        LearningRequest learningRequest = checkRequestAccess(accountId, requestId);

        if (!"OPEN".equals(learningRequest.getStatus())) {
            throw new ApiException("Only open requests can preview a proposed date");
        }

        RequestNegotiation negotiation = requestNegotiationRepository.findRequestNegotiationById(negotiationId);

        if (negotiation == null) {
            throw new ApiException("Negotiation response not found");
        }

        if (!requestId.equals(negotiation.getLearningRequestId())) {
            throw new ApiException("Negotiation does not belong to this learning request");
        }

        if (negotiation.getProposedDate() == null) {
            throw new ApiException("This response does not contain a proposed date");
        }

        if (!negotiation.getProposedDate().isAfter(LocalDateTime.now())) {
            throw new ApiException("Proposed date must be in the future");
        }

        Integer baseTokens = learningRequest.getBaseTokens();
        Integer urgentTokens = negotiation.getUrgentTokens();
        Integer weekendTokens = negotiation.getWeekendTokens();

        if (baseTokens == null || baseTokens <= 0 || urgentTokens == null || urgentTokens < 0 || weekendTokens == null || weekendTokens < 0) {
            throw new ApiException("Proposal token cost is invalid");
        }

        Integer totalTokens = baseTokens + urgentTokens + weekendTokens;
        Integer tokenBalance = learningRequest.getRequesterAccount().getTokenBalance();
        Boolean enoughTokens = tokenBalance != null && tokenBalance >= totalTokens;

        return new NegotiationProposalDtoOut(requestId, negotiationId, negotiation.getProposedDate(), baseTokens, urgentTokens, weekendTokens, totalTokens, tokenBalance, enoughTokens);
    }

    @Transactional
    public void acceptProposal(Integer accountId, Integer negotiationId) {

        Account account = accountAccessService.requireActive(accountId);

        if (!Boolean.TRUE.equals(account.getEmailVerified())) {
            throw new ApiException("Please verify your email first");
        }

        Integer requestId = requestNegotiationRepository.findRequestIdByNegotiationId(negotiationId);

        if (requestId == null) {
            throw new ApiException("Negotiation response or learning request not found");
        }

        LearningRequest learningRequest = learningRequestRepository.findLearningRequestForUpdate(requestId);

        if (learningRequest == null) {
            throw new ApiException("Learning request not found");
        }

        RequestNegotiation negotiation = requestNegotiationRepository.findRequestNegotiationById(negotiationId);

        if (negotiation == null) {
            throw new ApiException("Negotiation response not found");
        }

        if (!requestId.equals(negotiation.getLearningRequestId())) {
            throw new ApiException("Negotiation does not belong to this learning request");
        }

        Account requester = learningRequest.getRequesterAccount();
        Account provider = learningRequest.getProviderAccount();

        if (requester == null || provider == null) {
            throw new ApiException("Learning request accounts not found");
        }

        if (!accountId.equals(requester.getId()) && !accountId.equals(provider.getId())) {
            throw new ApiException("You cannot accept a proposal for this request");
        }

        if (!"OPEN".equals(learningRequest.getStatus())) {
            throw new ApiException("Only open requests can accept a proposal");
        }

        if (learningRequest.getAcceptedNegotiation() != null) {
            throw new ApiException("A proposal has already been accepted");
        }

        if (learningRequest.getExchange() != null) {
            throw new ApiException("An exchange already exists for this request");
        }

        Integer senderId = negotiation.getSenderAccountId();

        if (!requester.getId().equals(senderId) && !provider.getId().equals(senderId)) {
            throw new ApiException("Proposal sender does not belong to this request");
        }

        if (accountId.equals(senderId)) {
            throw new ApiException("You cannot accept your own proposal");
        }

        if (negotiation.getProposedDate() == null) {
            throw new ApiException("This response does not contain a proposed date");
        }

        if (!negotiation.getProposedDate().isAfter(LocalDateTime.now())) {
            throw new ApiException("Proposed date must be in the future");
        }

        if (!"ACTIVE".equals(requester.getStatus()) || !"ACTIVE".equals(provider.getStatus())) {
            throw new ApiException("Both accounts must be active");
        }

        if (!Boolean.TRUE.equals(requester.getEmailVerified()) || !Boolean.TRUE.equals(provider.getEmailVerified())) {
            throw new ApiException("Both accounts must have verified emails");
        }

        Integer baseTokens = learningRequest.getBaseTokens();
        Integer urgentTokens = negotiation.getUrgentTokens();
        Integer weekendTokens = negotiation.getWeekendTokens();

        if (baseTokens == null || baseTokens <= 0 || urgentTokens == null || urgentTokens < 0 || weekendTokens == null || weekendTokens < 0) {
            throw new ApiException("Proposal token cost is invalid");
        }

        int totalTokens = baseTokens + urgentTokens + weekendTokens;

        if (requester.getTokenBalance() == null || requester.getTokenBalance() < totalTokens) {
            throw new ApiException("Requester does not have enough tokens");
        }

        learningRequest.setNeededBy(negotiation.getProposedDate());
        learningRequest.setUrgent(urgentTokens > 0);
        learningRequest.setUrgentTokens(urgentTokens);
        learningRequest.setWeekend(weekendTokens > 0);
        learningRequest.setWeekendTokens(weekendTokens);
        learningRequest.setAcceptedNegotiation(negotiation);

        learningRequestRepository.save(learningRequest);

        Account recipient = negotiation.getSenderAccount();

        String subject = "Your proposed date has been accepted";

        String text = "Hello " + accountNameHelper.getAccountName(recipient)
                + ",\n\n"
                + accountNameHelper.getAccountName(account)
                + " accepted your proposal for learning request #" + learningRequest.getId()
                + ".\n\nAgreed date: " + negotiation.getProposedDate()
                + "\nAgreed cost: " + totalTokens + " tokens";

        brevoEmailService.sendEmail(recipient.getEmail(), subject, text);

        // whatsapp
    }

}
