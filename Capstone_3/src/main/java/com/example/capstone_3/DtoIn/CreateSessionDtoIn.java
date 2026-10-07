package com.example.capstone_3.DtoIn;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateSessionDtoIn {

    @NotNull(message = "Negotiation ID is required")
    @Positive(message = "Negotiation ID must be positive")
    private Integer negotiationId;

    @NotNull(message = "Start time is required")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least one minute")
    private Integer durationMinutes;

    @Pattern(regexp = "ONLINE|IN_PERSON", message = "Mode must be ONLINE or IN_PERSON")
    private String mode;

    @Size(max = 255, message = "Location must be at most 255 characters")
    private String location;
}