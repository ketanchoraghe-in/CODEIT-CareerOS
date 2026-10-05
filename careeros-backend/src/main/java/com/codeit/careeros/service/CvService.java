package com.codeit.careeros.service;

import com.codeit.careeros.common.enums.CvStatus;
import com.codeit.careeros.cv.CvDocument;
import com.codeit.careeros.cv.CvStorage;
import com.codeit.careeros.dto.cv.CvDocumentResponse;
import com.codeit.careeros.entity.User;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.repository.CvDocumentRepository;
import com.codeit.careeros.repository.UserRepository;
import com.codeit.careeros.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 5 CV upload & management. Files go to object storage (S3 or the
 * configured store); MySQL keeps one row per student with the storage key
 * and extracted basics. Every read is scoped to the authenticated student,
 * so nobody can ever see another student's CV.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvService {

    /** Reasonable CV size: well above real CVs, far below abuse levels. */
    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private static final Map<String, String> EXTENSION_TO_CONTENT_TYPE = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream");

    private final CvDocumentRepository cvDocumentRepository;
    private final UserRepository userRepository;
    private final CvStorage cvStorage;
    private final CvParsingService parsingService;

    /** Uploads (or replaces) the current student's CV: stores, parses, records metadata. */
    @Transactional
    public CvDocumentResponse upload(MultipartFile file) {
        Long userId = SecurityUtils.currentUserId();
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Please choose a CV file to upload");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest("CV file must be at most 5 MB");
        }
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = extensionOf(originalFilename);
        if (!EXTENSION_TO_CONTENT_TYPE.containsKey(extension)) {
            throw BusinessException.badRequest("Only PDF, DOC or DOCX files are accepted");
        }
        String declaredType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!declaredType.isBlank() && !ALLOWED_CONTENT_TYPES.contains(declaredType)) {
            throw BusinessException.badRequest("Only PDF, DOC or DOCX files are accepted");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw BusinessException.badRequest("Could not read the uploaded file");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found: " + userId));
        String contentType = EXTENSION_TO_CONTENT_TYPE.get(extension);
        String newKey = cvStorage.store(originalFilename, contentType, bytes);

        CvDocument document = cvDocumentRepository.findByUserId(userId)
                .orElseGet(() -> CvDocument.builder().user(user).build());
        String oldKey = document.getStorageKey();
        document.setStorageKey(newKey);
        document.setOriginalFilename(originalFilename);
        document.setContentType(contentType);
        document.setFileSize((long) bytes.length);

        try {
            CvParsingService.ParsedCv parsed = parsingService.parse(bytes, originalFilename);
            document.setStatus(CvStatus.PARSED);
            document.setParseError(null);
            document.setCandidateName(parsed.name());
            document.setCandidateEmail(parsed.email());
            document.setCandidatePhone(parsed.phone());
            document.setExtractedText(parsed.text());
        } catch (BusinessException e) {
            document.setStatus(CvStatus.FAILED);
            document.setParseError(e.getMessage());
            document.setCandidateName(null);
            document.setCandidateEmail(null);
            document.setCandidatePhone(null);
            document.setExtractedText(null);
            document = cvDocumentRepository.save(document);
            if (oldKey == null || !oldKey.equals(newKey)) {
                cvStorage.delete(oldKey);
            }
            log.warn("CV upload stored but not parsed for student {}: {}", userId, e.getMessage());
            throw e;
        }

        document = cvDocumentRepository.save(document);
        if (oldKey != null && !oldKey.equals(newKey)) {
            cvStorage.delete(oldKey);
        }
        log.info("Student {} uploaded CV {} ({} bytes, {})", userId, originalFilename, bytes.length, document.getStatus());
        return toResponse(document);
    }

    /** Current student's CV metadata, or null when none was uploaded yet. */
    @Transactional(readOnly = true)
    public CvDocumentResponse myDocument() {
        return cvDocumentRepository.findByUserId(SecurityUtils.currentUserId())
                .map(CvService::toResponse)
                .orElse(null);
    }

    /** File bytes of the current student's own CV. */
    @Transactional(readOnly = true)
    public CvDownload download() {
        CvDocument document = cvDocumentRepository.findByUserId(SecurityUtils.currentUserId())
                .orElseThrow(() -> BusinessException.notFound("No CV uploaded yet"));
        return new CvDownload(document.getOriginalFilename(), document.getContentType(),
                cvStorage.load(document.getStorageKey()));
    }

    public record CvDownload(String filename, String contentType, byte[] bytes) {
    }

    static CvDocumentResponse toResponse(CvDocument document) {
        return new CvDocumentResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSize(),
                document.getStatus().name(),
                document.getParseError(),
                document.getCandidateName(),
                document.getCandidateEmail(),
                document.getCandidatePhone(),
                document.getUpdatedAt());
    }

    private static String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            return "cv.pdf";
        }
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        name = name.replaceAll("[^A-Za-z0-9._()\\- ]", "_");
        if (name.isBlank() || name.length() > 200) {
            return "cv.pdf";
        }
        return name;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
