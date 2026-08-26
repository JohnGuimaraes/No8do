import { apiRequest } from "@/lib/api";
import { type Project } from "@/projects/projectApi";

export type IdeaType = "PROJECT" | "FEATURE" | "IMPROVEMENT" | "RESEARCH" | "PRODUCT" | "OTHER";

export type IdeaStatus = "INBOX" | "PLANNED" | "CONVERTED" | "ARCHIVED";

export type Idea = {
  id: string;
  workspaceId: string;
  title: string;
  description: string | null;
  type: IdeaType;
  status: IdeaStatus;
  convertedProjectId: string | null;
  convertedProjectName: string | null;
  createdBy: string;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
};

export type IdeaInput = {
  title: string;
  description?: string;
  type: IdeaType;
  status?: Exclude<IdeaStatus, "CONVERTED">;
};

export type IdeaConvertResult = {
  idea: Idea;
  project: Project;
};

export function listIdeas(workspaceId: string) {
  return apiRequest<Idea[]>(`/api/workspaces/${workspaceId}/ideas`);
}

export function listArchivedIdeas(workspaceId: string) {
  return apiRequest<Idea[]>(`/api/workspaces/${workspaceId}/ideas?archived=true`);
}

export function createIdea(workspaceId: string, input: IdeaInput) {
  return apiRequest<Idea>(`/api/workspaces/${workspaceId}/ideas`, {
    method: "POST",
    body: input,
  });
}

export function updateIdea(workspaceId: string, ideaId: string, input: IdeaInput) {
  return apiRequest<Idea>(`/api/workspaces/${workspaceId}/ideas/${ideaId}`, {
    method: "PATCH",
    body: input,
  });
}

export function convertIdeaToProject(workspaceId: string, ideaId: string) {
  return apiRequest<IdeaConvertResult>(`/api/workspaces/${workspaceId}/ideas/${ideaId}/convert-to-project`, {
    method: "POST",
  });
}

export function archiveIdea(workspaceId: string, ideaId: string) {
  return apiRequest<Idea>(`/api/workspaces/${workspaceId}/ideas/${ideaId}/archive`, { method: "POST" });
}

export function restoreIdea(workspaceId: string, ideaId: string) {
  return apiRequest<Idea>(`/api/workspaces/${workspaceId}/ideas/${ideaId}/restore`, { method: "POST" });
}

export function deleteIdea(workspaceId: string, ideaId: string) {
  return apiRequest<void>(`/api/workspaces/${workspaceId}/ideas/${ideaId}`, { method: "DELETE" });
}
