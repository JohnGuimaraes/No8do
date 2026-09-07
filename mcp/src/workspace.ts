const workspaceIdPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export type WorkspaceTransport = "stdio" | "http";

function assertValidWorkspaceId(workspaceId: string, message: string): string {
  if (!workspaceIdPattern.test(workspaceId)) throw new Error(message);
  return workspaceId;
}

export function requireRemoteWorkspaceId(workspaceId: string | undefined): string {
  if (!workspaceId?.trim()) throw new Error("NO8DO_WORKSPACE_ID é obrigatória no MCP remoto.");
  return assertValidWorkspaceId(workspaceId, "NO8DO_WORKSPACE_ID deve ser um UUID válido no MCP remoto.");
}

export function resolveWorkspaceId(explicitWorkspaceId: string | undefined, defaultWorkspaceId: string | undefined, transport: WorkspaceTransport = "stdio"): string {
  if (transport === "http") {
    const scopedWorkspaceId = requireRemoteWorkspaceId(defaultWorkspaceId);
    if (explicitWorkspaceId !== undefined && explicitWorkspaceId !== scopedWorkspaceId) {
      throw new Error("Workspace fora do escopo deste MCP remoto.");
    }
    return scopedWorkspaceId;
  }
  const workspaceId = explicitWorkspaceId ?? defaultWorkspaceId;
  if (!workspaceId) {
    throw new Error("workspaceId é obrigatório: informe-o na chamada ou defina NO8DO_WORKSPACE_ID.");
  }
  return assertValidWorkspaceId(workspaceId, "workspaceId deve ser um UUID válido.");
}
