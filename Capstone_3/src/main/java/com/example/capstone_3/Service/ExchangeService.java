package com.example.capstone_3.Service;

import com.example.capstone_3.Api.ApiException;
import com.example.capstone_3.DtoIn.ExchangeDtoIn;
import com.example.capstone_3.DtoOut.ExchangeDtoOut;
import com.example.capstone_3.Model.*;
import com.example.capstone_3.Repository.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExchangeService {
    private final AccountAccessService accountAccessService;

    private final ExchangeRepository exchangeRepository;
    private final AccountRepository accountRepository;
    private final LearningRequestRepository learningRequestRepository;
    private final SkillOfferRepository skillOfferRepository;
    private final TokenTransactionRepository tokenTransactionRepository;
    private final AccountNameHelper accountNameHelper;
    private final BrevoEmailService brevoEmailService;
    private final WhatsAppService whatsAppService;

    public List<Exchange> get(){
        return exchangeRepository.findAll();
    }


    public void add(ExchangeDtoIn exchangeDtoIn){

        Exchange exchange = new Exchange();

        exchange.setTokenAmount(exchangeDtoIn.getTokenAmount());

        exchange.setStatus("PENDING");
        exchange.setCreatedAt(LocalDateTime.now());
        exchange.setCompletedAt(null);

        exchangeRepository.save(exchange);
    }


    public void update(Integer id, ExchangeDtoIn exchangeDtoIn){

        Exchange oldExchange = exchangeRepository.findExchangeById(id);

        if(oldExchange == null){
            throw new ApiException("No exchange found");
        }

        oldExchange.setTokenAmount(exchangeDtoIn.getTokenAmount());

        exchangeRepository.save(oldExchange);
    }


    public void delete(Integer id){

        Exchange oldExchange = exchangeRepository.findExchangeById(id);

        if(oldExchange == null){
            throw new ApiException("No exchange found");
        }

        exchangeRepository.delete(oldExchange);
    }

    @Transactional
    public void createExchange(Integer accountId, Integer requestId, Integer offerId) {

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

        if (!accountId.equals(requester.getId())) {
            throw new ApiException("Only the requester can create this exchange");
        }

        if (!"OPEN".equals(learningRequest.getStatus())) {
            throw new ApiException("Only open requests can create an exchange");
        }

        if (learningRequest.getExchange() != null) {
            throw new ApiException("An exchange already exists for this request");
        }

        RequestNegotiation acceptedProposal = learningRequest.getAcceptedNegotiation();

        if (acceptedProposal == null) {
            throw new ApiException("Please accept a proposed date first");
        }

        if (!requestId.equals(acceptedProposal.getLearningRequestId())) {
            throw new ApiException("Accepted proposal does not belong to this request");
        }

        LocalDateTime now = LocalDateTime.now();

        if (acceptedProposal.getProposedDate() == null || !acceptedProposal.getProposedDate().isAfter(now)) {
            throw new ApiException("Accepted date must be in the future");
        }

        SkillOffer offer = skillOfferRepository.findSkillOfferById(offerId);

        if (offer == null) {
            throw new ApiException("Skill offer not found");
        }

        if (learningRequest.getSkillOffer() == null || !offerId.equals(learningRequest.getSkillOffer().getId())) {
            throw new ApiException("Skill offer does not match this learning request");
        }

        if (!"ACTIVE".equals(offer.getStatus())) {
            throw new ApiException("Skill offer is not active");
        }

        if (offer.getProviderAccount() == null || !provider.getId().equals(offer.getProviderAccount().getId())) {
            throw new ApiException("Offer provider does not match this request");
        }

        if (offer.getSkill() == null || learningRequest.getSkill() == null || !offer.getSkill().getId().equals(learningRequest.getSkill().getId())) {
            throw new ApiException("Offer skill does not match this request");
        }

        if (!"ACTIVE".equals(provider.getStatus())) {
            throw new ApiException("Provider account is not active");
        }

        if (!Boolean.TRUE.equals(provider.getEmailVerified())) {
            throw new ApiException("Provider must verify their email first");
        }

        Integer baseTokens = learningRequest.getBaseTokens();
        Integer urgentTokens = acceptedProposal.getUrgentTokens();
        Integer weekendTokens = acceptedProposal.getWeekendTokens();

        if (baseTokens == null || baseTokens <= 0 || urgentTokens == null || urgentTokens < 0 || weekendTokens == null || weekendTokens < 0) {
            throw new ApiException("Accepted proposal token cost is invalid");
        }

        int totalTokens = baseTokens + urgentTokens + weekendTokens;

        if (requester.getTokenBalance() == null || requester.getTokenBalance() < totalTokens) {
            throw new ApiException("Not enough tokens");
        }

        Exchange exchange = new Exchange();

        exchange.setTokenAmount(totalTokens);
        exchange.setStatus("PENDING");
        exchange.setCreatedAt(now);
        exchange.setCompletedAt(null);
        exchange.setLearningRequest(learningRequest);
        exchange.setSkillOffer(offer);

        exchangeRepository.save(exchange);

        learningRequest.setExchange(exchange);
        learningRequest.setNeededBy(acceptedProposal.getProposedDate());
        learningRequest.setUrgent(urgentTokens > 0);
        learningRequest.setUrgentTokens(urgentTokens);
        learningRequest.setWeekend(weekendTokens > 0);
        learningRequest.setWeekendTokens(weekendTokens);
        learningRequest.setStatus("MATCHED");

        learningRequestRepository.save(learningRequest);

        String subject = "New exchange awaiting your acceptance";

        String text = "Hello " + accountNameHelper.getAccountName(provider)
                + ",\n\n"
                + accountNameHelper.getAccountName(requester)
                + " created a new exchange awaiting your acceptance."
                + "\n\nExchange ID: " + exchange.getId()
                + "\nSkill: " + learningRequest.getSkill().getName()
                + "\nAgreed date: " + learningRequest.getNeededBy()
                + "\nAgreed cost: " + exchange.getTokenAmount() + " tokens";

        brevoEmailService.sendEmail(provider.getEmail(), subject, text);

        try {
            String phone;

            if (provider.getIndividualProfile() != null) {
                phone = provider.getIndividualProfile().getPhone();
            } else {
                phone = provider.getCompanyProfile().getPhone();
            }

            whatsAppService.sendMessage(phone, text);
        } catch (Exception e) {
            System.out.println("WhatsApp message failed: " + e.getMessage());
        }
    }

    @Transactional
    public ExchangeDtoOut getExchangeDetails(Integer accountId, Integer exchangeId) {

        Account account = accountAccessService.requireActive(accountId);

        Exchange exchange = exchangeRepository.findExchangeById(exchangeId);

        if (exchange == null) {
            throw new ApiException("Exchange not found");
        }

        LearningRequest learningRequest = exchange.getLearningRequest();

        if (learningRequest == null) {
            throw new ApiException("Learning request not found");
        }

        Account requester = learningRequest.getRequesterAccount();
        Account provider = learningRequest.getProviderAccount();

        if (requester == null || provider == null) {
            throw new ApiException("Exchange accounts not found");
        }

        if (!accountId.equals(requester.getId()) && !accountId.equals(provider.getId())) {
            throw new ApiException("You cannot view this exchange");
        }

        if (learningRequest.getSkill() == null || exchange.getSkillOffer() == null) {
            throw new ApiException("Exchange skill or offer not found");
        }

        ExchangeDtoOut dtoOut = new ExchangeDtoOut();

        dtoOut.setId(exchange.getId());
        dtoOut.setRequestId(learningRequest.getId());
        dtoOut.setOfferId(exchange.getSkillOffer().getId());
        dtoOut.setRequesterAccountId(requester.getId());
        dtoOut.setRequesterName(accountNameHelper.getAccountName(requester));
        dtoOut.setProviderAccountId(provider.getId());
        dtoOut.setProviderName(accountNameHelper.getAccountName(provider));
        dtoOut.setSkillId(learningRequest.getSkill().getId());
        dtoOut.setSkillName(learningRequest.getSkill().getName());
        dtoOut.setDescription(learningRequest.getDescription());
        dtoOut.setMode(learningRequest.getMode());
        dtoOut.setBaseTokens(learningRequest.getBaseTokens());
        dtoOut.setUrgentTokens(learningRequest.getUrgentTokens());
        dtoOut.setWeekendTokens(learningRequest.getWeekendTokens());
        dtoOut.setTokenAmount(exchange.getTokenAmount());
        dtoOut.setStatus(exchange.getStatus());
        dtoOut.setAgreedDate(learningRequest.getNeededBy());
        dtoOut.setCreatedAt(exchange.getCreatedAt());
        dtoOut.setCompletedAt(exchange.getCompletedAt());

        return dtoOut;
    }


    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void acceptExchange(Integer accountId, Integer exchangeId) {

        Exchange exchange = getExchangeForAction(accountId, exchangeId);
        LearningRequest request = exchange.getLearningRequest();

        if (!accountId.equals(request.getProviderAccount().getId())) {
            throw new ApiException("Only the teacher can accept this exchange");
        }

        if (!"PENDING".equals(exchange.getStatus())) {
            throw new ApiException("Only pending exchanges can be accepted");
        }

        if (Boolean.TRUE.equals(exchange.getTokensReserved())) {
            throw new ApiException("Tokens have already been reserved");
        }

        if (!"MATCHED".equals(request.getStatus()) || request.getAcceptedNegotiation() == null) {
            throw new ApiException("Learning request does not have an accepted agreement");
        }

        if (request.getNeededBy() == null || !request.getNeededBy().isAfter(LocalDateTime.now())) {
            throw new ApiException("Agreed date must be in the future");
        }

        SkillOffer offer = skillOfferRepository.findSkillOfferForUpdate(exchange.getSkillOffer().getId());

        if (offer == null || !"ACTIVE".equals(offer.getStatus())) {
            throw new ApiException("Skill offer is not active");
        }

        if (offer.getCapacity() == null || offer.getCapacity() < 1) {
            throw new ApiException("Offer capacity is invalid");
        }

        long occupiedCapacity = exchangeRepository.countBySkillOffer_IdAndStatusIn(offer.getId(), List.of("ACCEPTED", "IN_PROGRESS", "DISPUTED"));

        if (occupiedCapacity >= offer.getCapacity()) {
            throw new ApiException("Skill offer has reached its capacity");
        }

        Account requester = accountRepository.findAccountForTokenUpdate(request.getRequesterAccount().getId());
        Account provider = request.getProviderAccount();

        if (requester == null || !"ACTIVE".equals(requester.getStatus()) || !"ACTIVE".equals(provider.getStatus())) {
            throw new ApiException("Both accounts must be active");
        }

        if (!Boolean.TRUE.equals(requester.getEmailVerified()) || !Boolean.TRUE.equals(provider.getEmailVerified())) {
            throw new ApiException("Both accounts must have verified emails");
        }

        Integer amount = exchange.getTokenAmount();

        if (amount == null || amount <= 0) {
            throw new ApiException("Exchange token amount is invalid");
        }

        if (accountRepository.deductTokens(requester.getId(), amount) != 1) {
            throw new ApiException("Unable to deduct tokens. Check requester balance, account status and email verification");
        }

        saveTokenTransaction(requester, exchange, -amount, "LEARNING", "Tokens reserved for exchange");

        exchange.setTokensReserved(true);
        exchange.setStatus("ACCEPTED");

        exchangeRepository.save(exchange);

        String subject = "Your exchange has been accepted";

        String text = "Hello " + accountNameHelper.getAccountName(requester)
                + ",\n\n"
                + accountNameHelper.getAccountName(provider)
                + " accepted your exchange."
                + "\n\nExchange ID: " + exchange.getId()
                + "\nSkill: " + request.getSkill().getName()
                + "\nAgreed date: " + request.getNeededBy()
                + "\nReserved tokens: " + amount
                + "\n\nYour tokens have been reserved for this exchange.";

        brevoEmailService.sendEmail(requester.getEmail(), subject, text);

        try {
            String phone;

            if (requester.getIndividualProfile() != null) {
                phone = requester.getIndividualProfile().getPhone();
            } else {
                phone = requester.getCompanyProfile().getPhone();
            }

            whatsAppService.sendMessage(phone, text);
        } catch (Exception e) {
            System.out.println("WhatsApp message failed: " + e.getMessage());
        }

    }

    @Transactional
    public void cancelExchange(Integer accountId, Integer exchangeId) {

        Exchange exchange = getExchangeForAction(accountId, exchangeId);

        if (!"PENDING".equals(exchange.getStatus()) && !"ACCEPTED".equals(exchange.getStatus())) {
            throw new ApiException("Only pending or accepted exchanges can be cancelled");
        }

        int refundedTokens = Boolean.TRUE.equals(exchange.getTokensReserved()) ? exchange.getTokenAmount() : 0;


        if (Boolean.TRUE.equals(exchange.getTokensReserved())) {

            Account requester = accountRepository.findAccountForTokenUpdate(exchange.getLearningRequest().getRequesterAccount().getId());
            Integer amount = exchange.getTokenAmount();

            if (requester == null || requester.getTokenBalance() == null || amount == null || amount <= 0) {
                throw new ApiException("Cannot refund this exchange");
            }


            if (accountRepository.refundTokens(requester.getId(), amount) != 1) {
                throw new ApiException("Unable to refund tokens");
            }

            saveTokenTransaction(requester, exchange, amount, "REFUND", "Refund for cancelled exchange");

            exchange.setTokensReserved(false);
            try {
                String phone;
                String name;
                if (requester.getIndividualProfile() != null) {
                    phone = requester.getIndividualProfile().getPhone();
                    name = requester.getIndividualProfile().getName();
                } else {
                    phone = requester.getCompanyProfile().getPhone();
                    name = requester.getCompanyProfile().getName();
                }

                whatsAppService.sendMessage(
                        phone,
                        "Hello " + name + " 👋\n\n" +
                                "Your exchange #" + exchange.getId() + " has been cancelled.\n" +
                                "✅ " + amount + " tokens have been refunded to your account.\n\n" +
                                "Your current balance: " + (requester.getTokenBalance() + amount) + " tokens 💰"
                );
            } catch (Exception e) {
                System.out.println("WhatsApp message failed: " + e.getMessage());
            }
        }

        exchange.setStatus("CANCELLED");
        exchangeRepository.save(exchange);

        LearningRequest request = exchange.getLearningRequest();
        request.setStatus("CANCELLED");
        learningRequestRepository.save(request);

        Account learner = request.getRequesterAccount();
        Account teacher = request.getProviderAccount();

        Account cancelledBy = accountId.equals(learner.getId()) ? learner : teacher;
        Account recipient = accountId.equals(learner.getId()) ? teacher : learner;

        String subject = "Your exchange has been cancelled";

        String text = "Hello " + accountNameHelper.getAccountName(recipient)
                + ",\n\n"
                + accountNameHelper.getAccountName(cancelledBy)
                + " cancelled exchange #" + exchange.getId() + ".";

        if (recipient.getId().equals(learner.getId())) {
            text += refundedTokens > 0
                    ? "\n\nRefunded tokens: " + refundedTokens
                    : "\n\nNo tokens were reserved, so no refund was needed.";
        }

        brevoEmailService.sendEmail(recipient.getEmail(), subject, text);

        try {
            String phone;

            if (recipient.getIndividualProfile() != null) {
                phone = recipient.getIndividualProfile().getPhone();
            } else {
                phone = recipient.getCompanyProfile().getPhone();
            }

            if (!recipient.getId().equals(learner.getId()) || refundedTokens == 0) {
                whatsAppService.sendMessage(phone, text);
            }

        } catch (Exception e) {
            System.out.println("WhatsApp message failed: " + e.getMessage());
        }

        if (cancelledBy.getId().equals(learner.getId())) {
            String refundText = "Hello " + accountNameHelper.getAccountName(learner)
                    + ",\n\nYour exchange #" + exchange.getId() + " has been cancelled.";

            refundText += refundedTokens > 0
                    ? "\n\nRefunded tokens: " + refundedTokens
                    : "\n\nNo tokens were reserved, so no refund was needed.";

            brevoEmailService.sendEmail(learner.getEmail(), "Exchange cancellation confirmation", refundText);
        }

    }

    private Exchange getExchangeForAction(Integer accountId, Integer exchangeId) {

        Account account = accountAccessService.requireActive(accountId);

        Exchange exchange = exchangeRepository.findExchangeForUpdate(exchangeId);

        if (exchange == null) {
            throw new ApiException("Exchange not found");
        }

        LearningRequest request = exchange.getLearningRequest();

        if (request == null || request.getRequesterAccount() == null || request.getProviderAccount() == null || exchange.getSkillOffer() == null) {
            throw new ApiException("Exchange relationships not found");
        }

        if (!accountId.equals(request.getRequesterAccount().getId()) && !accountId.equals(request.getProviderAccount().getId())) {
            throw new ApiException("You cannot manage this exchange");
        }

        return exchange;
    }

    private void saveTokenTransaction(Account account, Exchange exchange, Integer amount, String type, String description) {

        TokenTransaction transaction = new TokenTransaction();

        transaction.setAccount(account);
        transaction.setExchange(exchange);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setDescription(description);
        transaction.setCreatedAt(LocalDateTime.now());

        tokenTransactionRepository.save(transaction);
    }

    @Transactional
    public List<ExchangeDtoOut> getAccountExchanges(Integer accountId) {

        Account account = accountAccessService.requireActive(accountId);

        List<Exchange> exchanges = exchangeRepository.findExchangesRelatedToAccount(accountId);
        List<ExchangeDtoOut> exchangesDtoOut = new ArrayList<>();

        for (Exchange exchange : exchanges) {
            exchangesDtoOut.add(getExchangeDetails(accountId, exchange.getId()));
        }

        return exchangesDtoOut;
    }




    @Transactional
    public void completeExchange(Integer accountId, Integer exchangeId) {
        Exchange exchange = getExchangeForAction(accountId, exchangeId);
        if (!accountId.equals(exchange.getLearningRequest().getRequesterAccount().getId())) {
            throw new ApiException("Only the learner can confirm exchange completion");
        }
        if (!"ACCEPTED".equals(exchange.getStatus()) && !"IN_PROGRESS".equals(exchange.getStatus())) {
            throw new ApiException("Only accepted or in-progress exchanges can be completed");
        }
        if (!Boolean.TRUE.equals(exchange.getTokensReserved())) {
            throw new ApiException("Exchange has no reserved tokens");
        }
        Integer amount = exchange.getTokenAmount();
        if (amount == null || amount <= 0) {
            throw new ApiException("Exchange token amount is invalid");
        }
        LearningRequest request = exchange.getLearningRequest();
        Account provider = accountRepository.findAccountForTokenUpdate(request.getProviderAccount().getId());
        if (provider == null || provider.getTokenBalance() == null) {
            throw new ApiException("Provider account cannot receive tokens");
        }
        if (accountRepository.refundTokens(provider.getId(), amount) != 1) {
            throw new ApiException("Unable to credit provider tokens");
        }
        saveTokenTransaction(provider, exchange, amount, "TEACHING", "Tokens earned for completed exchange");
        exchange.setTokensReserved(false);

        exchange.setStatus("COMPLETED");
        exchange.setCompletedAt(LocalDateTime.now());

        exchangeRepository.save(exchange);
        request.setStatus("CLOSED");
        learningRequestRepository.save(request);

        String learnerEmail = request.getRequesterAccount().getEmail();
        String providerEmail = request.getProviderAccount().getEmail();

        brevoEmailService.sendSessionEmail(
                learnerEmail,
                "Exchange Completed - Write a Review",
                "Your exchange has been completed successfully."
                        + "\nPlease write a review for this exchange."
        );

        brevoEmailService.sendSessionEmail(
                providerEmail,
                "Exchange Completed - Write a Review",
                "Your exchange has been completed successfully."
                        + "\nPlease write a review for this exchange."
        );
    }

}
