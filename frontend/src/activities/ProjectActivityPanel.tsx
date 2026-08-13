import { FormEvent, useEffect, useState } from "react";
import {
  ArrowRight,
  CheckCircle,
  Flag,
  NotePencil,
  Plus,
  WarningCircle,
  type Icon,
} from "@phosphor-icons/react";
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

const ACTIVITY_TYPES: Array<{
  value: ProjectActivityType;
  label: string;
  Icon: Icon;
  badgeClassName: string;
  markerClassName: string;
}> = [
  {
    value: "UPDATE",
    label: "Atualização",
    Icon: NotePencil,
    badgeClassName: "border-sky-200 bg-sky-50 text-sky-700",
    markerClassName: "border-sky-200 bg-sky-50 text-sky-700",
  },
  {
    value: "DECISION",
    label: "Decisão",
    Icon: CheckCircle,
    badgeClassName: "border-emerald-200 bg-emerald-50 text-emerald-700",
    markerClassName: "border-emerald-200 bg-emerald-50 text-emerald-700",
  },
  {
    value: "BLOCKER",
    label: "Bloqueio",
    Icon: WarningCircle,
    badgeClassName: "border-red-200 bg-red-50 text-red-700",
    markerClassName: "border-red-200 bg-red-50 text-red-700",
  },
  {
    value: "NEXT_STEP",
    label: "Próximo passo",
    Icon: ArrowRight,
    badgeClassName: "border-violet-200 bg-violet-50 text-violet-700",
    markerClassName: "border-violet-200 bg-violet-50 text-violet-700",
  },
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
    <div className="min-w-0 rounded-md border border-border bg-background p-3">
      <div className="mb-3 min-w-0">
        <p className="text-sm font-semibold text-foreground">Histórico do projeto</p>
        <p className="mt-1 text-xs text-muted-foreground">
          Registre updates, decisões, bloqueios e próximos passos.
        </p>
      </div>

      <form className="grid min-w-0 gap-2" onSubmit={handleSubmit}>
        <select
          className="h-9 w-full min-w-0 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
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
          className="min-h-24 w-full min-w-0 resize-y rounded-md border border-input bg-card px-3 py-2 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={content}
          onChange={(event) => setContent(event.target.value)}
          placeholder="Escreva o que mudou, o que foi decidido ou qual é o próximo passo."
          aria-label="Conteúdo da atividade"
        />
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
        <div className="flex justify-stretch sm:justify-end">
          <Button type="submit" size="sm" className="w-full sm:w-auto" disabled={creating}>
            <Plus className="h-4 w-4" />
            {creating ? "Registrando..." : "Registrar atividade"}
          </Button>
        </div>
      </form>

      <div className="mt-4 min-w-0">
        {loading ? <p className="text-sm text-muted-foreground">Carregando histórico...</p> : null}
        {error ? <p className="text-sm text-destructive">{error}</p> : null}
        {!loading && !error && activities.length === 0 ? (
          <div className="rounded-md border border-dashed border-border px-3 py-6 text-center">
            <p className="text-sm font-medium text-muted-foreground">Sem atividades ainda</p>
            <p className="mt-1 text-xs text-muted-foreground">
              O histórico guarda updates, decisões, bloqueios e próximos passos do projeto.
            </p>
          </div>
        ) : null}
        {!loading && !error && activities.length > 0 ? (
          <ol className="min-w-0 space-y-3">
            {activities.map((activity) => {
              const metadata = getActivityTypeMetadata(activity.type);
              const SourceIcon = isSystemActivity(activity) ? Flag : metadata.Icon;

              return (
                <li key={activity.id} className="grid min-w-0 grid-cols-[28px_minmax(0,1fr)] gap-3">
                  <div className="flex flex-col items-center">
                    <span
                      className={`flex h-7 w-7 items-center justify-center rounded-full border ${metadata.markerClassName}`}
                    >
                      <metadata.Icon className="h-4 w-4" />
                    </span>
                    <span className="mt-2 min-h-6 w-px flex-1 bg-border" />
                  </div>

                  <article className="min-w-0 rounded-md border border-border bg-card px-3 py-3">
                    <div className="mb-2 flex min-w-0 flex-wrap items-center gap-2">
                      <span
                        className={`inline-flex max-w-full items-center gap-1.5 break-words rounded-full border px-2 py-0.5 text-[11px] font-medium ${metadata.badgeClassName}`}
                      >
                        <metadata.Icon className="h-3.5 w-3.5 shrink-0" />
                        {metadata.label}
                      </span>
                      <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                        <SourceIcon className="h-3.5 w-3.5" />
                        {isSystemActivity(activity) ? "Sistema" : "Manual"}
                      </span>
                      <time className="break-words text-[11px] text-muted-foreground">
                        {formatDate(activity.createdAt)}
                      </time>
                    </div>
                    <p className="whitespace-pre-wrap break-words text-sm leading-6 text-card-foreground">
                      {activity.content}
                    </p>
                  </article>
                </li>
              );
            })}
          </ol>
        ) : null}
      </div>
    </div>
  );
}

function getActivityTypeMetadata(type: ProjectActivityType) {
  return ACTIVITY_TYPES.find((option) => option.value === type) ?? ACTIVITY_TYPES[0];
}

function isSystemActivity(activity: ProjectActivity) {
  return activity.content === "Projeto criado." || activity.content.startsWith("Projeto atualizado:");
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
