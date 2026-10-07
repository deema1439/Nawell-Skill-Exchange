package com.example.capstone_3.DtoIn;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OfferEvaluationDtoIn {

    @NotBlank(message = "Description is required for evaluation")
    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    @NotBlank(message = "Mode is required")
    @Pattern(regexp = "^(ONLINE|IN_PERSON|BOTH)$",
            message = "Mode must be ONLINE, IN_PERSON or BOTH")
    private String mode;

    @NotNull(message = "Token cost is required")
    @Positive(message = "Token cost must be positive")
    private Integer tokenCost;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;
}