import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { createMcpServer } from "./server.js";

const apiUrl = process.env.NO8DO_API_URL;
const token = process.env.NO8DO_API_TOKEN;
if (!apiUrl || !token) throw new Error("NO8DO_API_URL e NO8DO_API_TOKEN são obrigatórias.");

await createMcpServer({ apiUrl, token, defaultWorkspaceId: process.env.NO8DO_WORKSPACE_ID })
  .connect(new StdioServerTransport());
