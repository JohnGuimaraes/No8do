import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { createMcpServer } from "./server.js";
import { requireRemoteWorkspaceId } from "./workspace.js";

function bearer(request: IncomingMessage): string | undefined {
  const value = request.headers.authorization;
  return value && /^Bearer\s+\S+$/i.test(value) ? value.slice(value.indexOf(" ") + 1) : undefined;
}
function json(response: ServerResponse, status: number, value: object) { response.writeHead(status, { "content-type": "application/json" }); response.end(JSON.stringify(value)); }
async function body(request: IncomingMessage): Promise<unknown> { const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk)); return chunks.length ? JSON.parse(Buffer.concat(chunks).toString("utf8")) : undefined; }
export function createRemoteMcpService(apiUrl: string, workspaceId: string | undefined): Server {
  const scopedWorkspaceId = requireRemoteWorkspaceId(workspaceId);
  return createServer(async (request, response) => {
  if (request.url === "/health" && request.method === "GET") return json(response, 200, { status: "ok" });
  if (request.url !== "/mcp") return json(response, 404, { error: "Not found" });
  const token = bearer(request);
  if (!token) return json(response, 401, { error: "Unauthorized" });
  try {
    const server = createMcpServer({ apiUrl, token, defaultWorkspaceId: scopedWorkspaceId, transport: "http" });
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true });
    await server.connect(transport);
    await transport.handleRequest(request, response, request.method === "POST" ? await body(request) : undefined);
    response.on("close", () => { void transport.close(); void server.close(); });
  } catch { if (!response.headersSent) json(response, 500, { error: "Internal server error" }); }
  });
}

if (process.argv[1]?.endsWith("/http.js") || process.argv[1]?.endsWith("\\http.js")) {
  const apiUrl = process.env.NO8DO_API_URL;
  if (!apiUrl) throw new Error("NO8DO_API_URL é obrigatória.");
  createRemoteMcpService(apiUrl, process.env.NO8DO_WORKSPACE_ID).listen(Number(process.env.PORT ?? 3000), "0.0.0.0");
}
