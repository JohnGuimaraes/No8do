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
  assigneeUserId: string | null;
  assigneeName: string | null;
  dueDate: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
};

export type WorkspaceWorkItem = ProjectWorkItem & {
  projectName: string;
};

export type CreateProjectWorkItemInput = {
  type: ProjectWorkItemType;
  title: string;
  details?: string;
  assigneeUserId?: string | null;
  dueDate?: string | null;
};
export type UpdateProjectWorkItemInput = CreateProjectWorkItemInput;

export type UpdateProjectWorkItemStatusInput = {
  status: ProjectWorkItemStatus;
};

export function listProjectWorkItems(workspaceId: string, projectId: string) {
  return apiRequest<ProjectWorkItem[]>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/work-items`,
  );
}
export function updateProjectWorkItem(workspaceId:string,projectId:string,workItemId:string,input:UpdateProjectWorkItemInput){return apiRequest<ProjectWorkItem>(`/api/workspaces/${workspaceId}/projects/${projectId}/work-items/${workItemId}`,{method:"PUT",body:input});}

export function listWorkspaceWorkItems(
  workspaceId: string,
  filters: { status?: ProjectWorkItemStatus; type?: ProjectWorkItemType } = {},
) {
  const params = new URLSearchParams();

  if (filters.status) {
    params.set("status", filters.status);
  }
  if (filters.type) {
    params.set("type", filters.type);
  }

  const query = params.toString();
  return apiRequest<WorkspaceWorkItem[]>(
    `/api/workspaces/${workspaceId}/work-items${query ? `?${query}` : ""}`,
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
