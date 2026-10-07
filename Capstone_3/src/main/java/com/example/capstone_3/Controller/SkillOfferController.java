package com.example.capstone_3.Controller;

import com.example.capstone_3.Api.ApiResponse;
import com.example.capstone_3.DtoIn.OfferEvaluationDtoIn;
import com.example.capstone_3.Model.SkillOffer;
import com.example.capstone_3.Service.AIService;
import com.example.capstone_3.Service.SkillOfferService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/skill-offer")
@RequiredArgsConstructor
public class SkillOfferController {

    private final SkillOfferService skillOfferService;
    private final AIService aiService;

    @GetMapping("/get")
    public ResponseEntity<?> getSkillOffer() {
        return ResponseEntity.status(200).body(skillOfferService.getSkillOffer());
    }

    // #26 Create skill offer (login required)
    @PostMapping("/create/{skillId}")
    public ResponseEntity<?> addOffer(HttpSession session, @PathVariable Integer skillId,
                                      @RequestBody @Valid SkillOffer skillOffer) {
        skillOfferService.addOffer((Integer) session.getAttribute("accountId"), skillId, skillOffer);
        return ResponseEntity.status(200).body(new ApiResponse("Skill offer added"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateSkillOffer(@PathVariable Integer id, @RequestBody @Valid SkillOffer skillOffer) {
        skillOfferService.updateSkillOffer(id, skillOffer);
        return ResponseEntity.status(200).body(new ApiResponse("Skill offer updated"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteSkillOffer(@PathVariable Integer id) {
        skillOfferService.deleteSkillOffer(id);
        return ResponseEntity.status(200).body(new ApiResponse("Skill offer deleted"));
    }

    @GetMapping("/skill/{skillId}")
    public ResponseEntity<?> getOffersBySkill(@PathVariable Integer skillId) {
        return ResponseEntity.status(200).body(skillOfferService.getOffersBySkill(skillId));
    }


    @GetMapping("/provider/{providerId}")
    public ResponseEntity<?> getOffersCreatedByProvider(@PathVariable Integer providerId) {
        return ResponseEntity.status(200).body(skillOfferService.getOffersCreatedByProvider(providerId));
    }

    @GetMapping("/available")
    public ResponseEntity<?> getActiveOffers() {
        return ResponseEntity.status(200).body(skillOfferService.getActiveOffers());
    }

    @PostMapping("/create/{skillId}/evaluate")
    public ResponseEntity<?> evaluateOffer(HttpSession session, @PathVariable Integer skillId, @RequestBody @Valid OfferEvaluationDtoIn dto) {
        return ResponseEntity.status(200).body(aiService.evaluateOffer((Integer) session.getAttribute("accountId"), skillId, dto));
    }
}