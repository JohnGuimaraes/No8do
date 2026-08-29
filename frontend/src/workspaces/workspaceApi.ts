import { apiRequest, getApiUrl } from "@/lib/api";

export type WorkspaceRole = "OWNER" | "ADMIN" | "MEMBER" | "VIEWER";

export type Workspace = {
  id: string;
  name: string;
  role: WorkspaceRole;
  createdAt: string;
  updatedAt: string;
};
export type WorkspaceMember = { userId: string; name: string };
export function listWorkspacePublicMembers(workspaceId: string) { return apiRequest<WorkspaceMember[]>(`/api/workspaces/${workspaceId}/members/public`); }
export type WorkspaceMemberManagement = WorkspaceMember & { email: string; role: WorkspaceRole };
export function listWorkspaceMembers(workspaceId: string) { return apiRequest<WorkspaceMemberManagement[]>(`/api/workspaces/${workspaceId}/members`); }
export function updateWorkspaceMember(workspaceId: string, userId: string, role: Exclude<WorkspaceRole, "OWNER">) { return apiRequest<WorkspaceMemberManagement>(`/api/workspaces/${workspaceId}/members/${userId}`, { method: "PATCH", body: { role } }); }
export function removeWorkspaceMember(workspaceId: string, userId: string) { return apiRequest<void>(`/api/workspaces/${workspaceId}/members/${userId}`, { method: "DELETE" }); }

export type WorkspaceInviteRole = "ADMIN" | "VIEWER";
export type WorkspaceInvite = { id: string; email: string; role: WorkspaceInviteRole; expiresAt: string; acceptedAt: string | null; revokedAt: string | null; createdAt: string; inviteUrl: string | null };
export type WorkspaceInviteDetails = { workspaceId: string; workspaceName: string; email: string; role: WorkspaceInviteRole; expiresAt: string; status: "PENDING" };
export type WorkspaceInviteAcceptance = { workspaceId: string; workspaceName: string; role: WorkspaceRole };
export function listWorkspaceInvites(workspaceId: string) { return apiRequest<WorkspaceInvite[]>(`/api/workspaces/${workspaceId}/invites`); }
export function createWorkspaceInvite(workspaceId: string, input: { email: string; role: WorkspaceInviteRole }) { return apiRequest<WorkspaceInvite>(`/api/workspaces/${workspaceId}/invites`, { method: "POST", body: input }); }
export function revokeWorkspaceInvite(workspaceId: string, inviteId: string) { return apiRequest<void>(`/api/workspaces/${workspaceId}/invites/${inviteId}`, { method: "DELETE" }); }
export function getWorkspaceInvite(token: string) { return apiRequest<WorkspaceInviteDetails>(`/api/workspace-invites/${encodeURIComponent(token)}`); }
export function acceptWorkspaceInvite(token: string) { return apiRequest<WorkspaceInviteAcceptance>(`/api/workspace-invites/${encodeURIComponent(token)}/accept`, { method: "POST" }); }
export function registerWorkspaceInvite(token: string, input: { name: string; password: string }) { return apiRequest<{ user: { id: string; name: string; email: string }; invitation: WorkspaceInviteAcceptance }>(`/api/workspace-invites/${encodeURIComponent(token)}/register`, { method: "POST", body: input }); }

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

export function deleteWorkspace(workspaceId: string, confirmationName: string) {
  return apiRequest<void>(`/api/workspaces/${workspaceId}`, {
    method: "DELETE",
    body: { confirmationName },
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
