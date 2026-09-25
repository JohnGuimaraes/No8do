import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { PassThrough } from "node:stream";
import { createServer, type Server } from "node:http";
import test from "node:test";
import { once } from "node:events";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { AgentSessionHeartbeat } from "./agentSessionHeartbeat.js";
import { createStdioSessionCloseHandler } from "./stdioSessionLifecycle.js";
import { createMcpServer, StdioAgentSessionTransport } from "./server.js";
import { renderAgentProtocolBootstrap } from "./agentProtocolBootstrap.js";
import { AgentSessionHeader, No8doClient, type AgentProtocol } from "./no8doClient.js";

const protocol: AgentProtocol = {
  protocolName: "no8do-agent-protocol", protocolVersion: 1, systemName: "No8do", purpose: "Memória técnica",
  replayGuidance: { summary: "Pesquise conhecimento reutilizável.", searchBeforeNonTrivialWork: true, preferExistingKnowledge: true, searchBeforeCreate: true, recordUsageOnlyWhenMateriallyUsed: true, validatedRequiresEvidence: true, avoidTrivialKnowledge: true, avoidDuplicateKnowledge: true, neverStoreSecrets: true, neverStoreCredentials: true, avoidDiscardedAttempts: true },
  capabilities: { capabilities: [
    { id: "REPLAY_CATALOG_LIST", description: "Lista o catálogo de Replays.", readOnly: true },
    { id: "REPLAY_SEARCH", description: "Pesquisa Replays no workspace por texto e ordena correspondências lexicalmente.", readOnly: true },
    { id: "REUSABLE_KNOWLEDGE_DISCOVERY", description: "Sugere Replays reutilizáveis por relevância lexical determinística.", readOnly: true },
    { id: "REPLAY_READ", description: "Lê conteúdo completo de Replay.", readOnly: true },
    { id: "REPLAY_VERSION_READ", description: "Lê versões históricas imutáveis de Replay.", readOnly: true },
    { id: "REPLAY_QUALITY_READ", description: "Lê avaliação derivada de qualidade do Replay.", readOnly: true },
    { id: "REPLAY_RELATIONS", description: "Lista, cria e remove relações entre Replays.", readOnly: false },
    { id: "REPLAY_CREATE", description: "Cria Replays.", readOnly: false },
    { id: "REPLAY_UPDATE", description: "Atualiza Replays.", readOnly: false },
    { id: "REPLAY_USAGE_HISTORY_READ", description: "Lê o histórico de uso de um Replay.", readOnly: true },
    { id: "REPLAY_USAGE_RECORD", description: "Registra uso de Replay.", readOnly: false }
  ] }, policies: { policies: [{ id: "evidence-required-for-validated", description: "Require evidence", enforcement: "ENFORCED" }] }
};

async function listen(server: Server) { server.listen(0, "127.0.0.1"); await once(server, "listening"); return `http://127.0.0.1:${(server.address() as { port: number }).port}`; }
async function close(server: Server) { const closed = once(server, "close"); server.close(); server.closeAllConnections(); await closed; }
function nextMessage(output: PassThrough) {
  return new Promise<Record<string, unknown>>((resolve, reject) => {
    let buffer = "";
    const timeout = setTimeout(() => { output.off("data", onData); reject(new Error("stdio response timed out")); }, 3000);
    const onData = (chunk: Buffer) => {
      buffer += chunk.toString("utf8");
      const newline = buffer.indexOf("\n");
      if (newline < 0) return;
      clearTimeout(timeout); output.off("data", onData);
      try { resolve(JSON.parse(buffer.slice(0, newline)) as Record<string, unknown>); } catch (error) { reject(error); }
    };
    output.on("data", onData);
  });
}

