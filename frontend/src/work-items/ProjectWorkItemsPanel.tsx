import { FormEvent, useEffect, useMemo, useState } from "react";
import { ArrowRight, CheckCircle, Circle, WarningCircle, PencilSimple, Plus } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import {
  createProjectWorkItem,
  listProjectWorkItems,
  updateProjectWorkItemStatus,
  updateProjectWorkItem,
  type ProjectWorkItem,
  type ProjectWorkItemStatus,
  type ProjectWorkItemType,
} from "@/work-items/workItemApi";
import { listWorkspaceMembers, type WorkspaceMember } from "@/workspaces/workspaceApi";
import { getWorkItemDueDateLabel } from "@/work-items/workItemDate";

type ProjectWorkItemsPanelProps = {
  workspaceId: string;
  projectId: string;
};

const WORK_ITEM_TYPES: Array<{ value: ProjectWorkItemType; label: string }> = [
  { value: "NEXT_STEP", label: "Proximo passo" },
  { value: "PENDING", label: "Pendencia" },
  { value: "BLOCKER", label: "Bloqueio" },
];

const SECTIONS: Array<{ type: ProjectWorkItemType; title: string; emptyText: string }> = [
  { type: "NEXT_STEP", title: "Proximos passos", emptyText: "Nenhum proximo passo aberto." },
  { type: "PENDING", title: "Pendencias", emptyText: "Nenhuma pendencia aberta." },
  { type: "BLOCKER", title: "Bloqueios", emptyText: "Nenhum bloqueio aberto." },
];

const MAX_TITLE_LENGTH = 180;
const MAX_DETAILS_LENGTH = 2_000;

