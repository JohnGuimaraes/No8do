import test from "node:test";
import assert from "node:assert/strict";
import { remote, fixture, deferred, safe, secret, agentSession, emptySignal, timestamp } from "./fixtures/remote.mjs";
const repository = { vcs: "GIT", provider: "github", host: "github.com", namespace: "example", name: "repo" };
const input = (expectedVersion = null) => ({ expectedVersion, ...emptySignal(), repository: { ...repository }, branch: "feat/context",
  workingDirectory: "integration/core", references: [{ kind: "ISSUE", provider: "github", key: "123" }] });
const sid = "00000000-0000-4000-8000-000000000001";
const snapshot = () => ({ sessionId: sid, version: 0, signal: emptySignal(), resolution: {
  project: { id: null, status: "UNRESOLVED", confidence: null },
  workItem: { id: null, status: "UNRESOLVED", confidence: null } }, updatedAt: timestamp });
async function connected(t, options = {}) {
  const r = await remote(t, options), f = fixture(r.origin);
  t.after(() => f.core.closeRuntime());
  await f.core.connectRuntime();
  return { ...f, r };
}
test("B.3 requires runtime, never auto-connects or auto-authorizes", async () => {
  const f = fixture("https://mcp.example");
  for (const call of [() => f.core.getAgentSessionContext(), () => f.core.getOperationalContext(),
    () => f.core.replaceOperationalContext(input())])
    await assert.rejects(call(), { code: "RUNTIME_NOT_CONNECTED" });
  assert.equal(f.core.getRuntimeState().state, "DISCONNECTED");
  assert.equal(f.keys.length, 0);
});
test("real MCP observes server-created AgentSession and immutable presence/capabilities/policies", async t => {
  const f = await connected(t);
  const context = await f.core.getAgentSessionContext();
  assert.equal(context.sessionId, f.r.agents[0]);
  assert.equal(context.workspaceId, "11111111-1111-4111-8111-111111111111");
  assert.equal(context.transport, "MCP"); assert.equal(context.runtimeMode, "FULL");
  assert.equal(context.presenceStatus, "ACTIVE");
  assert.equal(context.lastSeenAt, timestamp);
  for (const value of [context, context.effectiveCapabilities, context.effectiveCapabilities[0], context.policies[0]])
    assert.ok(Object.isFrozen(value));
  const rpc = f.r.requests.find(r => r.body?.params?.name === "get_agent_context").body;
  assert.deepEqual(rpc.params, { name: "get_agent_context", arguments: {} });
  assert.ok(!JSON.stringify(context).includes(secret));
  safe(f);
});
test("operational custom GET and replace preserve null, version0, N and exact allowlist", async t => {
  const f = await connected(t);
  assert.deepEqual(await f.core.getOperationalContext(), { exists: false });
  const create = await f.core.replaceOperationalContext(input(null));
  assert.equal(create.version, 0);
  const read = await f.core.getOperationalContext();
  assert.equal(read.exists, true); assert.equal(read.context.version, 0);
  const replace = await f.core.replaceOperationalContext({ ...input(0), branch: null, workingDirectory: null, repository: null, references: [] });
  assert.equal(replace.version, 1);
  const next = await f.core.replaceOperationalContext(input(1));
  assert.equal(next.version, 2);
  assert.ok(Object.isFrozen(next.signal.repository));
  assert.ok(Object.isFrozen(next.resolution.project));
  const updates = f.r.requests.filter(r => r.body?.method === "no8do/operational-context/update");
  assert.deepEqual(updates.map(r => r.body.params.expectedVersion), [null, 0, 1]);
  for (const r of updates) assert.deepEqual(Object.keys(r.body.params).sort(),
    ["expectedVersion", "repository", "branch", "workingDirectory", "references"].sort());
  for (const r of f.r.requests.filter(r => r.body?.method === "no8do/operational-context/get"))
    assert.deepEqual(r.body.params, {});
  assert.ok(f.r.requests.every(r => r.url === "/mcp" && r.headers.cookie === undefined &&
    r.headers["x-no8do-agent-credential"] === undefined));
  safe(f);
});
test("optimistic conflict is typed, does not overwrite, retry or close healthy runtime", async t => {
  const f = await connected(t);
  await f.core.replaceOperationalContext(input());
  await assert.rejects(f.core.replaceOperationalContext(input(null)), { code: "OPERATIONAL_CONTEXT_CONFLICT" });
  assert.equal(f.core.getRuntimeState().state, "CONNECTED");
  assert.equal((await f.core.getOperationalContext()).context.version, 0);
  assert.equal(f.r.requests.filter(r => r.body?.method === "no8do/operational-context/update").length, 2);
  await f.core.replaceOperationalContext(input(0));
});
const invalids = [
  ["missing expectedVersion", v => { delete v.expectedVersion; }],
  ["negative version", v => { v.expectedVersion = -1; }],
  ["fractional version", v => { v.expectedVersion = 0.5; }],
  ["unsafe integer version", v => { v.expectedVersion = Number.MAX_SAFE_INTEGER + 1; }],
  ["absolute POSIX path", v => { v.workingDirectory = "/home/user"; }],
  ["drive path", v => { v.workingDirectory = "C:\\Users\\name"; }],
  ["UNC path", v => { v.workingDirectory = "\\\\server\\share"; }],
  ["parent segment", v => { v.workingDirectory = "../src"; }],
  ["dot segment", v => { v.workingDirectory = "src/./file"; }],
  ["path control", v => { v.workingDirectory = "src" + String.fromCharCode(10) + "file"; }],
  ["branch control", v => { v.branch = "main" + String.fromCharCode(10); }],
  ["branch limit", v => { v.branch = "a".repeat(256); }],
  ["directory limit", v => { v.workingDirectory = "a".repeat(1025); }],
  ["reference limit", v => { v.references = Array.from({ length: 21 }, (_,n) => ({ kind: "TASK", provider: "github", key: String(n) })); }],
  ["duplicate references", v => { v.references.push(v.references[0]); }],
  ["duplicate normalized provider", v => { v.references.push({ ...v.references[0], provider: "GitHub" }); }],
  ["reference key limit", v => { v.references[0].key = "a".repeat(129); }],
  ["reference URL", v => { v.references[0].key = "https://example.org"; }],
  ["reference PAT", v => { v.references[0].key = "ghp_FAKE_TEST_ONLY"; }],
  ["reference credential", v => { v.references[0].key = secret; }],
  ["provider limit", v => { v.references[0].provider = "a".repeat(65); }],
  ["unsupported kind", v => { v.references[0].kind = "CHAT"; }],
  ["repository raw URL", v => { v.repository = "https://github.com/example/repo"; }],
  ["cloneUrl", v => { v.cloneUrl = "https://example.org"; }],
  ["repository extra URL", v => { v.repository.remoteUrl = "https://example.org"; }],
  ["unsupported vcs", v => { v.repository.vcs = "SVN"; }],
  ["unsupported provider", v => { v.repository.provider = "gitlab"; }],
  ["unsupported host", v => { v.repository.host = "custom.example"; }],
  ["host limit", v => { v.repository.host = "a".repeat(254); }],
  ["namespace limit", v => { v.repository.namespace = "a".repeat(513); }],
  ["name limit", v => { v.repository.name = "a".repeat(256); }],
  ["namespace traversal", v => { v.repository.namespace = "../name"; }],
  ["branch secret", v => { v.branch = secret; }],
  ["chat payload", v => { v.chat = "private chat"; }],
  ["source payload", v => { v.code = "source"; }],
  ...["sessionId", "workspaceId", "agentId", "AgentId", "authorizationId", "userId", "workspaceHint", "projectId", "workItemId"]
    .map(k => [k + " authority", v => { v[k] = sid; }])
];
for (const [name, mutate] of invalids) test("input rejects " + name + " before network", async t => {
  const f = await connected(t), payload = input(); mutate(payload);
  const before = f.r.requests.length;
  await assert.rejects(f.core.replaceOperationalContext(payload), e => {
    assert.equal(e.code, "INVALID_OPERATIONAL_CONTEXT"); safe(f,e); return true;
  });
  assert.equal(f.r.requests.length, before);
});
test("canonical relative directory, null signals and bounded reference accepted", async t => {
  const f = await connected(t), payload = input();
  payload.workingDirectory = "integration" + String.fromCharCode(92) + "core";
  payload.references[0].provider = "GitHub";
  const value = await f.core.replaceOperationalContext(payload);
  assert.equal(value.signal.workingDirectory, "integration/core");
  assert.equal(value.signal.references[0].provider, "github");
});
for (const field of ["sessionId", "workspaceId", "runtimeMode", "presenceStatus", "effectiveCapabilities", "policies", "registeredAt"])
  test("session rejects malformed " + field, async t => {
    const response = agentSession(sid); response[field] = 42;
    const f = await connected(t, { sessionResponse: response });
    await assert.rejects(f.core.getAgentSessionContext(), { code: "INVALID_RESPONSE" });
  });
