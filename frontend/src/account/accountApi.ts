import { apiRequest } from "@/lib/api";
import type { AuthUser } from "@/auth/AuthContext";

export type DeleteAccountInput = {
  confirmationEmail: string;
  confirmationText: string;
};

export function deleteAccount(input: DeleteAccountInput) {
  return apiRequest<{ status: string }>("/api/auth/me", {
    method: "DELETE",
    body: input,
  });
}

export function updateProfile(input: { name: string }) {
  return apiRequest<AuthUser>("/api/auth/me", {
    method: "PATCH",
    body: input,
  });
}
