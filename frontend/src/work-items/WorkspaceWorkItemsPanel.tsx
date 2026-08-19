import { useEffect, useMemo, useState } from "react";
import { ArrowRight, CheckCircle, Circle, WarningCircle } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import {
  listWorkspaceWorkItems,
  updateProjectWorkItemStatus,
  type ProjectWorkItemStatus,
  type ProjectWorkItemType,
  type WorkspaceWorkItem,
} from "@/work-items/workItemApi";

type WorkspaceWorkItemsPanelProps = {
  workspaceId: string;
  onOpenProject: (projectId: string) => void;
};

const OPEN_SECTIONS: Array<{ type: ProjectWorkItemType; title: string; emptyText: string }> = [
  { type: "BLOCKER", title: "Bloqueios", emptyText: "Nenhum bloqueio aberto." },
  { type: "PENDING", title: "Pendencias", emptyText: "Nenhuma pendencia aberta." },
  { type: "NEXT_STEP", title: "Proximos passos", emptyText: "Nenhum proximo passo aberto." },
];

export function WorkspaceWorkItemsPanel({ workspaceId, onOpenProject }: WorkspaceWorkItemsPanelProps) {
  const [openItems, setOpenItems] = useState<WorkspaceWorkItem[]>([]);
  const [doneItems, setDoneItems] = useState<WorkspaceWorkItem[]>([]);
  const [showDone, setShowDone] = useState(false);
  const [loading, setLoading] = useState(true);
  const [savingItemId, setSavingItemId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadItems() {
      setLoading(true);
      setError(null);

      try {
        const [openResponse, doneResponse] = await Promise.all([
          listWorkspaceWorkItems(workspaceId, { status: "OPEN" }),
          listWorkspaceWorkItems(workspaceId, { status: "DONE" }),
        ]);

        if (active) {
          setOpenItems(openResponse);
          setDoneItems(doneResponse);
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Nao foi possivel carregar as pendencias.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadItems();

    return () => {
      active = false;
    };
  }, [workspaceId]);

  const groupedOpenItems = useMemo(
    () =>
      OPEN_SECTIONS.map((section) => ({
        ...section,
        items: openItems.filter((item) => item.type === section.type),
      })),
    [openItems],
  );

  async function handleStatusChange(item: WorkspaceWorkItem, status: ProjectWorkItemStatus) {
    setSavingItemId(item.id);
    setError(null);

    try {
      const updated = await updateProjectWorkItemStatus(workspaceId, item.projectId, item.id, { status });
      const updatedWorkspaceItem = { ...updated, projectName: item.projectName };
      if (updatedWorkspaceItem.status === "OPEN") {
        setDoneItems((current) => current.filter((currentItem) => currentItem.id !== updated.id));
        setOpenItems((current) => sortWorkspaceItems([updatedWorkspaceItem, ...current.filter((currentItem) => currentItem.id !== updated.id)]));
      } else {
        setOpenItems((current) => current.filter((currentItem) => currentItem.id !== updated.id));
        setDoneItems((current) => sortWorkspaceItems([updatedWorkspaceItem, ...current.filter((currentItem) => currentItem.id !== updated.id)]));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel atualizar o item.");
    } finally {
      setSavingItemId(null);
    }
  }

  const hasOpenItems = openItems.length > 0;

  return (
    <section className="grid min-w-0 gap-4">
      <div className="flex min-w-0 flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-base font-semibold text-foreground">Central de pendencias</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            Bloqueios, pendencias e proximos passos abertos em todos os projetos.
          </p>
        </div>
        <label className="inline-flex items-center gap-2 text-sm text-muted-foreground">
          <input
            type="checkbox"
            className="h-4 w-4 rounded border-input"
            checked={showDone}
            onChange={(event) => setShowDone(event.target.checked)}
          />
          Mostrar concluidos
        </label>
      </div>

      {loading ? <p className="text-sm text-muted-foreground">Carregando pendencias...</p> : null}
      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      {!loading && !error && !hasOpenItems ? (
        <p className="rounded-md border border-dashed border-border px-4 py-8 text-center text-sm text-muted-foreground">
          Nenhuma pendencia aberta.
        </p>
      ) : null}

      {!loading && !error ? (
        <div className="grid min-w-0 gap-4 xl:grid-cols-3">
          {groupedOpenItems.map((section) => (
            <WorkItemGroup
              key={section.type}
              title={section.title}
              emptyText={section.emptyText}
              items={section.items}
              savingItemId={savingItemId}
              onOpenProject={onOpenProject}
              onStatusChange={handleStatusChange}
            />
          ))}
        </div>
      ) : null}

      {!loading && !error && showDone ? (
        <WorkItemGroup
          title="Concluidos"
          emptyText="Nenhum item concluido."
          items={doneItems}
          savingItemId={savingItemId}
          onOpenProject={onOpenProject}
          onStatusChange={handleStatusChange}
        />
      ) : null}
    </section>
  );
}

function WorkItemGroup({
  title,
  emptyText,
  items,
  savingItemId,
  onOpenProject,
  onStatusChange,
}: {
  title: string;
  emptyText: string;
  items: WorkspaceWorkItem[];
  savingItemId: string | null;
  onOpenProject: (projectId: string) => void;
  onStatusChange: (item: WorkspaceWorkItem, status: ProjectWorkItemStatus) => void;
}) {
  return (
    <section className="grid min-w-0 content-start gap-2">
      <h4 className="text-xs font-semibold uppercase text-muted-foreground">{title}</h4>
      {items.length === 0 ? (
        <p className="rounded-md border border-dashed border-border px-3 py-6 text-center text-sm text-muted-foreground">
          {emptyText}
        </p>
      ) : (
        <ol className="grid min-w-0 gap-2">
          {items.map((item) => (
            <WorkItemCard
              key={item.id}
              item={item}
              saving={savingItemId === item.id}
              onOpenProject={onOpenProject}
              onStatusChange={onStatusChange}
            />
          ))}
        </ol>
      )}
    </section>
  );
}

function WorkItemCard({
  item,
  saving,
  onOpenProject,
  onStatusChange,
}: {
  item: WorkspaceWorkItem;
  saving: boolean;
  onOpenProject: (projectId: string) => void;
  onStatusChange: (item: WorkspaceWorkItem, status: ProjectWorkItemStatus) => void;
}) {
  const done = item.status === "DONE";
  const metadata = getWorkItemMetadata(item);

  return (
    <li className={`min-w-0 rounded-md border px-3 py-3 ${metadata.className} ${done ? "opacity-75" : ""}`}>
      <div className="grid min-w-0 gap-3">
        <div className="min-w-0">
          <div className="mb-2 flex min-w-0 flex-wrap items-center gap-2">
            <span className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[11px] font-medium ${metadata.badgeClassName}`}>
              <metadata.Icon className="h-3.5 w-3.5" />
              {metadata.label}
            </span>
            <time className="text-[11px] text-muted-foreground">{formatDate(item.updatedAt)}</time>
          </div>
          <p className={`break-words text-sm font-semibold ${done ? "text-muted-foreground line-through" : "text-card-foreground"}`}>
            {item.title}
          </p>
          <button
            type="button"
            className="mt-1 min-w-0 break-words text-left text-xs font-medium text-primary hover:underline"
            onClick={() => onOpenProject(item.projectId)}
          >
            {item.projectName}
          </button>
          {item.details ? (
            <p className="mt-2 line-clamp-3 whitespace-pre-wrap break-words text-sm leading-6 text-muted-foreground">
              {item.details}
            </p>
          ) : null}
        </div>
        <div className="flex min-w-0 flex-wrap items-center justify-between gap-2 text-[11px] text-muted-foreground">
          <span>Autor: {item.createdByName}</span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={saving}
            onClick={() => onStatusChange(item, done ? "OPEN" : "DONE")}
          >
            {done ? "Reabrir" : "Concluir"}
          </Button>
        </div>
      </div>
    </li>
  );
}

function getWorkItemMetadata(item: WorkspaceWorkItem) {
  if (item.status === "DONE") {
    return {
      label: item.type === "BLOCKER" ? "Resolvido" : "Concluido",
      Icon: CheckCircle,
      className: "border-border bg-card",
      badgeClassName: "border-emerald-200 bg-emerald-50 text-emerald-700",
    };
  }

  if (item.type === "BLOCKER") {
    return {
      label: "Bloqueio",
      Icon: WarningCircle,
      className: "border-red-200 bg-red-50/70",
      badgeClassName: "border-red-200 bg-red-50 text-red-700",
    };
  }

  if (item.type === "NEXT_STEP") {
    return {
      label: "Proximo passo",
      Icon: ArrowRight,
      className: "border-violet-200 bg-violet-50/70",
      badgeClassName: "border-violet-200 bg-violet-50 text-violet-700",
    };
  }

  return {
    label: "Pendencia",
    Icon: Circle,
    className: "border-border bg-card",
    badgeClassName: "border-border bg-background text-muted-foreground",
  };
}

function sortWorkspaceItems(items: WorkspaceWorkItem[]) {
  const typeOrder: Record<ProjectWorkItemType, number> = {
    BLOCKER: 0,
    PENDING: 1,
    NEXT_STEP: 2,
  };

  return [...items].sort((first, second) => {
    const typeDiff = typeOrder[first.type] - typeOrder[second.type];
    return typeDiff === 0 ? Date.parse(second.updatedAt) - Date.parse(first.updatedAt) : typeDiff;
  });
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
