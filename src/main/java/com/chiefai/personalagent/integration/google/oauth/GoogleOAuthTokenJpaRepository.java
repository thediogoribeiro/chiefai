package com.chiefai.personalagent.integration.google.oauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GoogleOAuthTokenJpaRepository extends JpaRepository<GoogleOAuthTokenEntity, UUID> {
    Optional<GoogleOAuthTokenEntity> findByProviderAccount(String providerAccount);
}
