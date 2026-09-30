package com.no8do.api.integration;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationAuthorizationUsageTouchService {
    private final IntegrationAuthorizationRepository repository;

    public IntegrationAuthorizationUsageTouchService(IntegrationAuthorizationRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touch(UUID authorizationId, Instant now) {
        repository.touchLastUsedAtIfDue(authorizationId, now, now.minusSeconds(15 * 60L));
    }
}
