import { apiRequest } from "@/lib/api";

export type Client = {
  id: string;
  workspaceId: string;
  name: string;
  companyName: string | null;
  email: string | null;
  phone: string | null;
  notes: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

export type ClientInput = {
  name: string;
  companyName?: string;
  email?: string;
  phone?: string;
  notes?: string;
};

export function listClients(workspaceId: string) {
  return apiRequest<Client[]>(`/api/workspaces/${workspaceId}/clients`);
}

export function createClient(workspaceId: string, input: ClientInput) {
  return apiRequest<Client>(`/api/workspaces/${workspaceId}/clients`, {
    method: "POST",
    body: input,
  });
}

export function updateClient(workspaceId: string, clientId: string, input: ClientInput) {
  return apiRequest<Client>(`/api/workspaces/${workspaceId}/clients/${clientId}`, {
    method: "PATCH",
    body: input,
  });
}
