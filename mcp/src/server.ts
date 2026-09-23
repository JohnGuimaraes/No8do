import { randomUUID } from "node:crypto";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { InitializeRequestSchema, type JSONRPCMessage } from "@modelcontextprotocol/sdk/types.js";
import type { Transport, TransportSendOptions } from "@modelcontextprotocol/sdk/shared/transport.js";
import { z } from "zod";
import { renderAgentProtocolBootstrap } from "./agentProtocolBootstrap.js";
import { compactReplay, No8doClient, type AgentProtocol, type AgentSessionHeader, type FindReusableKnowledgeInput, type RegisterReplayUsageMutation, type ReplayMutation, type ReplayRelationType, type ReplayUpdate } from "./no8doClient.js";
import { resolveWorkspaceId, type WorkspaceTransport } from "./workspace.js";
import { fingerprintTransportSession } from "./transportSessionFingerprint.js";

export type McpServerContext = { apiUrl: string; token: string; agentProtocol: AgentProtocol; defaultWorkspaceId?: string; transport?: WorkspaceTransport; agentSessionHeader?: AgentSessionHeader };

/** Adds one registration barrier to stdio initialize; stdio has no SDK-issued session ID. */
export class StdioAgentSessionTransport implements Transport {
  readonly sessionId = randomUUID();
  private messageHandler: Transport["onmessage"] = undefined;
  private closeHandler?: () => void;
  private errorHandler?: (error: Error) => void;
  private initializeRequestId?: string | number;
  private clientInfo?: { name: string; version: string };
  private registrationAttempted = false;

  constructor(
    private readonly delegate: Transport,
    private readonly register: (clientName: string, clientVersion: string, fingerprint: string) => Promise<unknown>
  ) {
    delegate.onmessage = (message, extra) => {
      const initialize = InitializeRequestSchema.safeParse(message);
      if (initialize.success && "id" in message) {
        this.initializeRequestId = message.id;
        this.clientInfo = initialize.data.params.clientInfo;
      }
      this.messageHandler?.(message, extra);
    };
    delegate.onclose = () => this.closeHandler?.();
    delegate.onerror = (error) => this.errorHandler?.(error);
  }

  get onmessage(): Transport["onmessage"] { return this.messageHandler; }
  set onmessage(handler: Transport["onmessage"]) { this.messageHandler = handler; }
  get onclose(): (() => void) | undefined { return this.closeHandler; }
  set onclose(handler: (() => void) | undefined) { this.closeHandler = handler; }
  get onerror(): ((error: Error) => void) | undefined { return this.errorHandler; }
  set onerror(handler: ((error: Error) => void) | undefined) { this.errorHandler = handler; }

  start(): Promise<void> { return this.delegate.start(); }
  close(): Promise<void> { return this.delegate.close(); }

  async send(message: JSONRPCMessage, options?: TransportSendOptions): Promise<void> {
    if (this.initializeRequestId !== undefined && !this.registrationAttempted && "id" in message
        && "result" in message && message.id === this.initializeRequestId) {
      this.registrationAttempted = true;
      try {
        if (!this.clientInfo) throw new Error("MCP initialize clientInfo is unavailable.");
        await this.register(this.clientInfo.name, this.clientInfo.version, fingerprintTransportSession(this.sessionId));
      } catch {
        await this.delegate.send({
          jsonrpc: "2.0",
          id: this.initializeRequestId,
          error: { code: -32000, message: "Falha ao registrar a sessão MCP no No8do." }
        });
        await this.delegate.close();
        return;
      }
    }
    await this.delegate.send(message, options);
  }
}
const type = z.enum(["FIX", "PATTERN", "RECIPE", "SNIPPET", "DECISION", "PROCEDURE", "CHECKLIST", "TROUBLESHOOTING", "PROMPT", "REFERENCE"]);
const status = z.enum(["DRAFT", "VALIDATED", "DEPRECATED"]);
const usageResult = z.enum(["SUCCESS", "FAILURE", "UNKNOWN"]);
const relationType = z.enum(["RELATED_TO", "SUPERSEDES", "RESOLVES", "DEPENDS_ON"]);
const workspace = { workspaceId: z.string().uuid().optional() };
const mutation = { title: z.string().min(1), type, problem: z.string().optional(), solution: z.string().optional(), context: z.string().optional(), tags: z.array(z.string()).optional(), stack: z.array(z.string()).optional(), status: status.optional(), projectId: z.string().uuid().nullable().optional() };
const text = (value: unknown) => ({ content: [{ type: "text" as const, text: JSON.stringify(value) }] });

