package com.codeit.careeros.cv;

/**
 * Object storage for CV files. Only metadata lives in MySQL; bytes go here.
 * The active implementation is chosen by {@code app.cv.storage}
 * ({@code s3} for AWS S3, {@code local} for environments without AWS).
 */
public interface CvStorage {

    /** Stores bytes under a server-generated key and returns that key. */
    String store(String suggestedName, String contentType, byte[] bytes);

    /** Loads the bytes for a previously stored key. */
    byte[] load(String storageKey);

    /** Deletes a previously stored key; best-effort, never throws for missing keys. */
    void delete(String storageKey);
}
