package com.codeit.careeros.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * The frozen question set of one assessment attempt.
 *
 * <p>Since Sprint 9 the engine no longer serves the whole question bank on
 * every attempt. When an attempt is created a randomized subset is selected
 * (weighted by the career competency framework, preferring questions the
 * student has not seen before, balanced across difficulties) and stored here
 * in presentation order. Resumes, refreshes and grading all read this frozen
 * set, so an attempt is stable while different attempts vary. Rows are
 * history: they are never updated or deleted except together with the
 * attempt itself.
 */
@Entity
@Table(
        name = "assessment_attempt_questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attempt_questions_attempt_question",
                columnNames = {"attempt_id", "question_id"}),
        indexes = {
                @Index(name = "idx_attempt_questions_attempt", columnList = "attempt_id"),
                @Index(name = "idx_attempt_questions_question", columnList = "question_id")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentAttemptQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AssessmentAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private AssessmentQuestion question;

    /** Position of the question inside this attempt (0-based, shuffled). */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
