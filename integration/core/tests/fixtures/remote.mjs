import { z } from "zod";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { CallToolRequestSchema, ListToolsRequestSchema, RequestSchema, McpError, ErrorCode } from "@modelcontextprotocol/sdk/types.js";
import { createIntegrationCore } from "../../dist/index.js";
const secret = "no8do_int_FAKE_TEST_ONLY.not-a-real-secret";
const id = "e5c24a61-2c65-40d1-8634-18f7897b2a18";
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const protocol = () => ({ protocolName: "no8do-agent-protocol", protocolVersion: 2, systemName: "No8do",
  purpose: "test", replayGuidance: { summary: "test", searchBeforeNonTrivialWork: true,
    preferExistingKnowledge: true, searchBeforeCreate: true, recordUsageOnlyWhenMateriallyUsed: true,
    validatedRequiresEvidence: true, avoidTrivialKnowledge: true, avoidDuplicateKnowledge: true,
    neverStoreSecrets: true, neverStoreCredentials: true, avoidDiscardedAttempts: true },
  capabilities: { capabilities: [{ id: "REPLAY_READ", description: "test", readOnly: true }] },
  policies: { policies: [{ id: "test-policy", description: "test", enforcement: "ADVISORY" }] },
  integrationExtensions: { extensions: [{ id: "no8do-integration", version: 1, operationalContext: {
    version: 1, getMethod: "no8do/operational-context/get", updateMethod: "no8do/operational-context/update",
    optimisticConcurrency: "EXPECTED_VERSION" } }] } });
