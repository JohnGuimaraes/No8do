import assert from "node:assert/strict";
import { createServer, request as httpRequest } from "node:http";
import test from "node:test";
import { once } from "node:events";
import { createHash } from "node:crypto";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { AgentSessionHeartbeat } from "./agentSessionHeartbeat.js";
import { renderAgentProtocolBootstrap } from "./agentProtocolBootstrap.js";
import { createRemoteMcpService } from "./http.js";
import type { AgentProtocol } from "./no8doClient.js";

const workspaceId = "11111111-1111-4111-8111-111111111111";
const otherWorkspaceId = "22222222-2222-4222-8222-222222222222";
const agentProtocol: AgentProtocol = {
  protocolName: "no8do-agent-protocol",
  protocolVersion: 1,
  systemName: "No8do",
  purpose: "Memória técnica reutilizável",
  replayGuidance: { summary: "Bootstrap fixture", searchBeforeNonTrivialWork: true, preferExistingKnowledge: true, searchBeforeCreate: true, recordUsageOnlyWhenMateriallyUsed: true, validatedRequiresEvidence: true, avoidTrivialKnowledge: true, avoidDuplicateKnowledge: true, neverStoreSecrets: true, neverStoreCredentials: true, avoidDiscardedAttempts: true },
  capabilities: { capabilities: [{ id: "REPLAY_CREATE", description: "Create Replays", readOnly: false }, { id: "REPLAY_SEARCH", description: "Search Replays", readOnly: true }] },
  policies: { policies: [{ id: "evidence-required-for-validated", description: "Require evidence for validated status", enforcement: "ENFORCED" }, { id: "material-usage-required-for-usage-record", description: "Agent ReplayUsage requires explicit material-use attestation and persisted application evidence.", enforcement: "ENFORCED" }, { id: "workspace-isolation-required", description: "Isolate workspaces", enforcement: "ENFORCED" }] },
  integrationExtensions: { extensions: [{ id: "no8do-integration", version: 1, operationalContext: { version: 1,
    getMethod: "no8do/operational-context/get", updateMethod: "no8do/operational-context/update", optimisticConcurrency: "EXPECTED_VERSION" } }] }
};
async function listen(server: ReturnType<typeof createServer>) { server.listen(0, "127.0.0.1"); await once(server, "listening"); return `http://127.0.0.1:${(server.address() as { port: number }).port}`; }
async function close(server: ReturnType<typeof createServer>) { const closed = once(server, "close"); server.close(); server.closeAllConnections(); await closed; }

async function postJson(url: string, headers: Record<string, string>, value: unknown) {
  return new Promise<{ statusCode: number; headers: import("node:http").IncomingHttpHeaders }>((resolve, reject) => {
    const request = httpRequest(url, { method: "POST", headers: { "content-type": "application/json", accept: "application/json, text/event-stream", ...headers } }, response => {
      response.resume(); response.on("end", () => resolve({ statusCode: response.statusCode ?? 0, headers: response.headers }));
    });
    request.on("error", reject); request.end(JSON.stringify(value));
  });
}

test("remote MCP legacy falha fechado no initialize sem workspace configurado válido", async () => {
  const service = createRemoteMcpService("http://127.0.0.1:9", undefined);
  const url = await listen(service);
  try {
    const initialize = { jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "test", version: "1" } } };
    assert.equal((await postJson(`${url}/mcp`, { Authorization: "Bearer PAT" }, initialize)).statusCode, 400);
    const invalid = createRemoteMcpService("http://127.0.0.1:9", "invalido");
    const invalidUrl = await listen(invalid);
    try { assert.equal((await postJson(`${invalidUrl}/mcp`, { Authorization: "Bearer PAT" }, initialize)).statusCode, 400); }
    finally { await close(invalid); }
  } finally { await close(service); }
});

test("remote MCP protege health e autenticação sem vazar bearer", async () => {
  const service = createRemoteMcpService("http://127.0.0.1:9", workspaceId); const url = await listen(service);
  try {
    const health = await fetch(`${url}/health`); assert.equal(health.status, 200); assert.deepEqual(await health.json(), { status: "ok" });
    for (const auth of [undefined, "Basic PAT_A", "Bearer ", "Bearer    "]) {
      const response = await fetch(`${url}/mcp`, { method: "POST", headers: auth ? { Authorization: auth } : {} });
      assert.equal(response.status, 401); assert.equal((await response.text()).includes("PAT_A"), false);
    }
  } finally { await close(service); }
});

test("Streamable HTTP limita cada instância ao workspace configurado e isola PATs", async () => {
  const received: Array<{ authorization: string; url: string; agentSessionId?: string }> = [];
  const api = createServer((request, response) => {
    received.push({ authorization: request.headers.authorization ?? "", url: request.url ?? "", agentSessionId: request.headers["x-no8do-agent-session-id"] as string | undefined });
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-sessions") return response.end(JSON.stringify({ sessionId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", clientName: "test", clientVersion: "1", workspaceId, transport: "MCP", runtimeMode: "FULL", protocolName: agentProtocol.protocolName, protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" }));
    response.end(JSON.stringify(request.url === "/api/agent-protocol" ? agentProtocol : []));
  });
  const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId); const url = await listen(service);
  const call = async (token: string, args: Record<string, unknown>) => {
    const client = new Client({ name: "test", version: "1" });
    await client.connect(new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { requestInit: { headers: { Authorization: `Bearer ${token}` } } }));
    const tools = await client.listTools(); assert.equal(tools.tools.length, 15);
    const result = await client.callTool({ name: "list_replays", arguments: args }); await client.close(); return result;
  };
  try {
    await Promise.all([call("PAT_A", {}), call("PAT_B", { workspaceId })]);
    assert.deepEqual([...new Set(received.map(({ authorization }) => authorization))].sort(), ["Bearer PAT_A", "Bearer PAT_B"]);
    assert.deepEqual([...new Set(received.map(({ url: requestUrl }) => requestUrl))].sort(), ["/api/agent-protocol", "/api/agent-sessions", `/api/workspaces/${workspaceId}/replays`].sort());
    assert.ok(received.filter(call => call.url.startsWith("/api/workspaces/")).every(call => call.agentSessionId));
    assert.equal(received.find(call => call.url === "/api/agent-sessions")?.agentSessionId, undefined);
    const beforeRejectedReplayCalls = received.filter(call => call.url.startsWith("/api/workspaces/")).length;
    const result = await call("PAT_A", { workspaceId: otherWorkspaceId });
    assert.equal(result.isError, true); assert.match(JSON.stringify(result), /Workspace fora do escopo/);
    assert.equal(received.filter(call => call.url.startsWith("/api/workspaces/")).length, beforeRejectedReplayCalls);
  } finally { await close(service); await close(api); }
});

