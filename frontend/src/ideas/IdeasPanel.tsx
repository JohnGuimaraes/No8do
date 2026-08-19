import { FormEvent, useEffect, useMemo, useState } from "react";
import { PencilSimple, Plus, X } from "@phosphor-icons/react";
import {
  convertIdeaToProject,
  createIdea,
  listIdeas,
  updateIdea,
  type Idea,
  type IdeaInput,
  type IdeaStatus,
  type IdeaType,
} from "@/ideas/ideaApi";
import { type Project } from "@/projects/projectApi";

type StatusFilter = IdeaStatus | "ALL";
type TypeFilter = IdeaType | "ALL";
type EditableStatus = Exclude<IdeaStatus, "CONVERTED">;

const TYPE_OPTIONS: Array<{ value: IdeaType; label: string }> = [
  { value: "PROJECT", label: "Sistema" },
  { value: "FEATURE", label: "Funcionalidade" },
  { value: "IMPROVEMENT", label: "Melhoria" },
  { value: "RESEARCH", label: "Pesquisa" },
  { value: "PRODUCT", label: "Produto" },
  { value: "OTHER", label: "Outro" },
];

const STATUS_OPTIONS: Array<{ value: EditableStatus; label: string }> = [
  { value: "INBOX", label: "Caixa de entrada" },
  { value: "PLANNED", label: "Planejada" },
  { value: "ARCHIVED", label: "Arquivada" },
];

const STATUS_FILTERS: Array<{ value: StatusFilter; label: string }> = [
  { value: "ALL", label: "Todas" },
  { value: "INBOX", label: "Caixa de entrada" },
  { value: "PLANNED", label: "Planejadas" },
  { value: "CONVERTED", label: "Convertidas" },
  { value: "ARCHIVED", label: "Arquivadas" },
];

const TYPE_FILTERS: Array<{ value: TypeFilter; label: string }> = [
  { value: "ALL", label: "Todos os tipos" },
  ...TYPE_OPTIONS,
];

const EMPTY_FORM: IdeaInput = {
  title: "",
  description: "",
  type: "PROJECT",
  status: "INBOX",
};

