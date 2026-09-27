package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class AgentCredentialServiceTests {

    @Autowired private AgentCredentialService credentialService;
    @Autowired private AgentCredentialVerificationService verificationService;
    @Autowired private AgentRegistryService registryService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentCredentialRepository credentialRepository;
    @Autowired private AgentRegistryAuditEntryRepository auditRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void createIssues256BitShowOnceSecretAndPersistsOnlyItsHash() throws Exception {
        Fixture fixture = fixture("credential-create");
        Agent agent = createAgent(fixture, "Credential target");

        AgentCredentialIssueResponse created = credentialService.create(fixture.workspace().getId(), agent.getId(),
                fixture.actor().getId());
        String[] parts = created.credential().split("\\.", -1);
        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isEqualTo("no8do_ac1");
        byte[] secretBytes = Base64.getUrlDecoder().decode(parts[2]);
        assertThat(secretBytes).hasSize(32);
        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes)).isEqualTo(parts[2]);
        assertThat(parts[1]).isEqualTo(created.publicCredentialId());

        AgentCredential stored = credentialRepository.findById(created.id()).orElseThrow();
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secretBytes));
        assertThat(stored.getSecretHash()).isEqualTo(expectedHash);
        assertThat(stored.getSecretHash()).isNotEqualTo(created.credential());
        assertThat(stored.toString().contains(created.credential())).isFalse();
        assertThat(stored.toString().contains(stored.getSecretHash())).isFalse();
        assertThat(created.toString().contains(created.credential())).isFalse();
        assertThat(verificationService.verify(created.credential()))
                .contains(new VerifiedAgentCredential(created.id(), agent.getId(), fixture.workspace().getId()));

        var metadata = credentialService.list(fixture.workspace().getId(), agent.getId(), fixture.actor().getId());
        assertThat(metadata).hasSize(1);
        String metadataJson = objectMapper.writeValueAsString(metadata);
        assertThat(metadataJson.contains(created.credential())).isFalse();
        assertThat(metadataJson).doesNotContain("secretHash", "secret", "credential\"");

        AgentRegistryAuditEntry audit = auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId()).stream()
                .filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_CREDENTIAL_CREATED)
                .findFirst().orElseThrow();
        assertThat(audit.getActorUserId()).isEqualTo(fixture.actor().getId());
        assertThat(audit.getWorkspaceId()).isEqualTo(fixture.workspace().getId());
        assertThat(audit.getMetadata().toString()).contains(created.id().toString(), created.publicCredentialId())
                .doesNotContain("secretHash");
        assertThat(audit.getMetadata().toString().contains(created.credential())).isFalse();
    }

    @Test
    void revokeIsIdempotentAndStopsVerification() {
        Fixture fixture = fixture("credential-revoke");
        Agent agent = createAgent(fixture, "Revoke target");
        AgentCredentialIssueResponse created = credentialService.create(fixture.workspace().getId(), agent.getId(),
                fixture.actor().getId());

        AgentCredentialMetadataResponse revoked = credentialService.revoke(fixture.workspace().getId(), agent.getId(),
                created.id(), fixture.actor().getId());
        InstantAssert.assertRevoked(revoked);
        assertThat(verificationService.verify(created.credential())).isEmpty();
        credentialService.revoke(fixture.workspace().getId(), agent.getId(), created.id(), fixture.actor().getId());

        assertThat(credentialRepository.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(AgentCredentialStatus.REVOKED);
        assertThat(auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId()).stream()
                .filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_CREDENTIAL_REVOKED))
                .hasSize(1);
    }

    @Test
    void rotationReplacesOnlySelectedCredentialAndReturnsDifferentShowOnceValue() {
        Fixture fixture = fixture("credential-rotate");
        Agent agent = createAgent(fixture, "Rotate target");
        AgentCredentialIssueResponse original = credentialService.create(fixture.workspace().getId(), agent.getId(),
                fixture.actor().getId());
        AgentCredentialIssueResponse independent = credentialService.create(fixture.workspace().getId(), agent.getId(),
                fixture.actor().getId());

        AgentCredentialIssueResponse rotated = credentialService.rotate(fixture.workspace().getId(), agent.getId(),
                original.id(), fixture.actor().getId());

        assertThat(rotated.id()).isNotEqualTo(original.id());
        assertThat(rotated.publicCredentialId()).isNotEqualTo(original.publicCredentialId());
        assertThat(rotated.credential().equals(original.credential())).isFalse();
        assertThat(credentialRepository.findById(original.id()).orElseThrow().getStatus())
                .isEqualTo(AgentCredentialStatus.REVOKED);
        assertThat(credentialRepository.findById(rotated.id()).orElseThrow().getStatus())
                .isEqualTo(AgentCredentialStatus.ACTIVE);
        assertThat(credentialRepository.findById(independent.id()).orElseThrow().getStatus())
                .isEqualTo(AgentCredentialStatus.ACTIVE);
        assertThat(verificationService.verify(original.credential())).isEmpty();
        assertThat(verificationService.verify(rotated.credential())).isPresent();
        assertThat(verificationService.verify(independent.credential())).isPresent();

        AgentRegistryAuditEntry audit = auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId()).stream()
                .filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_CREDENTIAL_ROTATED)
                .findFirst().orElseThrow();
        assertThat(audit.getMetadata().toString()).contains(original.id().toString(), rotated.id().toString())
                .doesNotContain("secretHash");
        assertThat(audit.getMetadata().toString().contains(original.credential())).isFalse();
        assertThat(audit.getMetadata().toString().contains(rotated.credential())).isFalse();
    }

    @Test
    void archivedAgentRevokesEveryActiveCredentialWithoutTouchingOtherAgents() {
        Fixture fixture = fixture("credential-archive");
        Agent archivedAgent = createAgent(fixture, "Will archive");
        Agent independentAgent = createAgent(fixture, "Must remain active");
        AgentCredentialIssueResponse first = credentialService.create(fixture.workspace().getId(), archivedAgent.getId(),
                fixture.actor().getId());
        AgentCredentialIssueResponse second = credentialService.create(fixture.workspace().getId(), archivedAgent.getId(),
                fixture.actor().getId());
        AgentCredentialIssueResponse independent = credentialService.create(fixture.workspace().getId(),
                independentAgent.getId(), fixture.actor().getId());

        registryService.changeLifecycle(fixture.workspace().getId(), archivedAgent.getId(), fixture.actor().getId(),
                AgentLifecycleStatus.ARCHIVED);

        for (AgentCredentialIssueResponse target : java.util.List.of(first, second)) {
            AgentCredential revoked = credentialRepository.findById(target.id()).orElseThrow();
            assertThat(revoked.getStatus()).isEqualTo(AgentCredentialStatus.REVOKED);
            assertThat(revoked.getRevokedAt()).isNotNull();
            assertThat(verificationService.verify(target.credential())).isEmpty();
        }
        AgentCredential unaffected = credentialRepository.findById(independent.id()).orElseThrow();
        assertThat(unaffected.getStatus()).isEqualTo(AgentCredentialStatus.ACTIVE);
        assertThat(unaffected.getRevokedAt()).isNull();
        assertThat(verificationService.verify(independent.credential())).isPresent();

        var entries = auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(archivedAgent.getId());
        assertThat(entries).extracting(AgentRegistryAuditEntry::getEventType)
                .contains(AgentRegistryAuditEventType.AGENT_LIFECYCLE_CHANGED)
                .contains(AgentRegistryAuditEventType.AGENT_CREDENTIAL_REVOKED);
        assertThat(entries.stream().filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_CREDENTIAL_REVOKED))
                .hasSize(2);
        assertThat(entries.stream().filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_CREDENTIAL_REVOKED)
                .map(entry -> entry.getMetadata().toString()).allMatch(metadata -> metadata.contains("AGENT_ARCHIVED")))
                .isTrue();
    }

    @Test
    void disabledAgentKeepsCredentialButCannotIssueOrRotateAndVerificationRejectsIt() {
        Fixture fixture = fixture("credential-disabled");
        Agent agent = createAgent(fixture, "Disable target");
        AgentCredentialIssueResponse existing = credentialService.create(fixture.workspace().getId(), agent.getId(),
                fixture.actor().getId());
        registryService.changeLifecycle(fixture.workspace().getId(), agent.getId(), fixture.actor().getId(),
                AgentLifecycleStatus.DISABLED);

        assertConflict(() -> credentialService.create(fixture.workspace().getId(), agent.getId(),
                fixture.actor().getId()));
        assertConflict(() -> credentialService.rotate(fixture.workspace().getId(), agent.getId(), existing.id(),
                fixture.actor().getId()));
        assertThat(credentialRepository.findById(existing.id()).orElseThrow().getStatus())
                .isEqualTo(AgentCredentialStatus.ACTIVE);
        assertThat(credentialRepository.findById(existing.id()).orElseThrow().getRevokedAt()).isNull();
        assertThat(verificationService.verify(existing.credential())).isEmpty();

        registryService.changeLifecycle(fixture.workspace().getId(), agent.getId(), fixture.actor().getId(),
                AgentLifecycleStatus.ACTIVE);
        assertThat(verificationService.verify(existing.credential())).isPresent();
    }

    @Test
    void verificationRejectsUnknownMalformedWrongAndRevokedCredentialsWithoutEchoingInput() {
        Fixture fixture = fixture("credential-invalid");
        Agent agent = createAgent(fixture, "Invalid target");
        AgentCredentialIssueResponse created = credentialService.create(fixture.workspace().getId(), agent.getId(),
                fixture.actor().getId());
        String[] parts = created.credential().split("\\.", -1);
        String wrongSecret = parts[0] + "." + parts[1] + "." + "A".repeat(43);
        String unknownId = parts[0] + "." + "A".repeat(22) + "." + parts[2];

        assertThat(verificationService.verify("not-a-credential")).isEmpty();
        assertThat(verificationService.verify(wrongSecret)).isEmpty();
        assertThat(verificationService.verify(unknownId)).isEmpty();
        assertThat(verificationService.verify(wrongSecret).toString().contains(wrongSecret)).isFalse();
    }

    private Fixture fixture(String prefix) {
        String suffix = UUID.randomUUID().toString();
        User actor = userRepository.save(new User(prefix, prefix + "-" + suffix + "@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace(prefix + " " + suffix));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, actor, WorkspaceRole.OWNER));
        return new Fixture(actor, workspace);
    }

    private Agent createAgent(Fixture fixture, String name) {
        return registryService.createAgent(fixture.workspace().getId(), fixture.actor().getId(), name, null, null);
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private record Fixture(User actor, Workspace workspace) {}

    private static final class InstantAssert {
        private static void assertRevoked(AgentCredentialMetadataResponse metadata) {
            assertThat(metadata.status()).isEqualTo(AgentCredentialStatus.REVOKED);
            assertThat(metadata.revokedAt()).isNotNull();
        }
    }
}
