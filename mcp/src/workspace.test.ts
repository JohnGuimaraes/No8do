import assert from "node:assert/strict";
import test from "node:test";
import { requireRemoteWorkspaceId, resolveWorkspaceId } from "./workspace.js";

const explicitWorkspaceId = "11111111-1111-4111-8111-111111111111";
const environmentWorkspaceId = "22222222-2222-4222-8222-222222222222";

test("usa workspace explícito", () => {
  assert.equal(resolveWorkspaceId(explicitWorkspaceId, undefined), explicitWorkspaceId);
});

test("usa workspace por ambiente quando não há argumento", () => {
  assert.equal(resolveWorkspaceId(undefined, environmentWorkspaceId), environmentWorkspaceId);
});

test("prioriza workspace explícito sobre o ambiente", () => {
  assert.equal(resolveWorkspaceId(explicitWorkspaceId, environmentWorkspaceId), explicitWorkspaceId);
});

test("falha claramente sem workspace explícito nem ambiente", () => {
  assert.throws(() => resolveWorkspaceId(undefined, undefined), /workspaceId é obrigatório/);
});

test("HTTP usa sempre o workspace configurado", () => {
  assert.equal(resolveWorkspaceId(undefined, environmentWorkspaceId, "http"), environmentWorkspaceId);
  assert.equal(resolveWorkspaceId(environmentWorkspaceId, environmentWorkspaceId, "http"), environmentWorkspaceId);
});

test("HTTP rejeita workspace fora do escopo", () => {
  assert.throws(() => resolveWorkspaceId(explicitWorkspaceId, environmentWorkspaceId, "http"), /fora do escopo/);
});

test("sessão Agent-bound usa o Workspace retornado pelo registro mesmo com legado configurado", () => {
  assert.equal(resolveWorkspaceId(undefined, environmentWorkspaceId, "http", "session-a", explicitWorkspaceId), explicitWorkspaceId);
  assert.equal(resolveWorkspaceId(explicitWorkspaceId, environmentWorkspaceId, "http", "session-a", explicitWorkspaceId), explicitWorkspaceId);
  assert.throws(() => resolveWorkspaceId(environmentWorkspaceId, environmentWorkspaceId, "http", "session-a", explicitWorkspaceId), /fora do escopo/);
  assert.throws(() => resolveWorkspaceId(undefined, environmentWorkspaceId, "http", "session-a", null), /no authorized Workspace/);
});

test("HTTP exige workspace configurado válido", () => {
  assert.throws(() => requireRemoteWorkspaceId(undefined), /NO8DO_WORKSPACE_ID é obrigatória/);
  assert.throws(() => requireRemoteWorkspaceId(""), /NO8DO_WORKSPACE_ID é obrigatória/);
  assert.throws(() => requireRemoteWorkspaceId("invalido"), /UUID válido/);
});
