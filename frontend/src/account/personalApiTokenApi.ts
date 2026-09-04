import { apiRequest } from "@/lib/api";
export type PersonalApiToken = { id: string; name: string; createdAt: string; revokedAt: string | null };
export type CreatedPersonalApiToken = { token: PersonalApiToken; value: string };
export function listPersonalApiTokens() { return apiRequest<PersonalApiToken[]>("/api/auth/api-tokens"); }
export function createPersonalApiToken(name: string) { return apiRequest<CreatedPersonalApiToken>("/api/auth/api-tokens", { method: "POST", body: { name } }); }
export function revokePersonalApiToken(id: string) { return apiRequest<void>(`/api/auth/api-tokens/${id}`, { method: "DELETE" }); }
