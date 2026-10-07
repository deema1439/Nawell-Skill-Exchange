package com.example.capstone_3.DtoOut;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OfferEvaluationDtoOut {

    private Integer skillId;
    private String skillName;
    private Integer proposedTokens;

    // FAIR, OVERPRICED, UNDERPRICED, INSUFFICIENT_INFORMATION
    private String verdict;

    // Null when there is insufficient information
    private Integer suggestedTokens;

    private String explanation;
    private List<String> suggestions;
    private Boolean aiGenerated;
}