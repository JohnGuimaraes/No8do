import assert from "node:assert/strict";
import test from "node:test";
import { resolveWorkspaceId } from "./workspace.js";

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
