import { apiRequest } from "@/lib/api";

export type ProjectStatus = "IDEA" | "PLANNING" | "ACTIVE" | "BLOCKED" | "PAUSED" | "DONE";

export type Project = {
  id: string;
  workspaceId: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  currentState: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

export type CreateProjectInput = {
  name: string;
  description?: string;
  currentState?: string;
  status?: ProjectStatus;
};

export type UpdateProjectInput = {
  name: string;
  description?: string;
  currentState?: string;
  status?: ProjectStatus;
};

export function listProjects(workspaceId: string) {
  return apiRequest<Project[]>(`/api/workspaces/${workspaceId}/projects`);
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
