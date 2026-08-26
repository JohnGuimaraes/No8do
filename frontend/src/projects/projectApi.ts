import { apiRequest, getApiUrl } from "@/lib/api";

export type ProjectStatus = "IDEA" | "PLANNING" | "ACTIVE" | "BLOCKED" | "PAUSED" | "DONE";

export type Project = {
  id: string;
  workspaceId: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  currentState: string | null;
  repositoryUrl: string | null;
  createdBy: string;
  createdByName: string;
  hasCover: boolean;
  coverUpdatedAt: string | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateProjectInput = {
  name: string;
  description?: string;
  currentState?: string;
  status?: ProjectStatus;
  repositoryUrl?: string;
};

export type UpdateProjectInput = {
  name: string;
  description?: string;
  currentState?: string;
  status?: ProjectStatus;
  repositoryUrl?: string;
};

export function listProjects(workspaceId: string, archived = false) {
  const query = archived ? "?archived=true" : "";
  return apiRequest<Project[]>(`/api/workspaces/${workspaceId}/projects${query}`);
}

export function createProject(workspaceId: string, input: CreateProjectInput) {
  return apiRequest<Project>(`/api/workspaces/${workspaceId}/projects`, {
    method: "POST",
    body: input,
  });
}

export function updateProject(workspaceId: string, projectId: string, input: UpdateProjectInput) {
  return apiRequest<Project>(`/api/workspaces/${workspaceId}/projects/${projectId}`, {
    method: "PATCH",
    body: input,
  });
}

export function archiveProject(workspaceId: string, projectId: string) {
  return apiRequest<Project>(`/api/workspaces/${workspaceId}/projects/${projectId}/archive`, { method: "POST" });
}

export function restoreProject(workspaceId: string, projectId: string) {
  return apiRequest<Project>(`/api/workspaces/${workspaceId}/projects/${projectId}/restore`, { method: "POST" });
}

export function deleteProject(workspaceId: string, projectId: string) {
  return apiRequest<void>(`/api/workspaces/${workspaceId}/projects/${projectId}`, { method: "DELETE" });
}

export async function uploadProjectCover(workspaceId: string, projectId: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<Project>(`/api/workspaces/${workspaceId}/projects/${projectId}/cover`, { method: "POST", body: formData });
}

export function deleteProjectCover(workspaceId: string, projectId: string) {
  return apiRequest<Project>(`/api/workspaces/${workspaceId}/projects/${projectId}/cover`, { method: "DELETE" });
}

export function getProjectCoverUrl(workspaceId: string, project: Project) {
  return project.hasCover
    ? getApiUrl(`/api/workspaces/${workspaceId}/projects/${project.id}/cover?v=${encodeURIComponent(project.coverUpdatedAt ?? "")}`)
    : null;
}
