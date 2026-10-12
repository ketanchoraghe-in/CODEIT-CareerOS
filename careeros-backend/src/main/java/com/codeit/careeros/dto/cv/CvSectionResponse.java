package com.codeit.careeros.dto.cv;

/**
 * One CV section check from the extracted text (same keyword signals as the
 * completeness checks, exposed per section so the UI can show score, status
 * and guidance without inventing information).
 *
 * <p>{@code evidence} is the verbatim CV snippet that justified the verdict
 * (heading line or content match, truncated) — empty when nothing was found.
 * It lets the UI show <em>why</em> a section passed or failed instead of
 * asking the user to trust a bare boolean.
 */
public record CvSectionResponse(
        String key,
        String title,
        Boolean passed,
        String note,
        String evidence) {
    public CvSectionResponse(String key, String title, Boolean passed, String note) {
        this(key, title, passed, note, "");
    }
}
