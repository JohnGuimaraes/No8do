import { apiRequest, getApiUrl } from "@/lib/api";

export type WorkspaceRole = "OWNER" | "ADMIN" | "MEMBER";

export type Workspace = {
  id: string;
  name: string;
  role: WorkspaceRole;
  createdAt: string;
  updatedAt: string;
};
export type WorkspaceMember = { userId: string; name: string };
export function listWorkspaceMembers(workspaceId: string) { return apiRequest<WorkspaceMember[]>(`/api/workspaces/${workspaceId}/members`); }

export type WorkspaceGithubLink = {
  linked: boolean;
  login: string | null;
  avatarUrl: string | null;
  linkedAt: string | null;
};

export type WorkspaceGithubAppInstallation = {
  installed: boolean;
  installationId: number | null;
  accountId: number | null;
  accountLogin: string | null;
  accountType: "USER" | "ORGANIZATION" | null;
  configuredAt: string | null;
};

export type WorkspaceGithubAppStatus = {
  configured: boolean;
  accountLogin: string | null;
  accountType: "USER" | "ORGANIZATION" | null;
};

export function listWorkspaces() {
  return apiRequest<Workspace[]>("/api/workspaces");
}

export function createWorkspace(name: string) {
  return apiRequest<Workspace>("/api/workspaces", {
    method: "POST",
    body: {
      name,
    },
  });
}

export function getWorkspaceGithubLink(workspaceId: string) {
  return apiRequest<WorkspaceGithubLink>(`/api/workspaces/${workspaceId}/integrations/github`);
}

export function linkMyGithubToWorkspace(workspaceId: string) {
  return apiRequest<WorkspaceGithubLink>(`/api/workspaces/${workspaceId}/integrations/github/link`, { method: "POST" });
}

export function unlinkWorkspaceGithub(workspaceId: string) {
  return apiRequest<void>(`/api/workspaces/${workspaceId}/integrations/github`, { method: "DELETE" });
}

export function getWorkspaceGithubAppInstallation(workspaceId: string) {
  return apiRequest<WorkspaceGithubAppInstallation>(`/api/workspaces/${workspaceId}/integrations/github/app-installation`);
}

export function getWorkspaceGithubAppStatus(workspaceId: string) {
  return apiRequest<WorkspaceGithubAppStatus>(`/api/workspaces/${workspaceId}/integrations/github/app`);
}

export function getWorkspaceGithubAppInstallUrl(workspaceId: string) {
  return getApiUrl(`/api/workspaces/${workspaceId}/integrations/github-app/install`);
}
