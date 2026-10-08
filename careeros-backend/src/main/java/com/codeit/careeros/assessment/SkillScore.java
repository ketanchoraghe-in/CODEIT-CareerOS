package com.codeit.careeros.assessment;

import com.codeit.careeros.common.enums.ResultLevel;
import com.codeit.careeros.skill.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Skill-wise result snapshot captured when an attempt is submitted. */
@Entity
@Table(
        name = "skill_scores",
        indexes = @Index(name = "idx_skill_scores_attempt", columnList = "attempt_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private AssessmentAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "score_percent", nullable = false)
    private int scorePercent;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    /** Importance of the skill for the career, snapshotted at submit time. */
    @Column(name = "weight_percent", nullable = false)
    private int weightPercent;

    /** Configured target competency percent, snapshotted at submit time. */
    @Column(name = "target_percent", nullable = false)
    private int targetPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResultLevel level;
}
