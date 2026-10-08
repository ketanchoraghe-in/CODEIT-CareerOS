package com.codeit.careeros.career;

import com.codeit.careeros.common.enums.SkillLevel;
import com.codeit.careeros.skill.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * Competency framework row: how much a skill matters for a career
 * (weight), the level expected and the target competency percent
 * (docs sections 12 and 67).
 */
@Entity
@Table(
        name = "career_skills",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_career_skills_career_skill",
                columnNames = {"career_id", "skill_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_id", nullable = false)
    private Career career;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "weight_percent", nullable = false)
    private int weightPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_level", nullable = false, length = 20)
    private SkillLevel requiredLevel;

    @Column(name = "target_percent", nullable = false)
    private int targetPercent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