for (const [name, mutate] of [
  ["extra secret", s => { s.credential = secret; }],
  ["secret description", s => { s.policies[0].description = secret; }],
  ["invalid capability flag", s => { s.effectiveCapabilities[0].readOnly = "yes"; }],
  ["invalid policy enforcement", s => { s.policies[0].enforcement = "CUSTOM"; }],
  ["invalid date", s => { s.lastActivityAt = "not-a-date"; }]
]) test("session rejects " + name, async t => {
  const response = agentSession(sid); mutate(response);
  const f = await connected(t, { sessionResponse: response });
  await assert.rejects(f.core.getAgentSessionContext(), e => { assert.equal(e.code, "INVALID_RESPONSE"); safe(f,e); return true; });
});
for (const kind of ["presence", "timestamp"]) test("disconnected session fails closed via " + kind, async t => {
  const response = agentSession(sid);
  if (kind === "presence") response.presenceStatus = "DISCONNECTED"; else response.disconnectedAt = timestamp;
  const f = await connected(t, { sessionResponse: response });
  await assert.rejects(f.core.getAgentSessionContext(), { code: "AGENT_SESSION_DISCONNECTED" });
  assert.equal(f.core.getRuntimeState().state, "FAILED");
  await assert.rejects(f.core.getOperationalContext(), { code: "RUNTIME_NOT_CONNECTED" });
});
for (const [name, response] of [["boolean", { exists: "false" }], ["missing context", { exists: true }],
  ["extra field", { exists: false, credential: secret }], ["bad snapshot", { exists: true, context: { ...snapshot(), version: -1 } }]])
  test("operational GET rejects " + name, async t => {
    const f = await connected(t, { readResponse: response });
    await assert.rejects(f.core.getOperationalContext(), e => { assert.equal(e.code, "INVALID_RESPONSE"); safe(f,e); return true; });
  });
