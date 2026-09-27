package com.no8do.api.agent;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal verification core. It intentionally has no controller or public authentication endpoint. */
@Service
public class AgentCredentialVerificationService {

    private final AgentCredentialService credentialService;

    public AgentCredentialVerificationService(AgentCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @Transactional(readOnly = true)
    public Optional<VerifiedAgentCredential> verify(String presentedCredential) {
        return credentialService.verify(presentedCredential);
    }
}
