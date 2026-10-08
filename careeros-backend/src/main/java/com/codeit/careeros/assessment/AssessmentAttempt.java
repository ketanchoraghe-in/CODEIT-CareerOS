package com.codeit.careeros.assessment;

import com.codeit.careeros.common.enums.AttemptStatus;
import com.codeit.careeros.entity.User;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One take of an assessment by a student. History is kept: every retake
 * creates a new attempt rather than overwriting previous results.
 */
@Entity
@Table(
        name = "assessment_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uk_attempts_code", columnNames = "attempt_code"),
        indexes = {
                @Index(name = "idx_attempts_user", columnList = "user_id"),
                @Index(name = "idx_attempts_assessment", columnList = "assessment_id")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_code", nullable = false, length = 40)
    private String attemptCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private AssessmentTest assessment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /**
     * Snapshot of the submission deadline: startedAt plus the assessment's
     * configured duration at the moment the attempt was created. The server
     * enforces this; the frontend timer is only a visual aid.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "answered_count", nullable = false)
    @Builder.Default
    private int answeredCount = 0;

    @Column(name = "overall_score")
    private Integer overallScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
