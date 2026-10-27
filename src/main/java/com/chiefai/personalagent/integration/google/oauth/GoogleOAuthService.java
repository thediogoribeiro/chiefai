package com.chiefai.personalagent.integration.google.oauth;

import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GoogleOAuthService {
    private final PersonalAgentProperties properties;
    private final GoogleOAuthTokenJpaRepository tokens;

    public GoogleOAuthService(PersonalAgentProperties properties, GoogleOAuthTokenJpaRepository tokens) {
        this.properties = properties;
        this.tokens = tokens;
    }

    public Map<String, String> authorizationUrl() {
        if (properties.google().clientId() == null || properties.google().clientId().isBlank()) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID must be configured before starting OAuth");
        }
        String url = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", properties.google().clientId())
                .queryParam("redirect_uri", properties.google().redirectUri())
                .queryParam("response_type", "code")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("scope", "https://www.googleapis.com/auth/gmail.modify https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/calendar.events")
                .build().toUriString();
        return Map.of("authorizationUrl", url);
    }

    @Transactional
    public Map<String, Object> complete(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Missing OAuth code");
        }
        String encrypted = encrypt("pending-token-exchange:" + code);
        var scopes = Jsonb.value(List.of("gmail.modify", "spreadsheets", "calendar.events"));
        tokens.findByProviderAccount("google")
                .ifPresentOrElse(token -> token.update(encrypted, scopes), () -> tokens.save(new GoogleOAuthTokenEntity("google", encrypted, scopes)));
        return Map.of("connected", true, "updatedAt", OffsetDateTime.now().toString());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        return tokens.findByProviderAccount("google")
                .map(token -> Map.<String, Object>of("connected", true, "updatedAt", token.getUpdatedAt()))
                .orElseGet(() -> Map.of("connected", false));
    }

    private String encrypt(String value) {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(properties.google().tokenEncryptionKey().getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt Google token", e);
        }
    }
}
