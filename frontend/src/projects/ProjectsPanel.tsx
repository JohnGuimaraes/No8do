import { FormEvent, useEffect, useState } from "react";
import { Circle, Plus } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import {
  createProject,
  listProjects,
  type CreateProjectInput,
  type Project,
  type ProjectStatus,
} from "@/projects/projectApi";
import { type Workspace } from "@/workspaces/workspaceApi";

const PROJECT_STATUS_OPTIONS: ProjectStatus[] = [
  "IDEA",
  "PLANNING",
  "ACTIVE",
  "BLOCKED",
  "PAUSED",
  "DONE",
];

export function ProjectsPanel({ workspace }: { workspace: Workspace }) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [currentState, setCurrentState] = useState("");
  const [status, setStatus] = useState<ProjectStatus | "">("");
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadProjects() {
      setLoading(true);
      setError(null);

      try {
        const items = await listProjects(workspace.id);

        if (!cancelled) {
          setProjects(items);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Nao foi possivel carregar projetos.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadProjects();

    return () => {
      cancelled = true;
    };
  }, [workspace.id]);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const normalizedName = name.trim();
    if (!normalizedName) {
      setError("Informe um nome para o projeto.");
      return;
    }

    const input: CreateProjectInput = {
      name: normalizedName,
    };
    const normalizedDescription = description.trim();
    const normalizedCurrentState = currentState.trim();

    if (normalizedDescription) {
      input.description = normalizedDescription;
    }
    if (normalizedCurrentState) {
      input.currentState = normalizedCurrentState;
    }
    if (status) {
      input.status = status;
    }

    setCreating(true);
    setError(null);

    try {
      const project = await createProject(workspace.id, input);
      setProjects((current) => [project, ...current]);
      setName("");
      setDescription("");
      setCurrentState("");
      setStatus("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel criar o projeto.");
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <form className="grid gap-3 rounded-lg border border-border bg-background p-4" onSubmit={handleCreate}>
        <div className="grid gap-3 md:grid-cols-[1fr_180px]">
          <input
            className="h-10 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Nome do projeto"
            aria-label="Nome do projeto"
          />
          <select
            className="h-10 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            value={status}
            onChange={(event) => setStatus(event.target.value as ProjectStatus | "")}
            aria-label="Status do projeto"
          >
            <option value="">Status padrao</option>
            {PROJECT_STATUS_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </div>

        <textarea
          className="min-h-20 rounded-md border border-input bg-card px-3 py-2 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          placeholder="Descricao"
          aria-label="Descricao do projeto"
        />

        <div className="flex flex-col gap-3 md:flex-row">
          <input
            className="h-10 min-w-0 flex-1 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            value={currentState}
            onChange={(event) => setCurrentState(event.target.value)}
            placeholder="Estado atual"
            aria-label="Estado atual do projeto"
          />
          <Button type="submit" disabled={creating}>
            <Plus className="h-4 w-4" />
            {creating ? "Criando..." : "Criar projeto"}
          </Button>
        </div>
      </form>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      {loading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Circle weight="fill" className="h-2 w-2 animate-pulse" />
          Carregando projetos...
        </div>
      ) : projects.length === 0 ? (
        <div className="rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground">
          Nenhum projeto neste workspace
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {projects.map((project) => (
            <article key={project.id} className="rounded-lg border border-border bg-background p-4">
              <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <h3 className="break-words text-base font-semibold text-foreground">{project.name}</h3>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Atualizado em {formatDate(project.updatedAt)}
                  </p>
                </div>
                <span className="rounded-md border border-border px-2 py-1 text-xs font-medium text-muted-foreground">
                  {project.status}
                </span>
              </div>

              {project.description ? (
                <p className="mb-3 whitespace-pre-wrap text-sm text-muted-foreground">{project.description}</p>
              ) : null}

              {project.currentState ? (
                <div className="rounded-md border border-border bg-card px-3 py-2 text-sm text-card-foreground">
                  {project.currentState}
                </div>
              ) : null}
            </article>
          ))}
        </div>
      )}
    </div>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
