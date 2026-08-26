import { useEffect, useMemo, useState } from "react";
import { ArrowRight, CheckCircle, Circle, WarningCircle } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { ViewModeToggle, type ViewMode } from "@/components/ViewModeToggle";
import {
  listWorkspaceWorkItems,
  updateProjectWorkItemStatus,
  type ProjectWorkItemStatus,
  type ProjectWorkItemType,
  type WorkspaceWorkItem,
} from "@/work-items/workItemApi";
import { getWorkItemDueDateLabel } from "@/work-items/workItemDate";

type WorkspaceWorkItemsPanelProps = {
  workspaceId: string;
  onOpenProject: (projectId: string) => void;
};

const OPEN_SECTIONS: Array<{ type: ProjectWorkItemType; title: string; emptyText: string }> = [
  { type: "NEXT_STEP", title: "Proximos passos", emptyText: "Nenhum proximo passo aberto." },
  { type: "PENDING", title: "Pendencias", emptyText: "Nenhuma pendencia aberta." },
  { type: "BLOCKER", title: "Bloqueios", emptyText: "Nenhum bloqueio aberto." },
];

export function WorkspaceWorkItemsPanel({ workspaceId, onOpenProject }: WorkspaceWorkItemsPanelProps) {
  const [openItems, setOpenItems] = useState<WorkspaceWorkItem[]>([]);
  const [doneItems, setDoneItems] = useState<WorkspaceWorkItem[]>([]);
  const [showDone, setShowDone] = useState(false);
  const [loading, setLoading] = useState(true);
  const [savingItemId, setSavingItemId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>("visual");

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
        <div className="flex flex-wrap items-center gap-3">
        <ViewModeToggle value={viewMode} onChange={setViewMode} visualLabel="Agrupado" />
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
      </div>

      {loading ? <p className="text-sm text-muted-foreground">Carregando pendencias...</p> : null}
      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      {!loading && !error && !hasOpenItems ? (
        <p className="rounded-md border border-dashed border-border px-4 py-8 text-center text-sm text-muted-foreground">
          Nenhuma pendencia aberta.
        </p>
      ) : null}

      {!loading && !error && viewMode === "visual" ? (
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

      {!loading && !error && viewMode === "list" ? (
        <WorkItemList items={showDone ? [...openItems, ...doneItems] : openItems} savingItemId={savingItemId} onOpenProject={onOpenProject} onStatusChange={handleStatusChange} />
      ) : null}

      {!loading && !error && showDone && viewMode === "visual" ? (
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
  const projectGroups = groupItemsByProject(items);

  return (
    <section className="grid min-w-0 content-start gap-2">
      <div className="flex items-center justify-between gap-3">
        <h4 className="text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground">{title}</h4>
      </div>
      {items.length === 0 ? (
        <p className="rounded-md border border-dashed border-border px-3 py-6 text-center text-sm text-muted-foreground">
          {emptyText}
        </p>
      ) : (
        <div className="grid min-w-0 gap-4">
          {projectGroups.map((group) => (
            <section key={group.projectId} className="grid min-w-0 gap-2">
              <button
                type="button"
                className="w-fit max-w-full truncate text-left text-sm font-semibold text-foreground hover:text-primary hover:underline"
                onClick={() => onOpenProject(group.projectId)}
              >
                {group.projectName}
              </button>
              <ol className="grid min-w-0 gap-2">
                {group.items.map((item) => (
                  <WorkItemCard
                    key={item.id}
                    item={item}
                    saving={savingItemId === item.id}
                    onOpenProject={onOpenProject}
                    onStatusChange={onStatusChange}
                  />
                ))}
              </ol>
            </section>
          ))}
        </div>
      )}
    </section>
  );
}

function WorkItemList({
  items,
  savingItemId,
  onOpenProject,
  onStatusChange,
}: {
  items: WorkspaceWorkItem[];
  savingItemId: string | null;
  onOpenProject: (projectId: string) => void;
  onStatusChange: (item: WorkspaceWorkItem, status: ProjectWorkItemStatus) => void;
}) {
  const sortedItems = sortWorkspaceItems(items);
  return <ul className="divide-y divide-border rounded-xl border border-border bg-card">{sortedItems.map((item) => {
    const metadata = getWorkItemMetadata(item);
    const done = item.status === "DONE";
    return <li key={item.id} className="grid min-w-0 gap-2 px-3 py-3 sm:grid-cols-[auto_minmax(0,1fr)_auto] sm:items-center sm:px-4"><span className={`inline-flex w-fit items-center gap-1.5 rounded-full border px-2 py-1 text-[11px] font-medium ${metadata.badgeClassName}`}><metadata.Icon className="h-3.5 w-3.5" aria-hidden="true" />{metadata.label}</span><div className="grid min-w-0 gap-1"><span className={`break-words text-sm font-semibold ${done ? "text-muted-foreground line-through" : "text-card-foreground"}`}>{item.title}</span><button type="button" className="w-fit max-w-full truncate text-left text-xs font-semibold text-foreground/80 hover:text-primary hover:underline" onClick={() => onOpenProject(item.projectId)}>Projeto: {item.projectName}</button><span className="line-clamp-1 text-xs text-muted-foreground">{item.details || "Sem detalhes adicionais."}</span><span className="text-[11px] text-muted-foreground">Atualizado {formatDate(item.updatedAt)}</span></div><Button type="button" variant="outline" size="sm" disabled={savingItemId === item.id} onClick={() => onStatusChange(item, done ? "OPEN" : "DONE")}>{done ? "Reabrir" : "Concluir"}</Button></li>;
  })}</ul>;
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
      <div className="grid min-w-0 gap-2">
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
            className="mt-1 min-w-0 break-words text-left text-xs font-semibold text-foreground/80 hover:text-primary hover:underline"
            onClick={() => onOpenProject(item.projectId)}
          >
            Projeto: {item.projectName}
          </button>
          {item.details ? (
            <p className="mt-2 line-clamp-3 whitespace-pre-wrap break-words text-sm leading-6 text-muted-foreground">
              {item.details}
            </p>
          ) : null}
          {item.assigneeName || item.dueDate ? <p className="mt-2 text-[11px] text-muted-foreground">{item.assigneeName ? `Responsável: ${item.assigneeName}` : ""}{item.assigneeName && item.dueDate ? " · " : ""}{item.dueDate ? getWorkItemDueDateLabel(item.dueDate, item.status) : ""}</p> : null}
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
      badgeClassName: "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/55 dark:text-emerald-200",
    };
  }

  if (item.type === "BLOCKER") {
    return {
      label: "Bloqueio",
      Icon: WarningCircle,
      className: "border-red-200 bg-red-50/70 dark:border-red-900 dark:bg-red-950/35",
      badgeClassName: "border-red-200 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950/55 dark:text-red-200",
    };
  }

  if (item.type === "NEXT_STEP") {
    return {
      label: "Proximo passo",
      Icon: ArrowRight,
      className: "border-violet-200 bg-violet-50/70 dark:border-violet-900 dark:bg-violet-950/35",
      badgeClassName: "border-violet-200 bg-violet-50 text-violet-700 dark:border-violet-800 dark:bg-violet-950/55 dark:text-violet-200",
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
    NEXT_STEP: 0,
    PENDING: 1,
    BLOCKER: 2,
  };

  return [...items].sort((first, second) => {
    const typeDiff = typeOrder[first.type] - typeOrder[second.type];
    return typeDiff === 0 ? Date.parse(second.updatedAt) - Date.parse(first.updatedAt) : typeDiff;
  });
}

function groupItemsByProject(items: WorkspaceWorkItem[]) {
  const groups = new Map<string, { projectId: string; projectName: string; items: WorkspaceWorkItem[] }>();

  for (const item of sortWorkspaceItems(items)) {
    const group = groups.get(item.projectId);
    if (group) {
      group.items.push(item);
    } else {
      groups.set(item.projectId, { projectId: item.projectId, projectName: item.projectName, items: [item] });
    }
  }

  return [...groups.values()].sort((first, second) => first.projectName.localeCompare(second.projectName, "pt-BR"));
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
