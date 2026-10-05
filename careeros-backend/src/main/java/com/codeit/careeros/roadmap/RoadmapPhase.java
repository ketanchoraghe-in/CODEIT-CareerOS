package com.codeit.careeros.roadmap;

import com.codeit.careeros.career.Career;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One phase of a career's roadmap (e.g. Foundation / Builder / Mastery).
 * Phases are database-driven per career and ordered by display order;
 * no career-specific roadmap structure is hardcoded anywhere.
 */
@Entity
@Table(name = "roadmap_phases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapPhase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_id", nullable = false)
    private Career career;

    @Column(nullable = false, length = 140)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;
}
