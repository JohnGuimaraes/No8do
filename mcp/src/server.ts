import { randomUUID } from "node:crypto";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { InitializeRequestSchema, RequestSchema, ErrorCode, McpError, type JSONRPCMessage } from "@modelcontextprotocol/sdk/types.js";
import type { Transport, TransportSendOptions } from "@modelcontextprotocol/sdk/shared/transport.js";
import { z } from "zod";
import { z as z4 } from "zod/v4";
import { renderAgentProtocolBootstrap } from "./agentProtocolBootstrap.js";
import { compactReplay, No8doApiError, No8doClient, type AgentProtocol, type AgentSession, type AgentSessionHeader, type FindReusableKnowledgeInput, type RegisterReplayUsageMutation, type ReplayMutation, type ReplayRelationType, type ReplayUpdate, type ReplayValidationEvidence } from "./no8doClient.js";
import { resolveWorkspaceId, type WorkspaceTransport } from "./workspace.js";
import { fingerprintTransportSession } from "./transportSessionFingerprint.js";

export type McpServerContext = { apiUrl: string; token: string; agentProtocol: AgentProtocol; defaultWorkspaceId?: string; transport?: WorkspaceTransport; agentSessionHeader?: AgentSessionHeader; integration?: boolean };

type ReadTool = { required: string[]; tool: ReturnType<McpServer["registerTool"]> };
const integrationTools = new WeakMap<McpServer, ReadTool[]>();
export function refreshIntegrationTools(server: McpServer, effective: { id: string }[]): void {
  const allowed = new Set(effective.map(capability => capability.id));
  for (const { required, tool } of integrationTools.get(server) ?? []) {
    const enabled = required.every(capability => allowed.has(capability));
    if (tool.enabled !== enabled) { if (enabled) tool.enable(); else tool.disable(); }
  }
}

/** Adds one registration barrier to stdio initialize; stdio has no SDK-issued session ID. */
export class StdioAgentSessionTransport implements Transport {
  readonly sessionId = randomUUID();
  private messageHandler: Transport["onmessage"] = undefined;
  private closeHandler?: () => void;
  private errorHandler?: (error: Error) => void;
  private initializeRequestId?: string | number;
  private clientInfo?: { name: string; version: string };
  private registrationAttempted = false;
  private closePromise?: Promise<void>;
  private delegateClosePromise?: Promise<void>;

  constructor(
    private readonly delegate: Transport,
    private readonly register: (clientName: string, clientVersion: string, fingerprint: string) => Promise<AgentSession>,
    private readonly onRegistered: (session: AgentSession) => void = () => {},
    private readonly onClosed: () => void | Promise<void> = () => {}
  ) {
    delegate.onmessage = (message, extra) => {
      const initialize = InitializeRequestSchema.safeParse(message);
      if (initialize.success && "id" in message) {
        this.initializeRequestId = message.id;
        this.clientInfo = initialize.data.params.clientInfo;
      }
      this.messageHandler?.(message, extra);
    };
    delegate.onclose = () => { void this.handleClose(); };
    delegate.onerror = (error) => this.errorHandler?.(error);
  }

  get onmessage(): Transport["onmessage"] { return this.messageHandler; }
  set onmessage(handler: Transport["onmessage"]) { this.messageHandler = handler; }
  get onclose(): (() => void) | undefined { return this.closeHandler; }
  set onclose(handler: (() => void) | undefined) { this.closeHandler = handler; }
  get onerror(): ((error: Error) => void) | undefined { return this.errorHandler; }
  set onerror(handler: ((error: Error) => void) | undefined) { this.errorHandler = handler; }

  start(): Promise<void> { return this.delegate.start(); }
  close(): Promise<void> {
    if (this.delegateClosePromise) return this.delegateClosePromise;
    if (this.closePromise) return this.closePromise;
    this.delegateClosePromise = (async () => {
      try { await this.delegate.close(); }
      finally { await this.handleClose(); }
    })();
    return this.delegateClosePromise;
  }

