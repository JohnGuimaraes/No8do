import { apiRequest } from "@/lib/api";

export type LibraryItemType =
  | "DOCUMENT"
  | "IDENTITY"
  | "LINK"
  | "TOOL"
  | "COMMAND"
  | "SNIPPET"
  | "REFERENCE"
  | "TEMPLATE"
  | "NOTE"
  | "DECISION"
  | "PROCESS"
  | "INFRASTRUCTURE"
  | "MATERIAL";

export type LibraryItem = {
  id: string;
  workspaceId: string;
  type: LibraryItemType;
  title: string;
  description: string | null;
  content: string | null;
  url: string | null;
  createdBy: string;
  createdByName: string;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type LibraryItemInput = {
  type: LibraryItemType;
  title: string;
  description?: string;
  content?: string;
  url?: string;
};

export function listLibraryItems(workspaceId: string, archived = false) {
  return apiRequest<LibraryItem[]>(`/api/workspaces/${workspaceId}/library-items${archived ? "?archived=true" : ""}`);
}

export function createLibraryItem(workspaceId: string, input: LibraryItemInput) {
  return apiRequest<LibraryItem>(`/api/workspaces/${workspaceId}/library-items`, {
    method: "POST",
    body: input,
  });
}

export function updateLibraryItem(workspaceId: string, itemId: string, input: LibraryItemInput) {
  return apiRequest<LibraryItem>(`/api/workspaces/${workspaceId}/library-items/${itemId}`, {
    method: "PATCH",
    body: input,
  });
}

export function archiveLibraryItem(workspaceId: string, itemId: string) {
  return apiRequest<LibraryItem>(`/api/workspaces/${workspaceId}/library-items/${itemId}/archive`, { method: "POST" });
}

export function restoreLibraryItem(workspaceId: string, itemId: string) {
  return apiRequest<LibraryItem>(`/api/workspaces/${workspaceId}/library-items/${itemId}/restore`, { method: "POST" });
}

export function deleteLibraryItem(workspaceId: string, itemId: string) {
  return apiRequest<void>(`/api/workspaces/${workspaceId}/library-items/${itemId}`, { method: "DELETE" });
}
