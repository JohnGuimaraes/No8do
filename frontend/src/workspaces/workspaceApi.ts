import { apiRequest } from "@/lib/api";

export type WorkspaceRole = "OWNER" | "ADMIN" | "MEMBER";

export type Workspace = {
  id: string;
  name: string;
  role: WorkspaceRole;
  createdAt: string;
  updatedAt: string;
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
