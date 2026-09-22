import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { No8doClient } from "./no8doClient.js";
import { createMcpServer } from "./server.js";

const apiUrl = process.env.NO8DO_API_URL;
const token = process.env.NO8DO_API_TOKEN;
if (!apiUrl || !token) throw new Error("NO8DO_API_URL e NO8DO_API_TOKEN são obrigatórias.");

const agentProtocol = await new No8doClient(apiUrl, token).getAgentProtocol();
await createMcpServer({ apiUrl, token, agentProtocol, defaultWorkspaceId: process.env.NO8DO_WORKSPACE_ID })
  .connect(new StdioServerTransport());