test("remote MCP encaminha Agent Credential somente ao registration e não a retém na sessão", async () => {
  const agentCredential = "no8do_agent_secret_private";
  const registrations: Array<{ path: string; authorization: string; credential?: string; body: string }> = [];
  const laterRequests: Array<{ path: string; credential?: string }> = [];
  const api = createServer(async (request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
    if (request.url === "/api/agent-sessions") {
      const chunks: Buffer[] = [];
      for await (const chunk of request) chunks.push(Buffer.from(chunk));
      registrations.push({ path: request.url, authorization: request.headers.authorization ?? "",
        credential: request.headers["x-no8do-agent-credential"] as string | undefined,
        body: Buffer.concat(chunks).toString("utf8") });
      return response.end(JSON.stringify({ sessionId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        clientName: "test", clientVersion: "1", workspaceId, transport: "MCP", runtimeMode: "FULL",
        protocolName: agentProtocol.protocolName, protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" }));
    }
    laterRequests.push({ path: request.url ?? "", credential: request.headers["x-no8do-agent-credential"] as string | undefined });
    response.end(JSON.stringify([]));
  });
  const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId); const url = await listen(service);
  const initialize = { jsonrpc: "2.0", id: 1, method: "initialize", params: {
    protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "test", version: "1" }
  } };
  try {
    const initialized = await postJson(`${url}/mcp`, {
      Authorization: "Bearer PAT_PRIVATE", "X-No8do-Agent-Credential": agentCredential
    }, initialize);
    assert.equal(initialized.statusCode, 200);
    const sessionId = initialized.headers["mcp-session-id"] as string;
    const ack = await postJson(`${url}/mcp`, {
      Authorization: "Bearer PAT_PRIVATE", "mcp-session-id": sessionId,
      "mcp-protocol-version": "2025-03-26"
    }, { jsonrpc: "2.0", method: "notifications/initialized" });
    assert.equal(ack.statusCode, 202);
    const toolCall = await postJson(`${url}/mcp`, {
      Authorization: "Bearer PAT_PRIVATE", "mcp-session-id": sessionId,
      "mcp-protocol-version": "2025-03-26"
    }, { jsonrpc: "2.0", id: 2, method: "tools/call", params: { name: "list_replays", arguments: {} } });
    assert.equal(toolCall.statusCode, 200);
    assert.deepEqual(registrations, [{ path: "/api/agent-sessions", authorization: "Bearer PAT_PRIVATE",
      credential: agentCredential, body: JSON.stringify({ clientName: "test", clientVersion: "1", workspaceId: null,
        transport: "MCP", transportSessionFingerprint: createHash("sha256").update(sessionId, "utf8").digest("hex") }) }]);
    assert.doesNotMatch(JSON.stringify(registrations[0]?.body), new RegExp(agentCredential));
    assert.ok(laterRequests.length > 0);
    assert.ok(laterRequests.every(call => call.credential === undefined));
    assert.ok(laterRequests.some(call => call.path === `/api/workspaces/${workspaceId}/replays`));
  } finally { await close(service); await close(api); }
});

test("mesmo Remote MCP atende Agent-bound em Workspaces diferentes sem respeitar o env legado", async () => {
  const sessionA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const sessionB = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
  const registrations: Array<{ credential?: string; body: Record<string, unknown> }> = [];
  const toolRequests: Array<{ url: string; session?: string; credential?: string }> = [];
  const api = createServer(async (request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
    if (request.url === "/api/agent-sessions") {
      const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk));
      const registration = JSON.parse(Buffer.concat(chunks).toString("utf8")) as Record<string, unknown>;
      const credential = request.headers["x-no8do-agent-credential"] as string | undefined;
      registrations.push({ credential, body: registration });
      const isB = credential === "CREDENTIAL_B";
      return response.end(JSON.stringify({ sessionId: isB ? sessionB : sessionA, ...registration,
        workspaceId: isB ? otherWorkspaceId : workspaceId, runtimeMode: "FULL",
        protocolName: agentProtocol.protocolName, protocolVersion: 2, registeredAt: "2026-01-01T00:00:00Z" }));
    }
    toolRequests.push({ url: request.url ?? "", session: request.headers["x-no8do-agent-session-id"] as string | undefined,
      credential: request.headers["x-no8do-agent-credential"] as string | undefined });
    if (request.url?.startsWith("/api/workspaces/")) return response.end(JSON.stringify([]));
    return response.end(JSON.stringify({ error: "AGENT_OPERATIONAL_CONTEXT_NOT_FOUND" }));
  });
  const apiUrl = await listen(api);
  const service = createRemoteMcpService(apiUrl, workspaceId);
  const url = await listen(service);
  const initialize = { jsonrpc: "2.0", id: 1, method: "initialize", params: {
    protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "test", version: "1" }
  } };
  const connect = async (credential: string) => {
    const initialized = await postJson(`${url}/mcp`, { Authorization: "Bearer PAT", "X-No8do-Agent-Credential": credential }, initialize);
    assert.equal(initialized.statusCode, 200);
    const session = initialized.headers["mcp-session-id"] as string;
    assert.ok(session);
    assert.equal((await postJson(`${url}/mcp`, { Authorization: "Bearer PAT", "mcp-session-id": session,
      "mcp-protocol-version": "2025-03-26" }, { jsonrpc: "2.0", method: "notifications/initialized" })).statusCode, 202);
    return session;
  };
  try {
    const transportA = await connect("CREDENTIAL_A");
    const transportB = await connect("CREDENTIAL_B");
    assert.deepEqual(registrations.map(registration => registration.body.workspaceId), [null, null]);
    assert.deepEqual(registrations.map(registration => registration.credential), ["CREDENTIAL_A", "CREDENTIAL_B"]);
    const callTool = (session: string, id: number, arguments_: Record<string, unknown>) => postJson(`${url}/mcp`, {
      Authorization: "Bearer PAT", "mcp-session-id": session, "mcp-protocol-version": "2025-03-26"
    }, { jsonrpc: "2.0", id, method: "tools/call", params: { name: "list_replays", arguments: arguments_ } });
    const toolsA = await postJson(`${url}/mcp`, { Authorization: "Bearer PAT", "mcp-session-id": transportA,
      "mcp-protocol-version": "2025-03-26" }, { jsonrpc: "2.0", id: 10, method: "tools/list", params: {} });
    const toolsB = await postJson(`${url}/mcp`, { Authorization: "Bearer PAT", "mcp-session-id": transportB,
      "mcp-protocol-version": "2025-03-26" }, { jsonrpc: "2.0", id: 11, method: "tools/list", params: {} });
    assert.equal(toolsA.statusCode, 200);
    assert.equal(toolsB.statusCode, 200);
    await callTool(transportA, 12, {});
    await callTool(transportB, 13, {});
    const denied = await callTool(transportB, 14, { workspaceId });
    assert.equal(denied.statusCode, 200);
    assert.ok(toolRequests.some(call => call.url === `/api/workspaces/${workspaceId}/replays` && call.session === sessionA));
    assert.ok(toolRequests.some(call => call.url === `/api/workspaces/${otherWorkspaceId}/replays` && call.session === sessionB));
    assert.ok(toolRequests.every(call => call.credential === undefined));
    assert.equal(toolRequests.filter(call => call.url.startsWith("/api/workspaces/")).length, 2,
      "a mismatched explicit workspace is rejected before reaching the API");
  } finally { await close(service); await close(api); }
});