test("stdio registra no initialize antes de responder, preserva bootstrap e não registra tools novamente", async () => {
  const registrations: Array<{ body: Record<string, unknown>; authorization: string }> = [];
  const toolHeaders: Array<string | undefined> = [];
  const heartbeatCalls: Array<{ url: string; authorization: string; agentSessionId?: string }> = [];
  const lifecycleEvents: string[] = [];
  const disconnectCalls: string[] = [];
  const heartbeatTicks: Array<() => void> = [];
  const stoppedHeartbeats: string[] = [];
  let resolveHeartbeatRequest!: () => void;
  const heartbeatReceived = new Promise<void>(resolve => { resolveHeartbeatRequest = resolve; });
  let runtimeMode = "FULL";
  const sessionId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const api = createServer(async (request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(protocol));
    if (request.url === `/api/agent-sessions/${sessionId}/heartbeat`) {
      heartbeatCalls.push({ url: request.url, authorization: request.headers.authorization ?? "",
        agentSessionId: request.headers["x-no8do-agent-session-id"] as string | undefined });
      resolveHeartbeatRequest();
      return response.end(JSON.stringify({ sessionId, lastSeenAt: "2026-09-23T12:00:00Z" }));
    }
    if (request.url === `/api/agent-sessions/${sessionId}/disconnect`) {
      disconnectCalls.push(sessionId);
      lifecycleEvents.push("disconnect");
      return response.end(JSON.stringify({ sessionId, presenceStatus: "DISCONNECTED", disconnectedAt: "2026-09-23T12:00:00Z" }));
    }
    if (request.url === `/api/agent-sessions/${sessionId}/context`) {
      const header = request.headers["x-no8do-agent-session-id"];
      toolHeaders.push(Array.isArray(header) ? header[0] : header);
      return response.end(JSON.stringify({ sessionId, clientName: "Claude Desktop", clientVersion: "2.4", workspaceId: null,
        transport: "MCP", protocolName: protocol.protocolName, protocolVersion: 1, runtimeMode,
        effectiveCapabilities: protocol.capabilities.capabilities, policies: protocol.policies.policies, registeredAt: "2026-01-01T00:00:00Z",
        presenceStatus: "CONNECTED", lastSeenAt: "2026-01-01T00:00:00Z", lastActivityAt: null, disconnectedAt: null }));
    }
    if (request.url?.includes("/replays")) {
      const header = request.headers["x-no8do-agent-session-id"];
      toolHeaders.push(Array.isArray(header) ? header[0] : header);
      return response.end(JSON.stringify([]));
    }
    const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk));
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8")) as Record<string, unknown>;
    registrations.push({ body, authorization: request.headers.authorization ?? "" });
    response.writeHead(201);
    response.end(JSON.stringify({ sessionId, ...body, runtimeMode: "FULL", protocolName: protocol.protocolName, protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" }));
  });
  const apiUrl = await listen(api);
  const input = new PassThrough(); const output = new PassThrough();
  const agentSessionHeader = new AgentSessionHeader();
  const server = createMcpServer({ apiUrl, token: "PAT_STDIO_ONLY", agentProtocol: protocol, transport: "stdio", agentSessionHeader });
  let heartbeat: AgentSessionHeartbeat | undefined;
  const onSessionClosed = createStdioSessionCloseHandler(
    () => heartbeat,
    () => { heartbeat = undefined; lifecycleEvents.push("cleanup"); }
  );
  const transport = new StdioAgentSessionTransport(new StdioServerTransport(input, output), (clientName, clientVersion, transportSessionFingerprint) =>
    new No8doClient(apiUrl, "PAT_STDIO_ONLY").registerAgentSession({ clientName, clientVersion, workspaceId: null, transport: "MCP", transportSessionFingerprint })
      .then(session => session),
    session => {
      assert.equal(registrations.length, 1);
      agentSessionHeader.set(session.sessionId);
      heartbeat = new AgentSessionHeartbeat(new No8doClient(apiUrl, "PAT_STDIO_ONLY", fetch, agentSessionHeader), session.sessionId,
        callback => { heartbeatTicks.push(callback); return () => { stoppedHeartbeats.push(session.sessionId); lifecycleEvents.push("stop"); }; });
      agentSessionHeader.setRevocationHandler(() => heartbeat?.markRevoked());
      heartbeat.start();
    },
    onSessionClosed);
  try {
    const initializeResponse = nextMessage(output);
    await server.connect(transport);
    assert.equal(heartbeatTicks.length, 0);
    input.write(`${JSON.stringify({ jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "Claude Desktop", version: "2.4" } } })}\n`);
    const initialized = await initializeResponse;
    assert.equal((initialized.result as Record<string, unknown>).instructions, renderAgentProtocolBootstrap(protocol));
    assert.deepEqual(registrations[0]?.body, {
      clientName: "Claude Desktop", clientVersion: "2.4", workspaceId: null, transport: "MCP",
      transportSessionFingerprint: createHash("sha256").update(transport.sessionId, "utf8").digest("hex")
    });
    assert.equal(registrations[0]?.authorization, "Bearer PAT_STDIO_ONLY");
    assert.doesNotMatch(JSON.stringify(registrations[0]?.body), /PAT_STDIO_ONLY|sessionId/);
    assert.equal(heartbeatTicks.length, 1);
    assert.equal(heartbeatCalls.length, 0);
    heartbeatTicks[0]?.();
    await heartbeatReceived;
    assert.deepEqual(heartbeatCalls[0], {
      url: `/api/agent-sessions/${sessionId}/heartbeat`, authorization: "Bearer PAT_STDIO_ONLY", agentSessionId: sessionId
    });

    input.write(`${JSON.stringify({ jsonrpc: "2.0", method: "notifications/initialized" })}\n`);
    const listToolsResponse = nextMessage(output);
    input.write(`${JSON.stringify({ jsonrpc: "2.0", id: 2, method: "tools/list", params: {} })}\n`);
    const tools = ((await listToolsResponse).result as { tools: Array<{ name: string; description: string; inputSchema: Record<string, unknown> }> }).tools;
    assert.equal(tools.length, 15);
    assert.ok(tools.some(tool => tool.name === "search_replays"));
    assert.ok(tools.some(tool => tool.name === "find_reusable_knowledge" && /determinística/.test(tool.description)));
    assert.doesNotMatch(JSON.stringify(tools.map(tool => tool.description)), /semantic|hybrid|context package|rendering/i);
    const contextTool = tools.find(tool => tool.name === "get_agent_context");
    assert.deepEqual(contextTool?.inputSchema.properties, {});
    assert.doesNotMatch(JSON.stringify(contextTool?.inputSchema), /sessionId/);
    const protocolResponse = nextMessage(output);
    input.write(`${JSON.stringify({ jsonrpc: "2.0", id: 3, method: "tools/call", params: { name: "get_agent_protocol", arguments: {} } })}\n`);
    const discovered = await protocolResponse;
    const discoveredProtocol = (discovered.result as { structuredContent: AgentProtocol }).structuredContent;
    assert.deepEqual(discoveredProtocol, protocol);
    assert.deepEqual(discoveredProtocol.capabilities.capabilities.map(capability => capability.id),
      protocol.capabilities.capabilities.map(capability => capability.id));
    assert.ok(!discoveredProtocol.capabilities.capabilities.some(capability =>
      ["SEMANTIC_DUPLICATE_SEARCH", "HYBRID_RETRIEVAL", "CONTEXT_PACKAGE_ASSEMBLY", "CONTEXT_RENDERING"].includes(capability.id)));
    const contextResponse = nextMessage(output);
    input.write(`${JSON.stringify({ jsonrpc: "2.0", id: 4, method: "tools/call", params: { name: "get_agent_context", arguments: {} } })}\n`);
    const firstContext = await contextResponse;
    assert.equal((firstContext.result as { structuredContent: { runtimeMode: string } }).structuredContent.runtimeMode, "FULL");
    assert.equal((firstContext.result as { structuredContent: { disconnectedAt: string | null } }).structuredContent.disconnectedAt, null);
    assert.deepEqual((firstContext.result as { structuredContent: { policies: unknown } }).structuredContent.policies, protocol.policies.policies);
    runtimeMode = "RETRIEVAL";
    const changedContextResponse = nextMessage(output);
    input.write(`${JSON.stringify({ jsonrpc: "2.0", id: 5, method: "tools/call", params: { name: "get_agent_context", arguments: {} } })}\n`);
    const changedContext = await changedContextResponse;
    assert.equal((changedContext.result as { structuredContent: { runtimeMode: string } }).structuredContent.runtimeMode, "RETRIEVAL");
    const replayResponse = nextMessage(output);
    input.write(`${JSON.stringify({ jsonrpc: "2.0", id: 6, method: "tools/call", params: { name: "list_replays", arguments: { workspaceId: "11111111-1111-4111-8111-111111111111" } } })}\n`);
    await replayResponse;
    assert.deepEqual(toolHeaders, [sessionId, sessionId, sessionId]);
    assert.equal(registrations.length, 1);
  } finally {
    await server.close();
    await transport.close();
    assert.deepEqual(stoppedHeartbeats, [sessionId]);
    assert.deepEqual(disconnectCalls, [sessionId]);
    assert.deepEqual(lifecycleEvents, ["stop", "disconnect", "cleanup"]);
    assert.equal(heartbeat, undefined);
    await close(api);
  }
});

