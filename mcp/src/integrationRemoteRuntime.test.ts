import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";
import { once } from "node:events";
import { randomUUID } from "node:crypto";
import test from "node:test";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { createRemoteMcpService } from "./http.js";
import { AgentSessionHeartbeat } from "./agentSessionHeartbeat.js";
import { AgentSessionHeader, No8doClient, No8doApiError, type AgentProtocol } from "./no8doClient.js";

const a = "11111111-1111-4111-8111-111111111111";
const b = "22222222-2222-4222-8222-222222222222";
const tokenA = "no8do_int_fixtureA.secretA", tokenB = "no8do_int_fixtureB.secretB";
const readCapabilities = ["REPLAY_CATALOG_LIST", "REPLAY_SEARCH", "REUSABLE_KNOWLEDGE_DISCOVERY",
  "REPLAY_READ", "REPLAY_QUALITY_READ", "REPLAY_VERSION_READ", "REPLAY_RELATIONS"];
const protocol: AgentProtocol = {
  protocolName: "test", protocolVersion: 2, systemName: "No8do", purpose: "test",
  replayGuidance: { summary: "test", searchBeforeNonTrivialWork: true, preferExistingKnowledge: true,
    searchBeforeCreate: true, recordUsageOnlyWhenMateriallyUsed: true, validatedRequiresEvidence: true,
    avoidTrivialKnowledge: true, avoidDuplicateKnowledge: true, neverStoreSecrets: true,
    neverStoreCredentials: true, avoidDiscardedAttempts: true },
  capabilities: { capabilities: readCapabilities.map(id => ({ id, description: id, readOnly: true })) },
  policies: { policies: [] }, integrationExtensions: { extensions: [] }
};
async function listen(server: Server) {
  server.listen(0, "127.0.0.1"); await once(server, "listening");
  return `http://127.0.0.1:${(server.address() as { port: number }).port}`;
}
async function close(server: Server) {
  const done = once(server, "close"); server.close(); server.closeAllConnections(); await done;
}
const initialize = { jsonrpc: "2.0", id: 1, method: "initialize", params: {
  protocolVersion: "2025-03-26", capabilities: {}, clientInfo: { name: "test", version: "1" } } };
const headers = (token: string) => ({ Authorization: `Bearer ${token}`, "content-type": "application/json",
  accept: "application/json, text/event-stream" });

async function fixture(env?: string) {
  const calls: { path: string; token: string; session?: string; agentCredential?: string; body: Record<string, unknown> }[] = [];
  const sessions = new Map<string, { token: string; workspace: string; capabilities: string[]; disconnected: boolean; revoked: boolean; version: number | null }>();
  const denied = new Set<string>();
  const api = createServer(async (request, response) => {
    const token = request.headers.authorization?.slice(7) ?? "";
    const path = request.url ?? "";
    const session = request.headers["x-no8do-agent-session-id"] as string | undefined;
    const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk));
    const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString()) as Record<string, unknown> : {};
    calls.push({ path, token, session, agentCredential: request.headers["x-no8do-agent-credential"] as string | undefined, body });
    response.setHeader("content-type", "application/json");
    const fail = (status: number, error: string) => { response.statusCode = status; response.end(JSON.stringify({ error })); };
    if (![tokenA, tokenB].includes(token) || denied.has(token)) return fail(401, "Unauthorized");
    if (path === "/api/agent-protocol") return response.end(JSON.stringify(protocol));
    if (path === "/api/agent-sessions") {
      assert.equal(body.workspaceId, null);
      const id = randomUUID(), workspace = token === tokenA ? a : b;
      sessions.set(id, { token, workspace, capabilities: [...readCapabilities], disconnected: false, revoked: false, version: null });
      return response.end(JSON.stringify({ ...body, sessionId: id, workspaceId: workspace, runtimeMode: "FULL",
        protocolName: "test", protocolVersion: 2, registeredAt: "2026-09-30T00:00:00Z" }));
    }
    const id = path.match(/^\/api\/agent-sessions\/([^/]+)/)?.[1] ?? session;
    const state = id ? sessions.get(id) : undefined;
    if (!state || state.token !== token) return fail(403, "Forbidden");
    if (state.revoked) return fail(409, "AGENT_SESSION_REVOKED");
    if (path.endsWith("/context")) return response.end(JSON.stringify({ sessionId: id, workspaceId: state.workspace,
      runtimeMode: state.capabilities.length ? "FULL" : "OFF", effectiveCapabilities: state.capabilities.map(capability => ({ id: capability, description: capability, readOnly: true })),
      presenceStatus: state.disconnected ? "DISCONNECTED" : "ACTIVE", disconnectedAt: state.disconnected ? "2026-09-30T00:00:00Z" : null }));
    if (path.endsWith("/disconnect")) return response.end("{}");
    if (state.disconnected) return fail(409, "AGENT_SESSION_DISCONNECTED");
    if (path.endsWith("/operational-context/state")) return response.end(JSON.stringify(state.version === null ? { exists: false } : { exists: true, context: { sessionId: id, version: state.version } }));
    if (path.endsWith("/operational-context")) {
      if (body.expectedVersion !== state.version) return fail(409, "Conflict");
      state.version = state.version === null ? 0 : state.version + 1;
      return response.end(JSON.stringify({ sessionId: id, version: state.version, workspaceId: state.workspace }));
    }
    if (path.startsWith("/api/integration-runtime/replays")) return response.end(JSON.stringify([{ id: state.workspace, title: state.workspace, workspaceId: state.workspace }]));
    return fail(403, "Forbidden");
  });
  const apiUrl = await listen(api);
  const service = createRemoteMcpService(apiUrl, env, (client, id) => new AgentSessionHeartbeat(client, id, () => () => {}));
  const url = await listen(service);
  const clients: Client[] = [];
  async function connect(token = tokenA) {
    const client = new Client({ name: "test", version: "1" }); clients.push(client);
    const transport = new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { requestInit: { headers: headers(token) } });
    await client.connect(transport); return { client, transport };
  }
  async function rpc(token: string, transportSession: string, method: string, params: object = {}) {
    return fetch(`${url}/mcp`, { method: "POST", headers: { ...headers(token), "mcp-session-id": transportSession },
      body: JSON.stringify({ jsonrpc: "2.0", id: 88, method, params }) });
  }
  return { calls, sessions, denied, connect, rpc, url, apiUrl,
    cleanup: async () => { for (const client of clients) await client.close().catch(() => {}); await close(service); await close(api); } };
}

