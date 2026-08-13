import { FormEvent, useEffect, useState } from "react";
import { Plus } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import {
  createProjectActivity,
  listProjectActivities,
  type ProjectActivity,
  type ProjectActivityType,
} from "@/activities/activityApi";

type ProjectActivityPanelProps = {
  workspaceId: string;
  projectId: string;
};

const ACTIVITY_TYPES: Array<{ value: ProjectActivityType; label: string }> = [
  { value: "UPDATE", label: "Atualização" },
  { value: "DECISION", label: "Decisão" },
  { value: "BLOCKER", label: "Bloqueio" },
  { value: "NEXT_STEP", label: "Próximo passo" },
];

export function ProjectActivityPanel({ workspaceId, projectId }: ProjectActivityPanelProps) {
  const [activities, setActivities] = useState<ProjectActivity[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [content, setContent] = useState("");
  const [type, setType] = useState<ProjectActivityType | "">("UPDATE");

  useEffect(() => {
    let active = true;

    async function loadActivities() {
      setLoading(true);
      setError(null);
      try {
        const response = await listProjectActivities(workspaceId, projectId);
        if (active) {
          setActivities(response);
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Não foi possível carregar o histórico.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadActivities();

    return () => {
      active = false;
    };
  }, [workspaceId, projectId]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedContent = content.trim();

    if (!normalizedContent) {
      setFormError("Informe o conteúdo da atividade.");
      return;
    }

    setCreating(true);
    setFormError(null);

    try {
      const created = await createProjectActivity(workspaceId, projectId, {
        content: normalizedContent,
        ...(type ? { type } : {}),
      });
      setActivities((current) => [created, ...current]);
      setContent("");
      setType("UPDATE");
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Não foi possível criar a atividade.");
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="rounded-md border border-border bg-background p-3">
      <div className="mb-3">
        <p className="text-sm font-semibold text-foreground">Histórico do projeto</p>
        <p className="mt-1 text-xs text-muted-foreground">Updates mais recentes aparecem primeiro.</p>
      </div>

      <form className="grid gap-2" onSubmit={handleSubmit}>
        <select
          className="h-9 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={type}
          onChange={(event) => setType(event.target.value as ProjectActivityType | "")}
          aria-label="Tipo da atividade"
        >
          {ACTIVITY_TYPES.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <textarea
          className="min-h-20 rounded-md border border-input bg-card px-3 py-2 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={content}
          onChange={(event) => setContent(event.target.value)}
          placeholder="Registre uma decisão, bloqueio, próximo passo ou atualização."
          aria-label="Conteúdo da atividade"
        />
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
        <div className="flex justify-end">
          <Button type="submit" size="sm" disabled={creating}>
            <Plus className="h-4 w-4" />
            {creating ? "Registrando..." : "Registrar atividade"}
          </Button>
        </div>
      </form>

      <div className="mt-4">
        {loading ? <p className="text-sm text-muted-foreground">Carregando histórico...</p> : null}
        {error ? <p className="text-sm text-destructive">{error}</p> : null}
        {!loading && !error && activities.length === 0 ? (
          <div className="rounded-md border border-dashed border-border px-3 py-6 text-center">
            <p className="text-sm font-medium text-muted-foreground">Sem atividades ainda</p>
            <p className="mt-1 text-xs text-muted-foreground">Registre o primeiro update deste projeto.</p>
          </div>
        ) : null}
        {!loading && !error && activities.length > 0 ? (
          <ol className="space-y-3">
            {activities.map((activity) => (
              <li key={activity.id} className="rounded-md border border-border bg-card px-3 py-2">
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                  <span className="rounded-full border border-border bg-background px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                    {getActivityTypeLabel(activity.type)}
                  </span>
                  <time className="text-[11px] text-muted-foreground">{formatDate(activity.createdAt)}</time>
                </div>
                <p className="whitespace-pre-wrap text-sm leading-6 text-card-foreground">{activity.content}</p>
              </li>
            ))}
          </ol>
        ) : null}
      </div>
    </div>
  );
}

function getActivityTypeLabel(type: ProjectActivityType) {
  return ACTIVITY_TYPES.find((option) => option.value === type)?.label ?? type;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