test("stdio falha initialize explicitamente quando registro backend falha", async () => {
  const api = createServer((request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(protocol));
    response.writeHead(503); response.end(JSON.stringify({ token: "PAT_SECRET" }));
  });
  const apiUrl = await listen(api);
  const input = new PassThrough(); const output = new PassThrough();
  const server = createMcpServer({ apiUrl, token: "PAT_SECRET", agentProtocol: protocol, transport: "stdio" });
  const transport = new StdioAgentSessionTransport(new StdioServerTransport(input, output), (clientName, clientVersion, transportSessionFingerprint) =>
    new No8doClient(apiUrl, "PAT_SECRET").registerAgentSession({ clientName, clientVersion, workspaceId: null, transport: "MCP", transportSessionFingerprint }));
  try {
    const initializeResponse = nextMessage(output);
    await server.connect(transport);
    input.write(`${JSON.stringify({ jsonrpc: "2.0", id: 7, method: "initialize", params: { protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "test", version: "1" } } })}\n`);
    const response = await initializeResponse;
    assert.equal(response.id, 7);
    assert.equal((response.error as { message: string }).message, "Falha ao registrar a sessão MCP no No8do.");
    assert.equal(response.result, undefined);
    assert.doesNotMatch(JSON.stringify(response), /PAT_SECRET/);
  } finally { await server.close(); await close(api); }
});

