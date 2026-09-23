import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { No8doClient } from "./no8doClient.js";
import { createMcpServer, StdioAgentSessionTransport } from "./server.js";

const apiUrl = process.env.NO8DO_API_URL;
const token = process.env.NO8DO_API_TOKEN;
if (!apiUrl || !token) throw new Error("NO8DO_API_URL e NO8DO_API_TOKEN são obrigatórias.");

const agentProtocol = await new No8doClient(apiUrl, token).getAgentProtocol();
const workspaceId = process.env.NO8DO_WORKSPACE_ID;
const client = new No8doClient(apiUrl, token);
const server = createMcpServer({ apiUrl, token, agentProtocol, defaultWorkspaceId: workspaceId });
const transport = new StdioAgentSessionTransport(new StdioServerTransport(), (clientName, clientVersion, transportSessionFingerprint) =>
  client.registerAgentSession({ clientName, clientVersion, workspaceId: workspaceId ?? null, transport: "MCP", transportSessionFingerprint }));
await server.connect(transport);