export function createMcpServer(context: McpServerContext) {
  const client = new No8doClient(context.apiUrl, context.token, fetch, context.agentSessionHeader);
  const server = new McpServer({ name: "no8do-replays", version: "0.1.0" }, { instructions: renderAgentProtocolBootstrap(context.agentProtocol) });
  const resolve = (workspaceId: string | undefined) => resolveWorkspaceId(workspaceId, context.defaultWorkspaceId, context.transport);
  const protocolOutput = z.object({
    protocolName: z.string(),
    protocolVersion: z.number().int().positive(),
    systemName: z.string(),
    purpose: z.string(),
    replayGuidance: z.object({
      summary: z.string(),
      searchBeforeNonTrivialWork: z.boolean(),
      preferExistingKnowledge: z.boolean(),
      searchBeforeCreate: z.boolean(),
      recordUsageOnlyWhenMateriallyUsed: z.boolean(),
      validatedRequiresEvidence: z.boolean(),
      avoidTrivialKnowledge: z.boolean(),
      avoidDuplicateKnowledge: z.boolean(),
      neverStoreSecrets: z.boolean(),
      neverStoreCredentials: z.boolean(),
      avoidDiscardedAttempts: z.boolean()
    }),
    capabilities: z.object({ capabilities: z.array(z.object({ id: z.string(), description: z.string(), readOnly: z.boolean() })) }),
    policies: z.object({ policies: z.array(z.object({ id: z.string(), description: z.string(), enforcement: z.enum(["ADVISORY", "ENFORCED"]) })) })
  });
  server.registerTool("get_agent_protocol", {
    description: "Obtenha o Agent Protocol canônico do No8do, incluindo orientação, capabilities e policies atuais.",
    outputSchema: protocolOutput
  }, async () => ({
    content: [{ type: "text" as const, text: `No8do Agent Protocol ${context.agentProtocol.protocolName} v${context.agentProtocol.protocolVersion}` }],
    structuredContent: context.agentProtocol as unknown as Record<string, unknown>
  }));
  const agentContextOutput = z.object({
    sessionId: z.string().uuid(),
    clientName: z.string(),
    clientVersion: z.string(),
    workspaceId: z.string().uuid().nullable(),
    transport: z.literal("MCP"),
    protocolName: z.string(),
    protocolVersion: z.number().int().positive(),
    runtimeMode: z.enum(["OFF", "READ_ONLY", "RETRIEVAL", "ASSISTED", "FULL"]),
    effectiveCapabilities: z.array(z.object({ id: z.string(), description: z.string(), readOnly: z.boolean() })),
    policies: z.array(z.object({ id: z.string(), description: z.string(), enforcement: z.enum(["ADVISORY", "ENFORCED"]) })),
    registeredAt: z.string()
  });
  server.registerTool("get_agent_context", {
    description: "Obtenha o contexto atual desta sessão Agent, incluindo runtime mode, effective capabilities e policies.",
    inputSchema: {},
    outputSchema: agentContextOutput
  }, async () => {
    const agentContext = await client.getAgentContext();
    return {
      content: [{ type: "text" as const, text: `Agent Session ${agentContext.sessionId} (${agentContext.runtimeMode})` }],
      structuredContent: agentContext as unknown as Record<string, unknown>
    };
  });
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
