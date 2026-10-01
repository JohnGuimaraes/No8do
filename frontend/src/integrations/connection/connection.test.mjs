import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import ts from "typescript";

// Native Node runner: no new test framework or dependency. Mock only the shared HTTP boundary.
function harness() {
  const calls = [];
  const storage = new Map();
  class ApiRequestError extends Error { constructor(status, message) { super(message); this.status = status; } }
  const source = readFileSync(new URL("./connectionApi.ts", import.meta.url), "utf8");
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const context = {
    exports: {},
    require: name => {
      assert.equal(name, "@/lib/api");
      return { ApiRequestError, apiRequest: (path, options) => { calls.push({ path, options }); return Promise.resolve({}); } };
    },
    sessionStorage: { setItem: (key, value) => storage.set(key, value), getItem: key => storage.get(key) ?? null, removeItem: key => storage.delete(key) },
  };
  vm.runInNewContext(compiled, context);
  return { api: context.exports, calls, storage, context, ApiRequestError };
}

test("code normalizes case, spaces and hyphens without accepting invalid alphabet", () => {
  const { api } = harness();
  assert.equal(api.normalizeUserCode(" n8d4-x2k7 "), "N8D4X2K7");
  assert.equal(api.displayUserCode("N8D4X2K7"), "N8D4-X2K7");
  assert.equal(api.validUserCode("N8D4X2K7"), true);
  for (const code of ["", "12345678", "OOOOOOOO", "IIIIIIII", "N8D4X2K7EXTRA"]) assert.equal(api.validUserCode(code), false);
});

test("inspect and deny use only human bootstrap endpoints with body, never URL code", async () => {
  const { api, calls } = harness();
  const signal = new AbortController().signal;
  await api.inspectConnection("N8D4X2K7", signal);
  await api.denyConnection("N8D4X2K7", signal);
  assert.deepEqual(calls.map(call => call.path), ["/api/integration-authorizations/bootstrap/inspect", "/api/integration-authorizations/bootstrap/deny"]);
  for (const { path, options } of calls) {
    assert.equal(path.includes("N8D4X2K7"), false);
    assert.equal(options.body.userCode, "N8D4X2K7");
    assert.equal(options.method, "POST"); assert.equal(options.signal, signal);
  }
});

test("approval sends exactly one existing Agent or newAgent.name without early creation", async () => {
  const { api, calls } = harness();
  const signal = new AbortController().signal;
  await api.approveConnection("N8D4X2K7", "workspace", { existingAgentId: "agent" }, signal);
  await api.approveConnection("N8D4X2K7", "workspace", { newAgent: { name: "New agent" } }, signal);
  assert.equal(calls.length, 2);
  assert.equal(calls[0].options.body.existingAgentId, "agent");
  assert.equal(calls[0].options.body.newAgent, undefined);
  assert.equal(calls[1].options.body.existingAgentId, undefined);
  assert.equal(calls[1].options.body.newAgent.name, "New agent");
  assert.deepEqual(Object.keys(calls[1].options.body.newAgent), ["name"]);
  assert.equal(calls.every(call => call.path.endsWith("/bootstrap/approve")), true);
});

test("Agent list is scoped and path encodes Workspace identity", async () => {
  const { api, calls } = harness();
  await api.listConnectionAgents("w/other", new AbortController().signal);
  assert.equal(calls[0].path, "/api/workspaces/w%2Fother/agents");
});

test("errors never display backend messages or secrets", () => {
  const { api, ApiRequestError } = harness();
  for (const status of [400, 401, 403, 404, 409, 429, 500]) {
    assert.equal(api.connectionError(new ApiRequestError(status, "SENSITIVE_BACKEND_DETAIL")).includes("SENSITIVE_BACKEND_DETAIL"), false);
  }
  assert.equal(api.connectionError(new Error("SECRET_NETWORK_ERROR")).includes("SECRET_NETWORK_ERROR"), false);
});

test("return marker stores only fixed intent, never a URL or code", () => {
  const { api, storage } = harness();
  assert.equal(api.hasConnectionReturn(), false);
  assert.equal(api.rememberConnectionReturn(), true);
  assert.equal(api.hasConnectionReturn(), true);
  assert.deepEqual([...storage.values()], ["1"]);
  api.clearConnectionReturn(); assert.equal(storage.size, 0);
});

test("unavailable storage fails safely without redirect input", () => {
  const { api, context } = harness();
  context.sessionStorage = { setItem() { throw new Error(); }, getItem() { throw new Error(); }, removeItem() { throw new Error(); } };
  assert.equal(api.rememberConnectionReturn(), false);
  assert.equal(api.hasConnectionReturn(), false);
  assert.doesNotThrow(() => api.clearConnectionReturn());
});

