import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { AgentSessionHeartbeat } from "./agentSessionHeartbeat.js";
import { createStdioSessionCloseHandler } from "./stdioSessionLifecycle.js";
import { AgentSessionHeader, No8doClient } from "./no8doClient.js";
import { createMcpServer, StdioAgentSessionTransport } from "./server.js";
import { takeStdioAgentCredential } from "./stdioAgentCredential.js";

const apiUrl = process.env.NO8DO_API_URL;
const token = process.env.NO8DO_API_TOKEN;
if (!apiUrl || !token) throw new Error("NO8DO_API_URL e NO8DO_API_TOKEN são obrigatórias.");

let agentCredential = takeStdioAgentCredential(process.env);
const agentBoundRegistration = agentCredential !== undefined;
const agentProtocol = await new No8doClient(apiUrl, token).getAgentProtocol();
const workspaceId = process.env.NO8DO_WORKSPACE_ID;
const agentSessionHeader = new AgentSessionHeader();
const client = new No8doClient(apiUrl, token);
const server = createMcpServer({ apiUrl, token, agentProtocol, defaultWorkspaceId: workspaceId, agentSessionHeader });
let heartbeat: AgentSessionHeartbeat | undefined;
const onSessionClosed = createStdioSessionCloseHandler(
  () => heartbeat,
  () => { heartbeat = undefined; }
);
const transport = new StdioAgentSessionTransport(new StdioServerTransport(),
  (clientName, clientVersion, transportSessionFingerprint) => {
    const credential = agentCredential;
    agentCredential = undefined;
    return client.registerAgentSession({
      clientName, clientVersion, workspaceId: credential !== undefined ? null : workspaceId ?? null,
      transport: "MCP", transportSessionFingerprint
    }, credential);
  },
  (session) => {
    if (agentBoundRegistration && !session.workspaceId) throw new Error("Agent-bound AgentSession has no authorized Workspace.");
    if (!agentBoundRegistration && workspaceId && session.workspaceId !== workspaceId) {
      throw new Error("Legacy AgentSession Workspace does not match its configured scope.");
    }
    agentSessionHeader.set(session.sessionId, session.workspaceId);
    heartbeat = new AgentSessionHeartbeat(client, session.sessionId);
    agentSessionHeader.setRevocationHandler(() => heartbeat?.markRevoked());
    heartbeat.start();
  },
  onSessionClosed);
await server.connect(transport);
