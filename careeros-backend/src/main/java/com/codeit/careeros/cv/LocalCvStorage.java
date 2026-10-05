package com.codeit.careeros.cv;

import com.codeit.careeros.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Filesystem implementation of {@link CvStorage} for development, tests and
 * any environment without AWS credentials. Keys are server-generated
 * (UUID + sanitized extension), so callers can never traverse directories.
 */
@Slf4j
public class LocalCvStorage implements CvStorage {

    private final Path baseDir;

    public LocalCvStorage(String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create CV storage directory: " + this.baseDir, e);
        }
    }

    @Override
    public String store(String suggestedName, String contentType, byte[] bytes) {
        String key = UUID.randomUUID() + extensionOf(suggestedName);
        try {
            Files.write(resolve(key), bytes);
        } catch (IOException e) {
            throw BusinessException.conflict("Could not store the CV file, please try again");
        }
        log.info("Stored CV ({} bytes) under key {}", bytes.length, key);
        return key;
    }

    @Override
    public byte[] load(String storageKey) {
        Path file = resolve(storageKey);
        if (!Files.isRegularFile(file)) {
            throw BusinessException.notFound("CV file not found in storage");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw BusinessException.conflict("Could not read the CV file, please try again");
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            log.warn("Could not delete replaced CV object {}", storageKey, e);
        }
    }

    private Path resolve(String storageKey) {
        String safe = Paths.get(storageKey).getFileName().toString();
        return baseDir.resolve(safe).normalize();
    }

    private static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{2,5}") ? ext : "";
    }
}
