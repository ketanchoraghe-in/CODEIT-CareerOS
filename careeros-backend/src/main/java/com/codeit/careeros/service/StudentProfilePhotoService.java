package com.codeit.careeros.service;

import com.codeit.careeros.dto.student.StudentProfileResponse;
import com.codeit.careeros.entity.StudentProfile;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.mapper.StudentProfileMapper;
import com.codeit.careeros.repository.StudentProfileRepository;
import com.codeit.careeros.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Student profile photo upload & serving. Files live on the local filesystem
 * (server-generated keys, no path traversal); MySQL keeps only the storage
 * key inside {@code student_profiles.profile_photo_url}. Every read/write is
 * scoped to the authenticated student.
 */
@Slf4j
@Service
public class StudentProfilePhotoService {

    /** Reasonable photo size: large enough for camera photos, small enough to stop abuse. */
    public static final long MAX_FILE_SIZE = 2L * 1024 * 1024;

    private static final Map<String, String> EXTENSION_TO_CONTENT_TYPE = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp");

    private final StudentProfileRepository studentProfileRepository;
    private final Path baseDir;

    public StudentProfilePhotoService(
            StudentProfileRepository studentProfileRepository,
            @Value("${app.profile-photo.local-dir:${java.io.tmpdir}/careeros-profile-photos}") String baseDir) {
        this.studentProfileRepository = studentProfileRepository;
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create profile photo directory: " + this.baseDir, e);
        }
    }

    /** Uploads (or replaces) the current student's profile photo. */
    @Transactional
    public StudentProfileResponse upload(MultipartFile file) {
        Long userId = SecurityUtils.currentUserId();
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Please choose a photo to upload");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest("Photo must be at most 2 MB");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!EXTENSION_TO_CONTENT_TYPE.containsKey(extension)) {
            throw BusinessException.badRequest("Only JPG, PNG or WebP photos are accepted");
        }
        String declaredType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!declaredType.isBlank() && !ALLOWED_CONTENT_TYPES.contains(declaredType)) {
            throw BusinessException.badRequest("Only JPG, PNG or WebP photos are accepted");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw BusinessException.badRequest("Could not read the uploaded photo");
        }

        StudentProfile profile = studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> BusinessException.notFound("Student profile not found for user " + userId));
        String newKey = UUID.randomUUID() + "." + extension;
        try {
            Files.write(resolve(newKey), bytes);
        } catch (IOException e) {
            throw BusinessException.conflict("Could not store the photo, please try again");
        }
        String oldKey = profile.getProfilePhotoUrl();
        profile.setProfilePhotoUrl(newKey);
        profile = studentProfileRepository.save(profile);
        deleteFileQuietly(oldKey, newKey);
        log.info("Student {} uploaded profile photo {} ({} bytes)", userId, newKey, bytes.length);
        return StudentProfileMapper.toResponse(profile);
    }

    /** Photo bytes of the current student. */
    @Transactional(readOnly = true)
    public PhotoDownload download() {
        StudentProfile profile = studentProfileRepository.findByUserId(SecurityUtils.currentUserId())
                .orElseThrow(() -> BusinessException.notFound("Student profile not found"));
        String key = profile.getProfilePhotoUrl();
        if (key == null || key.isBlank()) {
            throw BusinessException.notFound("No profile photo uploaded yet");
        }
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            throw BusinessException.notFound("Profile photo file not found in storage");
        }
        try {
            String contentType = EXTENSION_TO_CONTENT_TYPE.getOrDefault(extensionOf(key), "image/jpeg");
            return new PhotoDownload("profile-photo." + extensionOf(key), contentType, Files.readAllBytes(file));
        } catch (IOException e) {
            throw BusinessException.conflict("Could not read the profile photo, please try again");
        }
    }

    /** Removes the current student's profile photo. */
    @Transactional
    public StudentProfileResponse delete() {
        Long userId = SecurityUtils.currentUserId();
        StudentProfile profile = studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> BusinessException.notFound("Student profile not found for user " + userId));
        String oldKey = profile.getProfilePhotoUrl();
        if (oldKey == null || oldKey.isBlank()) {
            throw BusinessException.notFound("No profile photo uploaded yet");
        }
        profile.setProfilePhotoUrl(null);
        profile = studentProfileRepository.save(profile);
        deleteFileQuietly(oldKey, null);
        log.info("Student {} removed profile photo {}", userId, oldKey);
        return StudentProfileMapper.toResponse(profile);
    }

    public record PhotoDownload(String filename, String contentType, byte[] bytes) {
    }

    private Path resolve(String storageKey) {
        String safe = Paths.get(storageKey).getFileName().toString();
        return baseDir.resolve(safe).normalize();
    }

    private void deleteFileQuietly(String key, String keepKey) {
        if (key == null || key.isBlank() || key.equals(keepKey)) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete replaced profile photo {}", key, e);
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
