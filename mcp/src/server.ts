import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { compactReplay, No8doClient, type FindReusableKnowledgeInput, type RegisterReplayUsageMutation, type ReplayMutation, type ReplayRelationType, type ReplayUpdate } from "./no8doClient.js";
import { resolveWorkspaceId } from "./workspace.js";

export type McpServerContext = { apiUrl: string; token: string; defaultWorkspaceId?: string };
const type = z.enum(["FIX", "PATTERN", "RECIPE", "SNIPPET", "DECISION", "PROCEDURE", "CHECKLIST", "TROUBLESHOOTING", "PROMPT", "REFERENCE"]);
const status = z.enum(["DRAFT", "VALIDATED", "DEPRECATED"]);
const usageResult = z.enum(["SUCCESS", "FAILURE", "UNKNOWN"]);
const relationType = z.enum(["RELATED_TO", "SUPERSEDES", "RESOLVES", "DEPENDS_ON"]);
const workspace = { workspaceId: z.string().uuid().optional() };
const mutation = { title: z.string().min(1), type, problem: z.string().optional(), solution: z.string().optional(), context: z.string().optional(), tags: z.array(z.string()).optional(), stack: z.array(z.string()).optional(), status: status.optional(), projectId: z.string().uuid().nullable().optional() };
const text = (value: unknown) => ({ content: [{ type: "text" as const, text: JSON.stringify(value) }] });

export function createMcpServer(context: McpServerContext) {
  const client = new No8doClient(context.apiUrl, context.token);
  const server = new McpServer({ name: "no8do-replays", version: "0.1.0" });
  const resolve = (workspaceId: string | undefined) => resolveWorkspaceId(workspaceId, context.defaultWorkspaceId);
  server.registerTool("list_replays", { description: "Liste o catálogo de Replays do workspace sem aplicar busca textual.", inputSchema: workspace }, async ({ workspaceId }) => text((await client.listReplays(resolve(workspaceId))).map(compactReplay)));
  server.registerTool("search_replays", { description: "Pesquise conhecimento técnico reutilizável existente antes de resolver novamente ou criar um novo Replay.", inputSchema: { ...workspace, query: z.string().min(1) } }, async ({ workspaceId, query }) => text((await client.searchReplays(resolve(workspaceId), query)).map(compactReplay)));
  server.registerTool("find_reusable_knowledge", { description: "Sugira Replays reutilizáveis por relevância determinística. Revise candidatos antes de atualizar ou criar conteúdo.", inputSchema: { ...workspace, query: z.string().optional(), problem: z.string().optional(), stack: z.array(z.string()).optional(), tags: z.array(z.string()).optional(), type: type.optional() } }, async ({ workspaceId, ...input }) => text({ suggestions: await client.findReusableKnowledge(resolve(workspaceId), input as FindReusableKnowledgeInput) }));
  server.registerTool("get_replay_quality", { description: "Obtenha o score derivado de qualidade e seus sinais.", inputSchema: { ...workspace, replayId: z.string().uuid() } }, async ({ workspaceId, replayId }) => text(await client.getReplayQuality(resolve(workspaceId), replayId)));
  server.registerTool("get_replay", { description: "Obtenha o conteúdo completo de um Replay encontrado.", inputSchema: { ...workspace, replayId: z.string().uuid() } }, async ({ workspaceId, replayId }) => text(await client.getReplay(resolve(workspaceId), replayId)));
  server.registerTool("list_replay_versions", { description: "Liste snapshots imutáveis de conteúdo.", inputSchema: { ...workspace, replayId: z.string().uuid() } }, async ({ workspaceId, replayId }) => text(await client.listReplayVersions(resolve(workspaceId), replayId)));
  server.registerTool("get_replay_version", { description: "Obtenha um snapshot histórico.", inputSchema: { ...workspace, replayId: z.string().uuid(), version: z.number().int().min(1) } }, async ({ workspaceId, replayId, version }) => text(await client.getReplayVersion(resolve(workspaceId), replayId, version)));
  server.registerTool("list_replay_relations", { description: "Liste as relações do Replay.", inputSchema: { ...workspace, replayId: z.string().uuid() } }, async ({ workspaceId, replayId }) => text(await client.listReplayRelations(resolve(workspaceId), replayId)));
  server.registerTool("create_replay_relation", { description: "Crie uma relação entre Replays.", inputSchema: { ...workspace, replayId: z.string().uuid(), targetReplayId: z.string().uuid(), type: relationType } }, async ({ workspaceId, replayId, targetReplayId, type }) => text(await client.createReplayRelation(resolve(workspaceId), replayId, targetReplayId, type as ReplayRelationType)));
  server.registerTool("delete_replay_relation", { description: "Remova uma relação existente.", inputSchema: { ...workspace, replayId: z.string().uuid(), relationId: z.string().uuid() } }, async ({ workspaceId, replayId, relationId }) => { await client.deleteReplayRelation(resolve(workspaceId), replayId, relationId); return text({ deleted: true }); });
  server.registerTool("create_replay", { description: "Antes de criar, prefira find_reusable_knowledge.", inputSchema: { ...workspace, ...mutation } }, async ({ workspaceId, ...body }) => text(await client.createReplay(resolve(workspaceId), body as ReplayMutation)));
  server.registerTool("update_replay", { description: "Atualize conteúdo de um Replay existente.", inputSchema: { ...workspace, replayId: z.string().uuid(), ...mutation }, }, async ({ workspaceId, replayId, ...body }) => text(await client.updateReplay(resolve(workspaceId), replayId, body as ReplayUpdate)));
  server.registerTool("register_replay_usage", { description: "Registre somente quando um Replay foi realmente aplicado.", inputSchema: { ...workspace, replayId: z.string().uuid(), result: usageResult, projectId: z.string().uuid().nullable().optional(), replayVersion: z.number().int().min(1).optional(), context: z.string().optional() } }, async ({ workspaceId, replayId, ...body }) => text(await client.registerReplayUsage(resolve(workspaceId), replayId, body as RegisterReplayUsageMutation)));
  return server;
}
