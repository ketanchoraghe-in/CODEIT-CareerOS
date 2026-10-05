package com.codeit.careeros.config;

import com.codeit.careeros.cv.CvStorage;
import com.codeit.careeros.cv.LocalCvStorage;
import com.codeit.careeros.cv.S3CvStorage;
import com.codeit.careeros.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Selects the CV object storage. {@code app.cv.storage=s3} uses AWS S3
 * (credentials via explicit keys or the default provider chain, e.g. IAM
 * role); anything else uses the local filesystem store, which is the safe
 * default for development and tests.
 */
@Configuration
public class CvStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.cv.storage", havingValue = "s3")
    public CvStorage s3CvStorage(
            @Value("${app.cv.s3.bucket}") String bucket,
            @Value("${app.cv.s3.region:${AWS_REGION:us-east-1}}") String region,
            @Value("${app.cv.s3.prefix:cvs}") String prefix,
            @Value("${app.cv.s3.access-key:}") String accessKey,
            @Value("${app.cv.s3.secret-key:}") String secretKey,
            @Value("${app.cv.s3.endpoint:}") String endpoint) {
        if (bucket == null || bucket.isBlank()) {
            throw BusinessException.conflict("CV storage is set to S3 but app.cv.s3.bucket is not configured");
        }
        software.amazon.awssdk.services.s3.S3ClientBuilder builder =
                S3Client.builder().region(Region.of(region));
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
            builder.forcePathStyle(true);
        }
        return new S3CvStorage(builder.build(), bucket, prefix);
    }

    @Bean
    @ConditionalOnProperty(name = "app.cv.storage", havingValue = "local", matchIfMissing = true)
    public CvStorage localCvStorage(
            @Value("${app.cv.local-dir:${java.io.tmpdir}/careeros-cv}") String baseDir) {
        return new LocalCvStorage(baseDir);
    }
}
