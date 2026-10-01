import { apiRequest, ApiRequestError } from "@/lib/api";

export type ConnectionState = "PENDING" | "APPROVED" | "DENIED" | "EXPIRED" | "CONSUMED";
export type WorkspaceOption = { id: string; name: string; role: "OWNER" | "ADMIN" };
export type Inspection = {
  requestId: string; hostType: "CODEX" | "CLAUDE" | "VSCODE" | "IDE";
  displayLabel: string | null; integrationVersion: string; expiresAt: string;
  state: ConnectionState; eligibleWorkspaces: WorkspaceOption[];
};
export type ConnectionAgent = {
  id: string; workspaceId: string; name: string; providerDescriptor: string | null;
  lifecycleStatus: "ACTIVE" | "DISABLED" | "ARCHIVED";
};
type Decision = { state: ConnectionState; requestId: string; workspaceId: string | null; agentId: string | null };
export type AgentChoice = { existingAgentId: string; newAgent?: never } | { newAgent: { name: string }; existingAgentId?: never };
export function normalizeUserCode(value: string) { return value.replace(/[\s-]/g, "").toUpperCase(); }
export function validUserCode(value: string) { return /^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$/.test(value); }
export function displayUserCode(value: string) { return value.length > 4 ? `${value.slice(0, 4)}-${value.slice(4)}` : value; }
export function effectiveConnectionState(inspection: Pick<Inspection, "state" | "expiresAt">, now: number): ConnectionState {
  return inspection.state === "PENDING" && now >= Date.parse(inspection.expiresAt)
    ? "EXPIRED" : inspection.state;
}
export function loginIntentDestination(inviteToken: string | null, connectionReturn: boolean) {
  if (inviteToken) return `/invite?token=${encodeURIComponent(inviteToken)}`;
  return connectionReturn ? "/connect/no8do" : null;
}
const bootstrap = "/api/integration-authorizations/bootstrap";
export function inspectConnection(userCode: string, signal: AbortSignal) {
  return apiRequest<Inspection>(`${bootstrap}/inspect`, { method: "POST", body: { userCode }, signal });
}
export function listConnectionAgents(workspaceId: string, signal: AbortSignal) {
  return apiRequest<ConnectionAgent[]>(`/api/workspaces/${encodeURIComponent(workspaceId)}/agents`, { signal });
}
export function approveConnection(userCode: string, workspaceId: string, choice: AgentChoice, signal: AbortSignal) {
  return apiRequest<Decision>(`${bootstrap}/approve`, { method: "POST", body: { userCode, workspaceId, ...choice }, signal });
}
export function denyConnection(userCode: string, signal: AbortSignal) {
  return apiRequest<Decision>(`${bootstrap}/deny`, { method: "POST", body: { userCode }, signal });
}
export function connectionError(error: unknown) {
  if (error instanceof ApiRequestError) {
    if (error.status === 429) return "Há muitas tentativas. Aguarde um momento antes de tentar novamente.";
    if (error.status === 401) return "Sua sessão terminou. Entre novamente para continuar.";
    if (error.status === 403) return "Você não possui permissão para concluir esta conexão.";
    if ([400, 404].includes(error.status)) return "Não encontramos essa solicitação. Confira o código exibido pela integração.";
    if (error.status === 409) return "A solicitação mudou. Confira o estado novamente antes de continuar.";
  }
  return "Não foi possível concluir a conexão agora. Confira o estado novamente antes de tentar outra ação.";
}
const returnKey = "no8do-integration-connect-return";
export function rememberConnectionReturn() {
  try { sessionStorage.setItem(returnKey, "1"); return true; } catch { return false; }
}
export function hasConnectionReturn() {
  try { return sessionStorage.getItem(returnKey) === "1"; } catch { return false; }
}
export function clearConnectionReturn() {
  try { sessionStorage.removeItem(returnKey); } catch { /* Storage can be unavailable; never derive a redirect from it. */ }
}
