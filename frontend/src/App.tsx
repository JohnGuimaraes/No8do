import { useEffect, useState } from "react";
import { Circle, SignOut, Sparkle } from "@phosphor-icons/react";
import { LoginPage } from "@/auth/LoginPage";
import { RegisterPage } from "@/auth/RegisterPage";
import { useAuth } from "@/auth/AuthContext";
import { Button } from "@/components/ui/button";
import { WorkspacePanel } from "@/workspaces/WorkspacePanel";

type AuthView = "login" | "register";
type HealthStatus = "checking" | "online" | "offline";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
const API_HEALTH_URL = `${API_BASE_URL}/api/health`;

function useBackendHealth() {
  const [backendStatus, setBackendStatus] = useState<HealthStatus>("checking");

  useEffect(() => {
    let cancelled = false;

    fetch(API_HEALTH_URL, {
      credentials: "include",
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error("Backend offline");
        }
        if (!cancelled) {
          setBackendStatus("online");
        }
      })
      .catch(() => {
        if (!cancelled) {
          setBackendStatus("offline");
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return backendStatus;
}

function StatusBadge({ status }: { status: HealthStatus }) {
  const label = {
    checking: "Verificando backend...",
    online: "Backend conectado",
    offline: "Backend offline",
  }[status];

  const color = {
    checking: "text-muted-foreground",
    online: "text-emerald-600",
    offline: "text-muted-foreground",
  }[status];

  return (
    <div className={`flex items-center gap-2 text-xs ${color}`}>
      <Circle weight="fill" className="h-2 w-2" />
      <span>{label}</span>
    </div>
  );
}

function App() {
  const { status, user, logout } = useAuth();
  const [authView, setAuthView] = useState<AuthView>("login");
  const [loggingOut, setLoggingOut] = useState(false);
  const backendStatus = useBackendHealth();

  async function handleLogout() {
    setLoggingOut(true);

    try {
      await logout();
    } finally {
      setLoggingOut(false);
    }
  }

  if (status === "loading") {
    return (
      <main className="flex min-h-screen items-center justify-center bg-background px-6">
        <div className="flex items-center gap-3 text-sm text-muted-foreground">
          <Circle weight="fill" className="h-2 w-2 animate-pulse" />
          Carregando sessao...
        </div>
      </main>
    );
  }

  if (status === "unauthenticated") {
    return authView === "login" ? (
      <LoginPage onShowRegister={() => setAuthView("register")} />
    ) : (
      <RegisterPage onShowLogin={() => setAuthView("login")} />
    );
  }

  return (
    <main className="min-h-screen bg-background">
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex w-full max-w-5xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-2">
            <Sparkle weight="fill" className="h-5 w-5 text-primary" />
            <span className="font-semibold text-card-foreground">No8do</span>
          </div>

          <Button variant="outline" size="sm" onClick={handleLogout} disabled={loggingOut}>
            <SignOut className="h-4 w-4" />
            {loggingOut ? "Saindo..." : "Sair"}
          </Button>
        </div>
      </header>

      <section className="mx-auto flex w-full max-w-5xl flex-col gap-8 px-6 py-10">
        <div className="flex flex-col gap-2">
          <p className="text-sm text-muted-foreground">Sessao ativa</p>
          <h1 className="text-3xl font-semibold tracking-tight text-foreground">
            Ola, {user?.name}
          </h1>
          <p className="text-muted-foreground">{user?.email}</p>
          <StatusBadge status={backendStatus} />
        </div>

        <WorkspacePanel />
      </section>
    </main>
  );
}

export default App;