export function ProjectWorkItemsPanel({ workspaceId, projectId }: ProjectWorkItemsPanelProps) {
  const [items, setItems] = useState<ProjectWorkItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [savingItemId, setSavingItemId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [type, setType] = useState<ProjectWorkItemType>("NEXT_STEP");
  const [title, setTitle] = useState("");
  const [details, setDetails] = useState("");
  const [assigneeUserId, setAssigneeUserId] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [creatingOpen, setCreatingOpen] = useState(false);
  const [editing, setEditing] = useState<ProjectWorkItem | null>(null);
  const [members, setMembers] = useState<WorkspaceMember[]>([]);

  useEffect(() => {
    let active = true;

    async function loadItems() {
      setLoading(true);
      setError(null);

      try {
        const response = await listProjectWorkItems(workspaceId, projectId);
        if (active) {
          setItems(response);
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Nao foi possivel carregar os proximos passos.");
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
  }, [workspaceId, projectId]);
  useEffect(() => { setCreatingOpen(false); setEditing(null); setTitle(""); setDetails(""); setAssigneeUserId(""); setDueDate(""); }, [projectId]);
  useEffect(() => { void listWorkspaceMembers(workspaceId).then(setMembers).catch(() => setMembers([])); }, [workspaceId]);

  const doneItems = useMemo(() => items.filter((item) => item.status === "DONE"), [items]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedTitle = title.trim();
    const normalizedDetails = details.trim();

    if (!normalizedTitle) {
      setFormError("Informe um titulo para o item.");
      return;
    }
    if (normalizedTitle.length > MAX_TITLE_LENGTH) {
      setFormError("O titulo deve ter no maximo 180 caracteres.");
      return;
    }
    if (normalizedDetails.length > MAX_DETAILS_LENGTH) {
      setFormError("Os detalhes devem ter no maximo 2.000 caracteres.");
      return;
    }

    setCreating(true);
    setFormError(null);

    try {
      const input = {
        type,
        title: normalizedTitle,
        ...(normalizedDetails ? { details: normalizedDetails } : {}),
        ...(assigneeUserId ? { assigneeUserId } : { assigneeUserId: null }),
        ...(dueDate ? { dueDate } : { dueDate: null }),
      };
      const saved = editing ? await updateProjectWorkItem(workspaceId, projectId, editing.id, input) : await createProjectWorkItem(workspaceId, projectId, input);
      setItems((current) => sortItems(editing ? current.map((item) => item.id === saved.id ? saved : item) : [saved, ...current]));
      resetForm(); setCreatingOpen(false);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Nao foi possivel criar o item.");
    } finally {
      setCreating(false);
    }
  }
  function resetForm() { setType("NEXT_STEP"); setTitle(""); setDetails(""); setAssigneeUserId(""); setDueDate(""); setEditing(null); setFormError(null); }
  function startEditing(item: ProjectWorkItem) { setEditing(item); setType(item.type); setTitle(item.title); setDetails(item.details ?? ""); setAssigneeUserId(item.assigneeUserId ?? ""); setDueDate(item.dueDate ?? ""); }

  async function handleStatusChange(item: ProjectWorkItem, status: ProjectWorkItemStatus) {
    setSavingItemId(item.id);
    setError(null);

    try {
      const updated = await updateProjectWorkItemStatus(workspaceId, projectId, item.id, { status });
      setItems((current) => sortItems(current.map((currentItem) => (
        currentItem.id === updated.id ? updated : currentItem
      ))));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel atualizar o item.");
    } finally {
      setSavingItemId(null);
    }
  }

  return (
    <div className="min-w-0 rounded-md border border-border bg-background p-3">
      <div className="mb-3 min-w-0">
        <p className="text-sm font-semibold text-foreground">Proximos passos</p>
        <p className="mt-1 text-xs text-muted-foreground">
          Acompanhe o que fazer agora, o que falta resolver e o que esta bloqueando o projeto.
        </p>
      </div>

      {!creatingOpen && !editing ? <Button type="button" size="sm" variant="outline" onClick={() => setCreatingOpen(true)}><Plus className="h-4 w-4" />Criar item</Button> : null}
      {creatingOpen || editing ? <form className="mt-3 grid min-w-0 gap-2" onSubmit={handleSubmit}>
        <select
          className="h-9 w-full min-w-0 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={type}
          onChange={(event) => setType(event.target.value as ProjectWorkItemType)}
          aria-label="Tipo do item"
        >
          {WORK_ITEM_TYPES.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <input
          className="h-9 w-full min-w-0 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          maxLength={MAX_TITLE_LENGTH}
          placeholder="Titulo"
          aria-label="Titulo do item"
        />
        <textarea
          className="min-h-20 w-full min-w-0 resize-y rounded-md border border-input bg-card px-3 py-2 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={details}
          onChange={(event) => setDetails(event.target.value)}
          maxLength={MAX_DETAILS_LENGTH}
          placeholder="Detalhes opcionais"
          aria-label="Detalhes do item"
        />
        <select className="h-9 rounded-md border border-input bg-card px-3 text-sm" value={assigneeUserId} onChange={(event) => setAssigneeUserId(event.target.value)}><option value="">Sem responsável</option>{members.map((member) => <option key={member.userId} value={member.userId}>{member.name}</option>)}</select>
        <input className="h-9 rounded-md border border-input bg-card px-3 text-sm" type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} />
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
        <div className="flex justify-stretch gap-2 sm:justify-end">
          <Button type="button" variant="ghost" size="sm" disabled={creating} onClick={() => { resetForm(); setCreatingOpen(false); }}>Cancelar</Button>
          <Button type="submit" size="sm" className="w-full sm:w-auto" disabled={creating}>
            {creating ? "Salvando..." : editing ? "Salvar alterações" : "Criar item"}
          </Button>
        </div>
      </form> : null}

      <div className="mt-4 grid min-w-0 gap-4">
        {loading ? <p className="text-sm text-muted-foreground">Carregando itens...</p> : null}
        {error ? <p className="text-sm text-destructive">{error}</p> : null}
        {!loading && !error ? (
          <>
            {SECTIONS.map((section) => (
              <WorkItemSection
                key={section.type}
                title={section.title}
                emptyText={section.emptyText}
                items={items.filter((item) => item.type === section.type && item.status === "OPEN")}
                savingItemId={savingItemId}
                onStatusChange={handleStatusChange}
                onEdit={startEditing}
              />
            ))}

            {doneItems.length > 0 ? (
              <WorkItemSection
                title="Concluidos e resolvidos"
                emptyText=""
                items={doneItems}
                savingItemId={savingItemId}
                onStatusChange={handleStatusChange}
                onEdit={startEditing}
              />
            ) : null}
          </>
        ) : null}
      </div>
    </div>
  );
}

function WorkItemSection({
  title,
  emptyText,
  items,
  savingItemId,
  onStatusChange,
  onEdit,
}: {
  title: string;
  emptyText: string;
  items: ProjectWorkItem[];
  savingItemId: string | null;
  onStatusChange: (item: ProjectWorkItem, status: ProjectWorkItemStatus) => void;
  onEdit: (item: ProjectWorkItem) => void;
}) {
  return (
    <section className="min-w-0">
      <h4 className="mb-2 text-xs font-semibold uppercase text-muted-foreground">{title}</h4>
      {items.length === 0 ? (
        <div className="rounded-md border border-dashed border-border px-3 py-4 text-sm text-muted-foreground">
          {emptyText}
        </div>
      ) : (
        <ol className="min-w-0 space-y-2">
          {items.map((item) => (
            <WorkItemCard
              key={item.id}
              item={item}
              saving={savingItemId === item.id}
              onStatusChange={onStatusChange}
              onEdit={onEdit}
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
  onStatusChange,
  onEdit,
}: {
  item: ProjectWorkItem;
  saving: boolean;
  onStatusChange: (item: ProjectWorkItem, status: ProjectWorkItemStatus) => void;
  onEdit: (item: ProjectWorkItem) => void;
}) {
  const done = item.status === "DONE";
  const metadata = getWorkItemMetadata(item);

  return (
    <li className={`min-w-0 rounded-md border px-3 py-3 ${metadata.className} ${done ? "opacity-75" : ""}`}>
      <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="mb-1 flex min-w-0 flex-wrap items-center gap-2">
            <span className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[11px] font-medium ${metadata.badgeClassName}`}>
              <metadata.Icon className="h-3.5 w-3.5" />
              {metadata.label}
            </span>
            <time className="text-[11px] text-muted-foreground">{formatDate(item.updatedAt)}</time>
          </div>
          <p className={`break-words text-sm font-semibold ${done ? "text-muted-foreground line-through" : "text-card-foreground"}`}>
            {item.title}
          </p>
          {item.details ? (
            <p className="mt-1 whitespace-pre-wrap break-words text-sm leading-6 text-muted-foreground">
              {item.details}
            </p>
          ) : null}
          <p className="mt-2 text-[11px] text-muted-foreground">
            Autor: {item.createdByName}
            {item.assigneeName ? ` · Responsável: ${item.assigneeName}` : ""}
            {item.dueDate ? ` · ${getWorkItemDueDateLabel(item.dueDate, item.status)}` : ""}
            {item.completedAt ? ` · ${item.type === "BLOCKER" ? "Resolvido" : "Concluido"} em ${formatDate(item.completedAt)}` : ""}
          </p>
        </div>
        <div className="flex gap-1"><Button type="button" variant="ghost" size="sm" disabled={saving} onClick={() => onEdit(item)}><PencilSimple className="h-4 w-4" />Editar</Button><Button
          type="button"
          variant="outline"
          size="sm"
          disabled={saving}
          onClick={() => onStatusChange(item, done ? "OPEN" : "DONE")}
        >
          {done ? "Reabrir" : "Concluir"}
        </Button></div>
      </div>
    </li>
  );
}

function getWorkItemMetadata(item: ProjectWorkItem) {
  if (item.status === "DONE") {
    return {
      label: item.type === "BLOCKER" ? "Resolvido" : "Concluido",
      Icon: CheckCircle,
      className: "border-border bg-card",
      badgeClassName: "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/55 dark:text-emerald-200",
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

  if (item.type === "BLOCKER") {
    return {
      label: "Bloqueio",
      Icon: WarningCircle,
      className: "border-red-200 bg-red-50/70 dark:border-red-900 dark:bg-red-950/35",
      badgeClassName: "border-red-200 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950/55 dark:text-red-200",
    };
  }

  return {
    label: "Pendencia",
    Icon: Circle,
    className: "border-border bg-card",
    badgeClassName: "border-border bg-background text-muted-foreground",
  };
}

function sortItems(items: ProjectWorkItem[]) {
  return [...items].sort((first, second) => {
    if (first.status !== second.status) {
      return first.status === "OPEN" ? -1 : 1;
    }
    return new Date(second.updatedAt).getTime() - new Date(first.updatedAt).getTime();
  });
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
