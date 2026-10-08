package com.codeit.careeros.linkedin;

import com.codeit.careeros.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * One student's LinkedIn presence. LinkedIn offers no supported API for
 * fetching personal profiles, and scraping violates its terms — so the
 * student links their public URL and imports the visible sections
 * themselves (headline, about, experience, skills, education). Only what
 * the analysis needs is stored; nothing is fetched automatically.
 */
@Entity
@Table(
        name = "linkedin_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_linkedin_profiles_user",
                columnNames = "user_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedInProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "profile_url", nullable = false, length = 500)
    private String profileUrl;

    @Column(length = 220)
    private String headline;

    @Column(columnDefinition = "TEXT")
    private String about;

    @Column(name = "role_title", length = 180)
    private String currentRole;

    @Column(name = "experience_text", columnDefinition = "TEXT")
    private String experienceText;

    @Column(name = "skills_text", columnDefinition = "TEXT")
    private String skillsText;

    @Column(name = "education_text", columnDefinition = "TEXT")
    private String educationText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
