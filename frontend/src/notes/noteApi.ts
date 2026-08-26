import { apiRequest } from "@/lib/api";

export type ProjectNote = {
  id: string;
  projectId: string;
  createdBy: string;
  createdByName: string;
  content: string;
  type: "NOTE" | "DECISION" | "CONTEXT";
  createdAt: string;
  updatedAt: string;
};

export type CreateProjectNoteInput = {
  content: string;
  type: ProjectNote["type"];
};

export function listProjectNotes(workspaceId: string, projectId: string) {
  return apiRequest<ProjectNote[]>(`/api/workspaces/${workspaceId}/projects/${projectId}/notes`);
}
export function updateProjectNote(workspaceId: string, projectId: string, noteId: string, input: CreateProjectNoteInput) { return apiRequest<ProjectNote>(`/api/workspaces/${workspaceId}/projects/${projectId}/notes/${noteId}`, { method: "PUT", body: input }); }
export function deleteProjectNote(workspaceId: string, projectId: string, noteId: string) { return apiRequest<void>(`/api/workspaces/${workspaceId}/projects/${projectId}/notes/${noteId}`, { method: "DELETE" }); }

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
