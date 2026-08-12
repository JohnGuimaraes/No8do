import { useEffect, useState } from "react";
import { Sparkle, Circle } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";

type HealthStatus = "checking" | "online" | "offline";

const API_BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const API_HEALTH_URL = `${API_BASE_URL}/api/health`;

/**
 * Checagem simples e opcional de conectividade com o backend.
 * Não bloqueia a renderização da página e não implementa nenhuma
 * regra de negócio - serve apenas para confirmar que o ambiente
 * local (frontend + backend) está corretamente ligado quando ambos
 * estiverem rodando.
 */
function useBackendHealth() {
  const [status, setStatus] = useState<HealthStatus>("checking");

  useEffect(() => {
    let cancelled = false;

    fetch(API_HEALTH_URL)
      .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
      .then(() => {
        if (!cancelled) setStatus("online");
      })
      .catch(() => {
        if (!cancelled) setStatus("offline");
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return status;
}

function StatusBadge({ status }: { status: HealthStatus }) {
  const config = {
    checking: { label: "Verificando backend...", color: "text-muted-foreground" },
    online: { label: "Backend conectado", color: "text-emerald-600" },
    offline: { label: "Backend offline", color: "text-muted-foreground" },
  }[status];

  return (
    <div className={`flex items-center gap-2 text-xs ${config.color}`}>
      <Circle weight="fill" className="h-2 w-2" />
      <span>{config.label}</span>
    </div>
  );
}

function App() {
  const backendStatus = useBackendHealth();

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-6">
      <div className="flex max-w-xl flex-col items-center gap-6 text-center">
        <div className="flex items-center gap-2 rounded-full border border-border bg-card px-4 py-1.5 shadow-sm">
          <Sparkle weight="fill" className="h-4 w-4 text-primary" />
          <span className="text-sm font-medium text-card-foreground">No8do</span>
        </div>

        <h1 className="text-4xl font-semibold tracking-tight text-foreground sm:text-5xl">
          No8do
        </h1>

        <p className="text-lg text-muted-foreground">
          Workspace visual para projetos, ideias e decisões.
        </p>

        <div className="mt-2 flex flex-col items-center gap-3">
          <Button variant="default">Começar</Button>
          <StatusBadge status={backendStatus} />
        </div>
      </div>
    </div>
  );
}

export default App;
