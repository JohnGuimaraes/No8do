import assert from "node:assert/strict";
import { createServer } from "node:http";
import { once } from "node:events";
import test from "node:test";
import { AgentSessionHeader, compactReplay, No8doApiError, No8doClient, type AgentProtocol, type Replay } from "./no8doClient.js";
import { takeStdioAgentCredential } from "./stdioAgentCredential.js";

const replay: Replay = { id: "r1", workspaceId: "w1", projectId: null, title: "Replay", type: "FIX", problem: "p", solution: "s", context: null, tags: ["java"], stack: ["spring"], status: "DRAFT", version: 1, usageCount: 0, successCount: 0, failureCount: 0, lastUsedAt: null, createdBy: "u1", createdByName: "User", createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-02T00:00:00Z" };

function client(status = 200, body: unknown = replay) {
  const calls: Array<[string, RequestInit | undefined]> = [];
  const fetchImpl = (async (url: string, init?: RequestInit) => {
    calls.push([url, init]);
    return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
  }) as typeof fetch;
  return { client: new No8doClient("http://localhost:8080/api/", "no8do_pat_secret-value", fetchImpl), calls };
}

test("envia bearer e busca com query codificada", async () => {
  const setup = client(200, [replay]);
  await setup.client.searchReplays("workspace/a", "erro & retry");
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/workspaces/workspace%2Fa/replays/search?q=erro%20%26%20retry");
  assert.equal(new Headers(setup.calls[0][1]?.headers).get("Authorization"), "Bearer no8do_pat_secret-value");
  assert.equal(setup.calls[0][1]?.method, undefined);
});

test("lista o catálogo sem usar busca textual", async () => {
  const setup = client(200, [replay]);
  await setup.client.listReplays("w1");
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/workspaces/w1/replays");
  assert.equal(setup.calls[0][1]?.method, undefined);
});

test("busca sugestões reutilizáveis pelo endpoint similar", async () => {
  const setup = client(200, [{ id: "r1", title: "Replay", type: "FIX", status: "VALIDATED", version: 1, stack: ["Spring"], usageCount: 2, score: 100 }]);
  await setup.client.findReusableKnowledge("w1", { query: "Replay", tags: ["java"] });
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/workspaces/w1/replays/similar");
  assert.deepEqual(JSON.parse(String(setup.calls[0][1]?.body)), { query: "Replay", tags: ["java"] });
});

test("lista, cria e remove relações pelos endpoints corretos", async () => {
  const setup = client(200, []);
  await setup.client.listReplayRelations("w1", "r1");
  await setup.client.createReplayRelation("w1", "r1", "r2", "RELATED_TO");
  await setup.client.deleteReplayRelation("w1", "r1", "rel1");
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/workspaces/w1/replays/r1/relations");
  assert.deepEqual(JSON.parse(String(setup.calls[1][1]?.body)), { targetReplayId: "r2", type: "RELATED_TO" });
  assert.equal(setup.calls[2][1]?.method, "DELETE");
});

test("lista e obtém snapshots de versão pelos endpoints corretos", async () => {
  const setup = client(200, []);
  await setup.client.listReplayVersions("w1", "r1");
  await setup.client.getReplayVersion("w1", "r1", 2);
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/workspaces/w1/replays/r1/versions");
  assert.equal(setup.calls[1][0], "http://localhost:8080/api/workspaces/w1/replays/r1/versions/2");
});

test("lista retorna erro seguro para PAT inválido ou revogado", async () => {
  const setup = client(401, { message: "token interno" });
  await assert.rejects(() => setup.client.listReplays("w1"), (error: unknown) => error instanceof No8doApiError && error.message === "Token No8do ausente, inválido ou revogado.");
});

test("usa endpoints e corpos corretos para get, create, update e register usage", async () => {
  const setup = client();
  const validationEvidence = { summary: "Validação aprovada", method: "testes automatizados", reference: "ci://run/123" };
  await setup.client.getReplay("w1", "r1");
  await setup.client.createReplay("w1", { title: "Novo", type: "RECIPE", tags: ["node"], status: "VALIDATED", validationEvidence });
  await setup.client.createReplay("w1", { title: "Sem projeto", type: "RECIPE", projectId: null });
  await setup.client.updateReplay("w1", "r1", { solution: "melhor", validationEvidence });
  await setup.client.registerReplayUsage("w1", "r1", { result: "SUCCESS", materiallyUsed: true, replayVersion: 1, projectId: null, context: "aplicado" });
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/workspaces/w1/replays/r1");
  assert.equal(setup.calls[1][1]?.method, "POST");
  assert.deepEqual(JSON.parse(String(setup.calls[1][1]?.body)), { title: "Novo", type: "RECIPE", tags: ["node"], status: "VALIDATED", validationEvidence });
  assert.deepEqual(JSON.parse(String(setup.calls[2][1]?.body)), { title: "Sem projeto", type: "RECIPE" });
  assert.equal(setup.calls[3][1]?.method, "PATCH");
  assert.deepEqual(JSON.parse(String(setup.calls[3][1]?.body)), { solution: "melhor", validationEvidence });
  assert.equal(setup.calls[4][0], "http://localhost:8080/api/workspaces/w1/replays/r1/usages");
  assert.equal(setup.calls[4][1]?.method, "POST");
  assert.deepEqual(JSON.parse(String(setup.calls[4][1]?.body)), { result: "SUCCESS", materiallyUsed: true, replayVersion: 1, projectId: null, context: "aplicado", source: "MCP" });
});

test("força source MCP mesmo diante de entrada não tipada", async () => {
  const setup = client();
  await setup.client.registerReplayUsage("w1", "r1", { result: "SUCCESS", source: "MANUAL" } as never);
  assert.equal(JSON.parse(String(setup.calls[0][1]?.body)).source, "MCP");
});

for (const [status, expected] of [[400, "Request inválido."], [401, "Token No8do ausente, inválido ou revogado."], [403, "Usuário sem permissão no workspace."], [404, "Recurso não encontrado."], [500, "Falha da API No8do."]] as const) {
  test(`trata ${status} sem vazar token`, async () => {
    const setup = client(status, { message: "no8do_pat_secret-value" });
    await assert.rejects(() => setup.client.getReplay("w1", "r1"), (error: unknown) => error instanceof No8doApiError && error.message === expected && !error.message.includes("secret-value"));
  });
}

test("compacta o resultado de busca", () => {
  assert.deepEqual(compactReplay(replay), { id: "r1", title: "Replay", type: "FIX", status: "DRAFT", version: 1, projectId: null, tags: ["java"], stack: ["spring"], usageCount: 0, successCount: 0, failureCount: 0, lastUsedAt: null, updatedAt: "2026-01-02T00:00:00Z" });
});

test("obtém qualidade pelo endpoint correto e trata PAT inválido", async () => {
  const setup = client(200, { score: 45, level: "MEDIUM", usageCount: 0, successCount: 0, failureCount: 0, successRate: null, signals: ["Conhecimento validado"] });
  const quality = await setup.client.getReplayQuality("w1", "r1");
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/workspaces/w1/replays/r1/quality");
  assert.equal(quality.score, 45);
  assert.deepEqual(quality.signals, ["Conhecimento validado"]);
  const denied = client(401, {});
  await assert.rejects(() => denied.client.getReplayQuality("w1", "r1"), (error: unknown) => error instanceof No8doApiError && error.message === "Token No8do ausente, inválido ou revogado.");
});

test("obtém o protocolo canônico global pelo endpoint autenticado sem workspace", async () => {
  const protocol: AgentProtocol = {
    protocolName: "controlled-protocol",
    protocolVersion: 7,
    systemName: "Controlled No8do",
    purpose: "Test fixture",
    replayGuidance: { summary: "fixture", searchBeforeNonTrivialWork: true, preferExistingKnowledge: true, searchBeforeCreate: true, recordUsageOnlyWhenMateriallyUsed: true, validatedRequiresEvidence: true, avoidTrivialKnowledge: true, avoidDuplicateKnowledge: true, neverStoreSecrets: true, neverStoreCredentials: true, avoidDiscardedAttempts: true },
    capabilities: { capabilities: [{ id: "CONTROLLED", description: "controlled capability", readOnly: true }] },
    policies: { policies: [{ id: "controlled-policy", description: "controlled policy", enforcement: "ADVISORY" }] }
  };
  const setup = client(200, protocol);
  const result = await setup.client.getAgentProtocol();

  assert.deepEqual(result, protocol);
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/agent-protocol");
  assert.equal(setup.calls[0][1]?.method, undefined);
  assert.equal(new Headers(setup.calls[0][1]?.headers).get("Authorization"), "Bearer no8do_pat_secret-value");
});

test("registra Agent Session com somente a identidade MCP e fingerprint do transporte", async () => {
  const response = { sessionId: "session-id", clientName: "Codex Desktop", clientVersion: "9.8", workspaceId: null, transport: "MCP", runtimeMode: "FULL", protocolName: "no8do-agent-protocol", protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" };
  const setup = client(201, response);
  await setup.client.registerAgentSession({ clientName: "Codex Desktop", clientVersion: "9.8", workspaceId: null, transport: "MCP", transportSessionFingerprint: "a".repeat(64) });
  assert.equal(setup.calls[0]?.[0], "http://localhost:8080/api/agent-sessions");
  assert.equal(setup.calls[0]?.[1]?.method, "POST");
  assert.equal(new Headers(setup.calls[0]?.[1]?.headers).get("X-No8do-Agent-Credential"), null);
  assert.deepEqual(JSON.parse(String(setup.calls[0]?.[1]?.body)), { clientName: "Codex Desktop", clientVersion: "9.8", workspaceId: null, transport: "MCP", transportSessionFingerprint: "a".repeat(64) });
  assert.equal(JSON.stringify(setup.calls[0]?.[1]?.body).includes("PAT"), false);
  assert.equal(JSON.stringify(response).includes("transportSessionFingerprint"), false);
});

test("encaminha credential somente no header do registration e não segue redirects", async () => {
  const setup = client(201, { sessionId: "s1" });
  await setup.client.registerAgentSession({ clientName: "Codex", clientVersion: "1", workspaceId: null,
    transport: "MCP", transportSessionFingerprint: "a".repeat(64) }, "no8do_agent_private");
  const [url, init] = setup.calls[0]!;
  assert.equal(url, "http://localhost:8080/api/agent-sessions");
  const headers = new Headers(init?.headers);
  assert.equal(headers.get("X-No8do-Agent-Credential"), "no8do_agent_private");
  assert.equal(headers.get("Authorization"), "Bearer no8do_pat_secret-value");
  assert.equal(init?.redirect, "error");
  assert.doesNotMatch(String(init?.body), /no8do_agent_private/);
  assert.doesNotMatch(JSON.stringify({ ...init, headers: undefined }), /no8do_agent_private/);
});

test("credential de Agent rejeita destino sem HTTPS fora de loopback sem ecoá-la", async () => {
  const fetchImpl = (async () => { throw new Error("fetch must not be called"); }) as typeof fetch;
  const unsafe = new No8doClient("http://api.example", "PAT_PRIVATE", fetchImpl);
  await assert.rejects(() => unsafe.registerAgentSession({ clientName: "Codex", clientVersion: "1", workspaceId: null,
    transport: "MCP", transportSessionFingerprint: "a".repeat(64) }, "agent_secret_private"),
  (error: unknown) => error instanceof Error && error.message.includes("requires HTTPS")
    && !error.message.includes("agent_secret_private"));
});

test("401 de binding devolve erro genérico sem ecoar credential", async () => {
  const setup = client(401, { message: "agent_secret_private" });
  await assert.rejects(() => setup.client.registerAgentSession({ clientName: "Codex", clientVersion: "1", workspaceId: null,
    transport: "MCP", transportSessionFingerprint: "a".repeat(64) }, "agent_secret_private"),
  (error: unknown) => error instanceof No8doApiError && error.status === 401
    && error.message === "Credencial de Agent inválida." && !JSON.stringify(error).includes("agent_secret_private"));
});

test("STDIO consome a credential do ambiente uma vez", () => {
  const env: { NO8DO_AGENT_CREDENTIAL?: string } = { NO8DO_AGENT_CREDENTIAL: "agent_secret_private" };
  assert.equal(takeStdioAgentCredential(env), "agent_secret_private");
  assert.equal(env.NO8DO_AGENT_CREDENTIAL, undefined);
  assert.equal(takeStdioAgentCredential(env), undefined);
});

test("credential não atravessa redirect para outro origin", async () => {
  let otherOriginRequests = 0;
  const otherOrigin = createServer((_request, response) => { otherOriginRequests++; response.end("{}"); });
  otherOrigin.listen(0, "127.0.0.1"); await once(otherOrigin, "listening");
  const otherAddress = otherOrigin.address();
  assert.ok(otherAddress && typeof otherAddress !== "string");
  const otherUrl = `http://127.0.0.1:${otherAddress.port}/collect`;
  const configured = createServer((_request, response) => {
    response.writeHead(302, { location: otherUrl }); response.end();
  });
  configured.listen(0, "127.0.0.1"); await once(configured, "listening");
  const configuredAddress = configured.address();
  assert.ok(configuredAddress && typeof configuredAddress !== "string");
  try {
    const api = new No8doClient(`http://127.0.0.1:${configuredAddress.port}`, "PAT_PRIVATE");
    await assert.rejects(() => api.registerAgentSession({ clientName: "Codex", clientVersion: "1", workspaceId: null,
      transport: "MCP", transportSessionFingerprint: "a".repeat(64) }, "agent_secret_private"));
    assert.equal(otherOriginRequests, 0);
  } finally {
    const configuredClosed = once(configured, "close"); configured.close(); configured.closeAllConnections(); await configuredClosed;
    const otherClosed = once(otherOrigin, "close"); otherOrigin.close(); otherOrigin.closeAllConnections(); await otherClosed;
  }
});

test("anexa session header apenas após initialize e obtém contexto atualizado sem parâmetros", async () => {
  const sessionId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  let mode = "FULL";
  const calls: Array<{ url: string; headers: Headers }> = [];
  const sessionHeader = new AgentSessionHeader();
  const fetchImpl = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), headers: new Headers(init?.headers) });
    const body = String(url).endsWith("/context")
      ? { sessionId, clientName: "Codex", clientVersion: "1", workspaceId: null, transport: "MCP", protocolName: "no8do-agent-protocol", protocolVersion: 1, runtimeMode: mode, effectiveCapabilities: [], policies: [{ id: "secrets-forbidden", description: "Não salvar segredos", enforcement: "ADVISORY" }], registeredAt: "2026-01-01T00:00:00Z", presenceStatus: "CONNECTED", lastSeenAt: "2026-01-01T00:00:00Z", lastActivityAt: null, disconnectedAt: null }
      : { sessionId, clientName: "Codex", clientVersion: "1", workspaceId: null, transport: "MCP", runtimeMode: "FULL", protocolName: "no8do-agent-protocol", protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" };
    return new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;
  const client = new No8doClient("http://localhost:8080", "PAT_PRIVATE", fetchImpl, sessionHeader);

  const registered = await client.registerAgentSession({ clientName: "Codex", clientVersion: "1", workspaceId: null, transport: "MCP", transportSessionFingerprint: "a".repeat(64) });
  assert.equal(calls[0]?.headers.get("X-No8do-Agent-Session-Id"), null);
  sessionHeader.set(registered.sessionId);
  await client.listReplays("workspace");
  const full = await client.getAgentContext();
  mode = "RETRIEVAL";
  const retrieval = await client.getAgentContext();
  assert.equal(full.runtimeMode, "FULL");
  assert.equal(retrieval.runtimeMode, "RETRIEVAL");
  assert.equal(retrieval.policies[0]?.enforcement, "ADVISORY");
  assert.deepEqual(calls.slice(1).map(call => call.headers.get("X-No8do-Agent-Session-Id")), [sessionId, sessionId, sessionId]);
  assert.equal(calls[2]?.url, `http://localhost:8080/api/agent-sessions/${sessionId}/context`);
});

