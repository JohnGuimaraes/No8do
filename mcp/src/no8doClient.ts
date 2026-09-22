export type ReplayType = "FIX" | "PATTERN" | "RECIPE" | "SNIPPET" | "DECISION" | "PROCEDURE" | "CHECKLIST" | "TROUBLESHOOTING" | "PROMPT" | "REFERENCE";
export type ReplayStatus = "DRAFT" | "VALIDATED" | "DEPRECATED";
export type ReplayUsageResult = "SUCCESS" | "FAILURE" | "UNKNOWN";
export type ReplayUsageSource = "MCP" | "MANUAL" | "AUTOMATION" | "EXTENSION" | "OTHER";

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
}
export interface SimilarReplay { id: string; title: string; type: ReplayType; status: ReplayStatus; version: number; stack: string[]; usageCount: number; score: number; }
export type FindReusableKnowledgeInput = { query?: string; problem?: string; stack?: string[]; tags?: string[]; type?: ReplayType };
export type ReplayQuality = { score: number; level: "LOW" | "MEDIUM" | "HIGH"; usageCount: number; successCount: number; failureCount: number; successRate: number | null; signals: string[] };
export type ReplayRelationType = "RELATED_TO" | "SUPERSEDES" | "RESOLVES" | "DEPENDS_ON";
export type ReplayRelation = { id: string; type: ReplayRelationType; direction: "RELATED" | "OUTGOING" | "INCOMING"; relatedReplayId: string; relatedReplayTitle: string; relatedReplayType: ReplayType; relatedReplayStatus: ReplayStatus; relatedReplayVersion: number; createdAt: string };
export type ReplayVersion = { version: number; title: string; type: ReplayType; problem: string | null; solution: string | null; context: string | null; tags: string[]; stack: string[]; status: ReplayStatus; projectId: string | null; changedBy: string | null; changedByName: string; createdAt: string };
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
};

export type ReplayMutation = Pick<Replay, "title" | "type"> & Partial<Pick<Replay, "problem" | "solution" | "context" | "tags" | "stack" | "status" | "projectId">>;
export type ReplayUpdate = Partial<ReplayMutation>;
export type RegisterReplayUsageMutation = {
  result: ReplayUsageResult;
  projectId?: string | null;
  replayVersion?: number;
  context?: string | null;
};

export class No8doApiError extends Error {
  constructor(public readonly status: number) { super(messageForStatus(status)); }
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
  constructor(apiUrl: string, private readonly token: string, private readonly fetchImpl: typeof fetch = fetch) {
    this.baseUrl = apiUrl.replace(/\/+$/, "").replace(/\/api$/, "");
  }

  getAgentProtocol(): Promise<AgentProtocol> {
    return this.request("/api/agent-protocol");
  }
  listReplays(workspaceId: string): Promise<Replay[]> {
    return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays`);
  }
  searchReplays(workspaceId: string, query: string): Promise<Replay[]> {
    return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/search?q=${encodeURIComponent(query)}`);
  }
  findReusableKnowledge(workspaceId: string, input: FindReusableKnowledgeInput): Promise<SimilarReplay[]> {
    return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/similar`, { method: "POST", body: JSON.stringify({ query: input.query, problem: input.problem, stack: input.stack, tags: input.tags, type: input.type }) });
  }
  getReplay(workspaceId: string, replayId: string): Promise<Replay> {
    return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/${encodeURIComponent(replayId)}`);
  }
  getReplayQuality(workspaceId: string, replayId: string): Promise<ReplayQuality> { return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/${encodeURIComponent(replayId)}/quality`); }
  listReplayVersions(workspaceId: string, replayId: string): Promise<ReplayVersion[]> { return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/${encodeURIComponent(replayId)}/versions`); }
  getReplayVersion(workspaceId: string, replayId: string, version: number): Promise<ReplayVersion> { return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/${encodeURIComponent(replayId)}/versions/${version}`); }
  listReplayRelations(workspaceId: string, replayId: string): Promise<ReplayRelation[]> {
    return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/${encodeURIComponent(replayId)}/relations`);
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
  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await this.fetchImpl(`${this.baseUrl}${path}`, { ...init, headers: { Authorization: `Bearer ${this.token}`, "Content-Type": "application/json", ...init.headers } });
    if (!response.ok) throw new No8doApiError(response.status);
    return response.json() as Promise<T>;
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
