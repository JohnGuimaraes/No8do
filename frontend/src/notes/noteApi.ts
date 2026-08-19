import { apiRequest } from "@/lib/api";

export type ProjectNote = {
  id: string;
  projectId: string;
  createdBy: string;
  createdByName: string;
  content: string;
  createdAt: string;
};

export type CreateProjectNoteInput = {
  content: string;
};

export function listProjectNotes(workspaceId: string, projectId: string) {
  return apiRequest<ProjectNote[]>(`/api/workspaces/${workspaceId}/projects/${projectId}/notes`);
}

export function createProjectNote(
  workspaceId: string,
  projectId: string,
  input: CreateProjectNoteInput,
) {
  return apiRequest<ProjectNote>(`/api/workspaces/${workspaceId}/projects/${projectId}/notes`, {
    method: "POST",
    body: input,
  });
}
