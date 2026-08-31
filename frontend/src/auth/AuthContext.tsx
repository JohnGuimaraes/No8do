import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { apiRequest } from "@/lib/api";
import { deleteAccount, updateProfile, type DeleteAccountInput } from "@/account/accountApi";

export type AuthUser = {
  id: string;
  name: string;
  email: string;
};

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

type RegisterInput = {
  name: string;
  email: string;
  password: string;
};

type LoginInput = {
  email: string;
  password: string;
};

type AuthContextValue = {
  user: AuthUser | null;
  status: AuthStatus;
  register: (input: RegisterInput) => Promise<void>;
  registerWorkspaceInvite: (token: string, input: Omit<RegisterInput, "email">) => Promise<{ workspaceId: string }>;
  login: (input: LoginInput) => Promise<void>;
  logout: () => Promise<void>;
  deleteAccount: (input: DeleteAccountInput) => Promise<void>;
  updateProfile: (input: { name: string }) => Promise<void>;
  loadCurrentUser: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [status, setStatus] = useState<AuthStatus>("loading");

  const loadCurrentUser = useCallback(async () => {
    try {
      const currentUser = await apiRequest<AuthUser>("/api/auth/me");
      setUser(currentUser);
      setStatus("authenticated");
    } catch {
      setUser(null);
      setStatus("unauthenticated");
    }
  }, []);

  useEffect(() => {
    void loadCurrentUser();
  }, [loadCurrentUser]);

  const register = useCallback(async (input: RegisterInput) => {
    const currentUser = await apiRequest<AuthUser>("/api/auth/register", {
      method: "POST",
      body: input,
    });
    setUser(currentUser);
    setStatus("authenticated");
  }, []);

  const registerWorkspaceInvite = useCallback(async (token: string, input: Omit<RegisterInput, "email">) => {
    const response = await apiRequest<{ user: AuthUser; invitation: { workspaceId: string } }>(`/api/workspace-invites/${encodeURIComponent(token)}/register`, { method: "POST", body: input });
    setUser(response.user);
    setStatus("authenticated");
    return response.invitation;
  }, []);

  const login = useCallback(async (input: LoginInput) => {
    const currentUser = await apiRequest<AuthUser>("/api/auth/login", {
      method: "POST",
      body: input,
    });
    setUser(currentUser);
    setStatus("authenticated");
  }, []);

  const logout = useCallback(async () => {
    await apiRequest<{ status: string }>("/api/auth/logout", {
      method: "POST",
    });
    setUser(null);
    setStatus("unauthenticated");
  }, []);

  const removeAccount = useCallback(async (input: DeleteAccountInput) => {
    await deleteAccount(input);
    setUser(null);
    setStatus("unauthenticated");
  }, []);

  const updateCurrentProfile = useCallback(async (input: { name: string }) => {
    const currentUser = await updateProfile(input);
    setUser(currentUser);
    setStatus("authenticated");
  }, []);

  const value = useMemo(
    () => ({
      user,
      status,
      register,
      registerWorkspaceInvite,
      login,
      logout,
      deleteAccount: removeAccount,
      updateProfile: updateCurrentProfile,
      loadCurrentUser,
    }),
    [loadCurrentUser, login, logout, register, registerWorkspaceInvite, removeAccount, status, updateCurrentProfile, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth deve ser usado dentro de AuthProvider");
  }

  return context;
}
