import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { compactReplay, No8doClient, type FindReusableKnowledgeInput, type RegisterReplayUsageMutation, type ReplayMutation, type ReplayRelationType, type ReplayUpdate } from "./no8doClient.js";
import { resolveWorkspaceId } from "./workspace.js";

const apiUrl = process.env.NO8DO_API_URL;
const token = process.env.NO8DO_API_TOKEN;
if (!apiUrl || !token) throw new Error("NO8DO_API_URL e NO8DO_API_TOKEN são obrigatórias.");
const client = new No8doClient(apiUrl, token);
const server = new McpServer({ name: "no8do-replays", version: "0.1.0" });
const type = z.enum(["FIX", "PATTERN", "RECIPE", "SNIPPET", "DECISION", "PROCEDURE", "CHECKLIST", "TROUBLESHOOTING", "PROMPT", "REFERENCE"]);
const status = z.enum(["DRAFT", "VALIDATED", "DEPRECATED"]);
const usageResult = z.enum(["SUCCESS", "FAILURE", "UNKNOWN"]);
const relationType = z.enum(["RELATED_TO", "SUPERSEDES", "RESOLVES", "DEPENDS_ON"]);
const workspace = { workspaceId: z.string().uuid().optional() };
const mutation = { title: z.string().min(1), type, problem: z.string().optional(), solution: z.string().optional(), context: z.string().optional(), tags: z.array(z.string()).optional(), stack: z.array(z.string()).optional(), status: status.optional(), projectId: z.string().uuid().nullable().optional() };
const text = (value: unknown) => ({ content: [{ type: "text" as const, text: JSON.stringify(value) }] });

const resolveToolWorkspaceId = (workspaceId: string | undefined) => resolveWorkspaceId(workspaceId, process.env.NO8DO_WORKSPACE_ID);

server.registerTool("list_replays", { description: "Liste o catálogo de Replays do workspace sem aplicar busca textual.", inputSchema: workspace }, async ({ workspaceId }) => text((await client.listReplays(resolveToolWorkspaceId(workspaceId))).map(compactReplay)));
server.registerTool("search_replays", { description: "Pesquise conhecimento técnico reutilizável existente antes de resolver novamente ou criar um novo Replay.", inputSchema: { ...workspace, query: z.string().min(1) } }, async ({ workspaceId, query }) => text((await client.searchReplays(resolveToolWorkspaceId(workspaceId), query)).map(compactReplay)));
server.registerTool("find_reusable_knowledge", { description: "Sugira Replays reutilizáveis por relevância determinística. Revise os candidatos antes de atualizar ou criar conteúdo.", inputSchema: { ...workspace, query: z.string().optional(), problem: z.string().optional(), stack: z.array(z.string()).optional(), tags: z.array(z.string()).optional(), type: type.optional() } }, async ({ workspaceId, ...input }) => text({ suggestions: await client.findReusableKnowledge(resolveToolWorkspaceId(workspaceId), input as FindReusableKnowledgeInput) }));
server.registerTool("get_replay", { description: "Obtenha o conteúdo completo de um Replay encontrado.", inputSchema: { ...workspace, replayId: z.string().uuid() } }, async ({ workspaceId, replayId }) => text(await client.getReplay(resolveToolWorkspaceId(workspaceId), replayId)));
server.registerTool("list_replay_relations", { description: "Liste as relações do Replay, incluindo direção e os dados resumidos do Replay relacionado.", inputSchema: { ...workspace, replayId: z.string().uuid() } }, async ({ workspaceId, replayId }) => text(await client.listReplayRelations(resolveToolWorkspaceId(workspaceId), replayId)));
server.registerTool("create_replay_relation", { description: "Crie uma relação direcionada ou RELATED_TO entre dois Replays do mesmo workspace.", inputSchema: { ...workspace, replayId: z.string().uuid(), targetReplayId: z.string().uuid(), type: relationType } }, async ({ workspaceId, replayId, targetReplayId, type }) => text(await client.createReplayRelation(resolveToolWorkspaceId(workspaceId), replayId, targetReplayId, type as ReplayRelationType)));
server.registerTool("delete_replay_relation", { description: "Remova uma relação existente de um Replay.", inputSchema: { ...workspace, replayId: z.string().uuid(), relationId: z.string().uuid() } }, async ({ workspaceId, replayId, relationId }) => { await client.deleteReplayRelation(resolveToolWorkspaceId(workspaceId), replayId, relationId); return text({ deleted: true }); });
server.registerTool("create_replay", { description: "Antes de criar, prefira find_reusable_knowledge, revise candidatos e atualize apenas quando houver melhoria real. Crie quando não houver equivalente apropriado.", inputSchema: { ...workspace, ...mutation } }, async ({ workspaceId, ...body }) => text(await client.createReplay(resolveToolWorkspaceId(workspaceId), body as ReplayMutation)));
server.registerTool("update_replay", { description: "Atualize conteúdo de um Replay existente após revisar o conhecimento atual.", inputSchema: { ...workspace, replayId: z.string().uuid(), title: z.string().min(1).optional(), type: type.optional(), problem: z.string().optional(), solution: z.string().optional(), context: z.string().optional(), tags: z.array(z.string()).optional(), stack: z.array(z.string()).optional(), status: status.optional(), projectId: z.string().uuid().nullable().optional() } }, async ({ workspaceId, replayId, ...body }) => text(await client.updateReplay(resolveToolWorkspaceId(workspaceId), replayId, body as ReplayUpdate)));
server.registerTool("register_replay_usage", { description: "Registre somente quando um Replay foi realmente aplicado. Busca ou leitura sem aplicação não deve usar esta ferramenta.", inputSchema: { ...workspace, replayId: z.string().uuid(), result: usageResult, projectId: z.string().uuid().nullable().optional(), replayVersion: z.number().int().min(1).optional(), context: z.string().optional() } }, async ({ workspaceId, replayId, ...body }) => text(await client.registerReplayUsage(resolveToolWorkspaceId(workspaceId), replayId, body as RegisterReplayUsageMutation)));

await server.connect(new StdioServerTransport());
