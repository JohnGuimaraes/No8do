export type ReplayType = "FIX" | "PATTERN" | "RECIPE" | "SNIPPET" | "DECISION" | "PROCEDURE" | "CHECKLIST" | "TROUBLESHOOTING" | "PROMPT" | "REFERENCE";
export type ReplayStatus = "DRAFT" | "VALIDATED" | "DEPRECATED";
export type ReplayUsageResult = "SUCCESS" | "FAILURE" | "UNKNOWN";
export type ReplayUsageSource = "MCP" | "MANUAL" | "AUTOMATION" | "EXTENSION" | "OTHER";
export type ReplayValidationEvidence = { summary: string; method: string; reference?: string | null };

export interface ReplayUsage {
  id: string;
  replayId: string;
  projectId?: string | null;
  projectName?: string | null;
  usedBy: string | null;
  usedByName: string;
  replayVersion: number;
  result: ReplayUsageResult;
  source: ReplayUsageSource;
  context?: string | null;
  materiallyUsed?: boolean | null;
  usedAt: string;
}

export interface Replay {
  id: string;
  workspaceId: string;
  projectId?: string | null;
  projectName?: string | null;
  title: string;
  type: ReplayType;
  problem?: string | null;
  solution?: string | null;
  context?: string | null;
  tags: string[];
  stack: string[];
  status: ReplayStatus;
  version: number;
  usageCount: number;
  successCount: number;
  failureCount: number;
  lastUsedAt?: string | null;
  createdBy: string | null;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
  validationEvidence?: ReplayValidationEvidence | null;
}
export interface SimilarReplay { id: string; title: string; type: ReplayType; status: ReplayStatus; version: number; stack: string[]; usageCount: number; score: number; }
export type FindReusableKnowledgeInput = { query?: string; problem?: string; stack?: string[]; tags?: string[]; type?: ReplayType };
export type ReplayQuality = { score: number; level: "LOW" | "MEDIUM" | "HIGH"; usageCount: number; successCount: number; failureCount: number; successRate: number | null; signals: string[] };
export type ReplayRelationType = "RELATED_TO" | "SUPERSEDES" | "RESOLVES" | "DEPENDS_ON";
export type ReplayRelation = { id: string; type: ReplayRelationType; direction: "RELATED" | "OUTGOING" | "INCOMING"; relatedReplayId: string; relatedReplayTitle: string; relatedReplayType: ReplayType; relatedReplayStatus: ReplayStatus; relatedReplayVersion: number; createdAt: string };
export type ReplayVersion = { version: number; title: string; type: ReplayType; problem: string | null; solution: string | null; context: string | null; tags: string[]; stack: string[]; status: ReplayStatus; projectId: string | null; changedBy: string | null; changedByName: string; createdAt: string; validationEvidence?: ReplayValidationEvidence | null };
export type AgentCapability = { id: string; description: string; readOnly: boolean };
export type AgentPolicy = { id: string; description: string; enforcement: "ADVISORY" | "ENFORCED" };
export type AgentProtocol = {
  protocolName: string;
  protocolVersion: number;
  systemName: string;
  purpose: string;
  replayGuidance: {
    summary: string;
    searchBeforeNonTrivialWork: boolean;
    preferExistingKnowledge: boolean;
    searchBeforeCreate: boolean;
    recordUsageOnlyWhenMateriallyUsed: boolean;
    validatedRequiresEvidence: boolean;
    avoidTrivialKnowledge: boolean;
    avoidDuplicateKnowledge: boolean;
    neverStoreSecrets: boolean;
    neverStoreCredentials: boolean;
    avoidDiscardedAttempts: boolean;
  };
  capabilities: { capabilities: AgentCapability[] };
  policies: { policies: AgentPolicy[] };
  integrationExtensions: { extensions: AgentProtocolIntegrationExtension[] };
};
export type AgentProtocolIntegrationExtension = {
  id: string;
  version: number;
  operationalContext: { version: number; getMethod: string; updateMethod: string; optimisticConcurrency: "EXPECTED_VERSION" };
};
export type AgentSessionRegistration = {
  clientName: string;
  clientVersion: string;
  workspaceId: string | null;
  transport: "MCP";
  transportSessionFingerprint: string;
};
export type AgentSession = {
  sessionId: string;
  clientName: string;
  clientVersion: string;
  workspaceId: string | null;
  transport: "MCP";
  runtimeMode: AgentRuntimeMode;
  protocolName: string;
  protocolVersion: number;
  registeredAt: string;
};