test("um Remote MCP isola PATs, Workspaces e Operational Context entre Agents", async () => {
  const agentSessionA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const agentSessionB = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
  const bindings = {
    CREDENTIAL_A: { agentId: "agent-a", sessionId: agentSessionA, workspaceId },
    CREDENTIAL_B: { agentId: "agent-b", sessionId: agentSessionB, workspaceId: otherWorkspaceId }
  };
  const registrations: Array<{ authorization: string; credential?: string; agentId?: string; body: Record<string, unknown> }> = [];
  const apiCalls: Array<{ url: string; authorization: string }> = [];
  const toolRequests: Array<{ url: string; authorization: string; agentSessionId?: string }> = [];
  const contextReads: Array<{ url: string; authorization: string; agentSessionId?: string }> = [];
  const contextWrites: Array<{ url: string; authorization: string; agentSessionId?: string; body: Record<string, unknown> }> = [];
  const heartbeatRequests: Array<{ url: string; authorization: string; agentSessionId?: string }> = [];
  const disconnects: string[] = [];
  const heartbeatCallbacks = new Map<string, () => void>();
  const stoppedHeartbeats: string[] = [];
  const storedContexts = new Map<string, Record<string, unknown>>();
  let resolveBothHeartbeats!: () => void;
  const bothHeartbeats = new Promise<void>(resolve => { resolveBothHeartbeats = resolve; });
  const api = createServer(async (request, response) => {
    response.setHeader("content-type", "application/json");
    const authorization = request.headers.authorization ?? "";
    apiCalls.push({ url: request.url ?? "", authorization });
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
    if (request.url === "/api/agent-sessions") {
      const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk));
      const body = JSON.parse(Buffer.concat(chunks).toString("utf8")) as Record<string, unknown>;
      const credential = request.headers["x-no8do-agent-credential"] as string | undefined;
      const binding = credential ? bindings[credential as keyof typeof bindings] : undefined;
      assert.ok(binding, "registration must use one of the two distinct Agent Credentials");
      registrations.push({ authorization, credential, agentId: binding.agentId, body });
      return response.end(JSON.stringify({ sessionId: binding.sessionId, ...body, workspaceId: binding.workspaceId,
        transport: "MCP", runtimeMode: "FULL", protocolName: agentProtocol.protocolName, protocolVersion: 2,
        registeredAt: "2026-01-01T00:00:00Z" }));
    }
    const heartbeatId = [agentSessionA, agentSessionB].find(id => request.url === `/api/agent-sessions/${id}/heartbeat`);
    if (heartbeatId) {
      heartbeatRequests.push({ url: request.url ?? "", authorization,
        agentSessionId: request.headers["x-no8do-agent-session-id"] as string | undefined });
      if (heartbeatRequests.length === 2) resolveBothHeartbeats();
      return response.end(JSON.stringify({ sessionId: heartbeatId, lastSeenAt: "2026-09-23T12:00:00Z" }));
    }
    const disconnectId = [agentSessionA, agentSessionB].find(id => request.url === `/api/agent-sessions/${id}/disconnect`);
    if (disconnectId) {
      disconnects.push(disconnectId);
      return response.end(JSON.stringify({ sessionId: disconnectId, presenceStatus: "DISCONNECTED", disconnectedAt: "2026-09-23T12:00:00Z" }));
    }
    const stateId = request.url?.match(/^\/api\/agent-sessions\/([^/]+)\/operational-context\/state$/)?.[1];
    if (stateId) {
      contextReads.push({ url: request.url ?? "", authorization,
        agentSessionId: request.headers["x-no8do-agent-session-id"] as string | undefined });
      const context = storedContexts.get(stateId);
      return response.end(JSON.stringify(context ? { exists: true, context } : { exists: false }));
    }
    const updateId = request.url?.match(/^\/api\/agent-sessions\/([^/]+)\/operational-context$/)?.[1];
    if (updateId && request.method === "PUT") {
      const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk));
      const body = JSON.parse(Buffer.concat(chunks).toString("utf8")) as Record<string, unknown>;
      contextWrites.push({ url: request.url ?? "", authorization,
        agentSessionId: request.headers["x-no8do-agent-session-id"] as string | undefined, body });
      const context = { sessionId: updateId, version: 0,
        signal: { repository: body.repository, branch: body.branch, workingDirectory: body.workingDirectory, references: body.references },
        resolution: { project: { id: null, status: "UNRESOLVED", confidence: null }, workItem: { id: null, status: "UNRESOLVED", confidence: null } },
        updatedAt: "2026-09-23T12:00:00Z" };
      storedContexts.set(updateId, context);
      return response.end(JSON.stringify(context));
    }
    if (request.url?.startsWith("/api/workspaces/")) {
      toolRequests.push({ url: request.url, authorization,
        agentSessionId: request.headers["x-no8do-agent-session-id"] as string | undefined });
      return response.end(JSON.stringify([]));
    }
    return response.end(JSON.stringify({ error: "AGENT_OPERATIONAL_CONTEXT_NOT_FOUND" }));
  });
  const apiUrl = await listen(api);
  const service = createRemoteMcpService(apiUrl, workspaceId, (client, id) =>
    new AgentSessionHeartbeat(client, id, callback => {
      heartbeatCallbacks.set(id, callback);
      return () => { stoppedHeartbeats.push(id); };
    }));
  const url = await listen(service);
  const initialize = { jsonrpc: "2.0", id: 1, method: "initialize", params: {
    protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "test", version: "1" }
  } };
  const connect = async (pat: string, credential: string) => {
    const initialized = await fetch(`${url}/mcp`, { method: "POST", headers: {
      Authorization: `Bearer ${pat}`, "X-No8do-Agent-Credential": credential,
      "content-type": "application/json", accept: "application/json, text/event-stream"
    }, body: JSON.stringify(initialize) });
    assert.equal(initialized.status, 200);
    const session = initialized.headers.get("mcp-session-id");
    assert.ok(session);
    const ack = await fetch(`${url}/mcp`, { method: "POST", headers: {
      Authorization: `Bearer ${pat}`, "mcp-session-id": session, "mcp-protocol-version": "2025-03-26",
      "content-type": "application/json", accept: "application/json, text/event-stream"
    }, body: JSON.stringify({ jsonrpc: "2.0", method: "notifications/initialized" }) });
    assert.equal(ack.status, 202);
    return session;
  };
  const rpc = async (session: string, pat: string, id: number, method: string, params: Record<string, unknown>) => {
    const response = await fetch(`${url}/mcp`, { method: "POST", headers: {
      Authorization: `Bearer ${pat}`, "mcp-session-id": session, "mcp-protocol-version": "2025-03-26",
      "content-type": "application/json", accept: "application/json, text/event-stream"
    }, body: JSON.stringify({ jsonrpc: "2.0", id, method, params }) });
    const body = response.status === 202 ? undefined : await response.json() as Record<string, unknown>;
    return { status: response.status, body };
  };
  try {
    const mcpSessionA = await connect("PAT-A", "CREDENTIAL_A");
    const mcpSessionB = await connect("PAT-B", "CREDENTIAL_B");
    assert.deepEqual(registrations.map(({ authorization, credential, agentId, body }) => ({ authorization, credential, agentId, workspaceId: body.workspaceId })), [
      { authorization: "Bearer PAT-A", credential: "CREDENTIAL_A", agentId: "agent-a", workspaceId: null },
      { authorization: "Bearer PAT-B", credential: "CREDENTIAL_B", agentId: "agent-b", workspaceId: null }
    ]);

    const toolsA = await rpc(mcpSessionA, "PAT-A", 2, "tools/list", {});
    const toolsB = await rpc(mcpSessionB, "PAT-B", 3, "tools/list", {});
    assert.equal(toolsA.status, 200);
    assert.equal(toolsB.status, 200);
    await rpc(mcpSessionA, "PAT-A", 4, "tools/call", { name: "list_replays", arguments: {} });
    await rpc(mcpSessionB, "PAT-B", 5, "tools/call", { name: "list_replays", arguments: {} });
    const workspaceCallsBeforeMismatch = toolRequests.length;
    await rpc(mcpSessionA, "PAT-A", 6, "tools/call", { name: "list_replays", arguments: { workspaceId: otherWorkspaceId } });
    await rpc(mcpSessionB, "PAT-B", 7, "tools/call", { name: "list_replays", arguments: { workspaceId } });
    assert.equal(toolRequests.length, workspaceCallsBeforeMismatch, "mismatched Workspace calls must be rejected locally");
    assert.deepEqual(toolRequests.map(call => [call.url, call.authorization, call.agentSessionId]), [
      [`/api/workspaces/${workspaceId}/replays`, "Bearer PAT-A", agentSessionA],
      [`/api/workspaces/${otherWorkspaceId}/replays`, "Bearer PAT-B", agentSessionB]
    ]);

    const getA = await rpc(mcpSessionA, "PAT-A", 8, "no8do/operational-context/get", {});
    const getB = await rpc(mcpSessionB, "PAT-B", 9, "no8do/operational-context/get", {});
    assert.deepEqual(getA.body?.result, { exists: false });
    assert.deepEqual(getB.body?.result, { exists: false });
    const readsBeforeCallerSessionId = contextReads.length;
    const injectedSession = await rpc(mcpSessionA, "PAT-A", 10, "no8do/operational-context/get", { sessionId: agentSessionB });
    assert.ok(injectedSession.body?.error, "GET must reject caller-supplied sessionId");
    assert.equal(contextReads.length, readsBeforeCallerSessionId, "caller-supplied sessionId must not reach the API");

    const updateA = { expectedVersion: null, repository: { vcs: "GIT", provider: "GITHUB", host: "github.com", namespace: "org-a", name: "repo-a" },
      branch: "branch-a", workingDirectory: "cwd-a", references: [{ kind: "ISSUE", provider: "GITHUB", key: "A-1" }] };
    const updateB = { expectedVersion: null, repository: { vcs: "GIT", provider: "GITHUB", host: "github.com", namespace: "org-b", name: "repo-b" },
      branch: "branch-b", workingDirectory: "cwd-b", references: [{ kind: "TICKET", provider: "INTERNAL", key: "B-2" }] };
    const writeA = await rpc(mcpSessionA, "PAT-A", 11, "no8do/operational-context/update", updateA);
    const writeB = await rpc(mcpSessionB, "PAT-B", 12, "no8do/operational-context/update", updateB);
    assert.equal((writeA.body?.result as { version: number }).version, 0);
    assert.equal((writeB.body?.result as { version: number }).version, 0);
    assert.deepEqual(contextWrites.map(({ url, authorization, agentSessionId, body }) => ({ url, authorization, agentSessionId, body })), [
      { url: `/api/agent-sessions/${agentSessionA}/operational-context`, authorization: "Bearer PAT-A", agentSessionId: agentSessionA, body: updateA },
      { url: `/api/agent-sessions/${agentSessionB}/operational-context`, authorization: "Bearer PAT-B", agentSessionId: agentSessionB, body: updateB }
    ]);
    assert.equal(Object.hasOwn(contextWrites[0]!.body, "sessionId"), false);
    assert.equal(Object.hasOwn(contextWrites[1]!.body, "sessionId"), false);
    const savedA = await rpc(mcpSessionA, "PAT-A", 13, "no8do/operational-context/get", {});
    const savedB = await rpc(mcpSessionB, "PAT-B", 14, "no8do/operational-context/get", {});
    assert.equal((savedA.body?.result as { context: { sessionId: string; version: number; signal: { repository: { namespace: string } } } }).context.sessionId, agentSessionA);
    assert.equal((savedA.body?.result as { context: { version: number; signal: { repository: { namespace: string } } } }).context.version, 0);
    assert.equal((savedA.body?.result as { context: { signal: { repository: { namespace: string } } } }).context.signal.repository.namespace, "org-a");
    assert.equal((savedB.body?.result as { context: { sessionId: string; version: number; signal: { repository: { namespace: string } } } }).context.sessionId, agentSessionB);
    assert.equal((savedB.body?.result as { context: { version: number; signal: { repository: { namespace: string } } } }).context.version, 0);
    assert.equal((savedB.body?.result as { context: { signal: { repository: { namespace: string } } } }).context.signal.repository.namespace, "org-b");
    assert.deepEqual(contextReads.map(read => [read.url, read.authorization, read.agentSessionId]), [
      [`/api/agent-sessions/${agentSessionA}/operational-context/state`, "Bearer PAT-A", agentSessionA],
      [`/api/agent-sessions/${agentSessionB}/operational-context/state`, "Bearer PAT-B", agentSessionB],
      [`/api/agent-sessions/${agentSessionA}/operational-context/state`, "Bearer PAT-A", agentSessionA],
      [`/api/agent-sessions/${agentSessionB}/operational-context/state`, "Bearer PAT-B", agentSessionB]
    ]);

    assert.deepEqual([...heartbeatCallbacks.keys()], [agentSessionA, agentSessionB]);
    heartbeatCallbacks.get(agentSessionA)?.();
    heartbeatCallbacks.get(agentSessionB)?.();
    await bothHeartbeats;
    assert.deepEqual(heartbeatRequests.map(call => [call.url, call.authorization, call.agentSessionId]), [
      [`/api/agent-sessions/${agentSessionA}/heartbeat`, "Bearer PAT-A", undefined],
      [`/api/agent-sessions/${agentSessionB}/heartbeat`, "Bearer PAT-B", undefined]
    ]);

    const backendCallsBeforeCrossPat = apiCalls.length;
    const crossPatA = await rpc(mcpSessionA, "PAT-B", 15, "tools/call", { name: "list_replays", arguments: {} });
    const crossPatB = await rpc(mcpSessionB, "PAT-A", 16, "tools/call", { name: "list_replays", arguments: {} });
    assert.equal(crossPatA.status, 401);
    assert.equal(crossPatB.status, 401);
    assert.equal(apiCalls.length, backendCallsBeforeCrossPat, "cross-PAT requests must be rejected before backend access");

    const closeA = await fetch(`${url}/mcp`, { method: "DELETE", headers: {
      Authorization: "Bearer PAT-A", "mcp-session-id": mcpSessionA, "mcp-protocol-version": "2025-03-26"
    } });
    assert.equal(closeA.status, 200);
    assert.deepEqual(stoppedHeartbeats, [agentSessionA]);
    assert.deepEqual(disconnects, [agentSessionA]);
    const closeB = await fetch(`${url}/mcp`, { method: "DELETE", headers: {
      Authorization: "Bearer PAT-B", "mcp-session-id": mcpSessionB, "mcp-protocol-version": "2025-03-26"
    } });
    assert.equal(closeB.status, 200);
    assert.deepEqual(stoppedHeartbeats, [agentSessionA, agentSessionB]);
    assert.deepEqual(disconnects, [agentSessionA, agentSessionB]);
  } finally { await close(service); await close(api); }
});

