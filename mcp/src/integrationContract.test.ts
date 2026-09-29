import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";
import { once } from "node:events";
import { PassThrough } from "node:stream";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import test from "node:test";
import { AgentSessionHeader, type AgentProtocol } from "./no8doClient.js";
import { createMcpServer } from "./server.js";

const workspaceId = "11111111-1111-4111-8111-111111111111";
const sessionId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const protocol: AgentProtocol = {
  protocolName: "no8do-agent-protocol", protocolVersion: 2, systemName: "No8do", purpose: "Agent integration",
  replayGuidance: { summary: "fixture", searchBeforeNonTrivialWork: true, preferExistingKnowledge: true,
    searchBeforeCreate: true, recordUsageOnlyWhenMateriallyUsed: true, validatedRequiresEvidence: true,
    avoidTrivialKnowledge: true, avoidDuplicateKnowledge: true, neverStoreSecrets: true,
    neverStoreCredentials: true, avoidDiscardedAttempts: true },
  capabilities: { capabilities: [] }, policies: { policies: [] },
  integrationExtensions: { extensions: [{ id: "no8do-integration", version: 1, operationalContext: {
    version: 1, getMethod: "no8do/operational-context/get", updateMethod: "no8do/operational-context/update",
    optimisticConcurrency: "EXPECTED_VERSION"
  } }] }
};

async function listen(server: Server): Promise<string> {
  server.listen(0, "127.0.0.1"); await once(server, "listening");
  return `http://127.0.0.1:${(server.address() as { port: number }).port}`;
}
async function close(server: Server): Promise<void> {
  const closed = once(server, "close"); server.close(); server.closeAllConnections(); await closed;
}
function nextMessage(output: PassThrough): Promise<Record<string, unknown>> {
  return new Promise((resolve, reject) => {
    let buffer = "";
    const timer = setTimeout(() => reject(new Error("MCP response timed out")), 3000);
    output.on("data", function onData(chunk: Buffer) {
      buffer += chunk.toString("utf8");
      const newline = buffer.indexOf("\n");
      if (newline < 0) return;
      clearTimeout(timer); output.off("data", onData);
      try { resolve(JSON.parse(buffer.slice(0, newline)) as Record<string, unknown>); }
      catch (error) { reject(error); }
    });
  });
}

test("Operational Context usa requests internos session-scoped, expectedVersion e não aparece em tools/list", async () => {
  const calls: Array<{ url: string; session?: string }> = [];
  let exists = false;
  let revoked = false;
  let snapshot = { sessionId, version: 0,
    signal: { repository: null, branch: "main", workingDirectory: null, references: [] },
    resolution: { project: { id: null, status: "UNRESOLVED", confidence: null }, workItem: { id: null, status: "UNRESOLVED", confidence: null } },
    updatedAt: "2026-01-01T00:00:00Z" };
  const api = createServer(async (request, response) => {
    calls.push({ url: request.url ?? "", session: request.headers["x-no8do-agent-session-id"] as string | undefined });
    response.setHeader("content-type", "application/json");
    if (request.url === `/api/agent-sessions/${sessionId}/operational-context/state`) {
      if (revoked) { response.writeHead(409); return response.end(JSON.stringify({ error: "AGENT_SESSION_REVOKED" })); }
      return response.end(JSON.stringify({ exists, context: exists ? snapshot : null }));
    }
    if (request.url === `/api/agent-sessions/${sessionId}/operational-context` && request.method === "PUT") {
      if (revoked) { response.writeHead(409); return response.end(JSON.stringify({ error: "AGENT_SESSION_REVOKED" })); }
      const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk));
      const update = JSON.parse(Buffer.concat(chunks).toString("utf8")) as Record<string, unknown>;
      if (update.expectedVersion !== (exists ? snapshot.version : null)) {
        response.writeHead(409); return response.end(JSON.stringify({ error: "conflict" }));
      }
      snapshot = { ...snapshot, version: exists ? snapshot.version + 1 : 0,
        signal: { repository: update.repository as null, branch: update.branch as string,
          workingDirectory: update.workingDirectory as null, references: update.references as never[] } };
      exists = true;
      return response.end(JSON.stringify(snapshot));
    }
    return response.end(JSON.stringify([]));
  });
  const apiUrl = await listen(api);
  const header = new AgentSessionHeader();
  header.set(sessionId, workspaceId);
  const server = createMcpServer({ apiUrl, token: "PAT", agentProtocol: protocol, transport: "stdio", agentSessionHeader: header });
  const input = new PassThrough(); const output = new PassThrough();
  const transport = new StdioServerTransport(input, output);
  const send = async (message: Record<string, unknown>) => {
    const response = nextMessage(output); input.write(`${JSON.stringify(message)}\n`); return response;
  };
  const notify = (message: Record<string, unknown>) => input.write(`${JSON.stringify(message)}\n`);
  try {
    const initialized = send({ jsonrpc: "2.0", id: 1, method: "initialize", params: {
      protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "Integration Core", version: "1" }
    } });
    await server.connect(transport); await initialized;
    notify({ jsonrpc: "2.0", method: "notifications/initialized" });
    const capabilities = await send({ jsonrpc: "2.0", id: 2, method: "no8do/integration/capabilities", params: {} });
    assert.deepEqual((capabilities.result as AgentProtocol["integrationExtensions"]).extensions, protocol.integrationExtensions.extensions);
    const tools = await send({ jsonrpc: "2.0", id: 3, method: "tools/list", params: {} });
    const names = (tools.result as { tools: Array<{ name: string }> }).tools.map(tool => tool.name);
    assert.equal(names.length, 15);
    assert.ok(!names.some(name => /operational.context|set_current_project/i.test(name)));

    const noContext = await send({ jsonrpc: "2.0", id: 4, method: "no8do/operational-context/get", params: {} });
    assert.deepEqual(noContext.result, { exists: false });
    const first = await send({ jsonrpc: "2.0", id: 5, method: "no8do/operational-context/update", params: {
      expectedVersion: null, repository: null, branch: "main", workingDirectory: null, references: []
    } });
    assert.equal((first.result as { version: number }).version, 0);
    const second = await send({ jsonrpc: "2.0", id: 6, method: "no8do/operational-context/update", params: {
      expectedVersion: 0, repository: null, branch: "feature", workingDirectory: "backend", references: []
    } });
    assert.equal((second.result as { version: number }).version, 1);
    const stale = await send({ jsonrpc: "2.0", id: 7, method: "no8do/operational-context/update", params: {
      expectedVersion: 0, repository: null, branch: "main", workingDirectory: null, references: []
    } });
    assert.match(JSON.stringify(stale.error), /OPERATIONAL_CONTEXT_CONFLICT/);
    const beforeInvalid = calls.length;
    for (const extra of [{ sessionId }, { workspaceId }, { metadata: {} }]) {
      const invalid = await send({ jsonrpc: "2.0", id: 8, method: "no8do/operational-context/update", params: {
        expectedVersion: 1, repository: null, branch: "main", workingDirectory: null, references: [], ...extra
      } });
      assert.ok(invalid.error);
    }
    assert.equal(calls.length, beforeInvalid);
    assert.ok(calls.every(call => call.session === sessionId));
    revoked = true;
    const terminal = await send({ jsonrpc: "2.0", id: 9, method: "no8do/operational-context/get", params: {} });
    assert.match(JSON.stringify(terminal.error), /AGENT_SESSION_REVOKED/);
  } finally { await server.close(); await transport.close(); await close(api); }
});
