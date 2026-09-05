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

  searchReplays(workspaceId: string, query: string): Promise<Replay[]> {
    return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/search?q=${encodeURIComponent(query)}`);
  }
  getReplay(workspaceId: string, replayId: string): Promise<Replay> {
    return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays/${encodeURIComponent(replayId)}`);
  }
  createReplay(workspaceId: string, body: ReplayMutation): Promise<Replay> {
    return this.request(`/api/workspaces/${encodeURIComponent(workspaceId)}/replays`, { method: "POST", body: JSON.stringify(body) });
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
