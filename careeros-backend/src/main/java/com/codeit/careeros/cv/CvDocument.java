package com.codeit.careeros.cv;

import com.codeit.careeros.common.enums.CvStatus;
import com.codeit.careeros.entity.User;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * One student's current CV. The file itself lives in object storage
 * (S3 or the configured local store); only the storage key plus metadata
 * and the extracted contact basics are kept in MySQL — never the file bytes
 * and never more personal data than the analysis needs.
 */
@Entity
@Table(
        name = "cv_documents",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cv_documents_user",
                columnNames = "user_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "storage_key", nullable = false, length = 400)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CvStatus status = CvStatus.UPLOADED;

    @Column(name = "parse_error", length = 500)
    private String parseError;

    @Column(name = "candidate_name", length = 120)
    private String candidateName;

    @Column(name = "candidate_email", length = 190)
    private String candidateEmail;

    @Column(name = "candidate_phone", length = 40)
    private String candidatePhone;

    // LONGTEXT (not @Lob-default): Hibernate maps @Lob String to TINYTEXT
    // (255 bytes) on MySQL, which truncates every real CV. LONGTEXT is
    // accepted by H2 in MySQL compatibility mode as well.
    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    private String extractedText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
