package com.codeit.careeros.cv;

import com.codeit.careeros.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

/**
 * AWS S3 implementation of {@link CvStorage}. Objects are private to the
 * bucket (no public URLs); downloads stream through the backend so student
 * ownership is enforced on every access. Keys are server-generated.
 */
@Slf4j
public class S3CvStorage implements CvStorage {

    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public S3CvStorage(S3Client s3, String bucket, String prefix) {
        this.s3 = s3;
        this.bucket = bucket;
        this.prefix = prefix == null || prefix.isBlank() ? "cvs" : prefix.replaceAll("^/+|/+$", "");
    }

    @Override
    public String store(String suggestedName, String contentType, byte[] bytes) {
        String key = prefix + "/" + UUID.randomUUID() + extensionOf(suggestedName);
        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) bytes.length)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (RuntimeException e) {
            throw BusinessException.conflict("Could not store the CV file, please try again");
        }
        log.info("Stored CV ({} bytes) in S3 {}/{}", bytes.length, bucket, key);
        return key;
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(storageKey)
                            .build())
                    .asByteArray();
        } catch (NoSuchKeyException e) {
            throw BusinessException.notFound("CV file not found in storage");
        } catch (RuntimeException e) {
            throw BusinessException.conflict("Could not read the CV file, please try again");
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .build());
        } catch (RuntimeException e) {
            log.warn("Could not delete replaced CV object {}/{}", bucket, storageKey, e);
        }
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