test("pending expires locally; approved snapshot waits for canonical inspect; terminal states remain terminal", () => {
  const { api } = harness();
  const expiresAt = "2026-09-30T12:00:00Z";
  const boundary = Date.parse(expiresAt);
  for (const state of ["PENDING"]) {
    assert.equal(api.effectiveConnectionState({ state, expiresAt }, boundary - 1), state);
    assert.equal(api.effectiveConnectionState({ state, expiresAt }, boundary), "EXPIRED");
  }
  assert.equal(api.effectiveConnectionState({ state: "APPROVED", expiresAt }, boundary + 10000), "APPROVED");
  for (const state of ["DENIED", "CONSUMED", "EXPIRED"]) {
    assert.equal(api.effectiveConnectionState({ state, expiresAt }, boundary + 10000), state);
  }
});

test("explicit invite has priority over abandoned connection marker without open redirects", () => {
  const { api } = harness();
  assert.equal(api.loginIntentDestination("invite-token", true), "/invite?token=invite-token");
  assert.equal(api.loginIntentDestination("https://outside.test/", true), "/invite?token=https%3A%2F%2Foutside.test%2F");
  assert.equal(api.loginIntentDestination(null, true), "/connect/no8do");
  assert.equal(api.loginIntentDestination(null, false), null);
});

// Render the authored component tree with controlled hook state; no browser or HTTP requests.
function pageElements(overrides) {
  const source = readFileSync(new URL("./ConnectionPage.tsx", import.meta.url), "utf8");
  const parsed = ts.createSourceFile("ConnectionPage.tsx", source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const page = parsed.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === "ConnectionPage");
  const stateNames = page.body.statements.filter(ts.isVariableStatement).flatMap(statement =>
    statement.declarationList.declarations.filter(declaration => ts.isCallExpression(declaration.initializer)
      && declaration.initializer.expression.getText(parsed) === "useState")
      .map(declaration => declaration.name.elements[0].name.text));
  let stateIndex = 0;
  const jsx = (type, props) => ({ type, props: props || {} });
  const context = { exports: {}, Date, AbortController, require: name => {
    if (name === "react") return {
      useState: initial => { const key = stateNames[stateIndex++]; return [Object.hasOwn(overrides, key) ? overrides[key]
        : typeof initial === "function" ? initial() : initial, () => {}]; },
      useRef: current => ({ current }), useEffect: () => {},
    };
    if (name === "react/jsx-runtime") return { jsx, jsxs: jsx, Fragment: "fragment" };
    if (name === "@/components/ui/button") return { Button: props => jsx("button", props) };
    if (name === "@phosphor-icons/react") return new Proxy({}, { get: () => () => null });
    if (name === "./ConnectionPath") return { ConnectionPath: () => null };
    if (name === "./connectionApi") return harness().api;
    if (name === "./connection.css" || name === "@/assets/logo/no8do-icone.png") return {};
    throw new Error(`Unexpected UI dependency: ${name}`);
  } };
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX } }).outputText, context);
  const elements = [];
  function visit(node) {
    if (Array.isArray(node)) { node.forEach(visit); return; }
    if (!node || typeof node !== "object") return;
    if (typeof node.type === "function") { visit(node.type(node.props)); return; }
    elements.push(node); visit(node.props.children);
  }
  visit(context.exports.ConnectionPage({ onSessionExpired: async () => {} }));
  return elements;
}

test("pending request can be denied without any eligible Workspace or Agent", () => {
  const elements = pageElements({ code: "N8D4X2K7", inspection: { state: "PENDING", expiresAt: "2099-01-01T00:00:00Z",
    hostType: "CODEX", displayLabel: "Integration", eligibleWorkspaces: [] } });
  const deny = elements.find(node => node.type === "button" && node.props.children === "Recusar");
  assert.ok(deny); assert.equal(deny.props.disabled, false);
  const next = elements.find(node => node.type === "button" && node.props.children?.[0] === "Continuar");
  assert.equal(next.props.disabled, true);
});

test("approved snapshot after deadline remains approved and offers explicit canonical re-inspection", () => {
  const elements = pageElements({ code: "N8D4X2K7", inspection: { state: "APPROVED", expiresAt: "2020-01-01T00:00:00Z",
    hostType: "CODEX", displayLabel: "Integration", eligibleWorkspaces: [] } });
  assert.equal(elements.find(node => node.type === "h1").props.children, "Conexão aprovada");
  const inspect = elements.find(node => node.type === "button" && node.props.children === "Conferir estado novamente");
  assert.ok(inspect); assert.equal(inspect.props.disabled, false);
});
