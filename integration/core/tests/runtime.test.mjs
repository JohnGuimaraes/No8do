import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { createIntegrationCore } from "../dist/index.js";
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
  let sseResponse;
  let initializes = 0, deletes = 0, tools = 0;
  const http = createServer(async (req, res) => {
    const chunks = []; for await (const chunk of req) chunks.push(chunk);
    const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString()) : undefined;
    requests.push({ method: req.method, url: req.url, headers: req.headers, body });
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
      const server = new Server({ name: "local-test", version: "1" }, { capabilities: { tools: {} } });
      server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: [{ name: "get_agent_protocol", inputSchema: { type: "object" } }] }));
      server.setRequestHandler(CallToolRequestSchema, async request => {
        assert.equal(request.params.name, "get_agent_protocol");
        assert.deepEqual(request.params.arguments, {});
        tools++; toolEntered.resolve();
        if (options.toolGate) await options.toolGate.promise;
        return { content: [], structuredContent: options.protocol ?? protocol() };
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
  return { origin: "http://127.0.0.1:" + http.address().port, requests, toolEntered, initEntered, sseEntered, deleteEntered, failSse: () => sseResponse.destroy(),
    counts: () => ({ initializes, deletes, tools }) };
}
function safe(f, error) {
  assert.ok(!JSON.stringify([f.core.getRuntimeState(), f.core.getNegotiatedProtocol(), f.logs, error]).includes(secret));
  assert.ok(!String(error).includes(secret));
}
test("real SDK initialize, bearer on every method, neutral identity, separate state and immutable metadata", async t => {
  const r = await remote(t), f = fixture(r.origin + "/");
  t.after(() => f.core.closeRuntime());
  assert.equal(f.core.getRuntimeState().state, "DISCONNECTED");
  assert.equal(f.core.getNegotiatedProtocol(), null);
  const pending = f.core.connectRuntime();
  assert.equal(f.core.getRuntimeState().state, "CONNECTING");
  assert.equal(f.core.connectRuntime(), pending);
  const p = await pending;
  assert.equal(f.core.getRuntimeState().state, "CONNECTED");
  assert.equal(f.core.getAuthorizationState().state, "IDLE");
  assert.equal(p.protocolVersion, 2);
  assert.equal(p.integrationExtensionVersion, 1);
  assert.ok(Object.isFrozen(p) && Object.isFrozen(p.operationalContext));
  assert.equal(await f.core.connectRuntime(), p);
  assert.deepEqual(f.keys, [{ trustedOrigin: "https://api.no8do.example", installationId: id }]);
  const init = r.requests.find(r => r.body?.method === "initialize").body.params;
  assert.deepEqual(init.clientInfo, { name: "no8do-integration-core", version: "0.1.0" });
  assert.deepEqual(init.capabilities, {});
  for (const field of ["workspaceId", "agentId", "authorizationId", "sessionId", "workspaceHint"]) assert.ok(!(field in init));
  await f.core.closeRuntime();
  assert.equal(f.core.getRuntimeState().state, "DISCONNECTED");
  assert.equal(f.core.getNegotiatedProtocol(), null);
  assert.equal(f.deleted(), 0);
  assert.equal(r.counts().initializes, 1); assert.equal(r.counts().deletes, 1);
  for (const request of r.requests) {
    assert.equal(request.url, "/mcp");
    assert.equal(request.headers.authorization, "Bearer " + secret);
    assert.equal(request.headers["x-no8do-agent-credential"], undefined);
    assert.equal(request.headers.cookie, undefined);
    assert.ok(!(JSON.stringify(request.body) ?? "").includes(secret));
  }
  assert.deepEqual(f.logs.map(e => e.state), ["CONNECTING", "NEGOTIATING", "CONNECTED", "CLOSING", "DISCONNECTED"]);
  safe(f);
});
test("B.1 compatibility, absent explicit mcpOrigin, absent credential, invalid stored credential and store error", async t => {
  const r = await remote(t);
  const absent = fixture(undefined);
  await assert.rejects(absent.core.connectRuntime(), { code: "MCP_ORIGIN_REQUIRED" });
  for (const [load, code] of [
    [async () => null, "AUTHORIZATION_REQUIRED"],
    [async () => "fake-PAT", "RUNTIME_AUTHENTICATION_FAILED"],
    [async () => { throw Error(secret); }, "CREDENTIAL_STORE_FAILED"]
  ]) {
    const f = fixture(r.origin, { credentialStore: { load, save() {}, delete() {} } });
    await assert.rejects(f.core.connectRuntime(), e => { assert.equal(e.code, code); safe(f,e); return true; });
    assert.equal(f.core.getRuntimeState().state, "FAILED");
  }
  assert.equal(r.requests.length, 0);
});
test("MCP origins require HTTPS outside loopback and forbid path/userinfo/query/fragment", () => {
  for (const origin of ["https://mcp.example/", "http://localhost:3000", "http://127.0.0.1:3000", "http://[::1]:3000"])
    assert.doesNotThrow(() => fixture(origin));
  for (const origin of ["http://mcp.example", "https://user:pass@mcp.example", "https://mcp.example?x=y",
    "https://mcp.example#fragment", "https://mcp.example/mcp", "file:///mcp", "invalid"])
    assert.throws(() => fixture(origin), { code: "INVALID_ORIGIN" });
});
for (const [name, mutate, code] of [
  ["future protocol", p => { p.protocolVersion = 3; }, "UNSUPPORTED_PROTOCOL"],
  ["missing extension", p => { p.integrationExtensions.extensions = []; }, "MISSING_INTEGRATION_EXTENSION"],
  ["future extension", p => { p.integrationExtensions.extensions[0].version = 2; }, "UNSUPPORTED_INTEGRATION_EXTENSION"],
  ["malformed extensions", p => { p.integrationExtensions = null; }, "INVALID_RESPONSE"],
  ["duplicate extension", p => { p.integrationExtensions.extensions.push(p.integrationExtensions.extensions[0]); }, "INVALID_RESPONSE"],
  ["future context contract", p => { p.integrationExtensions.extensions[0].operationalContext.version = 2; }, "UNSUPPORTED_INTEGRATION_EXTENSION"],
  ["server string containing credential", p => { p.protocolName = secret; }, "INVALID_RESPONSE"]
]) test(name + " fails closed and deletes MCP session", async t => {
  const p = protocol(); mutate(p);
  const r = await remote(t, { protocol: p }), f = fixture(r.origin);
  await assert.rejects(f.core.connectRuntime(), e => { assert.equal(e.code, code); safe(f,e); return true; });
  assert.equal(f.core.getRuntimeState().state, "FAILED");
  assert.equal(f.core.getNegotiatedProtocol(), null);
  assert.equal(r.counts().deletes, 1);
  await f.core.closeRuntime();
});
for (const [status, code] of [[401, "RUNTIME_AUTHENTICATION_FAILED"], [403, "RUNTIME_AUTHORIZATION_FAILED"], [409, "RUNTIME_CONFLICT"]])
  test("HTTP " + status + " is safe, distinguished and has no retry/fallback", async t => {
    const r = await remote(t, { status }), f = fixture(r.origin);
    await assert.rejects(f.core.connectRuntime(), e => { assert.equal(e.code, code); safe(f,e); return true; });
    assert.equal(r.requests.length, 1);
    assert.equal(f.deleted(), 0);
    assert.equal(r.requests[0].headers.authorization, "Bearer " + secret);
  });
test("redirect never sends bearer to a different origin", async t => {
  let redirected = 0;
  const sink = createServer((req,res) => { redirected++; res.end(); });
  await new Promise(resolve => sink.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise(resolve => sink.close(resolve)));
  const r = await remote(t, { redirect: "http://127.0.0.1:" + sink.address().port + "/mcp" });
  const f = fixture(r.origin);
  await assert.rejects(f.core.connectRuntime(), { code: "TRANSPORT_ERROR" });
  assert.equal(redirected, 0);
});
test("close during non-cooperative store load is idempotent, late result cannot overwrite a new attempt", async t => {
  const r = await remote(t), gate = deferred(); let loads = 0;
  const f = fixture(r.origin, { credentialStore: { load: () => ++loads === 1 ? gate.promise : Promise.resolve(secret),
    save() {}, delete() {} } });
  t.after(() => f.core.closeRuntime());
  const pending = f.core.connectRuntime(); const rejected = assert.rejects(pending, { code: "RUNTIME_CANCELLED" });
  const close = f.core.closeRuntime();
  assert.equal(f.core.closeRuntime(), close);
  assert.equal(f.core.getRuntimeState().state, "CLOSING");
  await assert.rejects(f.core.connectRuntime(), { code: "RUNTIME_BUSY" });
  await close; await rejected;
  await f.core.connectRuntime();
  gate.resolve(secret);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(f.core.getRuntimeState().state, "CONNECTED");
  assert.equal(r.counts().initializes, 1);
});
for (const phase of ["initialize", "negotiation"]) test("close during " + phase + " ignores late completion", async t => {
  const gate = deferred();
  const r = await remote(t, phase === "initialize" ? { initGate: gate } : { toolGate: gate });
  const f = fixture(r.origin);
  const pending = f.core.connectRuntime(); const rejected = assert.rejects(pending, { code: "RUNTIME_CANCELLED" });
  await (phase === "initialize" ? r.initEntered.promise : r.toolEntered.promise);
  assert.equal(f.core.getRuntimeState().state, phase === "initialize" ? "CONNECTING" : "NEGOTIATING");
  await f.core.closeRuntime();
  gate.resolve();
  await rejected;
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(f.core.getRuntimeState().state, "DISCONNECTED");
  assert.equal(f.core.getNegotiatedProtocol(), null);
});
test("public boundary has no generic MCP, replay, operational context or session surface", () => {
  const f = fixture("https://mcp.no8do.example");
  assert.deepEqual(Object.keys(f.core).sort(), [
    "startAuthorization", "cancelAuthorization", "getAuthorizationState", "waitForAuthorization",
    "getInstallationId", "hasStoredAuthorization", "forgetLocalAuthorization",
    "connectRuntime", "getRuntimeState", "getNegotiatedProtocol", "closeRuntime"
  ].sort());
});

for (const field of ["purpose", "replayGuidance", "capabilities", "policies"]) {
  for (const mode of ["missing", "invalid"]) test("protocol " + field + " " + mode + " fails closed", async t => {
    const p = protocol(); if (mode === "missing") delete p[field]; else p[field] = 42;
    const r = await remote(t, { protocol: p }), f = fixture(r.origin);
    await assert.rejects(f.core.connectRuntime(), { code: "INVALID_RESPONSE" });
    assert.equal(f.core.getRuntimeState().state, "FAILED");
    assert.equal(r.counts().deletes, 1);
  });
}
test("explicit close during automatic SSE failure cleanup ends DISCONNECTED, without reconnect", async t => {
  const gate = deferred(), r = await remote(t, { sse: true, deleteGate: gate }), f = fixture(r.origin);
  t.after(() => { gate.resolve(); return f.core.closeRuntime(); });
  await f.core.connectRuntime(); await r.sseEntered.promise;
  r.failSse(); await r.deleteEntered.promise;
  assert.equal(f.core.getRuntimeState().state, "FAILED");
  assert.equal(f.core.getNegotiatedProtocol(), null);
  const close = f.core.closeRuntime();
  assert.equal(f.core.closeRuntime(), close);
  assert.equal(f.core.getRuntimeState().state, "CLOSING");
  await assert.rejects(f.core.connectRuntime(), { code: "RUNTIME_BUSY" });
  gate.resolve(); await close;
  assert.equal(f.core.getRuntimeState().state, "DISCONNECTED");
  assert.equal(r.counts().initializes, 1);
  assert.equal(r.counts().deletes, 1);
  safe(f);
});
test("logger reentrant connect shares owned operation and close cancels startup", async t => {
  const r = await remote(t); let core, reentrant;
  ({ core } = fixture(r.origin, { logger: { log(entry) {
    if (entry.state === "CONNECTING") { reentrant = core.connectRuntime(); void core.closeRuntime(); }
  } } }));
  const pending = core.connectRuntime();
  assert.equal(reentrant, pending);
  await assert.rejects(pending, { code: "RUNTIME_CANCELLED" });
  await core.closeRuntime();
  assert.equal(core.getRuntimeState().state, "DISCONNECTED");
  assert.equal(r.requests.length, 0);
});

for (const [name, mutate] of [
  ["guidance boolean", p => { p.replayGuidance.neverStoreSecrets = "true"; }],
  ["capability flag", p => { p.capabilities.capabilities[0].readOnly = 1; }],
  ["policy enforcement", p => { p.policies.policies[0].enforcement = ["ADVISORY"]; }],
  ["unknown extension schema", p => { p.integrationExtensions.extensions.push({ id: "future-extension", version: 1 }); }]
]) test("malformed " + name + " fails closed", async t => {
  const p = protocol(); mutate(p);
  const r = await remote(t, { protocol: p }), f = fixture(r.origin);
  await assert.rejects(f.core.connectRuntime(), { code: "INVALID_RESPONSE" });
  assert.equal(f.core.getRuntimeState().state, "FAILED");
  assert.equal(r.counts().deletes, 1);
});
test("connection deadline cancels a non-cooperative store with safe error", async t => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  const gate = deferred();
  const f = fixture("https://mcp.no8do.example", { credentialStore: { load: () => gate.promise, save() {}, delete() {} } });
  const pending = f.core.connectRuntime();
  const rejected = assert.rejects(pending, { code: "RUNTIME_CONNECTION_TIMEOUT" });
  await Promise.resolve();
  t.mock.timers.tick(30_000);
  await rejected;
  assert.equal(f.core.getRuntimeState().state, "FAILED");
  await f.core.closeRuntime();
  gate.resolve(secret);
  await Promise.resolve();
  assert.equal(f.core.getRuntimeState().state, "DISCONNECTED");
  safe(f);
});
