import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { compactReplay, No8doClient, type RegisterReplayUsageMutation, type ReplayMutation, type ReplayUpdate } from "./no8doClient.js";
import { resolveWorkspaceId } from "./workspace.js";

const apiUrl = process.env.NO8DO_API_URL;
const token = process.env.NO8DO_API_TOKEN;
if (!apiUrl || !token) throw new Error("NO8DO_API_URL e NO8DO_API_TOKEN são obrigatórias.");
const client = new No8doClient(apiUrl, token);
const server = new McpServer({ name: "no8do-replays", version: "0.1.0" });
const type = z.enum(["FIX", "PATTERN", "RECIPE", "SNIPPET", "DECISION", "PROCEDURE", "CHECKLIST", "TROUBLESHOOTING", "PROMPT", "REFERENCE"]);
const status = z.enum(["DRAFT", "VALIDATED", "DEPRECATED"]);
const usageResult = z.enum(["SUCCESS", "FAILURE", "UNKNOWN"]);
const workspace = { workspaceId: z.string().uuid().optional() };
const mutation = { title: z.string().min(1), type, problem: z.string().optional(), solution: z.string().optional(), context: z.string().optional(), tags: z.array(z.string()).optional(), stack: z.array(z.string()).optional(), status: status.optional(), projectId: z.string().uuid().nullable().optional() };
const text = (value: unknown) => ({ content: [{ type: "text" as const, text: JSON.stringify(value) }] });

const resolveToolWorkspaceId = (workspaceId: string | undefined) => resolveWorkspaceId(workspaceId, process.env.NO8DO_WORKSPACE_ID);

server.registerTool("list_replays", { description: "Liste o catálogo de Replays do workspace sem aplicar busca textual.", inputSchema: workspace }, async ({ workspaceId }) => text((await client.listReplays(resolveToolWorkspaceId(workspaceId))).map(compactReplay)));
server.registerTool("search_replays", { description: "Pesquise conhecimento técnico reutilizável existente antes de resolver novamente ou criar um novo Replay.", inputSchema: { ...workspace, query: z.string().min(1) } }, async ({ workspaceId, query }) => text((await client.searchReplays(resolveToolWorkspaceId(workspaceId), query)).map(compactReplay)));
server.registerTool("get_replay", { description: "Obtenha o conteúdo completo de um Replay encontrado.", inputSchema: { ...workspace, replayId: z.string().uuid() } }, async ({ workspaceId, replayId }) => text(await client.getReplay(resolveToolWorkspaceId(workspaceId), replayId)));
server.registerTool("create_replay", { description: "Pesquise antes de criar para evitar duplicatas. Cria um Replay no workspace informado.", inputSchema: { ...workspace, ...mutation } }, async ({ workspaceId, ...body }) => text(await client.createReplay(resolveToolWorkspaceId(workspaceId), body as ReplayMutation)));
server.registerTool("update_replay", { description: "Atualize conteúdo de um Replay existente após revisar o conhecimento atual.", inputSchema: { ...workspace, replayId: z.string().uuid(), title: z.string().min(1).optional(), type: type.optional(), problem: z.string().optional(), solution: z.string().optional(), context: z.string().optional(), tags: z.array(z.string()).optional(), stack: z.array(z.string()).optional(), status: status.optional(), projectId: z.string().uuid().nullable().optional() } }, async ({ workspaceId, replayId, ...body }) => text(await client.updateReplay(resolveToolWorkspaceId(workspaceId), replayId, body as ReplayUpdate)));
server.registerTool("register_replay_usage", { description: "Registre somente quando um Replay foi realmente aplicado. Busca ou leitura sem aplicação não deve usar esta ferramenta.", inputSchema: { ...workspace, replayId: z.string().uuid(), result: usageResult, projectId: z.string().uuid().nullable().optional(), replayVersion: z.number().int().min(1).optional(), context: z.string().optional() } }, async ({ workspaceId, replayId, ...body }) => text(await client.registerReplayUsage(resolveToolWorkspaceId(workspaceId), replayId, body as RegisterReplayUsageMutation)));

await server.connect(new StdioServerTransport());
