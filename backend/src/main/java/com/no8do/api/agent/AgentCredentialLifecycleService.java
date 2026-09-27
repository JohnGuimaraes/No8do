package com.no8do.api.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates credential revocation from Agent lifecycle transitions without depending on the credential API service. */
@Service
public class AgentCredentialLifecycleService {

    private final AgentCredentialRepository credentialRepository;
    private final AgentRegistryAuditService auditService;

    public AgentCredentialLifecycleService(AgentCredentialRepository credentialRepository,
            AgentRegistryAuditService auditService) {
        this.credentialRepository = credentialRepository;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeActiveForArchivedAgent(UUID actorUserId, Agent agent, Instant occurredAt) {
        List<AgentCredential> activeCredentials = credentialRepository
                .findByAgentIdAndStatusOrderByCreatedAtDescIdAsc(agent.getId(), AgentCredentialStatus.ACTIVE);
        for (AgentCredential credential : activeCredentials) {
            if (credential.revoke(occurredAt)) {
                credentialRepository.saveAndFlush(credential);
                auditService.recordCredentialRevoked(actorUserId, agent, credential, occurredAt, "AGENT_ARCHIVED");
            }
        }
    }
}