  async send(message: JSONRPCMessage, options?: TransportSendOptions): Promise<void> {
    if (this.initializeRequestId !== undefined && !this.registrationAttempted && "id" in message
        && "result" in message && message.id === this.initializeRequestId) {
      this.registrationAttempted = true;
      try {
        if (!this.clientInfo) throw new Error("MCP initialize clientInfo is unavailable.");
        const session = await this.register(this.clientInfo.name, this.clientInfo.version, fingerprintTransportSession(this.sessionId));
        this.onRegistered(session);
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

  private handleClose(): Promise<void> {
    if (this.closePromise) return this.closePromise;
    this.closePromise = Promise.resolve()
      .then(() => this.onClosed())
      .then(() => undefined)
      .catch(() => undefined)
      .finally(() => this.closeHandler?.());
    return this.closePromise;
  }
}
const type = z.enum(["FIX", "PATTERN", "RECIPE", "SNIPPET", "DECISION", "PROCEDURE", "CHECKLIST", "TROUBLESHOOTING", "PROMPT", "REFERENCE"]);
const status = z.enum(["DRAFT", "VALIDATED", "DEPRECATED"]);
const usageResult = z.enum(["SUCCESS", "FAILURE", "UNKNOWN"]);
const relationType = z.enum(["RELATED_TO", "SUPERSEDES", "RESOLVES", "DEPENDS_ON"]);
const workspace = { workspaceId: z.string().uuid().optional() };
const validationEvidence = z.object({ summary: z.string(), method: z.string(), reference: z.string().nullable().optional() }).nullable().optional();
const mutation = { title: z.string().min(1), type, problem: z.string().optional(), solution: z.string().optional(), context: z.string().optional(), tags: z.array(z.string()).optional(), stack: z.array(z.string()).optional(), status: status.optional(), projectId: z.string().uuid().nullable().optional(), validationEvidence };
const updateMutation = { ...mutation, title: mutation.title.optional(), type: mutation.type.optional() };
const text = (value: unknown) => ({ content: [{ type: "text" as const, text: JSON.stringify(value) }] });

export function createMcpServer(context: McpServerContext) {
  const client = new No8doClient(context.apiUrl, context.token, fetch, context.agentSessionHeader);
  const server = new McpServer({ name: "no8do-replays", version: "0.1.0" }, { instructions: renderAgentProtocolBootstrap(context.agentProtocol) });
  const readTools: ReadTool[] = [];
  if (context.integration) integrationTools.set(server, readTools);
  const rememberRead = (required: string[], tool: ReadTool["tool"]) => {
    if (context.integration) { tool.disable(); readTools.push({ required, tool }); }
    return tool;
  };
  const resolve = (workspaceId: string | undefined) => resolveWorkspaceId(workspaceId, context.defaultWorkspaceId,
    context.transport, context.agentSessionHeader?.get(), context.agentSessionHeader?.getWorkspaceId());
  const integrationManifest = context.agentProtocol.integrationExtensions;
  server.server.setRequestHandler(RequestSchema.extend({
    method: z4.literal("no8do/integration/capabilities"), params: z4.object({}).strict()
  }), async () => integrationManifest);
  server.server.setRequestHandler(RequestSchema.extend({
    method: z4.literal("no8do/operational-context/get"), params: z4.object({}).strict()
  }), async () => {
    requireRegisteredSession(context.agentSessionHeader);
    try {
      return await client.getOperationalContext();
    } catch (error) {
      throw operationalContextMcpError(error);
    }
  });
  const operationalContextUpdateSchema = z4.object({
    expectedVersion: z4.number().int().nonnegative().nullable(),
    repository: z4.object({ vcs: z4.string(), provider: z4.string(), host: z4.string(), namespace: z4.string(), name: z4.string() }).strict().nullable(),
    branch: z4.string().nullable(),
    workingDirectory: z4.string().nullable(),
    references: z4.array(z4.object({ kind: z4.enum(["ISSUE", "TICKET", "TASK", "WORK_ITEM"]), provider: z4.string(), key: z4.string() }).strict())
  }).strict();
  server.server.setRequestHandler(RequestSchema.extend({
    method: z4.literal("no8do/operational-context/update"), params: operationalContextUpdateSchema
  }), async request => {
    requireRegisteredSession(context.agentSessionHeader);
    try {
      return await client.replaceOperationalContext(request.params);
    } catch (error) {
      throw operationalContextMcpError(error);
    }
  });
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
    policies: z.object({ policies: z.array(z.object({ id: z.string(), description: z.string(), enforcement: z.enum(["ADVISORY", "ENFORCED"]) })) }),
    integrationExtensions: z.object({ extensions: z.array(z.object({
      id: z.string(), version: z.number().int().positive(),
      operationalContext: z.object({ version: z.number().int().positive(), getMethod: z.string(),
        updateMethod: z.string(), optimisticConcurrency: z.literal("EXPECTED_VERSION") })
    })) })
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
    registeredAt: z.string(),
    presenceStatus: z.enum(["CONNECTED", "ACTIVE", "IDLE", "DISCONNECTED"]),
    lastSeenAt: z.string(),
    lastActivityAt: z.string().nullable(),
    disconnectedAt: z.string().nullable()
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
  rememberRead(["REPLAY_CATALOG_LIST"], server.registerTool("list_replays", { description: "Liste o catálogo de Replays do workspace sem aplicar busca textual.", inputSchema: workspace }, async ({ workspaceId }) => text((await client.listReplays(resolve(workspaceId))).map(compactReplay))));
  rememberRead(["REPLAY_SEARCH"], server.registerTool("search_replays", { description: "Pesquise conhecimento técnico reutilizável existente antes de resolver novamente ou criar um novo Replay.", inputSchema: { ...workspace, query: z.string().min(1) } }, async ({ workspaceId, query }) => text((await client.searchReplays(resolve(workspaceId), query)).map(compactReplay))));
  rememberRead(["REPLAY_SEARCH","REUSABLE_KNOWLEDGE_DISCOVERY"], server.registerTool("find_reusable_knowledge", { description: "Sugira Replays reutilizáveis por relevância determinística. Revise candidatos antes de atualizar ou criar conteúdo.", inputSchema: { ...workspace, query: z.string().optional(), problem: z.string().optional(), stack: z.array(z.string()).optional(), tags: z.array(z.string()).optional(), type: type.optional() } }, async ({ workspaceId, ...input }) => text({ suggestions: await client.findReusableKnowledge(resolve(workspaceId), input as FindReusableKnowledgeInput) })));
  rememberRead(["REPLAY_QUALITY_READ"], server.registerTool("get_replay_quality", { description: "Obtenha o score derivado de qualidade e seus sinais.", inputSchema: { ...workspace, replayId: z.string().uuid() } }, async ({ workspaceId, replayId }) => text(await client.getReplayQuality(resolve(workspaceId), replayId))));
  rememberRead(["REPLAY_READ"], server.registerTool("get_replay", { description: "Obtenha o conteúdo completo de um Replay encontrado.", inputSchema: { ...workspace, replayId: z.string().uuid() } }, async ({ workspaceId, replayId }) => text(await client.getReplay(resolve(workspaceId), replayId))));
  rememberRead(["REPLAY_VERSION_READ"], server.registerTool("list_replay_versions", { description: "Liste snapshots imutáveis de conteúdo.", inputSchema: { ...workspace, replayId: z.string().uuid() } }, async ({ workspaceId, replayId }) => text(await client.listReplayVersions(resolve(workspaceId), replayId))));
  rememberRead(["REPLAY_VERSION_READ"], server.registerTool("get_replay_version", { description: "Obtenha um snapshot histórico.", inputSchema: { ...workspace, replayId: z.string().uuid(), version: z.number().int().min(1) } }, async ({ workspaceId, replayId, version }) => text(await client.getReplayVersion(resolve(workspaceId), replayId, version))));
  rememberRead(["REPLAY_RELATIONS"], server.registerTool("list_replay_relations", { description: "Liste as relações do Replay.", inputSchema: { ...workspace, replayId: z.string().uuid() } }, async ({ workspaceId, replayId }) => text(await client.listReplayRelations(resolve(workspaceId), replayId))));
  if (!context.integration) server.registerTool("create_replay_relation", { description: "Crie uma relação entre Replays.", inputSchema: { ...workspace, replayId: z.string().uuid(), targetReplayId: z.string().uuid(), type: relationType } }, async ({ workspaceId, replayId, targetReplayId, type }) => text(await client.createReplayRelation(resolve(workspaceId), replayId, targetReplayId, type as ReplayRelationType)));
  if (!context.integration) server.registerTool("delete_replay_relation", { description: "Remova uma relação existente.", inputSchema: { ...workspace, replayId: z.string().uuid(), relationId: z.string().uuid() } }, async ({ workspaceId, replayId, relationId }) => { await client.deleteReplayRelation(resolve(workspaceId), replayId, relationId); return text({ deleted: true }); });
  if (!context.integration) server.registerTool("create_replay", { description: "Antes de criar, prefira find_reusable_knowledge.", inputSchema: { ...workspace, ...mutation } }, async ({ workspaceId, ...body }: { workspaceId?: string; validationEvidence?: ReplayValidationEvidence | null; [key: string]: unknown }) => text(await client.createReplay(resolve(workspaceId), body as ReplayMutation)));
  if (!context.integration) server.registerTool("update_replay", { description: "Atualize conteúdo de um Replay existente.", inputSchema: { ...workspace, replayId: z.string().uuid(), ...updateMutation }, }, async ({ workspaceId, replayId, ...body }: { workspaceId?: string; replayId: string; validationEvidence?: ReplayValidationEvidence | null; [key: string]: unknown }) => text(await client.updateReplay(resolve(workspaceId), replayId, body as ReplayUpdate)));
  if (!context.integration) server.registerTool("register_replay_usage", { description: "Chame somente após aplicar materialmente o Replay. Para AgentSession, declare materiallyUsed=true e descreva em context, de forma breve, como o Replay foi aplicado; não infira nem invente essa declaração.", inputSchema: { ...workspace, replayId: z.string().uuid(), result: usageResult, materiallyUsed: z.boolean().optional(), projectId: z.string().uuid().nullable().optional(), replayVersion: z.number().int().min(1).optional(), context: z.string().optional() } }, async ({ workspaceId, replayId, ...body }) => text(await client.registerReplayUsage(resolve(workspaceId), replayId, body as RegisterReplayUsageMutation)));
  return server;
}

function requireRegisteredSession(header: AgentSessionHeader | undefined): void {
  if (!header?.get()) throw new McpError(ErrorCode.InvalidRequest, "AGENT_SESSION_REQUIRED");
}

function operationalContextMcpError(error: unknown): Error {
  if (error instanceof No8doApiError && error.code) {
    return new McpError(ErrorCode.InvalidRequest, error.code);
  }
  if (error instanceof No8doApiError && error.status === 409) {
    return new McpError(ErrorCode.InvalidRequest, "OPERATIONAL_CONTEXT_CONFLICT");
  }
  return error instanceof Error ? error : new Error("Operational Context request failed.");
}
