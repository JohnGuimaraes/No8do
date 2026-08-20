import { FormEvent, useEffect, useMemo, useState } from "react";
import { PencilSimple, Plus, X } from "@phosphor-icons/react";
import {
  createLibraryItem,
  listLibraryItems,
  updateLibraryItem,
  type LibraryItem,
  type LibraryItemInput,
  type LibraryItemType,
} from "@/library/libraryApi";

type FilterType = LibraryItemType | "ALL";

const TYPE_OPTIONS: Array<{ value: LibraryItemType; label: string }> = [
  { value: "LINK", label: "Link" },
  { value: "TOOL", label: "Ferramenta" },
  { value: "COMMAND", label: "Comando" },
  { value: "SNIPPET", label: "Snippet" },
  { value: "REFERENCE", label: "Referencia" },
  { value: "TEMPLATE", label: "Template" },
  { value: "NOTE", label: "Nota" },
];

const FILTER_OPTIONS: Array<{ value: FilterType; label: string }> = [
  { value: "ALL", label: "Todos" },
  { value: "LINK", label: "Links" },
  { value: "TOOL", label: "Ferramentas" },
  { value: "COMMAND", label: "Comandos" },
  { value: "SNIPPET", label: "Snippets" },
  { value: "REFERENCE", label: "Referencias" },
  { value: "TEMPLATE", label: "Templates" },
  { value: "NOTE", label: "Notas" },
];

const EMPTY_FORM: LibraryItemInput = {
  type: "LINK",
  title: "",
  description: "",
  content: "",
  url: "",
};

