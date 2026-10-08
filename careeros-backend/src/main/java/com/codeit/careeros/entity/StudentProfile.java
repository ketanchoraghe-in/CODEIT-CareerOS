package com.codeit.careeros.entity;

import com.codeit.careeros.career.Career;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "student_id", nullable = false, unique = true, length = 40)
    private String studentId;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(length = 20)
    private String mobile;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 150)
    private String college;

    @Column(length = 100)
    private String degree;

    @Column(length = 100)
    private String branch;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    /**
     * Sprint 8: optional — the profile API and UI both treat semester as
     * unsettable, and registration seeds it with 1. It was previously
     * {@code nullable = false}, which turned "no semester chosen" into an
     * SQL 500 on save. Production migration (safe, no data loss):
     * {@code ALTER TABLE student_profiles MODIFY COLUMN semester INT NULL;}
     */
    @Column
    private Integer semester;

    @Column(length = 150)
    private String location;

    @Column(name = "github_url", length = 250)
    private String githubUrl;

    @Column(name = "linkedin_url", length = 250)
    private String linkedinUrl;

    @Column(name = "portfolio_url", length = 250)
    private String portfolioUrl;

    @Column(name = "profile_photo_url", length = 300)
    private String profilePhotoUrl;

    /**
     * Sprint 2: the career the student is targeting. Nullable until the
     * student completes career selection (PUT /students/me/career).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_career_id")
    private Career targetCareer;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}