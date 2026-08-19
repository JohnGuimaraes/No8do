import { apiRequest } from "@/lib/api";

export type ProjectTechnicalInfo = {
  projectId: string;
  repositoryUrl: string | null;
  stack: string | null;
  productionUrl: string | null;
  developmentUrl: string | null;
  localPath: string | null;
  runCommand: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type ProjectTechnicalInfoInput = {
  repositoryUrl?: string;
  stack?: string;
  productionUrl?: string;
  developmentUrl?: string;
  localPath?: string;
  runCommand?: string;
};

export function getProjectTechnicalInfo(workspaceId: string, projectId: string) {
  return apiRequest<ProjectTechnicalInfo>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/technical-info`,
  );
}

export function saveProjectTechnicalInfo(
  workspaceId: string,
  projectId: string,
  input: ProjectTechnicalInfoInput,
) {
  return apiRequest<ProjectTechnicalInfo>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/technical-info`,
    {
      method: "PUT",
      body: input,
    },
  );
}
