import { apiRequest } from "@/lib/api";

export type ReplayType = "FIX" | "PATTERN" | "RECIPE" | "SNIPPET" | "DECISION" | "PROCEDURE" | "CHECKLIST" | "TROUBLESHOOTING" | "PROMPT" | "REFERENCE";
export type ReplayStatus = "DRAFT" | "VALIDATED" | "DEPRECATED";
export type ReplayUsageResult = "SUCCESS" | "FAILURE" | "UNKNOWN";
export type ReplayUsageSource = "MCP" | "MANUAL" | "AUTOMATION" | "EXTENSION" | "OTHER";
export type ReplayRelationType = "RELATED_TO" | "SUPERSEDES" | "RESOLVES" | "DEPENDS_ON";
export type ReplayRelation = { id: string; type: ReplayRelationType; direction: "RELATED" | "OUTGOING" | "INCOMING"; relatedReplayId: string; relatedReplayTitle: string; relatedReplayType: ReplayType; relatedReplayStatus: ReplayStatus; relatedReplayVersion: number; createdAt: string };
export type ReplayVersion = { version: number; title: string; type: ReplayType; problem: string | null; solution: string | null; context: string | null; tags: string[]; stack: string[]; status: ReplayStatus; projectId: string | null; changedBy: string | null; changedByName: string; createdAt: string };
export type ReplayQuality = { score: number; level: "LOW" | "MEDIUM" | "HIGH"; usageCount: number; successCount: number; failureCount: number; successRate: number | null; signals: string[] };
export type ReplayUsage = { id: string; replayId: string; projectId: string | null; projectName: string | null; usedBy: string | null; usedByName: string; replayVersion: number; result: ReplayUsageResult; source: ReplayUsageSource; context: string | null; usedAt: string };
export type Replay = { id: string; workspaceId: string; projectId: string | null; projectName: string | null; title: string; type: ReplayType; problem: string | null; solution: string | null; context: string | null; tags: string[]; stack: string[]; status: ReplayStatus; version: number; usageCount: number; successCount: number; failureCount: number; lastUsedAt: string | null; createdBy: string | null; createdByName: string; createdAt: string; updatedAt: string };
export type ReplayInput = { title: string; type: ReplayType; problem?: string; solution?: string; context?: string; tags?: string[]; stack?: string[]; status?: ReplayStatus; projectId?: string | null };
export function listReplays(workspaceId: string) { return apiRequest<Replay[]>(`/api/workspaces/${workspaceId}/replays`); }
export function searchReplays(workspaceId: string, query: string) { return apiRequest<Replay[]>(`/api/workspaces/${workspaceId}/replays/search?q=${encodeURIComponent(query)}`); }
export function getReplay(workspaceId: string, replayId: string) { return apiRequest<Replay>(`/api/workspaces/${workspaceId}/replays/${replayId}`); }
export function getReplayQuality(workspaceId: string, replayId: string) { return apiRequest<ReplayQuality>(`/api/workspaces/${workspaceId}/replays/${replayId}/quality`); }
export function listReplayUsages(workspaceId: string, replayId: string) { return apiRequest<ReplayUsage[]>(`/api/workspaces/${workspaceId}/replays/${replayId}/usages`); }
export function listReplayRelations(workspaceId: string, replayId: string) { return apiRequest<ReplayRelation[]>(`/api/workspaces/${workspaceId}/replays/${replayId}/relations`); }
export function listReplayVersions(workspaceId: string, replayId: string) { return apiRequest<ReplayVersion[]>(`/api/workspaces/${workspaceId}/replays/${replayId}/versions`); }
export function getReplayVersion(workspaceId: string, replayId: string, version: number) { return apiRequest<ReplayVersion>(`/api/workspaces/${workspaceId}/replays/${replayId}/versions/${version}`); }
export function createReplayRelation(workspaceId: string, replayId: string, targetReplayId: string, type: ReplayRelationType) { return apiRequest<ReplayRelation>(`/api/workspaces/${workspaceId}/replays/${replayId}/relations`, { method: "POST", body: { targetReplayId, type } }); }
export function deleteReplayRelation(workspaceId: string, replayId: string, relationId: string) { return apiRequest<void>(`/api/workspaces/${workspaceId}/replays/${replayId}/relations/${relationId}`, { method: "DELETE" }); }
export function createReplay(workspaceId: string, input: ReplayInput) { return apiRequest<Replay>(`/api/workspaces/${workspaceId}/replays`, { method: "POST", body: input }); }
export function updateReplay(workspaceId: string, replayId: string, input: Partial<ReplayInput>) { return apiRequest<Replay>(`/api/workspaces/${workspaceId}/replays/${replayId}`, { method: "PATCH", body: input }); }
export function parseCommaList(value: string) { return [...new Set(value.split(",").map((item) => item.trim()).filter(Boolean))]; }
