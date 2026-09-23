import { randomUUID } from "node:crypto";
import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { InitializeRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { No8doApiError, No8doClient } from "./no8doClient.js";
import { createMcpServer } from "./server.js";
import { fingerprintTransportSession } from "./transportSessionFingerprint.js";
import { requireRemoteWorkspaceId } from "./workspace.js";

type RemoteSession = { transport: StreamableHTTPServerTransport; server: ReturnType<typeof createMcpServer>; token: string };
function bearer(request: IncomingMessage): string | undefined {
  const value = request.headers.authorization;
  return value && /^Bearer\s+\S+$/i.test(value) ? value.slice(value.indexOf(" ") + 1) : undefined;
}
function json(response: ServerResponse, status: number, value: object) { response.writeHead(status, { "content-type": "application/json" }); response.end(JSON.stringify(value)); }
async function body(request: IncomingMessage): Promise<unknown> { const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk)); return chunks.length ? JSON.parse(Buffer.concat(chunks).toString("utf8")) : undefined; }

export function createRemoteMcpService(apiUrl: string, workspaceId: string | undefined): Server {
  const scopedWorkspaceId = requireRemoteWorkspaceId(workspaceId);
  const sessions = new Map<string, RemoteSession>();
  return createServer(async (request, response) => {
    if (request.url === "/health" && request.method === "GET") return json(response, 200, { status: "ok" });
    if (request.url !== "/mcp") return json(response, 404, { error: "Not found" });
    const token = bearer(request);
    if (!token) return json(response, 401, { error: "Unauthorized" });

    let parsedBody: unknown;
    try {
      if (request.method === "POST") parsedBody = await body(request);
      const sessionHeader = request.headers["mcp-session-id"];
      const sessionId = typeof sessionHeader === "string" ? sessionHeader : undefined;
      if (sessionId) {
        const session = sessions.get(sessionId);
        if (!session) return json(response, 404, { error: "Session not found" });
        if (session.token !== token) return json(response, 401, { error: "Unauthorized" });
        await new No8doClient(apiUrl, token).getAgentProtocol();
        await session.transport.handleRequest(request, response, parsedBody);
        return;
      }

      const initialize = InitializeRequestSchema.safeParse(parsedBody);
      if (request.method !== "POST" || !initialize.success) {
        return json(response, 400, { error: "A new MCP connection must begin with initialize." });
      }
      const agentProtocol = await new No8doClient(apiUrl, token).getAgentProtocol();
      const server = createMcpServer({ apiUrl, token, agentProtocol, defaultWorkspaceId: scopedWorkspaceId, transport: "http" });
      const transport = new StreamableHTTPServerTransport({
        sessionIdGenerator: randomUUID,
        enableJsonResponse: true,
        onsessioninitialized: async (createdSessionId) => {
          const clientInfo = initialize.data.params.clientInfo;
          const client = new No8doClient(apiUrl, token);
          await client.registerAgentSession({
            clientName: clientInfo.name,
            clientVersion: clientInfo.version,
            workspaceId: scopedWorkspaceId ?? null,
            transport: "MCP",
            transportSessionFingerprint: fingerprintTransportSession(createdSessionId)
          });
          sessions.set(createdSessionId, { transport, server, token });
        },
        onsessionclosed: (closedSessionId) => { sessions.delete(closedSessionId); }
      });
      await server.connect(transport);
      await transport.handleRequest(request, response, parsedBody);
    } catch (error) {
      if (response.headersSent) return;
      if (error instanceof No8doApiError && (error.status === 401 || error.status === 403 || error.status === 409)) {
        return json(response, error.status, { error: error.message });
      }
      json(response, 500, { error: "Internal server error" });
    }
  });
}

if (process.argv[1]?.endsWith("/http.js") || process.argv[1]?.endsWith("\\http.js")) {
  const apiUrl = process.env.NO8DO_API_URL;
  if (!apiUrl) throw new Error("NO8DO_API_URL é obrigatória.");
  createRemoteMcpService(apiUrl, process.env.NO8DO_WORKSPACE_ID).listen(Number(process.env.PORT ?? 3000), "0.0.0.0");
}
