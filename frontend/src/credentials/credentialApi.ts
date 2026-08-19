import { apiRequest } from "@/lib/api";

export type ProjectCredentialType = "PASSWORD" | "API_KEY" | "TOKEN" | "OTHER";

export type ProjectCredential = {
  id: string;
  projectId: string;
  label: string;
  type: ProjectCredentialType;
  username: string | null;
  notes: string | null;
  createdBy: string;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
};

export type CreateProjectCredentialInput = {
  label: string;
  type: ProjectCredentialType;
  username?: string;
  secret: string;
  notes?: string;
};

export type RevealProjectCredentialResponse = {
  id: string;
  secret: string;
};

export function listProjectCredentials(workspaceId: string, projectId: string) {
  return apiRequest<ProjectCredential[]>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/credentials`,
  );
}

export function createProjectCredential(
  workspaceId: string,
  projectId: string,
  input: CreateProjectCredentialInput,
) {
  return apiRequest<ProjectCredential>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/credentials`,
    {
      method: "POST",
      body: input,
    },
  );
}

export function revealProjectCredential(
  workspaceId: string,
  projectId: string,
  credentialId: string,
) {
  return apiRequest<RevealProjectCredentialResponse>(
    `/api/workspaces/${workspaceId}/projects/${projectId}/credentials/${credentialId}/reveal`,
    {
      method: "POST",
    },
  );
}