test("Streamable HTTP propaga 401 e 403 da API sem vazar PAT", async () => {
  for (const status of [401, 403]) {
    const api = createServer((request, response) => {
      response.setHeader("content-type", "application/json");
      if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
      if (request.url === "/api/agent-sessions") return response.end(JSON.stringify({ sessionId: "session", clientName: "test", clientVersion: "1", workspaceId, transport: "MCP", runtimeMode: "FULL", protocolName: agentProtocol.protocolName, protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" }));
      response.writeHead(status); response.end(JSON.stringify({ token: "PAT_SECRET" }));
    });
    const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId); const url = await listen(service);
    try {
      const client = new Client({ name: "test", version: "1" });
      await client.connect(new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { requestInit: { headers: { Authorization: "Bearer PAT_SECRET" } } }));
      const result = await client.callTool({ name: "list_replays", arguments: { workspaceId } });
      assert.equal(result.isError, true); assert.doesNotMatch(JSON.stringify(result), /PAT_SECRET/); await client.close();
    } finally { await close(service); await close(api); }
  }
});

test("tool MCP propaga AGENT_CAPABILITY_DENIED sem expor credenciais", async () => {
  const sessionId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const api = createServer((request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
    if (request.url === "/api/agent-sessions") return response.end(JSON.stringify({ sessionId, clientName: "test", clientVersion: "1", workspaceId, transport: "MCP", runtimeMode: "FULL", protocolName: agentProtocol.protocolName, protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" }));
    response.writeHead(403);
    response.end(JSON.stringify({ error: "AGENT_CAPABILITY_DENIED", metadata: { sessionId, runtimeMode: "OFF", requiredCapability: "REPLAY_READ" } }));
  });
  const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId); const url = await listen(service);
  const client = new Client({ name: "test", version: "1" });
  try {
    await client.connect(new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { requestInit: { headers: { Authorization: "Bearer PAT_PRIVATE" } } }));
    const result = await client.callTool({ name: "list_replays", arguments: { workspaceId } });
    assert.equal(result.isError, true);
    assert.match(JSON.stringify(result), /AGENT_CAPABILITY_DENIED/);
    assert.match(JSON.stringify(result), /REPLAY_READ/);
    assert.doesNotMatch(JSON.stringify(result), /PAT_PRIVATE/);
  } finally { await client.close(); await close(service); await close(api); }
});

test("tool MCP propaga AGENT_POLICY_DENIED com policyId e sem expor credenciais", async () => {
  const sessionId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const api = createServer((request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
    if (request.url === "/api/agent-sessions") return response.end(JSON.stringify({ sessionId, clientName: "test", clientVersion: "1", workspaceId, transport: "MCP", runtimeMode: "FULL", protocolName: agentProtocol.protocolName, protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" }));
    response.writeHead(403);
    response.end(JSON.stringify({ error: "AGENT_POLICY_DENIED", metadata: { sessionId, policyId: "workspace-isolation-required", reason: "workspace mismatch", fingerprint: "PRIVATE_FINGERPRINT" } }));
  });
  const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId); const url = await listen(service);
  const client = new Client({ name: "test", version: "1" });
  try {
    await client.connect(new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { requestInit: { headers: { Authorization: "Bearer PAT_PRIVATE" } } }));
    const result = await client.callTool({ name: "list_replays", arguments: {} });
    assert.equal(result.isError, true);
    assert.match(JSON.stringify(result), /AGENT_POLICY_DENIED/);
    assert.match(JSON.stringify(result), /workspace-isolation-required/);
    assert.doesNotMatch(JSON.stringify(result), /PAT_PRIVATE|PRIVATE_FINGERPRINT/);
  } finally { await client.close(); await close(service); await close(api); }
});

test("MCP exige evidência ao criar VALIDATED, aceita evidence em create/update e expõe policy ENFORCED", async () => {
  const sessionId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const mutations: Array<{ method: string; body: Record<string, unknown> }> = [];
  const api = createServer(async (request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
    if (request.url === "/api/agent-sessions") return response.end(JSON.stringify({ sessionId, clientName: "test", clientVersion: "1", workspaceId, transport: "MCP", runtimeMode: "FULL", protocolName: agentProtocol.protocolName, protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" }));
    if (request.url === `/api/agent-sessions/${sessionId}/context`) return response.end(JSON.stringify({
      sessionId, clientName: "test", clientVersion: "1", workspaceId, transport: "MCP", protocolName: agentProtocol.protocolName,
      protocolVersion: 1, runtimeMode: "FULL", effectiveCapabilities: agentProtocol.capabilities.capabilities,
      policies: agentProtocol.policies.policies, registeredAt: "2026-01-01T00:00:00Z",
      presenceStatus: "CONNECTED", lastSeenAt: "2026-01-01T00:00:00Z", lastActivityAt: null, disconnectedAt: null
    }));
    const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk));
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8")) as Record<string, unknown>;
    mutations.push({ method: request.method ?? "GET", body });
    if (request.method === "POST" && body.status === "VALIDATED" && !body.validationEvidence) {
      response.writeHead(403);
      return response.end(JSON.stringify({ error: "AGENT_POLICY_DENIED", metadata: { sessionId, policyId: "evidence-required-for-validated", reason: "evidence required" } }));
    }
    if (request.url?.endsWith("/usages") && (body.materiallyUsed !== true || typeof body.context !== "string" || !body.context.trim())) {
      response.writeHead(403);
      return response.end(JSON.stringify({ error: "AGENT_POLICY_DENIED", metadata: { sessionId, policyId: "material-usage-required-for-usage-record", reason: "attestation and evidence required" } }));
    }
    return response.end(JSON.stringify({ id: "replay-1", ...body }));
  });
  const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId); const url = await listen(service);
  const client = new Client({ name: "validation-evidence-test", version: "1" });
  const evidence = { summary: "Validation passed", method: "automated tests", reference: "ci://run/1" };
  try {
    await client.connect(new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { requestInit: { headers: { Authorization: "Bearer PAT_PRIVATE" } } }));
    const tools = (await client.listTools()).tools;
    assert.ok(tools.find(tool => tool.name === "create_replay")?.inputSchema.properties?.validationEvidence);
    assert.ok(tools.find(tool => tool.name === "update_replay")?.inputSchema.properties?.validationEvidence);
    const usageTool = tools.find(tool => tool.name === "register_replay_usage");
    assert.ok(usageTool?.inputSchema.properties?.materiallyUsed);
    assert.ok(usageTool?.inputSchema.properties?.context);
    assert.match(usageTool?.description ?? "", /somente após aplicar materialmente/);
    const protocol = await client.callTool({ name: "get_agent_protocol" });
    assert.equal((protocol.structuredContent as AgentProtocol).policies.policies.find(policy => policy.id === "evidence-required-for-validated")?.enforcement, "ENFORCED");
    assert.equal((protocol.structuredContent as AgentProtocol).policies.policies.find(policy => policy.id === "material-usage-required-for-usage-record")?.enforcement, "ENFORCED");
    const context = await client.callTool({ name: "get_agent_context", arguments: {} });
    assert.equal((context.structuredContent as { policies: AgentProtocol["policies"]["policies"] }).policies.find(policy => policy.id === "evidence-required-for-validated")?.enforcement, "ENFORCED");
    assert.equal((context.structuredContent as { policies: AgentProtocol["policies"]["policies"] }).policies.find(policy => policy.id === "material-usage-required-for-usage-record")?.enforcement, "ENFORCED");

    const denied = await client.callTool({ name: "create_replay", arguments: { workspaceId, title: "No evidence", type: "FIX", status: "VALIDATED" } });
    assert.equal(denied.isError, true);
    assert.match(JSON.stringify(denied), /AGENT_POLICY_DENIED/);
    assert.match(JSON.stringify(denied), /evidence-required-for-validated/);
    const beforeValidCreate = mutations.length;
    await client.callTool({ name: "create_replay", arguments: { workspaceId, title: "With evidence", type: "FIX", status: "VALIDATED", validationEvidence: evidence } });
    assert.deepEqual(mutations[beforeValidCreate]?.body.validationEvidence, evidence, `index=${beforeValidCreate}; calls=${JSON.stringify(mutations)}`);
    const beforeUpdate = mutations.length;
    const updated = await client.callTool({ name: "update_replay", arguments: { workspaceId, replayId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", status: "VALIDATED", validationEvidence: evidence } });
    assert.equal(updated.isError, undefined);
    assert.deepEqual(mutations[beforeUpdate]?.body.validationEvidence, evidence);
    const usageArgs = { workspaceId, replayId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", result: "SUCCESS" as const };
    const missingUsage = await client.callTool({ name: "register_replay_usage", arguments: usageArgs });
    assert.equal(missingUsage.isError, true);
    assert.match(JSON.stringify(missingUsage), /AGENT_POLICY_DENIED/);
    assert.match(JSON.stringify(missingUsage), /material-usage-required-for-usage-record/);
    const falseUsage = await client.callTool({ name: "register_replay_usage", arguments: { ...usageArgs, materiallyUsed: false, context: "Aplicado" } });
    assert.equal(falseUsage.isError, true);
    const beforeValidUsage = mutations.length;
    const validUsage = await client.callTool({ name: "register_replay_usage", arguments: { ...usageArgs, materiallyUsed: true, context: "Aplicado ao corrigir a migração de importação." } });
    assert.equal(validUsage.isError, undefined);
    assert.deepEqual(mutations[beforeValidUsage]?.body, { result: "SUCCESS", materiallyUsed: true, context: "Aplicado ao corrigir a migração de importação.", source: "MCP" });
  } finally { await client.close(); await close(service); await close(api); }
});

test("cliente MCP recebe instructions e discovery estruturado do protocolo da API", async () => {
  const received: Array<{ authorization: string; url: string }> = [];
  const api = createServer((request, response) => {
    received.push({ authorization: request.headers.authorization ?? "", url: request.url ?? "" });
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
    if (request.url === "/api/agent-sessions") return response.end(JSON.stringify({ sessionId: "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
      clientName: "bootstrap-test", clientVersion: "1", workspaceId, transport: "MCP", runtimeMode: "FULL",
      protocolName: agentProtocol.protocolName, protocolVersion: agentProtocol.protocolVersion, registeredAt: "2026-01-01T00:00:00Z" }));
    response.end(JSON.stringify([]));
  });
  const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId); const url = await listen(service);
  const client = new Client({ name: "bootstrap-test", version: "1" });
  try {
    await client.connect(new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { requestInit: { headers: { Authorization: "Bearer PAT_DISCOVERY" } } }));

    const instructions = client.getInstructions();
    assert.equal(instructions, renderAgentProtocolBootstrap(agentProtocol));
    assert.match(instructions ?? "", /No8do/);
    const tools = await client.listTools();
    assert.equal(tools.tools.length, 15);
    assert.deepEqual(tools.tools.find(tool => tool.name === "get_agent_protocol")?.inputSchema?.properties, {});
    const result = await client.callTool({ name: "get_agent_protocol" });
    assert.equal(result.isError, undefined);
    const structuredProtocol = result.structuredContent as AgentProtocol;
    assert.equal(structuredProtocol.protocolName, agentProtocol.protocolName);
    assert.equal(structuredProtocol.protocolVersion, agentProtocol.protocolVersion);
    assert.equal(structuredProtocol.systemName, agentProtocol.systemName);
    assert.equal(structuredProtocol.purpose, agentProtocol.purpose);
    assert.deepEqual(structuredProtocol.replayGuidance, agentProtocol.replayGuidance);
    assert.deepEqual(structuredProtocol.capabilities, agentProtocol.capabilities);
    assert.deepEqual(structuredProtocol.policies, agentProtocol.policies);
    assert.equal(structuredProtocol.policies.policies.find(policy => policy.id === "evidence-required-for-validated")?.enforcement, "ENFORCED");
    const capabilityIds = structuredProtocol.capabilities.capabilities.map(capability => capability.id);
    const policyIds = structuredProtocol.policies.policies.map(policy => policy.id);
    assert.deepEqual(capabilityIds, ["REPLAY_CREATE", "REPLAY_SEARCH"]);
    assert.deepEqual(policyIds, ["evidence-required-for-validated", "material-usage-required-for-usage-record", "workspace-isolation-required"]);
    assert.equal(new Set(capabilityIds).size, capabilityIds.length);
    assert.equal(new Set(policyIds).size, policyIds.length);
    assert.equal(structuredProtocol.policies.policies[0]?.enforcement, "ENFORCED");
    assert.equal(structuredProtocol.policies.policies[2]?.enforcement, "ENFORCED");
    assert.equal((result.content as Array<{ text: string }>)[0]?.text, "No8do Agent Protocol no8do-agent-protocol v1");
    assert.ok(received.length > 0);
    assert.ok(received.some(call => call.url === "/api/agent-sessions" && call.authorization === "Bearer PAT_DISCOVERY"));
    assert.ok(received.every(call => call.authorization === "Bearer PAT_DISCOVERY"));
  } finally { await client.close(); await close(service); await close(api); }
});

test("initialize registra uma vez por sessão, preserva clientInfo e cria fingerprint distinto na segunda conexão", async () => {
  const registrations: Array<{ body: Record<string, unknown>; authorization: string }> = [];
  const toolCalls: Array<{ url: string; agentSessionId?: string }> = [];
  const heartbeatCalls: Array<{ url: string; authorization: string; agentSessionId?: string }> = [];
  const disconnectCalls: string[] = [];
  const lifecycleEvents: string[] = [];
  const heartbeatTicks: Array<() => void> = [];
  const stoppedHeartbeats: string[] = [];
  let resolveHeartbeatRequest!: () => void;
  const heartbeatReceived = new Promise<void>(resolve => { resolveHeartbeatRequest = resolve; });
  let runtimeMode = "FULL";
  const firstAgentSessionId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const secondAgentSessionId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
  const api = createServer(async (request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
    const heartbeatSessionId = [firstAgentSessionId, secondAgentSessionId].find(id => request.url === `/api/agent-sessions/${id}/heartbeat`);
    if (heartbeatSessionId) {
      heartbeatCalls.push({ url: request.url ?? "", authorization: request.headers.authorization ?? "",
        agentSessionId: request.headers["x-no8do-agent-session-id"] as string | undefined });
      resolveHeartbeatRequest();
      return response.end(JSON.stringify({ sessionId: heartbeatSessionId, lastSeenAt: "2026-09-23T12:00:00Z" }));
    }
    const disconnectSessionId = [firstAgentSessionId, secondAgentSessionId].find(id => request.url === `/api/agent-sessions/${id}/disconnect`);
    if (disconnectSessionId) {
      disconnectCalls.push(disconnectSessionId);
      lifecycleEvents.push(`disconnect:${disconnectSessionId}`);
      return response.end(JSON.stringify({ sessionId: disconnectSessionId, presenceStatus: "DISCONNECTED", disconnectedAt: "2026-09-23T12:00:00Z" }));
    }
    const contextSessionId = [firstAgentSessionId, secondAgentSessionId].find(id => request.url === `/api/agent-sessions/${id}/context`);
    if (contextSessionId) {
      toolCalls.push({ url: request.url ?? "", agentSessionId: request.headers["x-no8do-agent-session-id"] as string | undefined });
      return response.end(JSON.stringify({ sessionId: contextSessionId, clientName: "Codex Desktop", clientVersion: "9.8", workspaceId,
        transport: "MCP", protocolName: agentProtocol.protocolName, protocolVersion: 1, runtimeMode,
        effectiveCapabilities: agentProtocol.capabilities.capabilities, policies: agentProtocol.policies.policies, registeredAt: "2026-01-01T00:00:00Z",
        presenceStatus: "CONNECTED", lastSeenAt: "2026-01-01T00:00:00Z", lastActivityAt: null, disconnectedAt: null }));
    }
    if (request.url === `/api/workspaces/${workspaceId}/replays`) {
      toolCalls.push({ url: request.url, agentSessionId: request.headers["x-no8do-agent-session-id"] as string | undefined });
      return response.end(JSON.stringify([]));
    }
    if (request.url === "/api/agent-sessions") {
      const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk));
      const registration = JSON.parse(Buffer.concat(chunks).toString("utf8")) as Record<string, unknown>;
      registrations.push({ body: registration, authorization: request.headers.authorization ?? "" });
      const sessionId = registrations.length === 1 ? firstAgentSessionId : secondAgentSessionId;
      return response.end(JSON.stringify({ sessionId, ...registration, runtimeMode: "FULL", protocolName: agentProtocol.protocolName, protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" }));
    }
    return response.end(JSON.stringify([]));
  });
  const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId, (client, agentSessionId) =>
    new AgentSessionHeartbeat(client, agentSessionId, callback => {
      heartbeatTicks.push(callback);
      return () => { stoppedHeartbeats.push(agentSessionId); lifecycleEvents.push(`stop:${agentSessionId}`); };
    })); const url = await listen(service);
  const connect = async () => {
    const client = new Client({ name: "Codex Desktop", version: "9.8" });
    const transport = new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { requestInit: { headers: { Authorization: "Bearer PAT_NOT_PERSISTED" } } });
    await client.connect(transport);
    return { client, transport };
  };
  try {
    const first = await connect();
    assert.equal(first.client.getInstructions(), renderAgentProtocolBootstrap(agentProtocol));
    assert.equal((await first.client.listTools()).tools.length, 15);
    const tools = (await first.client.listTools()).tools;
    assert.equal(tools.some(tool => tool.name === "heartbeat"), false);
    assert.equal(tools.some(tool => tool.name === "disconnect"), false);
    const contextTool = tools.find(tool => tool.name === "get_agent_context");
    assert.equal(heartbeatTicks.length, 1);
    assert.equal(heartbeatCalls.length, 0);
    assert.deepEqual(contextTool?.inputSchema.properties, {});
    assert.doesNotMatch(JSON.stringify(contextTool?.inputSchema), /sessionId/);
    await first.client.callTool({ name: "get_agent_protocol" });
    const initialContext = await first.client.callTool({ name: "get_agent_context", arguments: {} });
    assert.equal((initialContext.structuredContent as { runtimeMode: string } | undefined)?.runtimeMode, "FULL", JSON.stringify(initialContext));
    assert.equal((initialContext.structuredContent as { disconnectedAt: string | null }).disconnectedAt, null);
    assert.deepEqual((initialContext.structuredContent as { policies: unknown }).policies, agentProtocol.policies.policies);
    runtimeMode = "RETRIEVAL";
    const refreshedContext = await first.client.callTool({ name: "get_agent_context", arguments: {} });
    assert.equal((refreshedContext.structuredContent as { runtimeMode: string }).runtimeMode, "RETRIEVAL");
    await first.client.callTool({ name: "list_replays", arguments: {} });
    assert.equal(toolCalls.length, 3);
    assert.ok(toolCalls.every(call => call.agentSessionId === firstAgentSessionId));
    assert.equal(registrations.length, 1);
    const firstRegistration = registrations[0]!;
    assert.deepEqual(firstRegistration.body, {
      clientName: "Codex Desktop", clientVersion: "9.8", workspaceId, transport: "MCP",
      transportSessionFingerprint: createHash("sha256").update(first.transport.sessionId!, "utf8").digest("hex")
    });
    assert.equal(firstRegistration.authorization, "Bearer PAT_NOT_PERSISTED");
    assert.doesNotMatch(JSON.stringify(firstRegistration.body), /PAT_NOT_PERSISTED|Authorization|sessionId/);

    heartbeatTicks[0]?.();
    await heartbeatReceived;
    assert.deepEqual(heartbeatCalls[0], {
      url: `/api/agent-sessions/${firstAgentSessionId}/heartbeat`,
      authorization: "Bearer PAT_NOT_PERSISTED",
      agentSessionId: undefined
    });
    assert.equal(registrations.length, 1);

    const initializeEnvelope = { jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "Codex Desktop", version: "9.8" } } };
    const secondInitialize = await postJson(`${url}/mcp`, { Authorization: "Bearer PAT_NOT_PERSISTED" }, initializeEnvelope);
    assert.equal(secondInitialize.statusCode, 200);
    const secondSessionHeader = secondInitialize.headers["mcp-session-id"];
    assert.equal(typeof secondSessionHeader, "string");
    const secondSessionId = secondSessionHeader as string;
    const initialized = await postJson(`${url}/mcp`, { Authorization: "Bearer PAT_NOT_PERSISTED", "mcp-session-id": secondSessionId, "mcp-protocol-version": "2025-03-26" }, { jsonrpc: "2.0", method: "notifications/initialized" });
    assert.equal(initialized.statusCode, 202);
    assert.equal(registrations.length, 2);
    assert.equal(heartbeatTicks.length, 2);
    assert.notEqual(registrations[0]?.body.transportSessionFingerprint, registrations[1]?.body.transportSessionFingerprint);
    assert.equal(registrations[1]?.body.transportSessionFingerprint, createHash("sha256").update(secondSessionId!, "utf8").digest("hex"));

    const closedSession = await fetch(`${url}/mcp`, { method: "DELETE", headers: {
      Authorization: "Bearer PAT_NOT_PERSISTED", "mcp-session-id": first.transport.sessionId!, "mcp-protocol-version": "2025-03-26"
    } });
    assert.equal(closedSession.status, 200);
    assert.deepEqual(stoppedHeartbeats, [firstAgentSessionId]);
    assert.deepEqual(disconnectCalls, [firstAgentSessionId]);
    assert.deepEqual(lifecycleEvents, [`stop:${firstAgentSessionId}`, `disconnect:${firstAgentSessionId}`]);
    const closedSecondSession = await fetch(`${url}/mcp`, { method: "DELETE", headers: {
      Authorization: "Bearer PAT_NOT_PERSISTED", "mcp-session-id": secondSessionId, "mcp-protocol-version": "2025-03-26"
    } });
    assert.equal(closedSecondSession.status, 200);
    assert.deepEqual(stoppedHeartbeats, [firstAgentSessionId, secondAgentSessionId]);
    assert.deepEqual(disconnectCalls, [firstAgentSessionId, secondAgentSessionId]);
    assert.deepEqual(lifecycleEvents, [
      `stop:${firstAgentSessionId}`, `disconnect:${firstAgentSessionId}`,
      `stop:${secondAgentSessionId}`, `disconnect:${secondAgentSessionId}`
    ]);
  } finally {
    await close(service);
    await close(api);
  }
});

test("fechar o servidor HTTP encerra heartbeat de sessões ainda abertas", async () => {
  const sessionId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
  const stopped: string[] = [];
  const events: string[] = [];
  let resolveDisconnected!: () => void;
  const disconnected = new Promise<void>(resolve => { resolveDisconnected = resolve; });
  const api = createServer((request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
    if (request.url === "/api/agent-sessions") return response.end(JSON.stringify({ sessionId, clientName: "Codex", clientVersion: "1", workspaceId, transport: "MCP", runtimeMode: "FULL", protocolName: agentProtocol.protocolName, protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" }));
    if (request.url === `/api/agent-sessions/${sessionId}/disconnect`) {
      events.push("disconnect");
      resolveDisconnected();
      return response.end(JSON.stringify({ sessionId, presenceStatus: "DISCONNECTED", disconnectedAt: "2026-09-23T12:00:00Z" }));
    }
    return response.end(JSON.stringify([]));
  });
  const apiUrl = await listen(api);
  const service = createRemoteMcpService(apiUrl, workspaceId, (client, id) =>
    new AgentSessionHeartbeat(client, id, () => () => { stopped.push(id); events.push("stop"); }));
  const url = await listen(service);
  const client = new Client({ name: "shutdown-test", version: "1" });
  try {
    await client.connect(new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { requestInit: { headers: { Authorization: "Bearer PAT_SHUTDOWN" } } }));
    await close(service);
    await disconnected;
    assert.deepEqual(stopped, [sessionId]);
    assert.deepEqual(events, ["stop", "disconnect"]);
  } finally {
    if (service.listening) await close(service);
    await client.close().catch(() => undefined);
    await close(api);
  }
});

test("falha de registro impede resposta de initialize bem-sucedida", async () => {
  const api = createServer((request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
    response.writeHead(503); response.end(JSON.stringify({ token: "PAT_SECRET" }));
  });
  const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId); const url = await listen(service);
  try {
    const response = await fetch(`${url}/mcp`, { method: "POST", headers: { Authorization: "Bearer PAT_SECRET", "content-type": "application/json" }, body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "test", version: "1" } } }) });
    assert.notEqual(response.status, 200);
    assert.doesNotMatch(await response.text(), /PAT_SECRET/);
  } finally { await close(service); await close(api); }
});

