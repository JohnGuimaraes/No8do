import { FormEvent, useEffect, useState } from "react";
import { Buildings, Circle, Plus } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import {
  createWorkspace,
  listWorkspaces,
  type Workspace,
} from "@/workspaces/workspaceApi";

export function WorkspacePanel() {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [activeWorkspaceId, setActiveWorkspaceId] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const activeWorkspace =
    workspaces.find((workspace) => workspace.id === activeWorkspaceId) ?? null;

  useEffect(() => {
    let cancelled = false;

    async function loadWorkspaces() {
      setLoading(true);
      setError(null);

      try {
        const items = await listWorkspaces();

        if (!cancelled) {
          setWorkspaces(items);
          setActiveWorkspaceId(items[0]?.id ?? null);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Nao foi possivel carregar workspaces.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadWorkspaces();

    return () => {
      cancelled = true;
    };
  }, []);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const normalizedName = name.trim();
    if (!normalizedName) {
      setError("Informe um nome para o workspace.");
      return;
    }

    setCreating(true);
    setError(null);

    try {
      const workspace = await createWorkspace(normalizedName);
      setWorkspaces((current) => [...current, workspace]);
      setActiveWorkspaceId(workspace.id);
      setName("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel criar o workspace.");
    } finally {
      setCreating(false);
    }
  }

  return (
    <section className="grid gap-6 lg:grid-cols-[280px_1fr]">
      <aside className="rounded-lg border border-border bg-card p-4 shadow-sm">
        <div className="mb-4 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Buildings className="h-4 w-4 text-muted-foreground" />
            <h2 className="text-sm font-semibold text-card-foreground">Workspaces</h2>
          </div>
          {loading ? (
            <Circle weight="fill" className="h-2 w-2 animate-pulse text-muted-foreground" />
          ) : null}
        </div>

        <form className="mb-4 flex gap-2" onSubmit={handleCreate}>
          <input
            className="h-9 min-w-0 flex-1 rounded-md border border-input bg-background px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Novo workspace"
            aria-label="Nome do workspace"
          />
          <Button type="submit" size="icon" disabled={creating}>
            <Plus className="h-4 w-4" />
            <span className="sr-only">Criar workspace</span>
          </Button>
        </form>

        {error ? <p className="mb-3 text-sm text-destructive">{error}</p> : null}

        {loading ? (
          <p className="text-sm text-muted-foreground">Carregando...</p>
        ) : workspaces.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Crie seu primeiro workspace para organizar os projetos.
          </p>
        ) : (
          <div className="flex flex-col gap-2">
            {workspaces.map((workspace) => (
              <button
                key={workspace.id}
                type="button"
                className={`rounded-md border px-3 py-2 text-left text-sm transition-colors ${
                  workspace.id === activeWorkspaceId
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-border bg-background text-foreground hover:bg-accent"
                }`}
                onClick={() => setActiveWorkspaceId(workspace.id)}
              >
                <span className="block font-medium">{workspace.name}</span>
                <span className="text-xs opacity-80">{workspace.role}</span>
              </button>
            ))}
          </div>
        )}
      </aside>

      <section className="rounded-lg border border-border bg-card p-5 shadow-sm">
        {activeWorkspace ? (
          <div className="flex flex-col gap-6">
            <div className="flex flex-col gap-2">
              <p className="text-sm text-muted-foreground">Workspace ativo</p>
              <div className="flex flex-wrap items-center gap-3">
                <h2 className="text-2xl font-semibold tracking-tight text-card-foreground">
                  {activeWorkspace.name}
                </h2>
                <span className="rounded-md border border-border px-2 py-1 text-xs font-medium text-muted-foreground">
                  {activeWorkspace.role}
                </span>
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              {["Ideias", "Planejamento", "Em andamento"].map((title) => (
                <section key={title} className="rounded-lg border border-border bg-background p-4">
                  <h3 className="mb-4 text-sm font-semibold text-foreground">{title}</h3>
                  <div className="rounded-md border border-dashed border-border px-3 py-8 text-center text-sm text-muted-foreground">
                    Sem itens
                  </div>
                </section>
              ))}
            </div>
          </div>
        ) : (
          <div className="flex min-h-64 items-center justify-center rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground">
            Nenhum workspace selecionado
          </div>
        )}
      </section>
    </section>
  );
}