export type AgentRuntimeMode = "OFF" | "READ_ONLY" | "RETRIEVAL" | "ASSISTED" | "FULL";
export type AgentPresenceStatus = "CONNECTED" | "ACTIVE" | "IDLE" | "DISCONNECTED";
export type AgentSessionContext = {
  sessionId: string;
  clientName: string;
  clientVersion: string;
  workspaceId: string | null;
  transport: "MCP";
  protocolName: string;
  protocolVersion: number;
  runtimeMode: AgentRuntimeMode;
  effectiveCapabilities: AgentCapability[];
  policies: AgentPolicy[];
  registeredAt: string;
  presenceStatus: AgentPresenceStatus;
  lastSeenAt: string;
  lastActivityAt: string | null;
  disconnectedAt: string | null;
};

export type AgentSessionHeartbeat = { sessionId: string; lastSeenAt: string };
export type AgentOperationalContext = {
  sessionId: string;
  version: number;
  signal: { repository: { vcs: string; provider: string; host: string; namespace: string; name: string } | null; branch: string | null; workingDirectory: string | null; references: { kind: string; provider: string; key: string }[] };
  resolution: { project: { id: string | null; status: string; confidence: string | null }; workItem: { id: string | null; status: string; confidence: string | null } };
  updatedAt: string;
};
export type AgentOperationalContextRead = { exists: false } | { exists: true; context: AgentOperationalContext };

export class AgentSessionHeader {
  private sessionId?: string;
  private workspaceId?: string | null;
  private onRevoked?: () => void;

  set(sessionId: string, workspaceId?: string | null): void { this.sessionId = sessionId; this.workspaceId = workspaceId; }
  get(): string | undefined { return this.sessionId; }
  getWorkspaceId(): string | null | undefined { return this.workspaceId; }
  setRevocationHandler(handler: () => void): void { this.onRevoked = handler; }
  markRevoked(): void { this.onRevoked?.(); }
}

export type ReplayMutation = Pick<Replay, "title" | "type"> & Partial<Pick<Replay, "problem" | "solution" | "context" | "tags" | "stack" | "status" | "projectId" | "validationEvidence">>;
export type ReplayUpdate = Partial<ReplayMutation>;
export type RegisterReplayUsageMutation = {
  result: ReplayUsageResult;
  materiallyUsed?: boolean;
  projectId?: string | null;
  replayVersion?: number;
  context?: string | null;
};

export class No8doApiError extends Error {
  constructor(public readonly status: number, message = messageForStatus(status),
      public readonly metadata?: Record<string, unknown>,
      public readonly code?: "AGENT_CAPABILITY_DENIED" | "AGENT_POLICY_DENIED" | "AGENT_SESSION_DISCONNECTED" | "AGENT_SESSION_REVOKED" | "OPERATIONAL_CONTEXT_CONFLICT") { super(message); }
}

function messageForStatus(status: number): string {
  if (status === 400) return "Request inválido.";
  if (status === 401) return "Token No8do ausente, inválido ou revogado.";
  if (status === 403) return "Usuário sem permissão no workspace.";
  if (status === 404) return "Recurso não encontrado.";
  if (status === 409) return "Conflito ao processar a solicitação.";
  return "Falha da API No8do.";
}

