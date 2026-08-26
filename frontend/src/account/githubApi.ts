import { apiRequest } from "@/lib/api";

export type UserGithubConnection = {
  connected: boolean;
  login: string | null;
  avatarUrl: string | null;
  connectedAt: string | null;
};

export function getUserGithubConnection() {
  return apiRequest<UserGithubConnection>("/api/account/integrations/github");
}

export function disconnectUserGithub() {
  return apiRequest<void>("/api/account/integrations/github", { method: "DELETE" });
}
