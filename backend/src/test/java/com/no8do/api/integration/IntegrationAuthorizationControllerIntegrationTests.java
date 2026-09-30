package com.no8do.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.agent.Agent;
import com.no8do.api.agent.AgentRegistryService;
import com.no8do.api.agent.AgentRepository;
import com.no8do.api.agent.AgentSession;
import com.no8do.api.agent.AgentSessionRegistrationRequest;
import com.no8do.api.agent.AgentSessionRepository;
import com.no8do.api.agent.AgentTransport;
import com.no8do.api.agent.AgentAuditEntryRepository;
import com.no8do.api.agent.AgentAuditEntry;
import com.no8do.api.auth.CreatePersonalApiTokenRequest;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.auth.PersonalApiTokenService;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(IntegrationAuthorizationControllerIntegrationTests.TestClockConfiguration.class)
class IntegrationAuthorizationControllerIntegrationTests {
    private static final String ROOT = "/api/integration-authorizations";
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private IntegrationBootstrapService bootstrapService;
    @Autowired private IntegrationAuthorizationVerificationService verificationService;
    @Autowired private IntegrationCredentialCodec credentialCodec;
    @Autowired private IntegrationAuthorizationRepository authorizationRepository;
    @Autowired private IntegrationBootstrapRequestRepository bootstrapRepository;
    @Autowired private IntegrationAuthorizationAuditRepository auditRepository;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentSessionRepository agentSessionRepository;
    @Autowired private AgentAuditEntryRepository agentAuditEntryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private PersonalApiTokenService patService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AdjustableClock testClock;
    @Autowired private FilterChainProxy securityFilterChain;

    @BeforeEach void resetClock() { testClock.set(Instant.now()); }

    private Start start(UUID installationId, String host, String label) throws Exception {
        byte[] verifierBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(verifierBytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                IntegrationBootstrapCrypto.sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        MvcResult result = mockMvc.perform(post(ROOT + "/bootstrap").contentType(MediaType.APPLICATION_JSON)
                        .content(startBody(installationId, host, label, challenge)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Start(result, json, UUID.fromString(json.get("requestId").asText()),
                json.get("deviceCode").asText(), json.get("userCode").asText(), verifier, challenge);
    }

    private byte[] startBody(UUID installationId, String host, String label, String challenge) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("installationId", installationId.toString());
        body.put("integrationVersion", "1.0.0");
        body.put("hostType", host);
        if (label != null) body.put("displayLabel", label);
        body.put("codeChallenge", challenge);
        body.put("codeChallengeMethod", "S256");
        return objectMapper.writeValueAsBytes(body);
    }

    private ResultActions sessionPost(String path, User actor, Object body) throws Exception {
        return sessionPost(path, actor, body, true);
    }

    private ResultActions sessionPost(String path, User actor, Object body, boolean includeCsrf) throws Exception {
        var request = post(path).with(user(new No8doUserDetails(actor))).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body));
        return mockMvc.perform(includeCsrf ? request.with(csrf()) : request);
    }

