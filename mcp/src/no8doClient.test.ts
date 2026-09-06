import assert from "node:assert/strict";
import test from "node:test";
import { compactReplay, No8doApiError, No8doClient, type Replay } from "./no8doClient.js";

const replay: Replay = { id: "r1", workspaceId: "w1", projectId: null, title: "Replay", type: "FIX", problem: "p", solution: "s", context: null, tags: ["java"], stack: ["spring"], status: "DRAFT", version: 1, usageCount: 0, successCount: 0, failureCount: 0, lastUsedAt: null, createdBy: "u1", createdByName: "User", createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-02T00:00:00Z" };

function client(status = 200, body: unknown = replay) {
  const calls: Array<[string, RequestInit | undefined]> = [];
  const fetchImpl = (async (url: string, init?: RequestInit) => {
    calls.push([url, init]);
    return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
  }) as typeof fetch;
  return { client: new No8doClient("http://localhost:8080/api/", "no8do_pat_secret-value", fetchImpl), calls };
}

test("envia bearer e busca com query codificada", async () => {
  const setup = client(200, [replay]);
  await setup.client.searchReplays("workspace/a", "erro & retry");
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/workspaces/workspace%2Fa/replays/search?q=erro%20%26%20retry");
  assert.equal(new Headers(setup.calls[0][1]?.headers).get("Authorization"), "Bearer no8do_pat_secret-value");
  assert.equal(setup.calls[0][1]?.method, undefined);
});

test("lista o catálogo sem usar busca textual", async () => {
  const setup = client(200, [replay]);
  await setup.client.listReplays("w1");
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/workspaces/w1/replays");
  assert.equal(setup.calls[0][1]?.method, undefined);
});

test("busca sugestões reutilizáveis pelo endpoint similar", async () => {
  const setup = client(200, [{ id: "r1", title: "Replay", type: "FIX", status: "VALIDATED", version: 1, stack: ["Spring"], usageCount: 2, score: 100 }]);
  await setup.client.findReusableKnowledge("w1", { query: "Replay", tags: ["java"] });
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/workspaces/w1/replays/similar");
  assert.deepEqual(JSON.parse(String(setup.calls[0][1]?.body)), { query: "Replay", tags: ["java"] });
});

test("lista, cria e remove relações pelos endpoints corretos", async () => {
  const setup = client(200, []);
  await setup.client.listReplayRelations("w1", "r1");
  await setup.client.createReplayRelation("w1", "r1", "r2", "RELATED_TO");
  await setup.client.deleteReplayRelation("w1", "r1", "rel1");
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/workspaces/w1/replays/r1/relations");
  assert.deepEqual(JSON.parse(String(setup.calls[1][1]?.body)), { targetReplayId: "r2", type: "RELATED_TO" });
  assert.equal(setup.calls[2][1]?.method, "DELETE");
});

test("lista retorna erro seguro para PAT inválido ou revogado", async () => {
  const setup = client(401, { message: "token interno" });
  await assert.rejects(() => setup.client.listReplays("w1"), (error: unknown) => error instanceof No8doApiError && error.message === "Token No8do ausente, inválido ou revogado.");
});

test("usa endpoints e corpos corretos para get, create, update e register usage", async () => {
  const setup = client();
  await setup.client.getReplay("w1", "r1");
  await setup.client.createReplay("w1", { title: "Novo", type: "RECIPE", tags: ["node"] });
  await setup.client.createReplay("w1", { title: "Sem projeto", type: "RECIPE", projectId: null });
  await setup.client.updateReplay("w1", "r1", { solution: "melhor" });
  await setup.client.registerReplayUsage("w1", "r1", { result: "SUCCESS", replayVersion: 1, projectId: null, context: "aplicado" });
  assert.equal(setup.calls[0][0], "http://localhost:8080/api/workspaces/w1/replays/r1");
  assert.equal(setup.calls[1][1]?.method, "POST");
  assert.deepEqual(JSON.parse(String(setup.calls[1][1]?.body)), { title: "Novo", type: "RECIPE", tags: ["node"] });
  assert.deepEqual(JSON.parse(String(setup.calls[2][1]?.body)), { title: "Sem projeto", type: "RECIPE" });
  assert.equal(setup.calls[3][1]?.method, "PATCH");
  assert.deepEqual(JSON.parse(String(setup.calls[3][1]?.body)), { solution: "melhor" });
  assert.equal(setup.calls[4][0], "http://localhost:8080/api/workspaces/w1/replays/r1/usages");
  assert.equal(setup.calls[4][1]?.method, "POST");
  assert.deepEqual(JSON.parse(String(setup.calls[4][1]?.body)), { result: "SUCCESS", replayVersion: 1, projectId: null, context: "aplicado", source: "MCP" });
});

test("força source MCP mesmo diante de entrada não tipada", async () => {
  const setup = client();
  await setup.client.registerReplayUsage("w1", "r1", { result: "SUCCESS", source: "MANUAL" } as never);
  assert.equal(JSON.parse(String(setup.calls[0][1]?.body)).source, "MCP");
});

for (const [status, expected] of [[400, "Request inválido."], [401, "Token No8do ausente, inválido ou revogado."], [403, "Usuário sem permissão no workspace."], [404, "Recurso não encontrado."], [500, "Falha da API No8do."]] as const) {
  test(`trata ${status} sem vazar token`, async () => {
    const setup = client(status, { message: "no8do_pat_secret-value" });
    await assert.rejects(() => setup.client.getReplay("w1", "r1"), (error: unknown) => error instanceof No8doApiError && error.message === expected && !error.message.includes("secret-value"));
  });
}

test("compacta o resultado de busca", () => {
  assert.deepEqual(compactReplay(replay), { id: "r1", title: "Replay", type: "FIX", status: "DRAFT", version: 1, projectId: null, tags: ["java"], stack: ["spring"], usageCount: 0, successCount: 0, failureCount: 0, lastUsedAt: null, updatedAt: "2026-01-02T00:00:00Z" });
});