test("resolution is backend-derived and validated", async t => {
  const context = snapshot();
  context.resolution.project = { id: sid, status: "RESOLVED", confidence: "HIGH" };
  const f = await connected(t, { readResponse: { exists: true, context } });
  const read = await f.core.getOperationalContext();
  assert.equal(read.context.resolution.project.id, sid);
  assert.ok(Object.isFrozen(read.context.resolution.project));
});
test("inconsistent resolution rejected", async t => {
  const context = snapshot(); context.resolution.project.status = "RESOLVED";
  const f = await connected(t, { readResponse: { exists: true, context } });
  await assert.rejects(f.core.getOperationalContext(), { code: "INVALID_RESPONSE" });
});
test("response cannot switch observed AgentSession", async t => {
  const context = snapshot(); context.sessionId = "22222222-2222-4222-8222-222222222222";
  const f = await connected(t, { readResponse: { exists: true, context } });
  await f.core.getAgentSessionContext();
  await assert.rejects(f.core.getOperationalContext(), { code: "INVALID_RESPONSE" });
});
test("read operations reject caller authority without network", async t => {
  const f = await connected(t); const before = f.r.requests.length;
  await assert.rejects(f.core.getAgentSessionContext({ sessionId: sid }), { code: "INVALID_INPUT" });
  await assert.rejects(f.core.getOperationalContext({ workspaceId: sid }), { code: "INVALID_INPUT" });
  assert.equal(f.r.requests.length, before);
});
for (const method of ["session", "get", "update"]) test("close cancels " + method + " and old result cannot contaminate reconnect", async t => {
  const gate = deferred(), entered = deferred(), options = { [method + "Gate"]: gate, [method + "Entered"]: entered };
  const f = await connected(t, options);
  const call = method === "session" ? () => f.core.getAgentSessionContext() :
    method === "get" ? () => f.core.getOperationalContext() : () => f.core.replaceOperationalContext(input());
  const pending = call(), rejected = assert.rejects(pending, { code: "RUNTIME_CANCELLED" });
  await entered.promise;
  await f.core.closeRuntime(); await rejected;
  options[method + "Gate"] = undefined;
  await f.core.connectRuntime();
  gate.resolve(); await new Promise(resolve => setImmediate(resolve));
  const session = await f.core.getAgentSessionContext();
  assert.equal(session.sessionId, f.r.agents[1]);
  assert.deepEqual(await f.core.getOperationalContext(), { exists: false });
  assert.equal(f.core.getRuntimeState().state, "CONNECTED");
});
for (const code of ["AGENT_SESSION_REVOKED", "AGENT_SESSION_DISCONNECTED", "OPERATIONAL_CONTEXT_CONFLICT", "AGENT_SESSION_REQUIRED"])
  test("safe JSON-RPC discriminator " + code, async t => {
    const f = await connected(t, { rpcError: code });
    await assert.rejects(f.core.getOperationalContext(), e => {
      assert.equal(e.code, code === "AGENT_SESSION_REQUIRED" ? "AGENT_SESSION_UNAVAILABLE" : code); safe(f,e); return true;
    });
  });
for (const [status, code, httpError] of [[401, "RUNTIME_AUTHENTICATION_FAILED"], [403, "RUNTIME_AUTHORIZATION_FAILED"],
  [409, "RUNTIME_CONFLICT"], [409, "AGENT_SESSION_REVOKED", "AGENT_SESSION_REVOKED"]])
  test("context HTTP " + status + " " + code + " safe without fallback", async t => {
    const f = await connected(t, { httpStatusByMethod: { "no8do/operational-context/get": status }, httpError });
    await assert.rejects(f.core.getOperationalContext(), e => { assert.equal(e.code, code); safe(f,e); return true; });
    assert.equal(f.r.requests.filter(r => r.body?.method === "no8do/operational-context/get").length, 1);
  });
test("raw JSON-RPC exception text never escapes", async t => {
  const f = await connected(t, { rpcError: secret });
  await assert.rejects(f.core.getOperationalContext(), e => { assert.equal(e.code, "INVALID_RESPONSE"); safe(f,e); return true; });
});
