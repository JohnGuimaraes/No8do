import { apiRequest } from "@/lib/api";

export type ProjectWorkItemType = "NEXT_STEP" | "PENDING" | "BLOCKER";
export type ProjectWorkItemStatus = "OPEN" | "DONE";

export type ProjectWorkItem = {
  id: string;
  projectId: string;
  type: ProjectWorkItemType;
  status: ProjectWorkItemStatus;
  title: string;
  details: string | null;
  createdBy: string;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
};

export type CreateProjectWorkItemInput = {
  type: ProjectWorkItemType;
  title: string;
  details?: string;
};

export type UpdateProjectWorkItemStatusInput = {
  status: ProjectWorkItemStatus;
};

export function listProjectWorkItems(workspaceId: string, projectId: string) {
  return apiRequest<ProjectWorkItem[]>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/work-items`,
  );
}

export function createProjectWorkItem(
  workspaceId: string,
  projectId: string,
  input: CreateProjectWorkItemInput,
) {
  return apiRequest<ProjectWorkItem>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/work-items`,
    {
      method: "POST",
      body: input,
    },
  );
}

export function updateProjectWorkItemStatus(
  workspaceId: string,
  projectId: string,
  workItemId: string,
  input: UpdateProjectWorkItemStatusInput,
) {
  return apiRequest<ProjectWorkItem>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/work-items/${workItemId}`,
    {
      method: "PATCH",
      body: input,
    },
  );
}
