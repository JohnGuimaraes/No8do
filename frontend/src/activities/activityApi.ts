import { apiRequest } from "@/lib/api";

export type ProjectActivityType = "UPDATE" | "DECISION" | "BLOCKER" | "NEXT_STEP";

export type ProjectActivity = {
  id: string;
  projectId: string;
  createdBy: string;
  type: ProjectActivityType;
  content: string;
  createdAt: string;
};

export type CreateProjectActivityInput = {
  content: string;
  type?: ProjectActivityType;
};

export function listProjectActivities(workspaceId: string, projectId: string) {
  return apiRequest<ProjectActivity[]>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/activities`,
  );
}

export function createProjectActivity(
  workspaceId: string,
  projectId: string,
  input: CreateProjectActivityInput,
) {
  return apiRequest<ProjectActivity>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/activities`,
    {
      method: "POST",
      body: input,
    },
  );
}
