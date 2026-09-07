import assert from "node:assert/strict";
import { createServer } from "node:http";
import test from "node:test";
import { once } from "node:events";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { createRemoteMcpService } from "./http.js";

const workspaceId = "11111111-1111-4111-8111-111111111111";
const otherWorkspaceId = "22222222-2222-4222-8222-222222222222";
async function listen(server: ReturnType<typeof createServer>) { server.listen(0, "127.0.0.1"); await once(server, "listening"); return `http://127.0.0.1:${(server.address() as { port: number }).port}`; }
async function close(server: ReturnType<typeof createServer>) { server.close(); await once(server, "close"); }

test("remote MCP falha ao iniciar sem workspace configurado válido", () => {
  assert.throws(() => createRemoteMcpService("http://127.0.0.1:9", undefined), /NO8DO_WORKSPACE_ID é obrigatória/);
  assert.throws(() => createRemoteMcpService("http://127.0.0.1:9", "invalido"), /UUID válido/);
});

test("remote MCP protege health e autenticação sem vazar bearer", async () => {
  const service = createRemoteMcpService("http://127.0.0.1:9", workspaceId); const url = await listen(service);
  try {
    const health = await fetch(`${url}/health`); assert.equal(health.status, 200); assert.deepEqual(await health.json(), { status: "ok" });
    for (const auth of [undefined, "Basic PAT_A", "Bearer ", "Bearer    "]) {
      const response = await fetch(`${url}/mcp`, { method: "POST", headers: auth ? { Authorization: auth } : {} });
      assert.equal(response.status, 401); assert.equal((await response.text()).includes("PAT_A"), false);
    }
  } finally { await close(service); }
});

test("Streamable HTTP limita cada instância ao workspace configurado e isola PATs", async () => {
  const received: Array<{ authorization: string; url: string }> = [];
  const api = createServer((request, response) => { received.push({ authorization: request.headers.authorization ?? "", url: request.url ?? "" }); response.setHeader("content-type", "application/json"); response.end(JSON.stringify([])); });
  const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId); const url = await listen(service);
  const call = async (token: string, args: Record<string, unknown>) => {
    const client = new Client({ name: "test", version: "1" });
    await client.connect(new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { requestInit: { headers: { Authorization: `Bearer ${token}` } } }));
    const tools = await client.listTools(); assert.equal(tools.tools.length, 13);
    const result = await client.callTool({ name: "list_replays", arguments: args }); await client.close(); return result;
  };
  try {
    await Promise.all([call("PAT_A", {}), call("PAT_B", { workspaceId })]);
    assert.deepEqual(received.map(({ authorization }) => authorization).sort(), ["Bearer PAT_A", "Bearer PAT_B"]);
    assert.deepEqual(received.map(({ url: requestUrl }) => requestUrl), [`/api/workspaces/${workspaceId}/replays`, `/api/workspaces/${workspaceId}/replays`]);
    const beforeRejectedCall = received.length;
    const result = await call("PAT_A", { workspaceId: otherWorkspaceId });
    assert.equal(result.isError, true); assert.match(JSON.stringify(result), /Workspace fora do escopo/);
    assert.equal(received.length, beforeRejectedCall);
  } finally { await close(service); await close(api); }
});

test("Streamable HTTP propaga 401 e 403 da API sem vazar PAT", async () => {
  for (const status of [401, 403]) {
    const api = createServer((_request, response) => { response.writeHead(status, { "content-type": "application/json" }); response.end(JSON.stringify({ token: "PAT_SECRET" })); });
    const apiUrl = await listen(api); const service = createRemoteMcpService(apiUrl, workspaceId); const url = await listen(service);
    try {
      const client = new Client({ name: "test", version: "1" });
      await client.connect(new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { requestInit: { headers: { Authorization: "Bearer PAT_SECRET" } } }));
      const result = await client.callTool({ name: "list_replays", arguments: { workspaceId } });
      assert.equal(result.isError, true); assert.doesNotMatch(JSON.stringify(result), /PAT_SECRET/); await client.close();
    } finally { await close(service); await close(api); }
  }
});