test("Integration conecta sem PAT/env, usa sessão própria e somente ferramentas read-only", async () => {
  const f = await fixture("invalid-legacy-workspace");
  try {
    const [ca, cb] = await Promise.all([f.connect(tokenA), f.connect(tokenB)]);
    const names = (await ca.client.listTools()).tools.map(tool => tool.name);
    assert.deepEqual(names.filter(name => !name.startsWith("get_agent_")).sort(), ["find_reusable_knowledge", "get_replay", "get_replay_quality", "get_replay_version", "list_replay_relations", "list_replay_versions", "list_replays", "search_replays"].sort());
    assert.ok(!names.some(name => /create|update|usage|delete|operational/.test(name)));
    const resultA = await ca.client.callTool({ name: "list_replays", arguments: {} });
    const resultB = await cb.client.callTool({ name: "list_replays", arguments: {} });
    assert.match(JSON.stringify(resultA), new RegExp(a)); assert.doesNotMatch(JSON.stringify(resultA), new RegExp(b));
    assert.match(JSON.stringify(resultB), new RegExp(b)); assert.doesNotMatch(JSON.stringify(resultB), new RegExp(a));
    const registrations = f.calls.filter(call => call.path === "/api/agent-sessions");
    assert.equal(registrations.length, 2);
    assert.notEqual(registrations[0].body.transportSessionFingerprint, registrations[1].body.transportSessionFingerprint);
    assert.equal(f.sessions.size, 2);
    assert.ok(f.calls.every(call => !call.agentCredential && !JSON.stringify(call.body).includes("no8do_int_")));
    const before = f.calls.filter(call => call.path.startsWith("/api/integration-runtime/replays")).length;
    const mismatch = await ca.client.callTool({ name: "list_replays", arguments: { workspaceId: b } });
    assert.equal(mismatch.isError, true);
    for (const name of ["create_replay", "update_replay", "register_replay_usage", "create_replay_relation", "delete_replay_relation"]) {
      const result = await ca.client.callTool({ name, arguments: {} }); assert.equal(result.isError, true);
    }
    assert.equal(f.calls.filter(call => call.path.startsWith("/api/integration-runtime/replays")).length, before);
    assert.ok(f.calls.every(call => !call.path.startsWith("/api/workspaces/")));
    const ids = [...f.sessions.keys()];
    f.sessions.get(ids[0])!.capabilities = [];
    assert.ok(!(await ca.client.listTools()).tools.some(tool => tool.name === "list_replays"));
    assert.ok((await cb.client.listTools()).tools.some(tool => tool.name === "list_replays"));
    const swapped = await f.rpc(tokenB, ca.transport.sessionId!, "tools/list"); assert.equal(swapped.status, 401);
  } finally { await f.cleanup(); }
});

