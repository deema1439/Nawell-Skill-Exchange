package com.example.capstone_3.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;
import java.util.Set;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Check(constraints = "mode IN ('ONLINE', 'IN_PERSON') AND status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "The session title can't be blank")
    @Column(columnDefinition = "VARCHAR(150) not null")
    private String title;

    @NotNull(message = "The scheduled date can't be null")
    @Column(columnDefinition = "DATETIME not null")
    private LocalDateTime scheduledAt;

    @NotNull(message = "The duration can't be null")
    @Min(value = 1, message = "The duration must be at least one minute")
    @Column(columnDefinition = "INT not null")
    private Integer durationMinutes;

    @NotBlank(message = "The session mode can't be blank")
    @Pattern(regexp = "^(ONLINE|IN_PERSON)$", message = "The session mode must be ONLINE or IN_PERSON")
    @Column(columnDefinition = "VARCHAR(20) not null")
    private String mode;

    @Column(length = 2000)
    private String meetingLink;

    @JsonIgnore
    @Column(unique = true)
    private Long zoomMeetingId;

    private String location;

    @NotBlank(message = "The session status can't be blank")
    @Pattern(regexp = "^(SCHEDULED|COMPLETED|CANCELLED)$", message = "The session status must be SCHEDULED, COMPLETED, or CANCELLED")
    @Column(columnDefinition = "VARCHAR(20) not null DEFAULT 'SCHEDULED'")
    private String status = "SCHEDULED";

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean reminderSent = false;

    @NotNull(message = "The skill offer can't be null")
    @ManyToOne
    @JoinColumn(name = "skill_offer_id", nullable = false)
    @JsonIgnore
    private SkillOffer skillOffer;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "session")
    @JsonIgnore
    private Set<SessionParticipant> sessionParticipants;

}


