package com.example.capstone_3.Service;

import com.example.capstone_3.Api.ApiException;
import com.example.capstone_3.Model.AccountSkill;
import com.example.capstone_3.Model.SkillAssessment;
import com.example.capstone_3.Repository.AccountSkillRepository;
import com.example.capstone_3.Repository.SkillAssessmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillAssessmentService {
    private final AccountAccessService accountAccessService;
    private final SkillAssessmentRepository skillAssessmentRepository;
    private final AccountSkillRepository accountSkillRepository;


    public List<SkillAssessment>getAllSkillAssessments(){

        return skillAssessmentRepository.findAll();

    }


    public void addSkillAssessment(Integer accountId, Integer accountSkillId, SkillAssessment skillAssessment) {
        accountAccessService.requireActive(accountId);
        AccountSkill accountSkill = accountSkillRepository.findAccountSkillById(accountSkillId);
        if (accountSkill == null) {
            throw new ApiException("Account skill not found");
        }
        if (!accountSkill.getAccount().getId().equals(accountId)) {
            throw new ApiException("You can only take assessments for your own skills");
        }
        skillAssessment.setId(null);
        skillAssessment.setAccountSkill(accountSkill);
        skillAssessment.setAttemptedAt(LocalDateTime.now());
        skillAssessment.setAssessedLevel(calculateLevel(skillAssessment.getScore()));
        skillAssessmentRepository.save(skillAssessment);

        // طيب صار السكور اعلى من ٧٠ معناته ناجح على طول يتفعل حساب الاكونت سكل
        if (skillAssessment.getScore() >= 70) {
            accountSkill.setLevel(skillAssessment.getAssessedLevel());
            accountSkill.setVerified(true);
            accountSkillRepository.save(accountSkill);
        }
    }


    private void requireOwnership(Integer accountId, AccountSkill accountSkill) {
        if (accountSkill.getAccount() == null || !accountId.equals(accountSkill.getAccount().getId())) {
            throw new ApiException("You can only view assessments for your own skills");
        }
    }

    private String calculateLevel(Integer score){
        if(score>=90){
            return "EXPERT";
        }else if (score >= 80){
            return "ADVANCED";
        }else if (score >= 70) {
            return "INTERMEDIATE";
        }
        else {
            return "BEGINNER";
        }
    }



    //endpoint 11 done
   public List<SkillAssessment>getAssessmentHistory(Integer accountId, Integer accountSkillId){
    accountAccessService.requireActive(accountId);
    AccountSkill accountSkill=accountSkillRepository.findAccountSkillById(accountSkillId);
       if (accountSkill == null) {
           throw new ApiException("Account skill not found");
       }

       requireOwnership(accountId, accountSkill);
       return skillAssessmentRepository.findAllByAccountSkillOrderByAttemptedAtDesc(accountSkill);



   }

   //endpoint 12 done
   public SkillAssessment getLatestAssessment(Integer accountId, Integer accountSkillId){
    accountAccessService.requireActive(accountId);
    AccountSkill accountSkill=accountSkillRepository.findAccountSkillById(accountSkillId);
       if (accountSkill == null) {
           throw new ApiException("Account skill not found");
       }
       requireOwnership(accountId, accountSkill);
       SkillAssessment latest=skillAssessmentRepository.findTopByAccountSkillOrderByAttemptedAtDesc(accountSkill);
        if(latest==null){
            throw new ApiException("No assessments found for this skill");
        }

        return latest;
   }












































}
