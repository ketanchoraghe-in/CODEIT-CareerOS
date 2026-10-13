package com.codeit.careeros.service;

import com.codeit.careeros.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

/**
 * Verifies Google Sign-In ID tokens server-side via Google's tokeninfo
 * endpoint (Google checks the signature, expiry and audience for us).
 * The {@code aud} claim must equal our OAuth Web client ID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleTokenVerifier {

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.google.client-id:}")
    private String googleClientId;

    public record VerifiedGoogleUser(String email, String fullName, String googleSub, String pictureUrl) {
    }

    public VerifiedGoogleUser verify(String idToken) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw BusinessException.serviceUnavailable(
                    "Google sign-in is not configured on the server (GOOGLE_CLIENT_ID missing)");
        }
        String url = UriComponentsBuilder
                .fromHttpUrl("https://oauth2.googleapis.com/tokeninfo")
                .queryParam("id_token", idToken)
                .toUriString();
        JsonNode info;
        try {
            String body = restTemplate.getForObject(url, String.class);
            info = objectMapper.readTree(body);
        } catch (RestClientException e) {
            log.warn("Google tokeninfo rejected the ID token: {}", e.getMessage());
            throw BusinessException.unauthorized("Invalid Google credential. Please try again.");
        } catch (Exception e) {
            log.warn("Failed to parse Google tokeninfo response", e);
            throw BusinessException.unauthorized("Could not verify Google credential. Please try again.");
        }

        String aud = info.path("aud").asText("");
        String azp = info.path("azp").asText("");
        if (!googleClientId.equals(aud) && !googleClientId.equals(azp)) {
            log.warn("Google ID token audience mismatch (aud={}, azp={})", aud, azp);
            throw BusinessException.unauthorized("Google credential was not issued for this app.");
        }

        String iss = info.path("iss").asText("");
        if (!"accounts.google.com".equals(iss) && !"https://accounts.google.com".equals(iss)) {
            throw BusinessException.unauthorized("Invalid Google credential issuer.");
        }

        long exp = info.path("exp").asLong(0);
        if (exp > 0 && Instant.now().getEpochSecond() > exp) {
            throw BusinessException.unauthorized("Google credential has expired. Please try again.");
        }

        String email = info.path("email").asText("").toLowerCase().trim();
        if (email.isBlank()) {
            throw BusinessException.unauthorized("Google account has no email address.");
        }
        if (!"true".equalsIgnoreCase(info.path("email_verified").asText(""))) {
            throw BusinessException.unauthorized("Google email address is not verified.");
        }

        String name = info.path("name").asText("").trim();
        if (name.isBlank()) {
            String given = info.path("given_name").asText("").trim();
            String family = info.path("family_name").asText("").trim();
            name = (given + " " + family).trim();
        }
        if (name.isBlank()) {
            name = email.substring(0, email.indexOf('@'));
        }
        String sub = info.path("sub").asText("").trim();
        String picture = info.path("picture").asText("").trim();
        return new VerifiedGoogleUser(email, name, sub, picture.isBlank() ? null : picture);
    }
}