function fixture(origin, overrides = {}) {
  const logs = [], keys = []; let deleted = 0;
  const core = createIntegrationCore({
    origin: "https://api.no8do.example", verificationOrigin: "https://app.no8do.example", mcpOrigin: origin,
    installationStore: { async load() { return id; }, async saveIfAbsent() { return id; } },
    credentialStore: { async load(key) { keys.push(key); return secret; },
      async save() { throw Error("unused"); }, async delete() { deleted++; } },
    logger: { log(entry) { logs.push(entry); } }, ...overrides
  });
  return { core, logs, keys, deleted: () => deleted };
}
async function remote(t, options = {}) {
  const requests = [], sessions = new Map(), toolEntered = deferred(), initEntered = deferred(), sseEntered = deferred(), deleteEntered = deferred();
  let sseResponse; const agents = [];
  let initializes = 0, deletes = 0, tools = 0;
  const http = createServer(async (req, res) => {
    const chunks = []; for await (const chunk of req) chunks.push(chunk);
    const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString()) : undefined;
    requests.push({ method: req.method, url: req.url, headers: req.headers, body });
    if (options.httpStatusByMethod?.[body?.method]) { res.writeHead(options.httpStatusByMethod[body.method]); res.end(JSON.stringify({ error: options.httpError ?? secret })); return; }
    if (options.status) { res.writeHead(options.status); res.end(secret); return; }
    if (options.redirect) { res.writeHead(307, { location: options.redirect }); res.end(); return; }
    if (req.method === "GET") {
      if (options.sse) { sseResponse = res; res.writeHead(200, { "content-type": "text/event-stream" }); res.write(": keepalive\\n\\n".replaceAll("\\n", "\n")); sseEntered.resolve(); return; }
      res.writeHead(405); res.end(); return;
    }
    const sessionId = req.headers["mcp-session-id"];
    let transport = sessions.get(sessionId);
    if (body?.method === "initialize") {
      initializes++; initEntered.resolve();
      if (options.initGate) await options.initGate.promise;
      if (res.destroyed) return;
      const agentId = "00000000-0000-4000-8000-" + String(initializes).padStart(12, "0");
      agents.push(agentId); let snapshot = null;
      const context = () => ({ sessionId: agentId, version: snapshot?.version ?? 0, signal: snapshot?.signal ?? emptySignal(),
        resolution: { project: { id: null, status: "UNRESOLVED", confidence: null }, workItem: { id: null, status: "UNRESOLVED", confidence: null } }, updatedAt: timestamp });
      const server = new Server({ name: "local-test", version: "1" }, { capabilities: { tools: {} } });
      server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: [{ name: "get_agent_protocol", inputSchema: { type: "object" } }] }));
      server.setRequestHandler(CallToolRequestSchema, async request => {
        if (request.params.name === "get_agent_context") {
          options.sessionEntered?.resolve();
          if (options.sessionGate) await options.sessionGate.promise;
          assert.deepEqual(request.params.arguments, {});
          return { content: [], structuredContent: options.sessionResponse ?? agentSession(agentId) };
        }
        assert.equal(request.params.name, "get_agent_protocol");
        assert.deepEqual(request.params.arguments, {});
        tools++; toolEntered.resolve();
        if (options.toolGate) await options.toolGate.promise;
        return { content: [], structuredContent: options.protocol ?? protocol() };
      });
      server.setRequestHandler(RequestSchema.extend({ method: z.literal("no8do/operational-context/get"), params: z.object({}).strict() }), async request => {
        options.getEntered?.resolve();
        if (options.getGate) await options.getGate.promise;
        if (options.rpcError) throw new McpError(ErrorCode.InvalidRequest, options.rpcError);
        return options.readResponse ?? (snapshot ? { exists: true, context: context() } : { exists: false });
      });
      server.setRequestHandler(RequestSchema.extend({ method: z.literal("no8do/operational-context/update"), params: z.object({
        expectedVersion: z.number().int().nonnegative().nullable(), repository: z.unknown().nullable(),
        branch: z.string().nullable(), workingDirectory: z.string().nullable(), references: z.array(z.unknown())
      }).strict() }), async request => {
        options.updateEntered?.resolve();
        if (options.updateGate) await options.updateGate.promise;
        if (options.rpcError) throw new McpError(ErrorCode.InvalidRequest, options.rpcError);
        if (request.params.expectedVersion !== (snapshot?.version ?? null))
          throw new McpError(ErrorCode.InvalidRequest, "OPERATIONAL_CONTEXT_CONFLICT");
        const { expectedVersion, ...signal } = request.params;
        snapshot = { version: snapshot ? snapshot.version + 1 : 0, signal };
        return options.updateResponse ?? context();
      });
      transport = new StreamableHTTPServerTransport({
        sessionIdGenerator: () => "fake-session-" + initializes, enableJsonResponse: true,
        onsessioninitialized: sid => sessions.set(sid, transport),
        onsessionclosed: sid => { sessions.delete(sid); }
      });
      await server.connect(transport);
    }
    if (!transport) { res.writeHead(404); res.end(); return; }
    if (req.method === "DELETE") { deletes++; deleteEntered.resolve(); if (options.deleteGate) await options.deleteGate.promise; }
    await transport.handleRequest(req, res, body);
  });
  await new Promise(resolve => http.listen(0, "127.0.0.1", resolve));
  t.after(async () => {
    for (const transport of sessions.values()) await transport.close();
    http.closeAllConnections();
    await new Promise(resolve => http.close(resolve));
  });
  return { agents, origin: "http://127.0.0.1:" + http.address().port, requests, toolEntered, initEntered, sseEntered, deleteEntered, failSse: () => sseResponse.destroy(),
    counts: () => ({ initializes, deletes, tools }) };
}
function safe(f, error) {
  assert.ok(!JSON.stringify([f.core.getRuntimeState(), f.core.getNegotiatedProtocol(), f.logs, error]).includes(secret));
  assert.ok(!String(error).includes(secret));
}

const timestamp = "2026-10-01T00:00:00Z";
const emptySignal = () => ({ repository: null, branch: null, workingDirectory: null, references: [] });
const agentSession = sid => ({ sessionId: sid, workspaceId: "11111111-1111-4111-8111-111111111111",
  clientName: "no8do-integration-core", clientVersion: "0.1.0", transport: "MCP",
  protocolName: "no8do-agent-protocol", protocolVersion: 2, runtimeMode: "FULL",
  effectiveCapabilities: [{ id: "REPLAY_READ", description: "read", readOnly: true }],
  policies: [{ id: "workspace-isolation", description: "isolation", enforcement: "ENFORCED" }],
  registeredAt: timestamp, presenceStatus: "ACTIVE", lastSeenAt: timestamp, lastActivityAt: null, disconnectedAt: null });
export { remote, fixture, protocol, deferred, safe, secret, agentSession, emptySignal, timestamp };
