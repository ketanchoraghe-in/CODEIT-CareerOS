package com.codeit.careeros.dto.cv;

import java.time.Instant;

/** Current CV of the student: metadata plus extracted contact basics. */
public record CvDocumentResponse(
        Long documentId,
        String originalFilename,
        String contentType,
        Long fileSize,
        String status,
        String parseError,
        String candidateName,
        String candidateEmail,
        String candidatePhone,
        Instant uploadedAt) {
}
