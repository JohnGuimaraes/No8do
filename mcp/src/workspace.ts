const workspaceIdPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function resolveWorkspaceId(explicitWorkspaceId: string | undefined, defaultWorkspaceId: string | undefined): string {
  const workspaceId = explicitWorkspaceId ?? defaultWorkspaceId;
  if (!workspaceId) {
    throw new Error("workspaceId é obrigatório: informe-o na chamada ou defina NO8DO_WORKSPACE_ID.");
  }
  if (!workspaceIdPattern.test(workspaceId)) {
    throw new Error("workspaceId deve ser um UUID válido.");
  }
  return workspaceId;
}
