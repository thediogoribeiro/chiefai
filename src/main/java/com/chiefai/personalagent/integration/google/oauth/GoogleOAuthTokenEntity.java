package com.chiefai.personalagent.integration.google.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "google_oauth_token")
public class GoogleOAuthTokenEntity {
    @Id
    private UUID id;
    @Column(name = "provider_account", nullable = false, unique = true)
    private String providerAccount;
    @Column(name = "encrypted_refresh_token", nullable = false)
    private String encryptedRefreshToken;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode scopes;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Version
    private long version;

    public GoogleOAuthTokenEntity() {
    }

    public GoogleOAuthTokenEntity(String providerAccount, String encryptedRefreshToken, JsonNode scopes) {
        OffsetDateTime now = OffsetDateTime.now();
        this.id = UUID.randomUUID();
        this.providerAccount = providerAccount;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.scopes = scopes;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String encryptedRefreshToken, JsonNode scopes) {
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.scopes = scopes;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getProviderAccount() { return providerAccount; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
