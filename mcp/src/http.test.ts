import assert from "node:assert/strict";
import { createServer, request as httpRequest } from "node:http";
import test from "node:test";
import { once } from "node:events";
import { createHash } from "node:crypto";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
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
  policies: { policies: [{ id: "evidence-required-for-validated", description: "Require evidence for validated status", enforcement: "ENFORCED" }, { id: "material-usage", description: "Record material usage only", enforcement: "ADVISORY" }, { id: "workspace-isolation-required", description: "Isolate workspaces", enforcement: "ENFORCED" }] }
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

test("remote MCP falha ao iniciar sem workspace configurado válido", () => {
  assert.throws(() => createRemoteMcpService("http://127.0.0.1:9", undefined), /NO8DO_WORKSPACE_ID é obrigatória/);
  assert.throws(() => createRemoteMcpService("http://127.0.0.1:9", "invalido"), /UUID válido/);
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
      policies: agentProtocol.policies.policies, registeredAt: "2026-01-01T00:00:00Z"
    }));
    const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk));
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8")) as Record<string, unknown>;
    mutations.push({ method: request.method ?? "GET", body });
    if (request.method === "POST" && body.status === "VALIDATED" && !body.validationEvidence) {
      response.writeHead(403);
      return response.end(JSON.stringify({ error: "AGENT_POLICY_DENIED", metadata: { sessionId, policyId: "evidence-required-for-validated", reason: "evidence required" } }));
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
    const protocol = await client.callTool({ name: "get_agent_protocol" });
    assert.equal((protocol.structuredContent as AgentProtocol).policies.policies.find(policy => policy.id === "evidence-required-for-validated")?.enforcement, "ENFORCED");
    const context = await client.callTool({ name: "get_agent_context", arguments: {} });
    assert.equal((context.structuredContent as { policies: AgentProtocol["policies"]["policies"] }).policies.find(policy => policy.id === "evidence-required-for-validated")?.enforcement, "ENFORCED");

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
  } finally { await client.close(); await close(service); await close(api); }
});

test("cliente MCP recebe instructions e discovery estruturado do protocolo da API", async () => {
  const received: Array<{ authorization: string; url: string }> = [];
  const api = createServer((request, response) => {
    received.push({ authorization: request.headers.authorization ?? "", url: request.url ?? "" });
    response.setHeader("content-type", "application/json");
    response.end(JSON.stringify(request.url === "/api/agent-protocol" ? agentProtocol : []));
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
    assert.deepEqual(policyIds, ["evidence-required-for-validated", "material-usage", "workspace-isolation-required"]);
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
  let runtimeMode = "FULL";
  const firstAgentSessionId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const secondAgentSessionId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
  const api = createServer(async (request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(agentProtocol));
    const contextSessionId = [firstAgentSessionId, secondAgentSessionId].find(id => request.url === `/api/agent-sessions/${id}/context`);
    if (contextSessionId) {
      toolCalls.push({ url: request.url ?? "", agentSessionId: request.headers["x-no8do-agent-session-id"] as string | undefined });
      return response.end(JSON.stringify({ sessionId: contextSessionId, clientName: "Codex Desktop", clientVersion: "9.8", workspaceId,
        transport: "MCP", protocolName: agentProtocol.protocolName, protocolVersion: 1, runtimeMode,
        effectiveCapabilities: agentProtocol.capabilities.capabilities, policies: agentProtocol.policies.policies, registeredAt: "2026-01-01T00:00:00Z" }));
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
  const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId); const url = await listen(service);
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
    const contextTool = (await first.client.listTools()).tools.find(tool => tool.name === "get_agent_context");
    assert.deepEqual(contextTool?.inputSchema.properties, {});
    assert.doesNotMatch(JSON.stringify(contextTool?.inputSchema), /sessionId/);
    await first.client.callTool({ name: "get_agent_protocol" });
    const initialContext = await first.client.callTool({ name: "get_agent_context", arguments: {} });
    assert.equal((initialContext.structuredContent as { runtimeMode: string } | undefined)?.runtimeMode, "FULL", JSON.stringify(initialContext));
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

    const initializeEnvelope = { jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "Codex Desktop", version: "9.8" } } };
    const secondInitialize = await postJson(`${url}/mcp`, { Authorization: "Bearer PAT_NOT_PERSISTED" }, initializeEnvelope);
    assert.equal(secondInitialize.statusCode, 200);
    const secondSessionHeader = secondInitialize.headers["mcp-session-id"];
    assert.equal(typeof secondSessionHeader, "string");
    const secondSessionId = secondSessionHeader as string;
    const initialized = await postJson(`${url}/mcp`, { Authorization: "Bearer PAT_NOT_PERSISTED", "mcp-session-id": secondSessionId, "mcp-protocol-version": "2025-03-26" }, { jsonrpc: "2.0", method: "notifications/initialized" });
    assert.equal(initialized.statusCode, 202);
    assert.equal(registrations.length, 2);
    assert.notEqual(registrations[0]?.body.transportSessionFingerprint, registrations[1]?.body.transportSessionFingerprint);
    assert.equal(registrations[1]?.body.transportSessionFingerprint, createHash("sha256").update(secondSessionId!, "utf8").digest("hex"));
  } finally { await close(service); await close(api); }
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
