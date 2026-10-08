package com.codeit.careeros.dto.cv;

/**
 * One CV section check from the extracted text (same keyword signals as the
 * completeness checks, exposed per section so the UI can show score, status
 * and guidance without inventing information).
 */
public record CvSectionResponse(
        String key,
        String title,
        Boolean passed,
        String note) {
}
