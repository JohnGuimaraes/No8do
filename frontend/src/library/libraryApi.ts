import { apiRequest } from "@/lib/api";

export type LibraryItemType =
  | "LINK"
  | "TOOL"
  | "COMMAND"
  | "SNIPPET"
  | "REFERENCE"
  | "TEMPLATE"
  | "NOTE";

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

export function listLibraryItems(workspaceId: string) {
  return apiRequest<LibraryItem[]>(`/api/workspaces/${workspaceId}/library-items`);
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
