import { apiRequest } from "@/lib/api";

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
