import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { PassThrough } from "node:stream";
import { createServer, type Server } from "node:http";
import test from "node:test";
import { once } from "node:events";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { createMcpServer, StdioAgentSessionTransport } from "./server.js";
import { renderAgentProtocolBootstrap } from "./agentProtocolBootstrap.js";
import { No8doClient, type AgentProtocol } from "./no8doClient.js";

const protocol: AgentProtocol = {
  protocolName: "no8do-agent-protocol", protocolVersion: 1, systemName: "No8do", purpose: "Memória técnica",
  replayGuidance: { summary: "Pesquise conhecimento reutilizável.", searchBeforeNonTrivialWork: true, preferExistingKnowledge: true, searchBeforeCreate: true, recordUsageOnlyWhenMateriallyUsed: true, validatedRequiresEvidence: true, avoidTrivialKnowledge: true, avoidDuplicateKnowledge: true, neverStoreSecrets: true, neverStoreCredentials: true, avoidDiscardedAttempts: true },
  capabilities: { capabilities: [] }, policies: { policies: [] }
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
  const api = createServer(async (request, response) => {
    response.setHeader("content-type", "application/json");
    if (request.url === "/api/agent-protocol") return response.end(JSON.stringify(protocol));
    const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk));
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8")) as Record<string, unknown>;
    registrations.push({ body, authorization: request.headers.authorization ?? "" });
    response.writeHead(201);
    response.end(JSON.stringify({ sessionId: "backend-id", ...body, protocolName: protocol.protocolName, protocolVersion: 1, registeredAt: "2026-01-01T00:00:00Z" }));
  });
  const apiUrl = await listen(api);
  const input = new PassThrough(); const output = new PassThrough();
  const server = createMcpServer({ apiUrl, token: "PAT_STDIO_ONLY", agentProtocol: protocol, transport: "stdio" });
  const transport = new StdioAgentSessionTransport(new StdioServerTransport(input, output), (clientName, clientVersion, transportSessionFingerprint) =>
    new No8doClient(apiUrl, "PAT_STDIO_ONLY").registerAgentSession({ clientName, clientVersion, workspaceId: null, transport: "MCP", transportSessionFingerprint }));
  try {
    const initializeResponse = nextMessage(output);
    await server.connect(transport);
    input.write(`${JSON.stringify({ jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "Claude Desktop", version: "2.4" } } })}\n`);
    const initialized = await initializeResponse;
    assert.equal((initialized.result as Record<string, unknown>).instructions, renderAgentProtocolBootstrap(protocol));
    assert.deepEqual(registrations[0]?.body, {
      clientName: "Claude Desktop", clientVersion: "2.4", workspaceId: null, transport: "MCP",
      transportSessionFingerprint: createHash("sha256").update(transport.sessionId, "utf8").digest("hex")
    });
    assert.equal(registrations[0]?.authorization, "Bearer PAT_STDIO_ONLY");
    assert.doesNotMatch(JSON.stringify(registrations[0]?.body), /PAT_STDIO_ONLY|sessionId/);

    input.write(`${JSON.stringify({ jsonrpc: "2.0", method: "notifications/initialized" })}\n`);
    const listToolsResponse = nextMessage(output);
    input.write(`${JSON.stringify({ jsonrpc: "2.0", id: 2, method: "tools/list", params: {} })}\n`);
    assert.equal(((await listToolsResponse).result as { tools: unknown[] }).tools.length, 14);
    const protocolResponse = nextMessage(output);
    input.write(`${JSON.stringify({ jsonrpc: "2.0", id: 3, method: "tools/call", params: { name: "get_agent_protocol", arguments: {} } })}\n`);
    const discovered = await protocolResponse;
    assert.equal((discovered.result as { structuredContent: AgentProtocol }).structuredContent.protocolVersion, 1);
    assert.equal(registrations.length, 1);
  } finally { await server.close(); await close(api); }
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