export function IdeasPanel({
  workspaceId,
  projects,
  onProjectCreated,
  onOpenProject,
  selectedIdeaId,
  onIdeasChange,
}: {
  workspaceId: string;
  projects: Project[];
  onProjectCreated: (project: Project) => void;
  onOpenProject: (project: Project) => void;
  selectedIdeaId?: string | null;
  onIdeasChange?: (ideas: Idea[]) => void;
}) {
  const [ideas, setIdeas] = useState<Idea[]>([]);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [typeFilter, setTypeFilter] = useState<TypeFilter>("ALL");
  const [form, setForm] = useState<IdeaInput>(EMPTY_FORM);
  const [editForm, setEditForm] = useState<IdeaInput>(EMPTY_FORM);
  const [selectedIdea, setSelectedIdea] = useState<Idea | null>(null);
  const [editingIdeaId, setEditingIdeaId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [convertingIdeaId, setConvertingIdeaId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadIdeas() {
      setLoading(true);
      setError(null);
      setSelectedIdea(null);
      setEditingIdeaId(null);

      try {
        const items = await listIdeas(workspaceId);
        if (!cancelled) {
          setIdeas(items);
          onIdeasChange?.(items);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Nao foi possivel carregar ideias.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadIdeas();

    return () => {
      cancelled = true;
    };
  }, [onIdeasChange, workspaceId]);

  useEffect(() => {
    if (!selectedIdeaId) {
      return;
    }
    const idea = ideas.find((item) => item.id === selectedIdeaId);
    if (idea) {
      setSelectedIdea(idea);
      setEditingIdeaId(null);
    }
  }, [ideas, selectedIdeaId]);

  const filteredIdeas = useMemo(() => {
    return ideas.filter((idea) => {
      const matchesStatus = statusFilter === "ALL" || idea.status === statusFilter;
      const matchesType = typeFilter === "ALL" || idea.type === typeFilter;
      return matchesStatus && matchesType;
    });
  }, [ideas, statusFilter, typeFilter]);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const input = normalizeInput(form);
    if (!input.title) {
      setError("Informe um titulo para a ideia.");
      return;
    }

    setSaving(true);
    setError(null);

    try {
      const createdIdea = await createIdea(workspaceId, input);
      setIdeas((current) => {
        const nextIdeas = [createdIdea, ...current];
        onIdeasChange?.(nextIdeas);
        return nextIdeas;
      });
      setSelectedIdea(createdIdea);
      setForm(EMPTY_FORM);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel criar a ideia.");
    } finally {
      setSaving(false);
    }
  }

  function startEditing(idea: Idea) {
    setSelectedIdea(idea);
    setEditingIdeaId(idea.id);
    setEditForm({
      title: idea.title,
      description: idea.description ?? "",
      type: idea.type,
      status: idea.status === "CONVERTED" ? "PLANNED" : idea.status,
    });
    setError(null);
  }

  async function handleUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!selectedIdea || editingIdeaId !== selectedIdea.id) {
      return;
    }

    const input = normalizeInput(editForm);
    if (!input.title) {
      setError("Informe um titulo para a ideia.");
      return;
    }

    setSaving(true);
    setError(null);

    try {
      const updatedIdea = await updateIdea(workspaceId, selectedIdea.id, input);
      applyIdeaUpdate(updatedIdea);
      setEditingIdeaId(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel salvar a ideia.");
    } finally {
      setSaving(false);
    }
  }

  async function handleConvert(idea: Idea) {
    const confirmed = window.confirm("Transformar esta ideia em um projeto?");
    if (!confirmed) {
      return;
    }

    setConvertingIdeaId(idea.id);
    setError(null);

    try {
      const result = await convertIdeaToProject(workspaceId, idea.id);
      applyIdeaUpdate(result.idea);
      onProjectCreated(result.project);
      setSelectedIdea(result.idea);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel transformar a ideia em projeto.");
    } finally {
      setConvertingIdeaId(null);
    }
  }

  function applyIdeaUpdate(updatedIdea: Idea) {
    setIdeas((current) => {
      const nextIdeas = current
        .map((idea) => (idea.id === updatedIdea.id ? updatedIdea : idea))
        .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt));
      onIdeasChange?.(nextIdeas);
      return nextIdeas;
    });
    setSelectedIdea((current) => (current?.id === updatedIdea.id ? updatedIdea : current));
  }

  function findConvertedProject(idea: Idea) {
    if (!idea.convertedProjectId) {
      return null;
    }
    return projects.find((project) => project.id === idea.convertedProjectId) ?? null;
  }

  return (
    <section className="grid min-w-0 gap-5 xl:grid-cols-[minmax(280px,360px)_minmax(0,1fr)]">
      <div className="grid min-w-0 gap-4">
        <form className="grid gap-3 rounded-md border border-border bg-card p-4 shadow-sm" onSubmit={handleCreate}>
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold text-card-foreground">Nova ideia</h2>
            <Plus className="h-5 w-5 text-muted-foreground" aria-hidden="true" />
          </div>
          <p className="rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900">
            Nao armazene senhas, tokens ou chaves em ideias.
          </p>
          <select
            className="rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={form.type}
            onChange={(event) => setForm((current) => ({ ...current, type: event.target.value as IdeaType }))}
            aria-label="Tipo"
          >
            {TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <input
            className="rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={form.title}
            onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
            maxLength={180}
            placeholder="Titulo"
          />
          <textarea
            className="min-h-28 rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={form.description}
            onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            maxLength={10000}
            placeholder="Descricao"
          />
          <button
            type="submit"
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-60"
            disabled={saving}
          >
            {saving ? "Salvando..." : "Adicionar"}
          </button>
        </form>

        <FilterButtons
          label="Filtros por status"
          options={STATUS_FILTERS}
          value={statusFilter}
          onChange={setStatusFilter}
        />
        <FilterButtons label="Filtros por tipo" options={TYPE_FILTERS} value={typeFilter} onChange={setTypeFilter} />
      </div>

      <div className="grid min-w-0 gap-4">
        {error ? (
          <p className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {error}
          </p>
        ) : null}

        {loading ? (
          <div className="rounded-md border border-border bg-card px-4 py-6 text-sm text-muted-foreground">
            Carregando ideias...
          </div>
        ) : filteredIdeas.length === 0 ? (
          <div className="rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground">
            <p className="font-medium text-foreground">Nenhuma ideia registrada.</p>
            <p className="mt-1">Use o formulario ao lado para guardar uma oportunidade ou melhoria.</p>
          </div>
        ) : (
          <div className="grid min-w-0 gap-3">
            {filteredIdeas.map((idea) => {
              const convertedProject = findConvertedProject(idea);
              return (
                <button
                  key={idea.id}
                  type="button"
                  className="grid min-w-0 gap-2 rounded-md border border-border bg-card p-4 text-left shadow-sm transition-colors hover:bg-accent"
                  onClick={() => {
                    setSelectedIdea(idea);
                    setEditingIdeaId(null);
                  }}
                >
                  <span className="flex min-w-0 flex-wrap items-center gap-2">
                    <span className="rounded-full border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground">
                      {getTypeLabel(idea.type)}
                    </span>
                    <span className="rounded-full border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground">
                      {getStatusLabel(idea.status)}
                    </span>
                    <span className="min-w-0 break-words text-base font-semibold text-card-foreground">
                      {idea.title}
                    </span>
                  </span>
                  <span className="max-h-10 overflow-hidden text-sm text-muted-foreground">
                    {idea.description || "Sem descricao cadastrada."}
                  </span>
                  <span className="flex flex-wrap gap-2 text-xs text-muted-foreground">
                    <span>atualizada em {formatDate(idea.updatedAt)}</span>
                    {idea.status === "CONVERTED" ? (
                      <span>
                        Transformada em projeto{convertedProject ? `: ${convertedProject.name}` : ""}
                      </span>
                    ) : null}
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {selectedIdea ? (
        <div
          className="fixed inset-0 z-50 flex items-end bg-foreground/30 px-3 py-4 backdrop-blur-sm sm:items-center sm:justify-center sm:px-6"
          role="dialog"
          aria-modal="true"
          aria-labelledby="idea-details-title"
        >
          <div className="flex max-h-[92vh] w-full max-w-3xl flex-col overflow-hidden rounded-lg border border-border bg-card shadow-xl">
            <div className="flex items-start justify-between gap-3 border-b border-border bg-card/95 p-4 backdrop-blur">
              <div className="min-w-0">
                <p className="text-xs font-medium uppercase text-muted-foreground">
                  {getTypeLabel(selectedIdea.type)} / {getStatusLabel(selectedIdea.status)}
                </p>
                <h2 id="idea-details-title" className="mt-1 break-words text-lg font-semibold text-foreground">
                  {selectedIdea.title}
                </h2>
              </div>
              <div className="flex shrink-0 gap-2">
                {selectedIdea.status !== "CONVERTED" ? (
                  <button
                    type="button"
                    className="rounded-md border border-border p-2 text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                    onClick={() => startEditing(selectedIdea)}
                    aria-label="Editar ideia"
                    title="Editar ideia"
                  >
                    <PencilSimple className="h-4 w-4" aria-hidden="true" />
                  </button>
                ) : null}
                <button
                  type="button"
                  className="rounded-md border border-border p-2 text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                  onClick={() => {
                    setSelectedIdea(null);
                    setEditingIdeaId(null);
                  }}
                  aria-label="Fechar"
                  title="Fechar"
                >
                  <X className="h-4 w-4" aria-hidden="true" />
                </button>
              </div>
            </div>

            <div className="min-h-0 overflow-y-auto p-4 sm:p-5">
              {editingIdeaId === selectedIdea.id ? (
                <form className="grid gap-3" onSubmit={handleUpdate}>
                  <select
                    className="rounded-md border border-input bg-background px-3 py-2 text-sm"
                    value={editForm.type}
                    onChange={(event) => setEditForm((current) => ({ ...current, type: event.target.value as IdeaType }))}
                  >
                    {TYPE_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                  <select
                    className="rounded-md border border-input bg-background px-3 py-2 text-sm"
                    value={editForm.status}
                    onChange={(event) =>
                      setEditForm((current) => ({ ...current, status: event.target.value as EditableStatus }))
                    }
                  >
                    {STATUS_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                  <input
                    className="rounded-md border border-input bg-background px-3 py-2 text-sm"
                    value={editForm.title}
                    onChange={(event) => setEditForm((current) => ({ ...current, title: event.target.value }))}
                    maxLength={180}
                  />
                  <textarea
                    className="min-h-48 rounded-md border border-input bg-background px-3 py-2 text-sm"
                    value={editForm.description}
                    onChange={(event) => setEditForm((current) => ({ ...current, description: event.target.value }))}
                    maxLength={10000}
                  />
                  <div className="flex justify-end gap-2">
                    <button
                      type="button"
                      className="rounded-md border border-border px-4 py-2 text-sm font-medium"
                      onClick={() => setEditingIdeaId(null)}
                    >
                      Cancelar
                    </button>
                    <button
                      type="submit"
                      className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-60"
                      disabled={saving}
                    >
                      {saving ? "Salvando..." : "Salvar"}
                    </button>
                  </div>
                </form>
              ) : (
                <div className="grid gap-4">
                  {selectedIdea.description ? (
                    <p className="whitespace-pre-wrap text-sm text-muted-foreground">{selectedIdea.description}</p>
                  ) : (
                    <p className="text-sm text-muted-foreground">Sem descricao cadastrada.</p>
                  )}

                  {selectedIdea.status === "CONVERTED" ? (
                    <ConvertedProjectAction
                      idea={selectedIdea}
                      project={findConvertedProject(selectedIdea)}
                      onOpenProject={onOpenProject}
                    />
                  ) : (
                    <button
                      type="button"
                      className="w-fit rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-60"
                      onClick={() => void handleConvert(selectedIdea)}
                      disabled={convertingIdeaId === selectedIdea.id}
                    >
                      {convertingIdeaId === selectedIdea.id ? "Transformando..." : "Transformar em projeto"}
                    </button>
                  )}

                  <dl className="grid gap-2 border-t border-border pt-4 text-xs text-muted-foreground sm:grid-cols-2">
                    <div>
                      <dt className="font-medium text-foreground">Autor</dt>
                      <dd>{selectedIdea.createdByName}</dd>
                    </div>
                    <div>
                      <dt className="font-medium text-foreground">Criada em</dt>
                      <dd>{formatDate(selectedIdea.createdAt)}</dd>
                    </div>
                    <div>
                      <dt className="font-medium text-foreground">Atualizada em</dt>
                      <dd>{formatDate(selectedIdea.updatedAt)}</dd>
                    </div>
                  </dl>
                </div>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function FilterButtons<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Array<{ value: T; label: string }>;
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div className="flex min-w-0 gap-2 overflow-x-auto pb-1" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className={`shrink-0 rounded-md border px-3 py-1.5 text-xs font-medium transition-colors ${
            value === option.value
              ? "border-primary bg-primary text-primary-foreground"
              : "border-border bg-background text-muted-foreground hover:bg-accent hover:text-accent-foreground"
          }`}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

function ConvertedProjectAction({
  idea,
  project,
  onOpenProject,
}: {
  idea: Idea;
  project: Project | null;
  onOpenProject: (project: Project) => void;
}) {
  if (!project) {
    return (
      <p className="rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground">
        Transformada em projeto{idea.convertedProjectName ? `: ${idea.convertedProjectName}` : ""}.
      </p>
    );
  }

  return (
    <button
      type="button"
      className="w-fit rounded-md border border-border px-4 py-2 text-sm font-medium hover:bg-accent"
      onClick={() => onOpenProject(project)}
    >
      Abrir projeto: {project.name}
    </button>
  );
}

function normalizeInput(input: IdeaInput): IdeaInput {
  return {
    title: input.title.trim(),
    description: normalizeOptional(input.description),
    type: input.type,
    status: input.status,
  };
}

function normalizeOptional(value: string | undefined) {
  const normalized = value?.trim();
  return normalized ? normalized : undefined;
}

function getTypeLabel(type: IdeaType) {
  return TYPE_OPTIONS.find((option) => option.value === type)?.label ?? type;
}

function getStatusLabel(status: IdeaStatus) {
  if (status === "CONVERTED") {
    return "Convertida";
  }
  return STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
