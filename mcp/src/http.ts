import { randomUUID } from "node:crypto";
import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { InitializeRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { AgentSessionHeartbeat } from "./agentSessionHeartbeat.js";
import { AgentSessionHeader, No8doApiError, No8doClient } from "./no8doClient.js";
import { createMcpServer, refreshIntegrationTools } from "./server.js";
import { fingerprintTransportSession } from "./transportSessionFingerprint.js";
import { requireRemoteWorkspaceId } from "./workspace.js";

type RemoteSession = { transport: StreamableHTTPServerTransport; server: ReturnType<typeof createMcpServer>; token: string; heartbeat: AgentSessionHeartbeat; client: No8doClient };
export type HeartbeatFactory = (client: No8doClient, agentSessionId: string) => AgentSessionHeartbeat;
function bearer(request: IncomingMessage): string | undefined {
  const value = request.headers.authorization;
  return value?.match(/^Bearer ([^\s]+)$/i)?.[1];
}
function json(response: ServerResponse, status: number, value: object) { response.writeHead(status, { "content-type": "application/json" }); response.end(JSON.stringify(value)); }
async function body(request: IncomingMessage): Promise<unknown> { const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk)); return chunks.length ? JSON.parse(Buffer.concat(chunks).toString("utf8")) : undefined; }

export function createRemoteMcpService(apiUrl: string, workspaceId: string | undefined,
  heartbeatFactory: HeartbeatFactory = (client, agentSessionId) => new AgentSessionHeartbeat(client, agentSessionId)): Server {
  const sessions = new Map<string, RemoteSession>();
  const heartbeats = new Set<AgentSessionHeartbeat>();
  const closeSession = async (sessionId: string) => {
    const session = sessions.get(sessionId);
    if (!session) return;
    try { await session.heartbeat.close(); }
    finally {
      sessions.delete(sessionId);
      heartbeats.delete(session.heartbeat);
    }
  };
  const service = createServer(async (request, response) => {
    if (request.url === "/health" && request.method === "GET") return json(response, 200, { status: "ok" });
    if (request.url !== "/mcp") return json(response, 404, { error: "Not found" });
    const token = bearer(request);
    if (!token) return json(response, 401, { error: "Unauthorized" });
    const integration = token.startsWith("no8do_int_");
    if (integration && request.headers["x-no8do-agent-credential"] !== undefined) {
      return json(response, 400, { error: "Mixed credentials are not accepted." });
    }

    let parsedBody: unknown;
    try {
      if (request.method === "POST") parsedBody = await body(request);
      const sessionHeader = request.headers["mcp-session-id"];
      const sessionId = typeof sessionHeader === "string" ? sessionHeader : undefined;
      if (sessionId) {
        const session = sessions.get(sessionId);
        if (!session) return json(response, 404, { error: "Session not found" });
        if (session.token !== token) return json(response, 401, { error: "Unauthorized" });
        if (integration) {
          const context = await session.client.requireIntegrationSession();
          refreshIntegrationTools(session.server, context.effectiveCapabilities);
        } else {
          await new No8doClient(apiUrl, token).getAgentProtocol();
        }
        await session.transport.handleRequest(request, response, parsedBody);
        return;
      }

      const initialize = InitializeRequestSchema.safeParse(parsedBody);
      if (request.method !== "POST" || !initialize.success) {
        return json(response, 400, { error: "A new MCP connection must begin with initialize." });
      }
      if (integration) {
        const raw = parsedBody as { params?: Record<string, unknown> };
        if (["workspaceId", "agentId", "userId", "authorizationId", "sessionId", "workspaceHint"]
            .some(field => Object.hasOwn(raw.params ?? {}, field))) {
          return json(response, 400, { error: "Client-selected authority is not accepted." });
        }
      }
      const credentialHeader = request.headers["x-no8do-agent-credential"];
      let agentCredential = typeof credentialHeader === "string" ? credentialHeader : undefined;
      const agentBoundRegistration = agentCredential !== undefined;
      let scopedWorkspaceId: string | undefined;
      if (!agentBoundRegistration && !integration) {
        try { scopedWorkspaceId = requireRemoteWorkspaceId(workspaceId); }
        catch { return json(response, 400, { error: "Legacy remote MCP requires a valid NO8DO_WORKSPACE_ID." }); }
      }
      const agentProtocol = await new No8doClient(apiUrl, token).getAgentProtocol();
      const agentSessionHeader = new AgentSessionHeader();
      const server = createMcpServer({ apiUrl, token, agentProtocol, defaultWorkspaceId: scopedWorkspaceId, transport: "http", agentSessionHeader, integration });
      const transport = new StreamableHTTPServerTransport({
        sessionIdGenerator: randomUUID,
        enableJsonResponse: true,
        onsessioninitialized: async (createdSessionId) => {
          const clientInfo = initialize.data.params.clientInfo;
          const client = new No8doClient(apiUrl, token, fetch, agentSessionHeader);
          const credential = agentCredential;
          agentCredential = undefined;
          const registeredSession = await client.registerAgentSession({
            clientName: clientInfo.name,
            clientVersion: clientInfo.version,
            workspaceId: agentBoundRegistration || integration ? null : scopedWorkspaceId ?? null,
            transport: "MCP",
            transportSessionFingerprint: fingerprintTransportSession(createdSessionId)
          }, credential);
          if ((agentBoundRegistration || integration) && !registeredSession.workspaceId) {
            throw new Error("Agent-bound AgentSession has no authorized Workspace.");
          }
          if (!agentBoundRegistration && !integration && registeredSession.workspaceId !== scopedWorkspaceId) {
            throw new Error("Legacy AgentSession Workspace does not match its configured scope.");
          }
          agentSessionHeader.set(registeredSession.sessionId, registeredSession.workspaceId);
          if (integration) {
            const context = await client.requireIntegrationSession();
            refreshIntegrationTools(server, context.effectiveCapabilities);
          }
          const heartbeat = heartbeatFactory(new No8doClient(apiUrl, token), registeredSession.sessionId);
          agentSessionHeader.setRevocationHandler(() => heartbeat.markRevoked());
          sessions.set(createdSessionId, { transport, server, token, heartbeat, client });
          heartbeats.add(heartbeat);
          heartbeat.start();
        },
        onsessionclosed: async (closedSessionId) => {
          await closeSession(closedSessionId);
        }
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
  service.on("close", () => {
    for (const sessionId of [...sessions.keys()]) void closeSession(sessionId);
  });
  return service;
}

if (process.argv[1]?.endsWith("/http.js") || process.argv[1]?.endsWith("\\http.js")) {
  const apiUrl = process.env.NO8DO_API_URL;
  if (!apiUrl) throw new Error("NO8DO_API_URL é obrigatória.");
  createRemoteMcpService(apiUrl, process.env.NO8DO_WORKSPACE_ID).listen(Number(process.env.PORT ?? 3000), "0.0.0.0");
}