export class No8doClient {
  private readonly baseUrl: string;
  readonly integration: boolean;
  constructor(apiUrl: string, private readonly token: string, private readonly fetchImpl: typeof fetch = fetch,
      private readonly agentSessionHeader?: AgentSessionHeader) {
    this.baseUrl = apiUrl.replace(/\/+$/, "").replace(/\/api$/, "");
    this.integration = token.startsWith("no8do_int_");
  }

  getAgentProtocol(): Promise<AgentProtocol> {
    return this.request("/api/agent-protocol");
  }
  registerAgentSession(registration: AgentSessionRegistration, agentCredential?: string): Promise<AgentSession> {
    const headers = new Headers();
    if (agentCredential !== undefined) headers.set("X-No8do-Agent-Credential", agentCredential);
    return this.request("/api/agent-sessions", {
      method: "POST", headers, body: JSON.stringify(registration),
      ...(agentCredential !== undefined ? { redirect: "error" as const } : {})
    }, agentCredential !== undefined);
  }
  getAgentContext(): Promise<AgentSessionContext> {
    const sessionId = this.agentSessionHeader?.get();
    if (!sessionId) throw new Error("Agent session has not been registered.");
    return this.request(`/api/agent-sessions/${encodeURIComponent(sessionId)}/context`);
  }
  async requireIntegrationSession(): Promise<AgentSessionContext> {
    const context = await this.getAgentContext();
    if (context.sessionId !== this.agentSessionHeader?.get()
        || !context.workspaceId || context.workspaceId !== this.agentSessionHeader?.getWorkspaceId()) {
      throw new No8doApiError(403);
    }
    if (context.disconnectedAt !== null || context.presenceStatus === "DISCONNECTED") {
      throw new No8doApiError(409, "AGENT_SESSION_DISCONNECTED", undefined, "AGENT_SESSION_DISCONNECTED");
    }
    return context;
  }
  async getOperationalContext(): Promise<AgentOperationalContextRead> {
    const sessionId = this.agentSessionHeader?.get();
    if (!sessionId) throw new Error("Agent session has not been registered.");
    const state = await this.request<{ exists: boolean; context: AgentOperationalContext | null }>(
      `/api/agent-sessions/${encodeURIComponent(sessionId)}/operational-context/state`);
    if (!state.exists) return { exists: false };
    if (!state.context) throw new Error("No8do returned an inconsistent Operational Context state.");
    return { exists: true, context: state.context };
  }
  async replaceOperationalContext(input: {
    expectedVersion: number | null;
    repository: AgentOperationalContext["signal"]["repository"];
    branch: string | null;
    workingDirectory: string | null;
    references: AgentOperationalContext["signal"]["references"];
  }): Promise<AgentOperationalContext> {
    const sessionId = this.agentSessionHeader?.get();
    if (!sessionId) throw new Error("Agent session has not been registered.");
    try {
      return await this.request<AgentOperationalContext>(`/api/agent-sessions/${encodeURIComponent(sessionId)}/operational-context`, {
        method: "PUT", body: JSON.stringify(input)
      });
    } catch (error) {
      if (error instanceof No8doApiError && error.status === 409 && !error.code) {
        throw new No8doApiError(409, "Operational context version conflict.", undefined, "OPERATIONAL_CONTEXT_CONFLICT");
      }
      throw error;
    }
  }
  heartbeatAgentSession(sessionId: string): Promise<AgentSessionHeartbeat> {
    return this.request(`/api/agent-sessions/${encodeURIComponent(sessionId)}/heartbeat`, { method: "POST" });
  }
  disconnectAgentSession(sessionId: string): Promise<AgentSessionContext> {
    return this.request(`/api/agent-sessions/${encodeURIComponent(sessionId)}/disconnect`, {
      method: "POST",
      signal: AbortSignal.timeout(2_000)
    });
  }
  listReplays(workspaceId: string): Promise<Replay[]> {
    return this.request(this.replayPath(workspaceId));
  }
  searchReplays(workspaceId: string, query: string): Promise<Replay[]> {
    return this.request(`${this.replayPath(workspaceId)}/search?q=${encodeURIComponent(query)}`);
  }
  findReusableKnowledge(workspaceId: string, input: FindReusableKnowledgeInput): Promise<SimilarReplay[]> {
    return this.request(`${this.replayPath(workspaceId)}/similar`, { method: "POST", body: JSON.stringify({ query: input.query, problem: input.problem, stack: input.stack, tags: input.tags, type: input.type }) });
  }
  getReplay(workspaceId: string, replayId: string): Promise<Replay> {
    return this.request(`${this.replayPath(workspaceId)}/${encodeURIComponent(replayId)}`);
  }
  getReplayQuality(workspaceId: string, replayId: string): Promise<ReplayQuality> { return this.request(`${this.replayPath(workspaceId)}/${encodeURIComponent(replayId)}/quality`); }
  listReplayVersions(workspaceId: string, replayId: string): Promise<ReplayVersion[]> { return this.request(`${this.replayPath(workspaceId)}/${encodeURIComponent(replayId)}/versions`); }
  getReplayVersion(workspaceId: string, replayId: string, version: number): Promise<ReplayVersion> { return this.request(`${this.replayPath(workspaceId)}/${encodeURIComponent(replayId)}/versions/${version}`); }
  listReplayRelations(workspaceId: string, replayId: string): Promise<ReplayRelation[]> {
    return this.request(`${this.replayPath(workspaceId)}/${encodeURIComponent(replayId)}/relations`);
  }
  createReplayRelation(workspaceId: string, replayId: string, targetReplayId: string, type: ReplayRelationType): Promise<ReplayRelation> {
    return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/${encodeURIComponent(replayId)}/relations`, { method: "POST", body: JSON.stringify({ targetReplayId, type }) });
  }
  async deleteReplayRelation(workspaceId: string, replayId: string, relationId: string): Promise<void> {
    await this.request<unknown>(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/${encodeURIComponent(replayId)}/relations/${encodeURIComponent(relationId)}`, { method: "DELETE" });
  }
  createReplay(workspaceId: string, body: ReplayMutation): Promise<Replay> {
    const { projectId, ...withoutProjectId } = body;
    const payload = projectId === null ? withoutProjectId : body;
    return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays`, { method: "POST", body: JSON.stringify(payload) });
  }
  updateReplay(workspaceId: string, replayId: string, body: ReplayUpdate): Promise<Replay> {
    return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/${encodeURIComponent(replayId)}`, { method: "PATCH", body: JSON.stringify(body) });
  }
  registerReplayUsage(workspaceId: string, replayId: string, body: RegisterReplayUsageMutation): Promise<ReplayUsage> {
    return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/${encodeURIComponent(replayId)}/usages`, { method: "POST", body: JSON.stringify({ ...body, source: "MCP" }) });
  }
  private async request<T>(path: string, init: RequestInit = {}, sensitiveRegistration = false): Promise<T> {
    if (this.integration && sensitiveRegistration) throw new No8doApiError(400);
    if (this.integration && path.startsWith("/api/workspaces/")) throw new No8doApiError(403);
    if (sensitiveRegistration || this.integration) {
      const destination = new URL(`${this.baseUrl}${path}`);
      const loopback = ["localhost", "127.0.0.1", "::1", "[::1]"].includes(destination.hostname);
      if (destination.protocol !== "https:" && !(destination.protocol === "http:" && loopback)) {
        throw new Error("Credential transport requires HTTPS except for loopback development.");
      }
    }
    const sessionId = this.agentSessionHeader?.get();
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${this.token}`);
    headers.set("Content-Type", "application/json");
    if (sessionId) headers.set("X-No8do-Agent-Session-Id", sessionId);
    let response: Response;
    try {
      response = await this.fetchImpl(`${this.baseUrl}${path}`, { ...init, headers,
        ...(this.integration ? { redirect: "error" as const } : {}) });
    } catch (error) {
      if (this.integration) throw new No8doApiError(502);
      throw error;
    }
    if (!response.ok) {
      let body: { error?: unknown; metadata?: unknown } = {};
      try { body = await response.clone().json() as typeof body; } catch { /* retain the safe status message */ }
      const isCapabilityDenied = body.error === "AGENT_CAPABILITY_DENIED";
      const isPolicyDenied = body.error === "AGENT_POLICY_DENIED";
      const isSessionDisconnected = body.error === "AGENT_SESSION_DISCONNECTED";
      const isSessionRevoked = response.status === 409 && body.error === "AGENT_SESSION_REVOKED";
      const rawMetadata = (isCapabilityDenied || isPolicyDenied || isSessionDisconnected) && body.metadata && typeof body.metadata === "object" && !Array.isArray(body.metadata)
        ? body.metadata as Record<string, unknown> : undefined;
      const metadata: Record<string, unknown> | undefined = rawMetadata ? {
        ...(typeof rawMetadata.sessionId === "string" ? { sessionId: rawMetadata.sessionId } : {}),
        ...(isPolicyDenied && typeof rawMetadata.policyId === "string" ? { policyId: rawMetadata.policyId } : {}),
        ...(isPolicyDenied && typeof rawMetadata.reason === "string" ? { reason: rawMetadata.reason } : {}),
        ...(isCapabilityDenied && typeof rawMetadata.runtimeMode === "string" ? { runtimeMode: rawMetadata.runtimeMode } : {}),
        ...(isCapabilityDenied && typeof rawMetadata.requiredCapability === "string" ? { requiredCapability: rawMetadata.requiredCapability } : {})
      } : undefined;
      const invalidAgentCredential = sensitiveRegistration && response.status === 401;
      const message = invalidAgentCredential
        ? "Credencial de Agent inválida."
        : isCapabilityDenied
        ? `AGENT_CAPABILITY_DENIED${metadata && Object.keys(metadata).length ? `: ${JSON.stringify(metadata)}` : ""}`
        : isPolicyDenied
          ? `AGENT_POLICY_DENIED${metadata && Object.keys(metadata).length ? `: ${JSON.stringify(metadata)}` : ""}`
          : isSessionDisconnected
            ? `AGENT_SESSION_DISCONNECTED${metadata && Object.keys(metadata).length ? `: ${JSON.stringify(metadata)}` : ""}`
            : isSessionRevoked
              ? "AGENT_SESSION_REVOKED"
              : messageForStatus(response.status);
      const code = isCapabilityDenied ? "AGENT_CAPABILITY_DENIED"
        : isPolicyDenied ? "AGENT_POLICY_DENIED"
        : isSessionDisconnected ? "AGENT_SESSION_DISCONNECTED"
        : isSessionRevoked ? "AGENT_SESSION_REVOKED"
        : undefined;
      if (code === "AGENT_SESSION_REVOKED") this.agentSessionHeader?.markRevoked();
      throw new No8doApiError(response.status, message, metadata, code);
    }
    try { return await response.json() as T; }
    catch (error) {
      if (this.integration) throw new No8doApiError(502);
      throw error;
    }
  }
  private replayPath(workspaceId: string): string {
    if (!this.integration) return `/api/workspaces/${encodeURIComponent(workspaceId)}/replays`;
    if (!this.agentSessionHeader?.get() || workspaceId !== this.agentSessionHeader.getWorkspaceId()) {
      throw new No8doApiError(403);
    }
    return "/api/integration-runtime/replays";
  }
}

export function compactReplay(replay: Replay) {
  return {
    id: replay.id,
    title: replay.title,
    type: replay.type,
    status: replay.status,
    version: replay.version,
    projectId: replay.projectId ?? null,
    tags: replay.tags,
    stack: replay.stack,
    usageCount: replay.usageCount,
    successCount: replay.successCount,
    failureCount: replay.failureCount,
    lastUsedAt: replay.lastUsedAt ?? null,
    updatedAt: replay.updatedAt
  };
}
