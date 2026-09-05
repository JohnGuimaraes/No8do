import { apiRequest } from "@/lib/api";

export type ReplayType = "FIX" | "PATTERN" | "RECIPE" | "SNIPPET" | "DECISION" | "PROCEDURE" | "CHECKLIST" | "TROUBLESHOOTING" | "PROMPT" | "REFERENCE";
export type ReplayStatus = "DRAFT" | "VALIDATED" | "DEPRECATED";
export type ReplayUsageResult = "SUCCESS" | "FAILURE" | "UNKNOWN";
export type ReplayUsageSource = "MCP" | "MANUAL" | "AUTOMATION" | "EXTENSION" | "OTHER";
export type ReplayUsage = { id: string; replayId: string; projectId: string | null; projectName: string | null; usedBy: string | null; usedByName: string; replayVersion: number; result: ReplayUsageResult; source: ReplayUsageSource; context: string | null; usedAt: string };
export type Replay = { id: string; workspaceId: string; projectId: string | null; projectName: string | null; title: string; type: ReplayType; problem: string | null; solution: string | null; context: string | null; tags: string[]; stack: string[]; status: ReplayStatus; version: number; usageCount: number; successCount: number; failureCount: number; lastUsedAt: string | null; createdBy: string | null; createdByName: string; createdAt: string; updatedAt: string };
export type ReplayInput = { title: string; type: ReplayType; problem?: string; solution?: string; context?: string; tags?: string[]; stack?: string[]; status?: ReplayStatus; projectId?: string | null };
export function listReplays(workspaceId: string) { return apiRequest<Replay[]>(`/api/workspaces/${workspaceId}/replays`); }
export function searchReplays(workspaceId: string, query: string) { return apiRequest<Replay[]>(`/api/workspaces/${workspaceId}/replays/search?q=${encodeURIComponent(query)}`); }
export function getReplay(workspaceId: string, replayId: string) { return apiRequest<Replay>(`/api/workspaces/${workspaceId}/replays/${replayId}`); }
export function listReplayUsages(workspaceId: string, replayId: string) { return apiRequest<ReplayUsage[]>(`/api/workspaces/${workspaceId}/replays/${replayId}/usages`); }
export function createReplay(workspaceId: string, input: ReplayInput) { return apiRequest<Replay>(`/api/workspaces/${workspaceId}/replays`, { method: "POST", body: input }); }
export function updateReplay(workspaceId: string, replayId: string, input: Partial<ReplayInput>) { return apiRequest<Replay>(`/api/workspaces/${workspaceId}/replays/${replayId}`, { method: "PATCH", body: input }); }
export function parseCommaList(value: string) { return [...new Set(value.split(",").map((item) => item.trim()).filter(Boolean))]; }