test("heartbeat usa POST autenticado e sessionId interno sem body de timestamp", async () => {
  const sessionId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const sessionHeader = new AgentSessionHeader();
  sessionHeader.set(sessionId);
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  const fetchImpl = (async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), init });
    return new Response(JSON.stringify({ sessionId, lastSeenAt: "2026-09-23T12:00:00Z" }), { status: 200 });
  }) as typeof fetch;
  const client = new No8doClient("http://localhost:8080", "PAT_PRIVATE", fetchImpl, sessionHeader);

  await client.heartbeatAgentSession(sessionId);

  assert.equal(calls[0]?.url, `http://localhost:8080/api/agent-sessions/${sessionId}/heartbeat`);
  assert.equal(calls[0]?.init?.method, "POST");
  assert.equal(calls[0]?.init?.body, undefined);
  const headers = new Headers(calls[0]?.init?.headers);
  assert.equal(headers.get("Authorization"), "Bearer PAT_PRIVATE");
  assert.equal(headers.get("X-No8do-Agent-Session-Id"), sessionId);
  assert.doesNotMatch(JSON.stringify(calls[0]?.init), /PAT_PRIVATE/);
});

test("propaga AGENT_CAPABILITY_DENIED e metadata sem expor bearer", async () => {
  const response = { error: "AGENT_CAPABILITY_DENIED", metadata: { sessionId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", runtimeMode: "OFF", requiredCapability: "REPLAY_READ" } };
  const setup = client(403, response);
  await assert.rejects(() => setup.client.getReplay("w1", "r1"), (error: unknown) => {
    assert.ok(error instanceof No8doApiError);
    assert.match(error.message, /^AGENT_CAPABILITY_DENIED:/);
    assert.deepEqual(error.metadata, response.metadata);
    assert.doesNotMatch(error.message, /secret-value/);
    return true;
  });
});

test("propaga AGENT_POLICY_DENIED e apenas metadata segura", async () => {
  const response = { error: "AGENT_POLICY_DENIED", metadata: { sessionId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", policyId: "workspace-isolation-required", reason: "workspace mismatch", fingerprint: "sensitive" } };
  const setup = client(403, response);
  await assert.rejects(() => setup.client.listReplays("w1"), (error: unknown) => {
    assert.ok(error instanceof No8doApiError);
    assert.equal(error.status, 403);
    assert.match(error.message, /^AGENT_POLICY_DENIED:/);
    assert.deepEqual(error.metadata, { sessionId: response.metadata.sessionId, policyId: response.metadata.policyId, reason: response.metadata.reason });
    assert.doesNotMatch(error.message, /sensitive|no8do_pat_secret-value/);
    return true;
  });
});

test("propaga AGENT_SESSION_DISCONNECTED e filtra metadata sensível", async () => {
  const response = { error: "AGENT_SESSION_DISCONNECTED", metadata: { sessionId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", fingerprint: "PRIVATE_FINGERPRINT" } };
  const setup = client(409, response);
  await assert.rejects(() => setup.client.getReplay("w1", "r1"), (error: unknown) => {
    assert.ok(error instanceof No8doApiError);
    assert.equal(error.status, 409);
    assert.match(error.message, /^AGENT_SESSION_DISCONNECTED:/);
    assert.deepEqual(error.metadata, { sessionId: response.metadata.sessionId });
    assert.doesNotMatch(error.message, /PRIVATE_FINGERPRINT|no8do_pat_secret-value/);
    return true;
  });
});

test("propaga AGENT_SESSION_REVOKED como código terminal sanitizado", async () => {
  const setup = client(409, { error: "AGENT_SESSION_REVOKED", metadata: { fingerprint: "PRIVATE_FINGERPRINT" } });
  await assert.rejects(() => setup.client.getReplay("w1", "r1"), (error: unknown) => {
    assert.ok(error instanceof No8doApiError);
    assert.equal(error.status, 409);
    assert.equal(error.code, "AGENT_SESSION_REVOKED");
    assert.equal(error.message, "AGENT_SESSION_REVOKED");
    assert.equal(error.metadata, undefined);
    assert.doesNotMatch(JSON.stringify(error), /PRIVATE_FINGERPRINT|no8do_pat_secret-value/);
    return true;
  });
});

test("outros 409, 5xx, falha de rede e payload inválido não viram sessão revogada", async () => {
  for (const setup of [
    client(409, { error: "OTHER_CONFLICT" }),
    client(503, { error: "AGENT_SESSION_REVOKED" }),
    client(409, { error: { code: "AGENT_SESSION_REVOKED" } }),
    { client: new No8doClient("http://localhost:8080", "no8do_pat_secret-value", (async () => { throw new Error("network failed"); }) as typeof fetch) }
  ]) {
    await assert.rejects(() => setup.client.getReplay("w1", "r1"), (error: unknown) => {
      assert.ok(!(error instanceof No8doApiError) || error.code !== "AGENT_SESSION_REVOKED");
      return true;
    });
  }
});