export function LibraryPanel({
  workspaceId,
  selectedItemId,
  onItemsChange,
}: {
  workspaceId: string;
  selectedItemId?: string | null;
  onItemsChange?: (items: LibraryItem[]) => void;
}) {
  const [items, setItems] = useState<LibraryItem[]>([]);
  const [filter, setFilter] = useState<FilterType>("ALL");
  const [form, setForm] = useState<LibraryItemInput>(EMPTY_FORM);
  const [editForm, setEditForm] = useState<LibraryItemInput>(EMPTY_FORM);
  const [selectedItem, setSelectedItem] = useState<LibraryItem | null>(null);
  const [editingItemId, setEditingItemId] = useState<string | null>(null);
  const [createFormOpen, setCreateFormOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadItems() {
      setLoading(true);
      setError(null);
      setSelectedItem(null);
      setEditingItemId(null);

      try {
        const nextItems = await listLibraryItems(workspaceId);
        if (!cancelled) {
          setItems(nextItems);
          onItemsChange?.(nextItems);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Nao foi possivel carregar a biblioteca.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadItems();

    return () => {
      cancelled = true;
    };
  }, [onItemsChange, workspaceId]);

  useEffect(() => {
    if (!selectedItemId) {
      return;
    }
    const item = items.find((currentItem) => currentItem.id === selectedItemId);
    if (item) {
      setSelectedItem(item);
      setEditingItemId(null);
    }
  }, [items, selectedItemId]);

  const filteredItems = useMemo(() => {
    if (filter === "ALL") {
      return items;
    }
    return items.filter((item) => item.type === filter);
  }, [filter, items]);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const input = normalizeInput(form);
    if (!input.title) {
      setError("Informe um titulo para o item.");
      return;
    }

    setSaving(true);
    setError(null);

    try {
      const createdItem = await createLibraryItem(workspaceId, input);
      setItems((current) => {
        const nextItems = [createdItem, ...current];
        onItemsChange?.(nextItems);
        return nextItems;
      });
      setSelectedItem(createdItem);
      setForm(EMPTY_FORM);
      setCreateFormOpen(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel criar o item.");
    } finally {
      setSaving(false);
    }
  }

  function handleCancelCreate() {
    setForm(EMPTY_FORM);
    setError(null);
    setCreateFormOpen(false);
  }

  function startEditing(item: LibraryItem) {
    setSelectedItem(item);
    setEditingItemId(item.id);
    setEditForm({
      type: item.type,
      title: item.title,
      description: item.description ?? "",
      content: item.content ?? "",
      url: item.url ?? "",
    });
    setError(null);
  }

  async function handleUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!selectedItem || editingItemId !== selectedItem.id) {
      return;
    }

    const input = normalizeInput(editForm);
    if (!input.title) {
      setError("Informe um titulo para o item.");
      return;
    }

    setSaving(true);
    setError(null);

    try {
      const updatedItem = await updateLibraryItem(workspaceId, selectedItem.id, input);
      setItems((current) => {
        const nextItems = current
          .map((item) => (item.id === updatedItem.id ? updatedItem : item))
          .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt));
        onItemsChange?.(nextItems);
        return nextItems;
      });
      setSelectedItem(updatedItem);
      setEditingItemId(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel salvar o item.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="grid min-w-0 gap-5 xl:grid-cols-[minmax(280px,360px)_minmax(0,1fr)]">
      <div className="grid min-w-0 gap-4">
        <div className="flex min-w-0 flex-wrap items-center justify-between gap-3">
          <div className="min-w-0">
            <h2 className="text-base font-semibold text-foreground">Biblioteca</h2>
            <p className="mt-1 text-sm text-muted-foreground">Conhecimento reutilizavel do workspace.</p>
          </div>
          {!createFormOpen ? (
            <button
              type="button"
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
              onClick={() => setCreateFormOpen(true)}
            >
              <Plus className="h-4 w-4" aria-hidden="true" />
              Novo item
            </button>
          ) : null}
        </div>

        {createFormOpen ? (
        <form className="grid gap-3 rounded-md border border-border bg-card p-4 shadow-sm" onSubmit={handleCreate}>
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold text-card-foreground">Novo item</h2>
            <Plus className="h-5 w-5 text-muted-foreground" aria-hidden="true" />
          </div>
          <p className="rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900">
            Nao armazene senhas, tokens ou chaves aqui. Use o Cofre de um projeto.
          </p>
          <select
            className="rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={form.type}
            onChange={(event) => setForm((current) => ({ ...current, type: event.target.value as LibraryItemType }))}
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
            className="min-h-20 rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={form.description}
            onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
            maxLength={2000}
            placeholder="Descricao curta"
          />
          <input
            className="rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={form.url}
            onChange={(event) => setForm((current) => ({ ...current, url: event.target.value }))}
            maxLength={2000}
            placeholder="URL"
          />
          <textarea
            className="min-h-28 rounded-md border border-input bg-background px-3 py-2 font-mono text-sm"
            value={form.content}
            onChange={(event) => setForm((current) => ({ ...current, content: event.target.value }))}
            maxLength={20000}
            placeholder="Conteudo, comando ou snippet"
          />
          <div className="flex flex-wrap justify-end gap-2">
            <button
              type="button"
              className="rounded-md border border-border px-4 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-60"
              disabled={saving}
              onClick={handleCancelCreate}
            >
              Cancelar
            </button>
            <button
              type="submit"
              className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-60"
              disabled={saving}
            >
              {saving ? "Salvando..." : "Adicionar"}
            </button>
          </div>
        </form>
        ) : null}

        <div className="flex min-w-0 gap-2 overflow-x-auto pb-1" aria-label="Filtros da biblioteca">
          {FILTER_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={`shrink-0 rounded-md border px-3 py-1.5 text-xs font-medium transition-colors ${
                filter === option.value
                  ? "border-primary bg-primary text-primary-foreground"
                  : "border-border bg-background text-muted-foreground hover:bg-accent hover:text-accent-foreground"
              }`}
              onClick={() => setFilter(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      <div className="grid min-w-0 gap-4">
        {error ? (
          <p className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {error}
          </p>
        ) : null}

        {loading ? (
          <div className="rounded-md border border-border bg-card px-4 py-6 text-sm text-muted-foreground">
            Carregando biblioteca...
          </div>
        ) : filteredItems.length === 0 ? (
          <div className="rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground">
            <p className="font-medium text-foreground">Nenhum item na biblioteca.</p>
            <p className="mt-1">Use Novo item para adicionar conhecimento reutilizavel.</p>
          </div>
        ) : (
          <div className="grid min-w-0 gap-3">
            {filteredItems.map((item) => (
              <button
                key={item.id}
                type="button"
                className="grid min-w-0 gap-2 rounded-md border border-border bg-card p-4 text-left shadow-sm transition-colors hover:bg-accent"
                onClick={() => {
                  setSelectedItem(item);
                  setEditingItemId(null);
                }}
              >
                <span className="flex min-w-0 flex-wrap items-center gap-2">
                  <span className="rounded-full border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground">
                    {getTypeLabel(item.type)}
                  </span>
                  <span className="min-w-0 break-words text-base font-semibold text-card-foreground">
                    {item.title}
                  </span>
                </span>
                <span className="max-h-10 overflow-hidden text-sm text-muted-foreground">
                  {item.description || item.content || item.url || "Sem descricao cadastrada."}
                </span>
                <span className="text-xs text-muted-foreground">atualizado em {formatDate(item.updatedAt)}</span>
              </button>
            ))}
          </div>
        )}
      </div>

      {selectedItem ? (
        <div
          className="fixed inset-0 z-50 flex items-end bg-foreground/30 px-3 py-4 backdrop-blur-sm sm:items-center sm:justify-center sm:px-6"
          role="dialog"
          aria-modal="true"
          aria-labelledby="library-item-details-title"
        >
          <div className="flex max-h-[92vh] w-full max-w-3xl flex-col overflow-hidden rounded-lg border border-border bg-card shadow-xl">
            <div className="flex items-start justify-between gap-3 border-b border-border bg-card/95 p-4 backdrop-blur">
              <div className="min-w-0">
                <p className="text-xs font-medium uppercase text-muted-foreground">{getTypeLabel(selectedItem.type)}</p>
                <h2 id="library-item-details-title" className="mt-1 break-words text-lg font-semibold text-foreground">
                  {selectedItem.title}
                </h2>
              </div>
              <div className="flex shrink-0 gap-2">
                <button
                  type="button"
                  className="rounded-md border border-border p-2 text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                  onClick={() => startEditing(selectedItem)}
                  aria-label="Editar item"
                  title="Editar item"
                >
                  <PencilSimple className="h-4 w-4" aria-hidden="true" />
                </button>
                <button
                  type="button"
                  className="rounded-md border border-border p-2 text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                  onClick={() => {
                    setSelectedItem(null);
                    setEditingItemId(null);
                  }}
                  aria-label="Fechar"
                  title="Fechar"
                >
                  <X className="h-4 w-4" aria-hidden="true" />
                </button>
              </div>
            </div>

            <div className="min-h-0 overflow-y-auto p-4 sm:p-5">
              {editingItemId === selectedItem.id ? (
                <form className="grid gap-3" onSubmit={handleUpdate}>
                  <select
                    className="rounded-md border border-input bg-background px-3 py-2 text-sm"
                    value={editForm.type}
                    onChange={(event) =>
                      setEditForm((current) => ({ ...current, type: event.target.value as LibraryItemType }))
                    }
                  >
                    {TYPE_OPTIONS.map((option) => (
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
                    className="min-h-24 rounded-md border border-input bg-background px-3 py-2 text-sm"
                    value={editForm.description}
                    onChange={(event) => setEditForm((current) => ({ ...current, description: event.target.value }))}
                    maxLength={2000}
                  />
                  <input
                    className="rounded-md border border-input bg-background px-3 py-2 text-sm"
                    value={editForm.url}
                    onChange={(event) => setEditForm((current) => ({ ...current, url: event.target.value }))}
                    maxLength={2000}
                  />
                  <textarea
                    className="min-h-48 rounded-md border border-input bg-background px-3 py-2 font-mono text-sm"
                    value={editForm.content}
                    onChange={(event) => setEditForm((current) => ({ ...current, content: event.target.value }))}
                    maxLength={20000}
                  />
                  <div className="flex justify-end gap-2">
                    <button
                      type="button"
                      className="rounded-md border border-border px-4 py-2 text-sm font-medium"
                      onClick={() => setEditingItemId(null)}
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
                  {selectedItem.description ? (
                    <p className="whitespace-pre-wrap text-sm text-muted-foreground">{selectedItem.description}</p>
                  ) : null}
                  {selectedItem.url ? (
                    isHttpUrl(selectedItem.url) ? (
                      <a
                        className="break-all text-sm font-medium text-primary underline-offset-4 hover:underline"
                        href={selectedItem.url}
                        target="_blank"
                        rel="noreferrer noopener"
                      >
                        {selectedItem.url}
                      </a>
                    ) : (
                      <p className="break-all text-sm text-muted-foreground">{selectedItem.url}</p>
                    )
                  ) : null}
                  {selectedItem.content ? (
                    isCodeType(selectedItem.type) ? (
                      <pre className="max-h-96 overflow-auto rounded-md border border-border bg-muted p-4 text-sm">
                        <code>{selectedItem.content}</code>
                      </pre>
                    ) : (
                      <p className="whitespace-pre-wrap text-sm text-foreground">{selectedItem.content}</p>
                    )
                  ) : null}
                  <dl className="grid gap-2 border-t border-border pt-4 text-xs text-muted-foreground sm:grid-cols-2">
                    <div>
                      <dt className="font-medium text-foreground">Autor</dt>
                      <dd>{selectedItem.createdByName}</dd>
                    </div>
                    <div>
                      <dt className="font-medium text-foreground">Criado em</dt>
                      <dd>{formatDate(selectedItem.createdAt)}</dd>
                    </div>
                    <div>
                      <dt className="font-medium text-foreground">Atualizado em</dt>
                      <dd>{formatDate(selectedItem.updatedAt)}</dd>
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

function normalizeInput(input: LibraryItemInput): LibraryItemInput {
  return {
    type: input.type,
    title: input.title.trim(),
    description: normalizeOptional(input.description),
    content: normalizeOptional(input.content),
    url: normalizeOptional(input.url),
  };
}

function normalizeOptional(value: string | undefined) {
  const normalized = value?.trim();
  return normalized ? normalized : undefined;
}

function getTypeLabel(type: LibraryItemType) {
  return TYPE_OPTIONS.find((option) => option.value === type)?.label ?? type;
}

function isCodeType(type: LibraryItemType) {
  return type === "COMMAND" || type === "SNIPPET";
}

function isHttpUrl(value: string) {
  return value.startsWith("http://") || value.startsWith("https://");
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