test("operação revogada propaga erro e encerra lifecycle STDIO sem disconnect", async () => {
  const sessionId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
  const ticks: Array<() => void> = [];
  const stopped: string[] = [];
  const disconnects: string[] = [];
  let registrations = 0;
  const api = createServer(async (request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(protocol));
    if (request.url === "/api/agent-sessions") {
      registrations++;
      return response.end(JSON.stringify({ sessionId, clientName: "test", clientVersion: "1", workspaceId: null, transport: "MCP", runtimeMode: "FULL", protocolName: protocol.protocolName, protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" }));
    }
    if (request.url === `/api/agent-sessions/${sessionId}/disconnect`) {
      disconnects.push(sessionId);
      return response.end(JSON.stringify({ sessionId, disconnectedAt: "2026-09-23T12:00:00Z" }));
    }
    if (request.url?.includes("/replays")) {
      response.writeHead(409);
      return response.end(JSON.stringify({ error: "AGENT_SESSION_REVOKED", fingerprint: "PRIVATE_FINGERPRINT" }));
    }
    return response.end(JSON.stringify([]));
  });
  const apiUrl = await listen(api);
  const input = new PassThrough(); const output = new PassThrough();
  const agentSessionHeader = new AgentSessionHeader();
  const server = createMcpServer({ apiUrl, token: "PAT_PRIVATE", agentProtocol: protocol, transport: "stdio", agentSessionHeader });
  let heartbeat: AgentSessionHeartbeat | undefined;
  const transport = new StdioAgentSessionTransport(new StdioServerTransport(input, output),
    (clientName, clientVersion, transportSessionFingerprint) => new No8doClient(apiUrl, "PAT_PRIVATE")
      .registerAgentSession({ clientName, clientVersion, workspaceId: null, transport: "MCP", transportSessionFingerprint }),
    session => {
      agentSessionHeader.set(session.sessionId);
      heartbeat = new AgentSessionHeartbeat(new No8doClient(apiUrl, "PAT_PRIVATE", fetch, agentSessionHeader), session.sessionId,
        callback => { ticks.push(callback); return () => { stopped.push(session.sessionId); }; });
      agentSessionHeader.setRevocationHandler(() => heartbeat?.markRevoked());
      heartbeat.start();
    },
    createStdioSessionCloseHandler(() => heartbeat, () => { heartbeat = undefined; }));
  try {
    const initialized = nextMessage(output);
    await server.connect(transport);
    input.write(`${JSON.stringify({ jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "test", version: "1" } } })}\n`);
    await initialized;
    input.write(`${JSON.stringify({ jsonrpc: "2.0", method: "notifications/initialized" })}\n`);
    const revoked = nextMessage(output);
    input.write(`${JSON.stringify({ jsonrpc: "2.0", id: 2, method: "tools/call", params: { name: "list_replays", arguments: { workspaceId: "11111111-1111-4111-8111-111111111111" } } })}\n`);
    const response = await revoked;
    assert.match(JSON.stringify(response), /AGENT_SESSION_REVOKED/);
    assert.doesNotMatch(JSON.stringify(response), /PRIVATE_FINGERPRINT|PAT_PRIVATE/);
    assert.equal(heartbeat?.isRevoked(), true);
    ticks[0]?.();
    await new Promise<void>(resolve => setImmediate(resolve));
    assert.deepEqual(stopped, [sessionId]);
    assert.equal(registrations, 1);
  } finally {
    await server.close(); await transport.close();
    assert.deepEqual(disconnects, []);
    await close(api);
  }
});