    private ResultActions exchange(Start start) throws Exception {
        return mockMvc.perform(post(ROOT + "/bootstrap/exchange").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("deviceCode", start.deviceCode(),
                        "codeVerifier", start.verifier()))));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Fixture fixture(String label, WorkspaceRole role) {
        String unique = UUID.randomUUID().toString();
        User actor = userRepository.saveAndFlush(new User(label + unique, label + unique + "@example.test", "hash"));
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace(label + unique));
        workspaceMemberRepository.saveAndFlush(new WorkspaceMember(workspace, actor, role));
        Agent agent = role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN
                ? agentRegistryService.createAgent(workspace.getId(), actor.getId(), label + " Agent", null, null)
                : null;
        return new Fixture(actor, workspace, agent);
    }

    private void approveExisting(Fixture fixture, Start request, Agent agent) throws Exception {
        sessionPost(ROOT + "/bootstrap/approve", fixture.actor(), Map.of("userCode", request.userCode(),
                "workspaceId", fixture.workspace().getId(), "existingAgentId", agent.getId()))
                .andExpect(status().isOk());
    }

    private String issue(Fixture fixture, Agent agent) {
        IntegrationCredentialCodec.IssuedIntegrationCredential issued = credentialCodec.issue();
        Instant now = testClock.instant();
        IntegrationAuthorization authorization = new IntegrationAuthorization(UUID.randomUUID(), issued.selector(),
                issued.tokenHash(), agent, fixture.actor().getId(), UUID.randomUUID(), IntegrationHostType.CODEX,
                "runtime-test", "1.0.0", now, now.plus(Duration.ofDays(180)));
        authorizationRepository.saveAndFlush(authorization);
        return issued.serialized();
    }

    private static int indexOf(List<jakarta.servlet.Filter> filters, Class<?> filterType) {
        for (int index = 0; index < filters.size(); index++) {
            if (filterType.isInstance(filters.get(index))) return index;
        }
        return Integer.MAX_VALUE;
    }

    private void advance(Duration duration) { testClock.advance(duration); }

    private static String tokenFrom(JsonNode response) { return response.get("integrationCredential").asText(); }

    @Test
    void startIsAnonymousStrictAndStoresOnlyHashes() throws Exception {
        Start start = start(UUID.randomUUID(), "CODEX", "Codex desktop");
        assertThat(start.json().get("expiresIn").asInt()).isEqualTo(600);
        assertThat(start.json().get("interval").asInt()).isEqualTo(5);
        assertThat(start.json().get("verificationUri").asText()).isEqualTo("http://localhost:5173/connect/no8do");
        assertThat(start.result().getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        IntegrationBootstrapRequest persisted = bootstrapRepository.findById(start.requestId()).orElseThrow();
        assertThat(persisted.getDeviceCodeHash()).isNotEqualTo(start.deviceCode());
        assertThat(persisted.getUserCodeHmac()).isNotEqualTo(start.userCode());
        assertThat(persisted.toString()).doesNotContain(start.deviceCode(), start.userCode(), start.verifier());

        byte[] validBody = startBody(UUID.randomUUID(), "CODEX", "test", start.challenge());
        Map<String, Object> strictBody = Map.of("installationId", UUID.randomUUID().toString(),
                "integrationVersion", "1.0.0", "hostType", "CODEX", "codeChallenge", start.challenge(),
                "codeChallengeMethod", "S256", "workspaceId", UUID.randomUUID().toString());
        mockMvc.perform(post(ROOT + "/bootstrap").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(strictBody))).andExpect(status().isBadRequest());
        mockMvc.perform(post(ROOT + "/bootstrap").contentType(MediaType.APPLICATION_JSON)
                .content(startBody(UUID.randomUUID(), "UNKNOWN", "test", start.challenge())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(ROOT + "/bootstrap").contentType(MediaType.APPLICATION_JSON)
                .content(startBody(UUID.randomUUID(), "CODEX", "test", "invalid"))).andExpect(status().isBadRequest());
    }

    @Test
    void inspectCreateAgentAndExchangeIssueOnlyOneHashStoredCredential() throws Exception {
        Fixture owner = fixture("bootstrap-owner", WorkspaceRole.OWNER);
        Start request = start(UUID.randomUUID(), "CODEX", "Codex Desktop");
        JsonNode inspection = json(sessionPost(ROOT + "/bootstrap/inspect", owner.actor(),
                Map.of("userCode", request.userCode())).andExpect(status().isOk()).andReturn());
        assertThat(inspection.get("eligibleWorkspaces").size()).isEqualTo(1);
        assertThat(inspection.toString()).doesNotContain(request.deviceCode(), request.userCode(), request.verifier(), request.challenge());

        JsonNode approval = json(sessionPost(ROOT + "/bootstrap/approve", owner.actor(), Map.of(
                "userCode", request.userCode(), "workspaceId", owner.workspace().getId(),
                "newAgent", Map.of("name", "Codex — Desktop"))).andExpect(status().isOk()).andReturn());
        UUID agentId = UUID.fromString(approval.get("agentId").asText());
        assertThat(agentRepository.findById(agentId).orElseThrow().getName()).isEqualTo("Codex — Desktop");
        assertThat(authorizationRepository.findByWorkspaceId(owner.workspace().getId())).isEmpty();

        exchange(request).andExpect(status().isTooManyRequests()).andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        advance(Duration.ofSeconds(10));
        JsonNode issued = json(exchange(request).andExpect(status().isOk()).andReturn());
        String credential = tokenFrom(issued);
        assertThat(credential).startsWith("no8do_int_");
        assertThat(issued.get("state").asText()).isEqualTo("CONSUMED");
        IntegrationBootstrapRequest consumed = bootstrapRepository.findById(request.requestId()).orElseThrow();
        assertThat(consumed.getState()).isEqualTo(IntegrationBootstrapState.CONSUMED);

        String selector = credential.substring("no8do_int_".length()).split("\\.")[0];
        IntegrationAuthorization authorization = authorizationRepository.findBySelector(selector).orElseThrow();
        assertThat(authorization.getAgent().getId()).isEqualTo(agentId);
        assertThat(authorization.getAuthorizedByUserId()).isEqualTo(owner.actor().getId());
        assertThat(authorization.getAgent().getWorkspace().getId()).isEqualTo(owner.workspace().getId());
        assertThat(authorization.getExpiresAt()).isEqualTo(authorization.getCreatedAt().plus(Duration.ofDays(180)));
        assertThat(authorization.getTokenHash()).hasSize(64).doesNotContain(credential);
        assertThat(authorization.toString()).doesNotContain(credential, authorization.getTokenHash(), selector);
        JsonNode replay = json(exchange(request).andExpect(status().isOk()).andReturn());
        assertThat(replay.get("state").asText()).isEqualTo("CONSUMED");
        assertThat(replay.get("integrationCredential").isNull()).isTrue();
        assertThat(auditRepository.findByAuthorizationIdOrderByOccurredAtAscIdAsc(authorization.getId()))
                .extracting(IntegrationAuthorizationAuditEntry::getEventType)
                .containsExactly(IntegrationAuthorizationAuditEventType.INTEGRATION_AUTHORIZATION_ISSUED);
        assertThat(auditRepository.findByAuthorizationIdOrderByOccurredAtAscIdAsc(authorization.getId()).toString())
                .doesNotContain(credential, authorization.getTokenHash(), request.userCode(), request.deviceCode());
    }

    @Test
    void onlyManagersCanApproveAndAgentAndWorkspaceMustMatch() throws Exception {
        Fixture owner = fixture("bootstrap-scope-owner", WorkspaceRole.OWNER);
        Fixture other = fixture("bootstrap-scope-other", WorkspaceRole.OWNER);
        Start request = start(UUID.randomUUID(), "CLAUDE", null);
        sessionPost(ROOT + "/bootstrap/approve", owner.actor(), Map.of("userCode", request.userCode(),
                "workspaceId", owner.workspace().getId(), "existingAgentId", other.agent().getId()))
                .andExpect(status().isNotFound());
        sessionPost(ROOT + "/bootstrap/approve", owner.actor(), Map.of("userCode", request.userCode(),
                "workspaceId", owner.workspace().getId(), "existingAgentId", owner.agent().getId(),
                "newAgent", Map.of("name", "second choice"))).andExpect(status().isBadRequest());
        sessionPost(ROOT + "/bootstrap/approve", owner.actor(), Map.of("userCode", request.userCode(),
                "workspaceId", owner.workspace().getId())).andExpect(status().isBadRequest());

        Fixture member = fixture("bootstrap-scope-member", WorkspaceRole.MEMBER);
        Start denied = start(UUID.randomUUID(), "VSCODE", null);
        sessionPost(ROOT + "/bootstrap/approve", member.actor(), Map.of("userCode", denied.userCode(),
                "workspaceId", member.workspace().getId(), "newAgent", Map.of("name", "not allowed")))
                .andExpect(status().isForbidden());
        assertThat(bootstrapRepository.findById(denied.requestId()).orElseThrow().getState())
                .isEqualTo(IntegrationBootstrapState.PENDING);

        Start expiredAgent = start(UUID.randomUUID(), "IDE", null);
        agentRegistryService.changeLifecycle(owner.workspace().getId(), owner.agent().getId(),
                owner.actor().getId(), com.no8do.api.agent.AgentLifecycleStatus.DISABLED);
        sessionPost(ROOT + "/bootstrap/approve", owner.actor(), Map.of("userCode", expiredAgent.userCode(),
                "workspaceId", owner.workspace().getId(), "existingAgentId", owner.agent().getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void denyIsIdempotentAndInvalidDeviceOrUserCodesAreGeneric() throws Exception {
        Fixture owner = fixture("bootstrap-deny", WorkspaceRole.ADMIN);
        Start request = start(UUID.randomUUID(), "IDE", null);
        sessionPost(ROOT + "/bootstrap/deny", owner.actor(), Map.of("userCode", request.userCode()))
                .andExpect(status().isOk());
        sessionPost(ROOT + "/bootstrap/deny", owner.actor(), Map.of("userCode", request.userCode()))
                .andExpect(status().isOk());
        JsonNode denied = json(exchange(request).andExpect(status().isOk()).andReturn());
        assertThat(denied.get("state").asText()).isEqualTo("DENIED");
        assertThat(denied.get("integrationCredential").isNull()).isTrue();
        String wrongDevice = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        String wrongBody = objectMapper.writeValueAsString(Map.of("deviceCode", wrongDevice,
                "codeVerifier", request.verifier()));
        String response = mockMvc.perform(post(ROOT + "/bootstrap/exchange").contentType(MediaType.APPLICATION_JSON)
                        .content(wrongBody)).andExpect(status().isBadRequest()).andReturn()
                .getResponse().getContentAsString();
        assertThat(response).doesNotContain(wrongDevice, request.userCode(), request.deviceCode());
    }

    @Test
    void patCannotInspectApproveListOrRevokeAndHumanMutationsRequireCsrf() throws Exception {
        Fixture owner = fixture("bootstrap-human", WorkspaceRole.OWNER);
        String pat = patService.create(owner.actor().getId(), new CreatePersonalApiTokenRequest("bootstrap-test")).value();
        Start request = start(UUID.randomUUID(), "CODEX", null);
        Map<String, Object> approval = Map.of("userCode", request.userCode(), "workspaceId", owner.workspace().getId(),
                "existingAgentId", owner.agent().getId());
        mockMvc.perform(post(ROOT + "/bootstrap/inspect").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("userCode", request.userCode())))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + pat)).andExpect(status().isForbidden());
        mockMvc.perform(post(ROOT + "/bootstrap/approve").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(approval)).header(HttpHeaders.AUTHORIZATION, "Bearer " + pat))
                .andExpect(status().isForbidden());
        sessionPost(ROOT + "/bootstrap/approve", owner.actor(), approval, false).andExpect(status().isForbidden());
        Start denyRequest = start(UUID.randomUUID(), "CLAUDE", null);
        mockMvc.perform(post(ROOT + "/bootstrap/deny").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("userCode", denyRequest.userCode())))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + pat)).andExpect(status().isForbidden());
        sessionPost(ROOT + "/bootstrap/deny", owner.actor(), Map.of("userCode", denyRequest.userCode()), false)
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ROOT).param("workspaceId", owner.workspace().getId().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + pat)).andExpect(status().isForbidden());
        sessionPost(ROOT + "/bootstrap/approve", owner.actor(), approval).andExpect(status().isOk());
        advance(Duration.ofSeconds(5));
        String credential = tokenFrom(json(exchange(request).andExpect(status().isOk()).andReturn()));
        String selector = credential.substring("no8do_int_".length()).split("\\.")[0];
        IntegrationAuthorization authorization = authorizationRepository.findBySelector(selector).orElseThrow();
        mockMvc.perform(post(ROOT + "/" + authorization.getId() + "/revoke")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + pat)).andExpect(status().isForbidden());
        assertThat(authorization.getStatus()).isEqualTo(IntegrationAuthorizationStatus.ACTIVE);
        assertThat(bootstrapRepository.findById(request.requestId()).orElseThrow().getState())
                .isEqualTo(IntegrationBootstrapState.CONSUMED);
        assertThat(bootstrapRepository.findById(denyRequest.requestId()).orElseThrow().getState())
                .isEqualTo(IntegrationBootstrapState.PENDING);
    }

    @Test
    void verificationRechecksRoleUserAgentAndTokenExpiration() throws Exception {
        Fixture owner = fixture("bootstrap-verify", WorkspaceRole.OWNER);
        Start request = start(UUID.randomUUID(), "CODEX", "Verification");
        approveExisting(owner, request, owner.agent());
        advance(Duration.ofSeconds(5));
        String token = tokenFrom(json(exchange(request).andExpect(status().isOk()).andReturn()));
        VerifiedIntegrationAuthorization verified = verificationService.verify(token).orElseThrow();
        assertThat(verified.authorizedByUserId()).isEqualTo(owner.actor().getId());
        assertThat(verified.agentId()).isEqualTo(owner.agent().getId());
        assertThat(verified.workspaceId()).isEqualTo(owner.workspace().getId());
        assertThat(verificationService.verify("malformed")).isEmpty();

        WorkspaceMember membership = workspaceMemberRepository.findByWorkspaceIdAndUserId(
                owner.workspace().getId(), owner.actor().getId()).orElseThrow();
        membership.setRole(WorkspaceRole.MEMBER);
        workspaceMemberRepository.saveAndFlush(membership);
        assertThat(verificationService.verify(token)).isEmpty();
        mockMvc.perform(get("/api/agent-protocol").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        membership.setRole(WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(membership);
        owner.actor().setEnabled(false);
        userRepository.saveAndFlush(owner.actor());
        assertThat(verificationService.verify(token)).isEmpty();
        owner.actor().setEnabled(true);
        userRepository.saveAndFlush(owner.actor());

        advance(Duration.ofDays(181));
        assertThat(verificationService.verify(token)).isEmpty();
        mockMvc.perform(get("/api/agent-protocol").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        IntegrationAuthorization authorization = authorizationRepository.findById(verified.authorizationId()).orElseThrow();
        assertThat(authorization.getStatus()).isEqualTo(IntegrationAuthorizationStatus.ACTIVE);
        assertThat(authorization.getTokenHash()).doesNotContain(token);
    }

    @Test
    void integrationCredentialIsDistinctAndRestrictedToRuntimeAllowlist() throws Exception {
        var filters = securityFilterChain.getFilters("/api/agent-protocol");
        int contextIndex = indexOf(filters, org.springframework.security.web.context.SecurityContextHolderFilter.class);
        int integrationIndex = indexOf(filters, IntegrationCredentialAuthenticationFilter.class);
        int patIndex = indexOf(filters, com.no8do.api.auth.PersonalApiTokenAuthenticationFilter.class);
        int csrfIndex = indexOf(filters, CsrfFilter.class);
        assertThat(contextIndex).isLessThan(integrationIndex);
        assertThat(integrationIndex).isLessThan(patIndex);
        assertThat(patIndex).isLessThan(csrfIndex);

        Fixture owner = fixture("runtime-filter", WorkspaceRole.OWNER);
        String integration = issue(owner, owner.agent());
        String selector = integration.substring("no8do_int_".length()).split("\\.")[0];
        IntegrationAuthorization authorization = authorizationRepository.findBySelector(selector).orElseThrow();
        assertThat(authorization.getLastUsedAt()).isNull();

        assertThat(verificationService.verify(integration)).isPresent();
        assertThat(authorizationRepository.findById(authorization.getId()).orElseThrow().getLastUsedAt()).isNull();

        Instant firstUse = testClock.instant();
        assertThat(authorizationRepository.touchLastUsedAtIfDue(authorization.getId(), firstUse,
                firstUse.minus(Duration.ofMinutes(15)))).isEqualTo(1);
        assertThat(authorizationRepository.touchLastUsedAtIfDue(authorization.getId(), firstUse.plusSeconds(1),
                firstUse.plusSeconds(1).minus(Duration.ofMinutes(15)))).isZero();
        testClock.advance(Duration.ofMinutes(14));
        Instant beforeWindow = testClock.instant();
        assertThat(authorizationRepository.touchLastUsedAtIfDue(authorization.getId(), beforeWindow,
                beforeWindow.minus(Duration.ofMinutes(15)))).isZero();
        testClock.advance(Duration.ofMinutes(1));
        Instant afterWindow = testClock.instant();
        assertThat(authorizationRepository.touchLastUsedAtIfDue(authorization.getId(), afterWindow,
                afterWindow.minus(Duration.ofMinutes(15)))).isEqualTo(1);

        mockMvc.perform(get("/api/agent-protocol").header(HttpHeaders.AUTHORIZATION, "Bearer " + integration))
                .andExpect(status().isOk());
        mockMvc.perform(get(ROOT).param("workspaceId", owner.workspace().getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration))
                .andExpect(status().isForbidden());
        String personalToken = patService.create(owner.actor().getId(),
                new CreatePersonalApiTokenRequest("runtime-filter-test")).value();
        mockMvc.perform(get("/api/agent-protocol")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + personalToken))
                .andExpect(status().isOk());

        String malformed = "no8do_int_malformed-secret-value";
        MvcResult invalid = mockMvc.perform(get("/api/agent-protocol")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + malformed))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(invalid.getResponse().getContentAsString()).doesNotContain(malformed);

        String fingerprint = UUID.randomUUID().toString().replace("-", "").repeat(2);
        AgentSessionRegistrationRequest request = new AgentSessionRegistrationRequest("Codex", "1",
                owner.workspace().getId(), AgentTransport.MCP, fingerprint);
        MvcResult registered = mockMvc.perform(post("/api/agent-sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated()).andReturn();
        UUID sessionId = UUID.fromString(json(registered).get("sessionId").asText());
        AgentSession session = agentSessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getUserId()).isNull();
        assertThat(session.getWorkspaceId()).isEqualTo(owner.workspace().getId());
        assertThat(session.getAgent().getId()).isEqualTo(owner.agent().getId());
        assertThat(session.getAgentCredential()).isNull();
        assertThat(session.getIntegrationAuthorization().getId()).isEqualTo(authorization.getId());

        String sessionPath = "/api/agent-sessions/" + sessionId;
        mockMvc.perform(post(sessionPath + "/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration))
                .andExpect(status().isOk());
        mockMvc.perform(get(sessionPath + "/context")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration)
                        .header("X-No8do-Agent-Session-Id", sessionId.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get(sessionPath + "/operational-context/state")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration))
                .andExpect(status().isOk());
        String operationalContext = "{\"expectedVersion\":null,\"repository\":null,"
                + "\"branch\":\"feature/a2\",\"workingDirectory\":null,\"references\":[],"
                + "\"workspaceHint\":\"" + owner.workspace().getId() + "\"}";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        sessionPath + "/operational-context")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration)
                        .contentType(MediaType.APPLICATION_JSON).content(operationalContext))
                .andExpect(status().isOk());

        AgentAuditEntry sessionBindingAudit = agentAuditEntryRepository.findAll().stream()
                .filter(entry -> entry.getSessionId().equals(sessionId)
                        && entry.getEventType() == com.no8do.api.agent.AgentAuditEventType.AGENT_SESSION_BOUND)
                .findFirst().orElseThrow();
        assertThat(sessionBindingAudit.getUserId()).isEqualTo(owner.actor().getId());
        assertThat(sessionBindingAudit.getMetadata().path("bindingType").asText())
                .isEqualTo("INTEGRATION_AUTHORIZATION");
        assertThat(sessionBindingAudit.getMetadata().path("integrationAuthorizationId").asText())
                .isEqualTo(authorization.getId().toString());
        assertThat(sessionBindingAudit.getMetadata().toString()).doesNotContain(integration,
                authorization.getTokenHash(), authorization.getTokenSelector(), fingerprint);

        mockMvc.perform(post(sessionPath + "/revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(sessionPath + "/operational-context")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(sessionPath + "/disconnect")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration))
                .andExpect(status().isOk());
        mockMvc.perform(post(sessionPath + "/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/agent-sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + integration)
                        .header("X-No8do-Agent-Credential", "no8do_ac1.dummy")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());

        String secondAuthorization = issue(owner, owner.agent());
        MvcResult collision = mockMvc.perform(post("/api/agent-sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAuthorization)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isConflict()).andReturn();
        assertThat(collision.getResponse().getContentAsString()).doesNotContain(sessionId.toString());

        AgentSessionRegistrationRequest wrongWorkspace = new AgentSessionRegistrationRequest("Codex", "1",
                UUID.randomUUID(), AgentTransport.MCP, UUID.randomUUID().toString().replace("-", "").repeat(2));
        mockMvc.perform(post("/api/agent-sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + integration)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(wrongWorkspace)))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeIsManagerScopedIdempotentAndStopsVerification() throws Exception {
        Fixture owner = fixture("bootstrap-revoke-owner", WorkspaceRole.OWNER);
        Fixture other = fixture("bootstrap-revoke-other", WorkspaceRole.OWNER);
        Start request = start(UUID.randomUUID(), "CLAUDE", null);
        approveExisting(owner, request, owner.agent());
        advance(Duration.ofSeconds(5));
        String token = tokenFrom(json(exchange(request).andExpect(status().isOk()).andReturn()));
        String selector = token.substring("no8do_int_".length()).split("\\.")[0];
        IntegrationAuthorization authorization = authorizationRepository.findBySelector(selector).orElseThrow();
        String path = ROOT + "/" + authorization.getId() + "/revoke";

        mockMvc.perform(post(path).with(user(new No8doUserDetails(other.actor()))).with(csrf()))
                .andExpect(status().isForbidden());
        User member = userRepository.saveAndFlush(new User("member" + UUID.randomUUID(),
                UUID.randomUUID() + "@example.test", "hash"));
        workspaceMemberRepository.saveAndFlush(new WorkspaceMember(owner.workspace(), member, WorkspaceRole.MEMBER));
        mockMvc.perform(post(path).with(user(new No8doUserDetails(member))).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path).with(user(new No8doUserDetails(owner.actor())))).andExpect(status().isForbidden());
        mockMvc.perform(post(path).with(user(new No8doUserDetails(owner.actor()))).with(csrf()))
                .andExpect(status().isOk());
        advance(Duration.ofSeconds(1));
        mockMvc.perform(post(path).with(user(new No8doUserDetails(owner.actor()))).with(csrf()))
                .andExpect(status().isOk());
        assertThat(verificationService.verify(token)).isEmpty();
        mockMvc.perform(get("/api/agent-protocol").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertThat(auditRepository.findByAuthorizationIdOrderByOccurredAtAscIdAsc(authorization.getId()))
                .extracting(IntegrationAuthorizationAuditEntry::getEventType)
                .containsExactlyInAnyOrder(IntegrationAuthorizationAuditEventType.INTEGRATION_AUTHORIZATION_ISSUED,
                        IntegrationAuthorizationAuditEventType.INTEGRATION_AUTHORIZATION_REVOKED);
    }

    @Test
    void archivedAgentAndExpiredBootstrapCannotAuthenticate() throws Exception {
        Fixture owner = fixture("bootstrap-archive", WorkspaceRole.ADMIN);
        Start bootstrap = start(UUID.randomUUID(), "IDE", null);
        approveExisting(owner, bootstrap, owner.agent());
        advance(Duration.ofSeconds(5));
        String token = tokenFrom(json(exchange(bootstrap).andExpect(status().isOk()).andReturn()));
        agentRegistryService.changeLifecycle(owner.workspace().getId(), owner.agent().getId(),
                owner.actor().getId(), com.no8do.api.agent.AgentLifecycleStatus.ARCHIVED);
        assertThat(verificationService.verify(token)).isEmpty();

        Start expired = start(UUID.randomUUID(), "IDE", null);
        advance(Duration.ofMinutes(11));
        JsonNode expiredResult = json(exchange(expired).andExpect(status().isOk()).andReturn());
        assertThat(expiredResult.get("state").asText()).isEqualTo("EXPIRED");
        assertThat(expiredResult.get("integrationCredential").isNull()).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentExchangeConsumesExactlyOnce() throws Exception {
        Fixture owner = fixture("bootstrap-concurrent", WorkspaceRole.OWNER);
        Start request = start(UUID.randomUUID(), "CODEX", null);
        approveExisting(owner, request, owner.agent());
        advance(Duration.ofSeconds(5));
        IntegrationBootstrapExchangeRequest exchange = objectMapper.readValue(objectMapper.writeValueAsBytes(
                Map.of("deviceCode", request.deviceCode(), "codeVerifier", request.verifier())),
                IntegrationBootstrapExchangeRequest.class);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentExchange(exchange, ready, go, "198.51.100.10"));
            var second = executor.submit(() -> concurrentExchange(exchange, ready, go, "203.0.113.20"));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            IntegrationBootstrapResult one = first.get(20, TimeUnit.SECONDS);
            IntegrationBootstrapResult two = second.get(20, TimeUnit.SECONDS);
            assertThat(Stream.of(one.getIntegrationCredential(), two.getIntegrationCredential())
                    .filter(java.util.Objects::nonNull).count()).isEqualTo(1);
            assertThat(List.of(one.getState(), two.getState()))
                    .containsExactlyInAnyOrder(IntegrationBootstrapState.CONSUMED, IntegrationBootstrapState.CONSUMED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentExchangesForOneInstallationAcrossAgentsHaveOneWinnerAndOneConflict() throws Exception {
        Fixture firstOwner = fixture("bootstrap-install-race-a", WorkspaceRole.OWNER);
        Fixture secondOwner = fixture("bootstrap-install-race-b", WorkspaceRole.ADMIN);
        UUID installationId = UUID.randomUUID();
        Start firstRequest = start(installationId, "CODEX", null);
        Start secondRequest = start(installationId, "CLAUDE", null);
        approveExisting(firstOwner, firstRequest, firstOwner.agent());
        approveExisting(secondOwner, secondRequest, secondOwner.agent());
        advance(Duration.ofSeconds(5));

        IntegrationBootstrapExchangeRequest firstExchange = exchangeRequest(firstRequest);
        IntegrationBootstrapExchangeRequest secondExchange = exchangeRequest(secondRequest);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentExchangeOutcome(firstExchange, ready, go, "198.51.100.30"));
            var second = executor.submit(() -> concurrentExchangeOutcome(secondExchange, ready, go, "203.0.113.40"));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            Object firstOutcome = first.get(20, TimeUnit.SECONDS);
            Object secondOutcome = second.get(20, TimeUnit.SECONDS);
            List<Object> outcomes = List.of(firstOutcome, secondOutcome);
            assertThat(outcomes.stream().filter(IntegrationBootstrapResult.class::isInstance).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(Integer.class::isInstance).map(Integer.class::cast).toList())
                    .containsExactly(409);
            assertThat(jdbcTemplate.queryForObject("select count(*) from integration_authorizations "
                    + "where installation_id = ? and status = 'ACTIVE'", Long.class, installationId)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private IntegrationBootstrapExchangeRequest exchangeRequest(Start request) throws Exception {
        return objectMapper.readValue(objectMapper.writeValueAsBytes(Map.of("deviceCode", request.deviceCode(),
                "codeVerifier", request.verifier())), IntegrationBootstrapExchangeRequest.class);
    }

    private Object concurrentExchangeOutcome(IntegrationBootstrapExchangeRequest request, CountDownLatch ready,
            CountDownLatch go, String remoteAddress) {
        try {
            return concurrentExchange(request, ready, go, remoteAddress);
        } catch (ResponseStatusException conflict) {
            return conflict.getStatusCode().value();
        }
    }

    @Test
    void migrationV59AddsNullableIntegrationBindingWithoutBackfill() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '58' and success", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '59' and success", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema = current_schema() and table_name in "
                + "('integration_authorizations', 'integration_bootstrap_requests', 'integration_authorization_audit_entries')",
                Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_indexes where schemaname = current_schema() "
                + "and indexname in ('uq_integration_authorizations_token_selector', 'uq_integration_authorizations_active_installation', "
                + "'idx_integration_authorizations_agent_status', 'idx_integration_authorizations_grantor_status', "
                + "'uq_integration_bootstrap_device_hash', 'uq_integration_bootstrap_user_hmac', "
                + "'idx_integration_bootstrap_state_expiry', 'idx_integration_bootstrap_installation_state')", Integer.class))
                .isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("select count(*) from information_schema.columns "
                + "where table_schema = current_schema() and table_name = 'agent_sessions' "
                + "and column_name = 'integration_authorization_id'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from information_schema.columns "
                + "where table_schema = current_schema() and table_name = 'agent_sessions' "
                + "and column_name = 'user_id' and is_nullable = 'YES'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_indexes where schemaname = current_schema() "
                + "and indexname = 'idx_agent_sessions_integration_authorization_id'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_constraint "
                + "where conname in ('ck_agent_sessions_single_agent_binding', "
                + "'ck_agent_sessions_integration_binding_without_user')", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select confdeltype from pg_constraint "
                + "where conname = 'fk_agent_sessions_integration_authorization'", String.class)).isEqualTo("n");
        assertThat(jdbcTemplate.queryForObject("select count(*) from agent_sessions "
                + "where integration_authorization_id is not null", Integer.class)).isZero();
    }

    private IntegrationBootstrapResult concurrentExchange(IntegrationBootstrapExchangeRequest request,
            CountDownLatch ready, CountDownLatch go, String remoteAddress) {
        ready.countDown();
        try {
            if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("exchange test timed out");
            testClock.advance(Duration.ofSeconds(5));
            return bootstrapService.exchange(request, remoteAddress);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("exchange test interrupted");
        }
    }

    private record Fixture(User actor, Workspace workspace, Agent agent) {}
    private record Start(MvcResult result, JsonNode json, UUID requestId, String deviceCode,
            String userCode, String verifier, String challenge) {}

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean @Primary AdjustableClock integrationTestClock() { return new AdjustableClock(Instant.now()); }
    }

    static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> now;
        AdjustableClock(Instant initial) { now = new AtomicReference<>(initial); }
        void set(Instant value) { now.set(value); }
        void advance(Duration value) { now.updateAndGet(previous -> previous.plus(value)); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