test("Integration revalida sessão mesmo com protocolo válido: revoke/disconnect e authorization inválida", async () => {
  for (const reason of ["session-revoked", "disconnected", "authorization-invalid"] as const) {
    const f = await fixture();
    try {
      const { client, transport } = await f.connect();
      await client.callTool({ name: "list_replays", arguments: {} });
      const before = f.calls.filter(call => call.path.startsWith("/api/integration-runtime/replays")).length;
      const state = [...f.sessions.values()][0];
      if (reason === "session-revoked") state.revoked = true;
      else if (reason === "disconnected") state.disconnected = true;
      else f.denied.add(tokenA);
      const response = await f.rpc(tokenA, transport.sessionId!, "tools/call", { name: "list_replays", arguments: {} });
      assert.equal(response.status, reason === "authorization-invalid" ? 401 : 409);
      assert.ok(!(await response.text()).includes(tokenA));
      assert.equal(f.calls.filter(call => call.path.startsWith("/api/integration-runtime/replays")).length, before);
    } finally { await f.cleanup(); }
  }
});

test("Integration context interno isolado preserva expectedVersion e rejeita troca de autoridade", async () => {
  const f = await fixture();
  try {
    const ca = await f.connect(tokenA), cb = await f.connect(tokenB);
    const update = { expectedVersion: null, repository: null, branch: "main", workingDirectory: null, references: [] };
    const first = await f.rpc(tokenA, ca.transport.sessionId!, "no8do/operational-context/update", update);
    assert.equal(first.status, 200); assert.equal((await first.json() as { result: { version: number } }).result.version, 0);
    const other = await f.rpc(tokenB, cb.transport.sessionId!, "no8do/operational-context/get");
    assert.deepEqual((await other.json() as { result: object }).result, { exists: false });
    const conflict = await f.rpc(tokenA, ca.transport.sessionId!, "no8do/operational-context/update", update);
    assert.match(await conflict.text(), /OPERATIONAL_CONTEXT_CONFLICT/);
    const forbidden = await f.rpc(tokenA, ca.transport.sessionId!, "no8do/operational-context/update", { ...update, agentId: b });
    assert.match(await forbidden.text(), /error/);
    for (const field of ["workspaceId", "agentId", "userId", "authorizationId", "sessionId"]) {
      const response = await fetch(`${f.url}/mcp`, { method: "POST", headers: headers(tokenA),
        body: JSON.stringify({ ...initialize, params: { ...initialize.params, [field]: b } }) });
      assert.equal(response.status, 400);
    }
  } finally { await f.cleanup(); }
});

test("Integration mixed/invalid falha fechado sem fallback", async () => {
  const f = await fixture();
  try {
    const mixed = await fetch(`${f.url}/mcp`, { method: "POST", headers: { ...headers(tokenA), "X-No8do-Agent-Credential": "legacy-secret" }, body: JSON.stringify(initialize) });
    assert.equal(mixed.status, 400); assert.equal(f.calls.length, 0);
    const invalid = await fetch(`${f.url}/mcp`, { method: "POST", headers: headers("no8do_int_invalid"), body: JSON.stringify(initialize) });
    assert.equal(invalid.status, 401); assert.equal(f.sessions.size, 0);
    assert.ok(f.calls.every(call => call.token.startsWith("no8do_int_")));
  } finally { await f.cleanup(); }
});

test("Integration transport exige HTTPS, nega redirects e sanitiza erros sem vazar Authorization", async () => {
  let calls = 0;
  const fake = (async (_url, init) => { calls++; assert.equal(init?.redirect, "error"); throw new Error(tokenA); }) as typeof fetch;
  const remote = new No8doClient("http://example.test", tokenA, fake);
  await assert.rejects(remote.getAgentProtocol(), /requires HTTPS/); assert.equal(calls, 0);
  const secure = new No8doClient("https://example.test", tokenA, fake);
  await assert.rejects(secure.getAgentProtocol(), error => error instanceof No8doApiError && !String(error).includes(tokenA));
  const target = createServer((_request, response) => { calls++; response.end("{}"); });
  const targetUrl = await listen(target);
  const redirect = createServer((_request, response) => { response.writeHead(302, { location: targetUrl }); response.end(); });
  const redirectUrl = await listen(redirect), before = calls;
  try {
    await assert.rejects(new No8doClient(redirectUrl, tokenA).getAgentProtocol()); assert.equal(calls, before);
  } finally { await close(redirect); await close(target); }
  const session = new AgentSessionHeader(); session.set(randomUUID(), a);
  const client = new No8doClient("https://example.test", tokenA, fake, session);
  await assert.rejects(client.createReplay(a, { title: "forbidden", type: "FIX" }), error => error instanceof No8doApiError && error.status === 403);
});