test("discovery autenticado rejeita PAT inválido antes de inicializar sem vazar credencial", async () => {
  const api = createServer((_request, response) => { response.writeHead(401, { "content-type": "application/json" }); response.end(JSON.stringify({ token: "PAT_SECRET" })); });
  const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId); const url = await listen(service);
  try {
    const response = await fetch(`${url}/mcp`, { method: "POST", headers: { Authorization: "Bearer PAT_SECRET", "content-type": "application/json" }, body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "test", version: "1" } } }) });
    assert.equal(response.status, 401);
    assert.doesNotMatch(await response.text(), /PAT_SECRET/);
  } finally { await close(service); await close(api); }
});

test("operação revogada encerra somente o lifecycle HTTP correspondente sem disconnect", async () => {
  const firstSessionId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const secondSessionId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
  const ticks: Array<() => void> = [];
  const stopped: string[] = [];
  const heartbeats: string[] = [];
  const disconnects: string[] = [];
  let resolveSecondHeartbeat!: () => void;
  const secondHeartbeat = new Promise<void>(resolve => { resolveSecondHeartbeat = resolve; });
  let resolveSecondDisconnect!: () => void;
  const secondDisconnected = new Promise<void>(resolve => { resolveSecondDisconnect = resolve; });
  let registrations = 0;
  const api = createServer((request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
    if (request.url === "/api/agent-sessions") {
      const sessionId = ++registrations === 1 ? firstSessionId : secondSessionId;
      return response.end(JSON.stringify({ sessionId, clientName: "test", clientVersion: "1", workspaceId, transport: "MCP", runtimeMode: "FULL", protocolName: agentProtocol.protocolName, protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" }));
    }
    const heartbeatId = [firstSessionId, secondSessionId].find(id => request.url === `/api/agent-sessions/${id}/heartbeat`);
    if (heartbeatId) {
      heartbeats.push(heartbeatId);
      if (heartbeatId === secondSessionId) resolveSecondHeartbeat();
      return response.end(JSON.stringify({ sessionId: heartbeatId, lastSeenAt: "2026-09-23T12:00:00Z" }));
    }
    const disconnectId = [firstSessionId, secondSessionId].find(id => request.url === `/api/agent-sessions/${id}/disconnect`);
    if (disconnectId) {
      disconnects.push(disconnectId);
      if (disconnectId === secondSessionId) resolveSecondDisconnect();
      return response.end(JSON.stringify({ sessionId: disconnectId, disconnectedAt: "2026-09-23T12:00:00Z" }));
    }
    if (request.url === `/api/workspaces/${workspaceId}/replays`) {
      const sessionId = request.headers["x-no8do-agent-session-id"];
      if (sessionId === firstSessionId) {
        response.writeHead(409);
        return response.end(JSON.stringify({ error: "AGENT_SESSION_REVOKED", fingerprint: "PRIVATE_FINGERPRINT" }));
      }
      return response.end(JSON.stringify([]));
    }
    return response.end(JSON.stringify([]));
  });
  const apiUrl = await listen(api);
  const service = createRemoteMcpService(apiUrl, workspaceId, (client, id) =>
    new AgentSessionHeartbeat(client, id, callback => { ticks.push(callback); return () => { stopped.push(id); }; }));
  const url = await listen(service);
  const connect = async () => {
    const client = new Client({ name: "test", version: "1" });
    const transport = new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { requestInit: { headers: { Authorization: "Bearer PAT_PRIVATE" } } });
    await client.connect(transport);
    return { client, transport };
  };
  const first = await connect(); const second = await connect();
  try {
    const revoked = await first.client.callTool({ name: "list_replays", arguments: {} });
    assert.equal(revoked.isError, true);
    assert.match(JSON.stringify(revoked), /AGENT_SESSION_REVOKED/);
    assert.doesNotMatch(JSON.stringify(revoked), /PRIVATE_FINGERPRINT|PAT_PRIVATE/);
    ticks[0]?.(); ticks[1]?.();
    await secondHeartbeat;
    assert.deepEqual(stopped, [firstSessionId]);
    assert.deepEqual(heartbeats, [secondSessionId]);
    assert.equal(registrations, 2);
    const closeFirst = await fetch(`${url}/mcp`, { method: "DELETE", headers: { Authorization: "Bearer PAT_PRIVATE", "mcp-session-id": first.transport.sessionId!, "mcp-protocol-version": "2025-03-26" } });
    assert.equal(closeFirst.status, 200);
    assert.deepEqual(disconnects, []);
    const closeSecond = await fetch(`${url}/mcp`, { method: "DELETE", headers: { Authorization: "Bearer PAT_PRIVATE", "mcp-session-id": second.transport.sessionId!, "mcp-protocol-version": "2025-03-26" } });
    assert.equal(closeSecond.status, 200);
    await secondDisconnected;
    assert.deepEqual(disconnects, [secondSessionId]);
  } finally {
    await first.client.close().catch(() => undefined); await second.client.close().catch(() => undefined);
    await close(service); await close(api);
  }
});
