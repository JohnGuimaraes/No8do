import { FormEvent, useEffect, useMemo, useState, type ChangeEvent } from "react";
import { BookOpen, Code, FileText, Fingerprint, FlowArrow, GearSix, HardDrives, ImageSquare, LinkSimple, MagnifyingGlass, PencilSimple, Plus, SealCheck, Trash, X } from "@phosphor-icons/react";
import { ConfirmationDialog } from "@/components/ConfirmationDialog";
import { ToastNotification } from "@/components/ToastNotification";
import { ViewModeToggle, type ViewMode } from "@/components/ViewModeToggle";
import { MarkdownEditor } from "@/components/MarkdownEditor";
import { MarkdownRenderer } from "@/components/MarkdownRenderer";
import { LibraryListSection } from "@/library/LibraryListSection";
import { LibraryItemCard } from "@/library/LibraryItemCard";
import { AcervoEditorialBackground } from "@/library/AcervoEditorialBackground";
import { LibrarySection } from "@/library/LibrarySection";
import { GithubRepositoriesPanel } from "@/github/GithubRepositoriesPanel";
import { listGithubAppRepositories, type GithubAppRepository } from "@/github/githubAppApi";
import { getWorkspaceGithubAppStatus } from "@/workspaces/workspaceApi";
import {
  archiveLibraryItem,
  createLibraryItem,
  deleteLibraryItem,
  listLibraryItems,
  restoreLibraryItem,
  updateLibraryItem,
  type LibraryItem,
  type LibraryItemInput,
  type LibraryItemType,
} from "@/library/libraryApi";

type AcervoFilter = "ALL" | "IDENTITY" | "DOCUMENTS" | "NOTES" | "CODE" | "DECISIONS" | "PROCESS" | "TOOLS" | "REFERENCES" | "LINKS" | "INFRASTRUCTURE" | "MATERIALS";

const TYPE_OPTIONS: Array<{ value: LibraryItemType; label: string }> = [
  { value: "DOCUMENT", label: "Documento" },
  { value: "IDENTITY", label: "Identidade" },
  { value: "LINK", label: "Link" },
  { value: "TOOL", label: "Ferramenta" },
  { value: "COMMAND", label: "Comando" },
  { value: "SNIPPET", label: "Snippet" },
  { value: "REFERENCE", label: "Referencia" },
  { value: "TEMPLATE", label: "Template" },
  { value: "NOTE", label: "Nota" },
  { value: "DECISION", label: "Decisão" },
  { value: "PROCESS", label: "Processo" },
  { value: "INFRASTRUCTURE", label: "Infraestrutura" },
  { value: "MATERIAL", label: "Material" },
];

const FILTER_OPTIONS: Array<{ value: AcervoFilter; label: string }> = [
  { value: "ALL", label: "Todos" },
  { value: "IDENTITY", label: "Identidade" },
  { value: "DOCUMENTS", label: "Documentos" },
  { value: "NOTES", label: "Notas" },
  { value: "CODE", label: "Código" },
  { value: "DECISIONS", label: "Decisões" },
  { value: "PROCESS", label: "Processos" },
  { value: "TOOLS", label: "Ferramentas" },
  { value: "REFERENCES", label: "Referências" },
  { value: "LINKS", label: "Links" },
  { value: "INFRASTRUCTURE", label: "Infraestrutura" },
  { value: "MATERIALS", label: "Materiais" },
];

const SECTION_TYPES: LibraryItemType[] = ["DOCUMENT", "IDENTITY", "LINK", "TOOL", "COMMAND", "SNIPPET", "REFERENCE", "TEMPLATE", "NOTE", "DECISION", "PROCESS", "INFRASTRUCTURE", "MATERIAL"];
const HOME_SECTIONS: Array<{ filter: Exclude<AcervoFilter, "ALL">; title: string }> = [
  { filter: "IDENTITY", title: "Identidade" },
  { filter: "DOCUMENTS", title: "Documentos" },
  { filter: "NOTES", title: "Notas" },
  { filter: "DECISIONS", title: "Decisões" },
  { filter: "PROCESS", title: "Processos" },
  { filter: "TOOLS", title: "Ferramentas" },
  { filter: "REFERENCES", title: "Referências" },
  { filter: "LINKS", title: "Links" },
  { filter: "INFRASTRUCTURE", title: "Infraestrutura" },
  { filter: "MATERIALS", title: "Materiais" },
];

const EMPTY_FORM: LibraryItemInput = {
  type: "LINK",
  title: "",
  description: "",
  content: "",
  url: "",
};

type FormField = "title" | "description" | "url" | "content";
type FormConfig = { label: string; submitLabel: string; fields: FormField[]; required: FormField[]; placeholders: Partial<Record<FormField, string>>; securityNotice?: boolean };
type CreatableLibraryItemType = Exclude<LibraryItemType, "TEMPLATE">;
type TypeChoice = CreatableLibraryItemType | "CODE";

const FORM_CONFIG: Record<LibraryItemType, FormConfig> = {
  NOTE: { label: "Nota", submitLabel: "Criar nota", fields: ["title", "content", "url"], required: ["title", "content"], placeholders: { title: "Título", content: "Escreva a nota...", url: "Link relacionado (opcional)" } },
  DOCUMENT: { label: "Documento", submitLabel: "Criar documento", fields: ["title", "description", "content", "url"], required: ["title"], placeholders: { title: "Título", description: "Resumo (opcional)", content: "Conteúdo (opcional)", url: "Fonte ou URL (opcional)" } },
  LINK: { label: "Link", submitLabel: "Adicionar link", fields: ["title", "url", "description", "content"], required: ["title", "url"], placeholders: { title: "Título", url: "https://", description: "Descrição (opcional)", content: "Notas (opcional)" } },
  REFERENCE: { label: "Referência", submitLabel: "Adicionar referência", fields: ["title", "url", "description", "content"], required: ["title", "url"], placeholders: { title: "Título", url: "URL ou fonte", description: "Por que guardar?", content: "Notas (opcional)" } },
  TOOL: { label: "Ferramenta", submitLabel: "Adicionar ferramenta", fields: ["title", "description", "url", "content"], required: ["title"], placeholders: { title: "Nome", description: "Finalidade", url: "URL ou host (opcional)", content: "Notas de uso (opcional)" }, securityNotice: true },
  INFRASTRUCTURE: { label: "Infraestrutura", submitLabel: "Adicionar infraestrutura", fields: ["title", "description", "url", "content"], required: ["title"], placeholders: { title: "Nome", description: "Descrição", url: "Host, domínio ou URL (opcional)", content: "Notas técnicas (opcional)" }, securityNotice: true },
  DECISION: { label: "Decisão", submitLabel: "Registrar decisão", fields: ["title", "description", "content", "url"], required: ["title", "content"], placeholders: { title: "Título", description: "Resumo (opcional)", content: "Contexto, decisão tomada e justificativa...", url: "Referência (opcional)" } },
  PROCESS: { label: "Processo", submitLabel: "Criar processo", fields: ["title", "description", "content", "url"], required: ["title", "content"], placeholders: { title: "Nome", description: "Objetivo (opcional)", content: "1. ...\n2. ...\n3. ...", url: "Referência (opcional)" } },
  SNIPPET: { label: "Snippet", submitLabel: "Criar snippet", fields: ["title", "content", "description"], required: ["title", "content"], placeholders: { title: "Título", content: "Código", description: "Descrição (opcional)" } },
  COMMAND: { label: "Comando", submitLabel: "Criar comando", fields: ["title", "content", "description"], required: ["title", "content"], placeholders: { title: "Título", content: "Comando", description: "Descrição (opcional)" } },
  IDENTITY: { label: "Identidade", submitLabel: "Adicionar identidade", fields: ["title", "description", "url", "content"], required: ["title"], placeholders: { title: "Nome", description: "Descrição ou uso", url: "URL do asset (opcional)", content: "Notas (opcional)" } },
  MATERIAL: { label: "Material", submitLabel: "Adicionar material", fields: ["title", "description", "url", "content"], required: ["title"], placeholders: { title: "Nome", description: "Descrição", url: "URL (opcional)", content: "Notas (opcional)" } },
  TEMPLATE: { label: "Template", submitLabel: "Salvar template", fields: ["title", "description", "content", "url"], required: ["title"], placeholders: { title: "Título", description: "Resumo (opcional)", content: "Conteúdo (opcional)", url: "URL (opcional)" } },
};

const NEW_TYPE_OPTIONS: ReadonlyArray<readonly [TypeChoice, string, typeof FileText]> = [
  ["NOTE", "Nota", FileText], ["DOCUMENT", "Documento", FileText], ["LINK", "Link", LinkSimple], ["REFERENCE", "Referência", BookOpen], ["TOOL", "Ferramenta", GearSix], ["CODE", "Código", Code], ["DECISION", "Decisão", SealCheck], ["PROCESS", "Processo", FlowArrow], ["IDENTITY", "Identidade", Fingerprint], ["INFRASTRUCTURE", "Infraestrutura", HardDrives], ["MATERIAL", "Material", ImageSquare],
] as const;

export function LibraryPanel({
  workspaceId,
  canWrite,
  selectedItemId,
  onItemsChange,
  onItemRestored,
}: {
  workspaceId: string;
  canWrite: boolean;
  selectedItemId?: string | null;
  onItemsChange?: (items: LibraryItem[]) => void;
  onItemRestored?: (item: LibraryItem) => void;
}) {
  const [items, setItems] = useState<LibraryItem[]>([]);
  const [filter, setFilter] = useState<AcervoFilter>("ALL");
  const [searchTerm, setSearchTerm] = useState("");
  const [codeView, setCodeView] = useState<"items" | "repositories">("items");
  const [form, setForm] = useState<LibraryItemInput>(EMPTY_FORM);
  const [editForm, setEditForm] = useState<LibraryItemInput>(EMPTY_FORM);
  const [selectedItem, setSelectedItem] = useState<LibraryItem | null>(null);
  const [editingItemId, setEditingItemId] = useState<string | null>(null);
  const [createFormOpen, setCreateFormOpen] = useState(false);
  const [createType, setCreateType] = useState<LibraryItemType | null>(null);
  const [choosingCodeSubtype, setChoosingCodeSubtype] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>("visual");
  const [archivedMode, setArchivedMode] = useState(false);
  const [actionItemId, setActionItemId] = useState<string | null>(null);
  const [pendingDeletion, setPendingDeletion] = useState<LibraryItem | null>(null);
  const [toast, setToast] = useState<{ title: string; message: string } | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadItems() {
      setLoading(true);
      setError(null);
      setSelectedItem(null);
      setEditingItemId(null);

      try {
        const nextItems = await listLibraryItems(workspaceId, archivedMode);
        if (!cancelled) {
          setItems(nextItems);
          if (!archivedMode) onItemsChange?.(nextItems);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Não foi possível carregar o Acervo.");
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
  }, [archivedMode, onItemsChange, workspaceId]);

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
    return items.filter((item) => matchesAcervoFilter(item, filter) && matchesAcervoSearch(item, searchTerm));
  }, [filter, items, searchTerm]);
  const itemsByType = useMemo(() => {
    const grouped = SECTION_TYPES.reduce((result, type) => {
      result[type] = [];
      return result;
    }, {} as Record<LibraryItemType, LibraryItem[]>);
    for (const item of filteredItems) grouped[item.type].push(item);
    return grouped;
  }, [filteredItems]);
  const recentItems = useMemo(() => [...items].sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt)).slice(0, 4), [items]);

  useEffect(() => {
    if (filter !== "CODE") setCodeView("items");
  }, [filter]);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const input = normalizeInput(form);
    const validationError = validateLibraryInput(input);
    if (validationError) {
      setError(validationError);
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
    setCreateType(null);
    setChoosingCodeSubtype(false);
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
    const validationError = validateLibraryInput(input);
    if (validationError) {
      setError(validationError);
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

  function showToast(title: string, message: string) { setToast({ title, message }); window.setTimeout(() => setToast(null), 3000); }
  async function handleArchive(item: LibraryItem) { setActionItemId(item.id); setError(null); try { const archived = await archiveLibraryItem(workspaceId, item.id); setItems((current) => current.filter((value) => value.id !== archived.id)); onItemsChange?.(items.filter((value) => value.id !== archived.id)); setSelectedItem(null); showToast("Item arquivado", `${archived.title} foi movido para Arquivados.`); } catch (err) { setError(err instanceof Error ? err.message : "Nao foi possivel arquivar o item."); } finally { setActionItemId(null); } }
  async function handleRestore(item: LibraryItem) { setActionItemId(item.id); setError(null); try { const restored = await restoreLibraryItem(workspaceId, item.id); setItems((current) => current.filter((value) => value.id !== restored.id)); onItemRestored?.(restored); setSelectedItem(null); showToast("Item restaurado", `${restored.title} foi restaurado.`); } catch (err) { setError(err instanceof Error ? err.message : "Nao foi possivel restaurar o item."); } finally { setActionItemId(null); } }
  async function handleDelete() { const item = pendingDeletion; if (!item) return; setActionItemId(item.id); setError(null); try { await deleteLibraryItem(workspaceId, item.id); setItems((current) => current.filter((value) => value.id !== item.id)); if (!archivedMode) onItemsChange?.(items.filter((value) => value.id !== item.id)); setSelectedItem(null); setPendingDeletion(null); showToast("Item excluído", `${item.title} foi excluído permanentemente.`); } catch (err) { setError(err instanceof Error ? err.message : "Nao foi possivel excluir o item."); } finally { setActionItemId(null); } }

  return (
    <section className="acervo-panel grid min-w-0 gap-7">
      <AcervoEditorialBackground />
      <div className="grid min-w-0 gap-4">
        <div className="acervo-panel__heading flex min-w-0 flex-wrap items-center justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs font-medium uppercase tracking-[.1em] text-muted-foreground">Acervo</p>
            <h2 className="mt-1 text-xl font-semibold text-foreground">Acervo do workspace</h2>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">Memória, conhecimento e ativos compartilhados do workspace.</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
          <button type="button" className="rounded-md border border-border px-3 py-2 text-sm font-medium text-foreground hover:bg-accent" onClick={() => setArchivedMode((current) => !current)}>{archivedMode ? "Voltar ao Acervo" : "Arquivados"}</button>
          {filter !== "ALL" && !archivedMode ? <ViewModeToggle value={viewMode} onChange={setViewMode} /> : null}
          {canWrite && !createFormOpen && !archivedMode ? (
            <button
              type="button"
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
              onClick={() => { setForm(EMPTY_FORM); setCreateType(null); setChoosingCodeSubtype(false); setError(null); setCreateFormOpen(true); }}
            >
              <Plus className="h-4 w-4" aria-hidden="true" />
              Novo item
            </button>
          ) : null}
          </div>
        </div>

        {canWrite && createFormOpen && !archivedMode ? <div className="max-w-2xl rounded-md border border-border bg-card p-4 shadow-sm">{createType ? <LibraryItemForm title={`Novo ${FORM_CONFIG[createType].label.toLowerCase()}`} form={form} config={FORM_CONFIG[createType]} saving={saving} submitLabel={FORM_CONFIG[createType].submitLabel} onSubmit={handleCreate} onChange={setForm} onCancel={handleCancelCreate} onChangeType={() => { setForm(EMPTY_FORM); setCreateType(null); setChoosingCodeSubtype(false); }} /> : choosingCodeSubtype ? <TypeChooser title="Qual tipo de código?" options={[["SNIPPET", "Snippet", Code], ["COMMAND", "Comando", Code]]} onChoose={(type) => { if (type === "CODE") return; setForm({ ...EMPTY_FORM, type }); setCreateType(type); setChoosingCodeSubtype(false); }} onCancel={handleCancelCreate} /> : <TypeChooser title="O que você quer adicionar?" options={NEW_TYPE_OPTIONS} onChoose={(type) => { if (type === "CODE") { setChoosingCodeSubtype(true); return; } setForm({ ...EMPTY_FORM, type }); setCreateType(type); }} onCancel={handleCancelCreate} />}</div> : null}

        {!archivedMode ? <label className="relative block max-w-xl"><MagnifyingGlass className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" /><span className="sr-only">Buscar no Acervo</span><input value={searchTerm} onChange={(event) => setSearchTerm(event.target.value)} className="h-10 w-full rounded-md border border-input bg-background pl-9 pr-3 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-ring focus:ring-2 focus:ring-ring/25" placeholder="Buscar no Acervo" /></label> : null}

        {!archivedMode ? <div className="flex min-w-0 flex-wrap gap-2" aria-label="Categorias do Acervo">
          {FILTER_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={`shrink-0 rounded-md border px-3 py-1.5 text-xs font-medium ${
                filter === option.value
                  ? "border-primary bg-primary text-primary-foreground"
                  : "border-border bg-background text-muted-foreground hover:bg-accent hover:text-accent-foreground"
              }`}
              onClick={() => { setFilter(option.value); setSearchTerm(""); }}
            >
              {option.label}
            </button>
          ))}
        </div> : null}
      </div>

      <div className="grid min-w-0 gap-4">
        {error ? (
          <p className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {error}
          </p>
        ) : null}

        {!archivedMode && filter === "CODE" ? <div className="flex w-fit items-center gap-1 rounded-md border border-border bg-muted/55 p-1" role="tablist" aria-label="Código no Acervo"><button type="button" role="tab" aria-selected={codeView === "items"} onClick={() => setCodeView("items")} className={`rounded px-2.5 py-1.5 text-xs font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${codeView === "items" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}>Itens de código</button><button type="button" role="tab" aria-selected={codeView === "repositories"} onClick={() => setCodeView("repositories")} className={`rounded px-2.5 py-1.5 text-xs font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${codeView === "repositories" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}>Repositórios GitHub</button></div> : null}

        {loading ? (
          <div className="rounded-md border border-border bg-card px-4 py-6 text-sm text-muted-foreground">
            Carregando Acervo...
          </div>
        ) : !archivedMode && filter === "ALL" && !searchTerm.trim() ? <AcervoHome items={items} recentItems={recentItems} workspaceId={workspaceId} canWrite={canWrite} onShowMore={setFilter} onOpenRepositories={() => { setFilter("CODE"); setCodeView("repositories"); }} onOpen={(item) => { setSelectedItem(item); setEditingItemId(null); }} onArchive={(item) => void handleArchive(item)} onDelete={setPendingDeletion} /> : !archivedMode && filter === "CODE" && codeView === "repositories" ? <GithubRepositoriesPanel workspaceId={workspaceId} /> : (
          <>
          {filteredItems.length === 0 ? (
            <div className="rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground">
              <p className="font-medium text-foreground">{archivedMode ? "Nenhum item arquivado." : filter === "ALL" ? "Nenhum conteúdo no Acervo." : `Nenhum conteúdo em ${FILTER_OPTIONS.find((option) => option.value === filter)?.label.toLowerCase()}.`}</p>
              {!archivedMode ? <p className="mt-1">Use Novo item para preservar conhecimento compartilhado.</p> : null}
            </div>
          ) : archivedMode ? <ArchivedLibraryItems items={filteredItems} canWrite={canWrite} actionId={actionItemId} onRestore={(item) => void handleRestore(item)} onDelete={setPendingDeletion} /> : <div className="grid min-w-0 gap-10">{SECTION_TYPES.filter((type) => itemsByType[type].length > 0).map((type) => viewMode === "visual" ? <LibrarySection key={type} type={type} items={itemsByType[type]} canWrite={canWrite} onOpen={(item) => { setSelectedItem(item); setEditingItemId(null); }} onArchive={(item) => void handleArchive(item)} onDelete={setPendingDeletion} /> : <LibraryListSection key={type} type={type} items={itemsByType[type]} canWrite={canWrite} onOpen={(item) => { setSelectedItem(item); setEditingItemId(null); }} onArchive={(item) => void handleArchive(item)} onDelete={setPendingDeletion} />)}</div>}
          </>
        )}
      </div>

      {selectedItem ? (
        <div
          className="fixed inset-0 z-50 flex items-end bg-foreground/40 px-3 py-4 sm:items-center sm:justify-center sm:px-6"
          role="dialog"
          aria-modal="true"
          aria-labelledby="library-item-details-title"
        >
          <div className="acervo-item-dialog flex max-h-[92vh] w-full max-w-3xl flex-col overflow-hidden rounded-lg border border-border bg-card shadow-xl" data-acervo-type={selectedItem.type}>
            <div className="acervo-item-dialog__header flex items-start justify-between gap-3 border-b border-border bg-card p-4">
              <div className="min-w-0">
                <p className="text-xs font-medium uppercase text-muted-foreground">{getTypeLabel(selectedItem.type)}</p>
                <h2 id="library-item-details-title" className="mt-1 break-words text-lg font-semibold text-foreground">
                  {selectedItem.title}
                </h2>
              </div>
              <div className="flex shrink-0 gap-2">
                {canWrite && !archivedMode ? <button
                  type="button"
                  className="rounded-md border border-border p-2 text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                  onClick={() => startEditing(selectedItem)}
                  aria-label="Editar item"
                  title="Editar item"
                >
                  <PencilSimple className="h-4 w-4" aria-hidden="true" />
                </button> : null}
                {canWrite ? <><button type="button" className="rounded-md border border-border px-3 py-2 text-sm font-medium text-muted-foreground hover:bg-accent" disabled={actionItemId === selectedItem.id} onClick={() => archivedMode ? void handleRestore(selectedItem) : void handleArchive(selectedItem)}>{actionItemId === selectedItem.id ? "Processando..." : archivedMode ? "Restaurar" : "Arquivar"}</button><button type="button" className="rounded-md border border-destructive/40 p-2 text-destructive hover:bg-destructive/10" disabled={actionItemId === selectedItem.id} onClick={() => setPendingDeletion(selectedItem)} aria-label="Excluir item permanentemente" title="Excluir item permanentemente"><Trash className="h-4 w-4" aria-hidden="true" /></button></> : null}
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

            <div className="acervo-item-dialog__body min-h-0 overflow-y-auto p-4 sm:p-5">
              {canWrite && editingItemId === selectedItem.id ? (
                <LibraryItemForm title={`Editar ${FORM_CONFIG[editForm.type].label.toLowerCase()}`} form={editForm} config={FORM_CONFIG[editForm.type]} saving={saving} submitLabel="Salvar" onSubmit={handleUpdate} onChange={setEditForm} onCancel={() => setEditingItemId(null)} />
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
                    selectedItem.type === "NOTE" ? <MarkdownRenderer content={selectedItem.content} /> : <p className="whitespace-pre-wrap text-sm text-foreground">{selectedItem.content}</p>
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
      <ConfirmationDialog open={Boolean(pendingDeletion)} title="Excluir item permanentemente?" message="Esta ação removerá o item do Acervo e não poderá ser desfeita." itemName={pendingDeletion?.title} confirmLabel="Excluir permanentemente" loadingLabel="Excluindo..." destructive loading={pendingDeletion !== null && actionItemId === pendingDeletion.id} onCancel={() => setPendingDeletion(null)} onConfirm={() => void handleDelete()} />
      {toast ? <ToastNotification {...toast} onDismiss={() => setToast(null)} /> : null}
    </section>
  );
}

function TypeChooser({ title, options, onChoose, onCancel }: { title: string; options: ReadonlyArray<readonly [TypeChoice, string, typeof FileText]>; onChoose: (type: TypeChoice) => void; onCancel: () => void }) {
  return <div className="grid gap-4"><div><h2 className="text-base font-semibold text-card-foreground">{title}</h2><p className="mt-1 text-sm text-muted-foreground">Escolha um objeto para mostrar apenas os campos relevantes.</p></div><div className="grid gap-2 sm:grid-cols-2">{options.map(([type, label, Icon]) => <button key={type} type="button" onClick={() => onChoose(type)} className="flex items-center gap-2 rounded-md border border-border bg-background px-3 py-2.5 text-left text-sm font-medium text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><Icon className="h-4 w-4 text-primary" />{label}</button>)}</div><div className="flex justify-end"><button type="button" className="rounded-md border border-border px-4 py-2 text-sm font-medium" onClick={onCancel}>Cancelar</button></div></div>;
}

function LibraryItemForm({ title, form, config, saving, submitLabel, onSubmit, onChange, onCancel, onChangeType }: { title: string; form: LibraryItemInput; config: FormConfig; saving: boolean; submitLabel: string; onSubmit: (event: FormEvent<HTMLFormElement>) => void; onChange: (next: LibraryItemInput) => void; onCancel: () => void; onChangeType?: () => void }) {
  return <form className="grid max-w-xl gap-4" onSubmit={onSubmit}><div className="flex flex-wrap items-start justify-between gap-2"><div><h2 className="text-base font-semibold text-card-foreground">{title}</h2><p className="mt-1 text-xs text-muted-foreground">{config.label}</p></div>{onChangeType ? <button type="button" className="text-sm font-medium text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={onChangeType}>Alterar tipo</button> : null}</div>{config.securityNotice ? <p className="rounded-md border border-amber-300/60 bg-amber-50/70 px-3 py-2 text-xs text-amber-900 dark:border-amber-800/70 dark:bg-amber-950/40 dark:text-amber-100">Não armazene senhas, tokens ou chaves aqui. Use o Cofre de um projeto.</p> : null}<div className="grid gap-3">{config.fields.map((field) => <SemanticField key={field} field={field} config={config} value={form[field] ?? ""} onChange={(value) => onChange({ ...form, [field]: value })} />)}</div>{(form.type === "IDENTITY" || form.type === "MATERIAL") && isImageUrl(form.url) ? <img className="h-20 w-20 rounded-md border border-border bg-muted object-contain p-2" src={form.url} alt="Prévia do asset" loading="lazy" decoding="async" referrerPolicy="no-referrer" onError={(event) => { event.currentTarget.style.display = "none"; }} /> : null}<div className="flex flex-wrap justify-end gap-2"><button type="button" className="rounded-md border border-border px-4 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-60" disabled={saving} onClick={onCancel}>Cancelar</button><button type="submit" className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-60" disabled={saving}>{saving ? "Salvando..." : submitLabel}</button></div></form>;
}

function SemanticField({ field, config, value, onChange }: { field: FormField; config: FormConfig; value: string; onChange: (value: string) => void }) {
  const required = config.required.includes(field);
  const label = getFieldLabel(config.label, field);
  const common = { value, onChange: (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => onChange(event.target.value), required, "aria-required": required, maxLength: field === "title" ? 180 : field === "content" ? 20000 : 2000, placeholder: config.placeholders[field] };
  return <label className="grid gap-1.5 text-sm font-medium text-foreground"><span>{label}{required ? " *" : ""}</span>{field === "title" || field === "url" ? <input {...common} autoFocus={field === "title"} type={field === "url" ? "url" : "text"} className="rounded-md border border-input bg-background px-3 py-2 text-sm font-normal text-foreground outline-none focus:border-ring focus:ring-2 focus:ring-ring/25" /> : field === "content" && config.label === "Nota" ? <MarkdownEditor value={value} onChange={onChange} maxLength={20000} placeholder={config.placeholders[field] ?? "Escreva em Markdown."} ariaLabel={label} /> : <textarea {...common} className={`rounded-md border border-input bg-background px-3 py-2 text-sm font-normal text-foreground outline-none focus:border-ring focus:ring-2 focus:ring-ring/25 ${field === "content" ? "min-h-32" : "min-h-20"}`} />}</label>;
}

function getFieldLabel(typeLabel: string, field: FormField) {
  if (field === "title") return ["Ferramenta", "Infraestrutura", "Identidade", "Material", "Processo"].includes(typeLabel) ? "Nome" : "Título";
  if (field === "description") return typeLabel === "Ferramenta" ? "Finalidade" : typeLabel === "Processo" ? "Objetivo" : typeLabel === "Referência" ? "Por que guardar?" : "Resumo";
  if (field === "url") return typeLabel === "Link" ? "URL" : typeLabel === "Referência" ? "URL / Fonte" : typeLabel === "Ferramenta" ? "URL / Host" : typeLabel === "Infraestrutura" ? "Host / domínio / URL" : typeLabel === "Decisão" || typeLabel === "Processo" ? "Referência" : "URL";
  if (typeLabel === "Nota") return "Conteúdo";
  if (typeLabel === "Decisão") return "Registro da decisão";
  if (typeLabel === "Processo") return "Procedimento";
  if (typeLabel === "Snippet") return "Código";
  if (typeLabel === "Comando") return "Comando";
  if (typeLabel === "Ferramenta") return "Notas de uso";
  if (typeLabel === "Infraestrutura") return "Notas técnicas";
  return "Conteúdo";
}

function validateLibraryInput(input: LibraryItemInput) {
  const config = FORM_CONFIG[input.type];
  for (const field of config.required) { if (!input[field]?.trim()) return `Informe ${getFieldLabel(config.label, field).toLocaleLowerCase()}.`; }
  if (input.url?.trim() && !isHttpUrl(input.url)) return `Informe ${getFieldLabel(config.label, "url").toLocaleLowerCase()} HTTP ou HTTPS válida.`;
  return null;
}

function isImageUrl(value: string | undefined) { return Boolean(value && isHttpUrl(value) && /\.(avif|gif|jpe?g|png|svg|webp)(\?.*)?$/i.test(value)); }

function matchesAcervoFilter(item: LibraryItem, filter: AcervoFilter) {
  if (filter === "ALL") return true;
  if (filter === "IDENTITY") return item.type === "IDENTITY";
  if (filter === "DOCUMENTS") return item.type === "DOCUMENT" || item.type === "TEMPLATE";
  if (filter === "NOTES") return item.type === "NOTE";
  if (filter === "REFERENCES") return item.type === "REFERENCE";
  if (filter === "LINKS") return item.type === "LINK";
  if (filter === "DECISIONS") return item.type === "DECISION";
  if (filter === "PROCESS") return item.type === "PROCESS";
  if (filter === "TOOLS") return item.type === "TOOL";
  if (filter === "INFRASTRUCTURE") return item.type === "INFRASTRUCTURE";
  if (filter === "MATERIALS") return item.type === "MATERIAL";
  return item.type === "COMMAND" || item.type === "SNIPPET";
}

function matchesAcervoSearch(item: LibraryItem, value: string) {
  const query = value.trim().toLocaleLowerCase();
  return !query || [item.title, item.description, item.content, item.url, getTypeLabel(item.type)].some((field) => field?.toLocaleLowerCase().includes(query));
}

function getRecentSummary(item: LibraryItem) { return item.description || item.content || item.url || "Conteúdo preservado no Acervo."; }

function AcervoHome({ items, recentItems, workspaceId, canWrite, onShowMore, onOpenRepositories, onOpen, onArchive, onDelete }: { items: LibraryItem[]; recentItems: LibraryItem[]; workspaceId: string; canWrite: boolean; onShowMore: (filter: AcervoFilter) => void; onOpenRepositories: () => void; onOpen: (item: LibraryItem) => void; onArchive: (item: LibraryItem) => void; onDelete: (item: LibraryItem) => void }) {
  const sections = useMemo(() => HOME_SECTIONS.map((section) => ({ ...section, items: items.filter((item) => matchesAcervoFilter(item, section.filter)) })).filter((section) => section.items.length > 0), [items]);
  const codeItems = useMemo(() => items.filter((item) => matchesAcervoFilter(item, "CODE")), [items]);
  const sectionGroups = groupHomeSections(sections);
  return <div className="acervo-home">
    <section className="acervo-home__recent" aria-labelledby="acervo-recentes-title">
      <div className="acervo-home__section-heading acervo-home__section-heading--recent">
        <div><h3 id="acervo-recentes-title" className="text-lg font-semibold text-foreground">Recentes</h3><p className="mt-1 text-sm text-muted-foreground">Últimas leituras da memória do workspace.</p></div>
        <span className="acervo-home__signature">Memória em movimento</span>
      </div>
      {recentItems.length ? <div className="acervo-recent-composition acervo-recent-composition--editorial"><div className={`acervo-recent-hero ${recentItems.length === 1 ? "acervo-recent-hero--single" : ""}`} role="list"><div role="listitem"><button type="button" onClick={() => onOpen(recentItems[0])} className="acervo-recent-item acervo-recent-item--primary" data-acervo-type={recentItems[0].type}><span className="acervo-recent-item__type">{getTypeLabel(recentItems[0].type)}</span><span className="acervo-recent-item__title">{recentItems[0].title}</span><span className="acervo-recent-item__summary">{getRecentSummary(recentItems[0])}</span><span className="acervo-recent-item__meta">Atualizado {formatDate(recentItems[0].updatedAt)}</span></button></div><div className="acervo-recent-hero__secondary">{recentItems.slice(1).map((item) => <div key={item.id} role="listitem"><button type="button" onClick={() => onOpen(item)} className="acervo-recent-item" data-acervo-type={item.type}><span className="acervo-recent-item__type">{getTypeLabel(item.type)}</span><span className="acervo-recent-item__title">{item.title}</span><span className="acervo-recent-item__meta">Atualizado {formatDate(item.updatedAt)}</span></button></div>)}</div></div><aside className="acervo-recent-identity"><BookOpen aria-hidden="true" weight="thin" /><span>Acervo</span><p>Escrita, memória<br />e conhecimento.</p></aside></div> : <p className="rounded-md border border-dashed border-border px-4 py-6 text-sm text-muted-foreground">Ainda não há conteúdos no Acervo.</p>}
    </section>
    <AcervoHomeSection filter="CODE" title="Código" items={codeItems} canWrite={canWrite} onShowMore={() => onShowMore("CODE")} onOpen={onOpen} onArchive={onArchive} onDelete={onDelete} />
    <AcervoGithubHighlights workspaceId={workspaceId} onOpenRepositories={onOpenRepositories} />
    <div className="acervo-home__section-groups">{sectionGroups.map((group, index) => group.length === 1 ? <AcervoHomeSection key={group[0].filter} filter={group[0].filter} title={group[0].title} items={group[0].items} canWrite={canWrite} onShowMore={() => onShowMore(group[0].filter)} onOpen={onOpen} onArchive={onArchive} onDelete={onDelete} /> : <div key={`pair-${index}`} className="acervo-home__section-pair">{group.map((section) => <AcervoHomeSection key={section.filter} compact filter={section.filter} title={section.title} items={section.items} canWrite={canWrite} onShowMore={() => onShowMore(section.filter)} onOpen={onOpen} onArchive={onArchive} onDelete={onDelete} />)}</div>)}</div>
  </div>;
}

function AcervoHomeSection({ compact = false, filter, title, items, canWrite, onShowMore, onOpen, onArchive, onDelete }: { compact?: boolean; filter: Exclude<AcervoFilter, "ALL">; title: string; items: LibraryItem[]; canWrite: boolean; onShowMore: () => void; onOpen: (item: LibraryItem) => void; onArchive: (item: LibraryItem) => void; onDelete: (item: LibraryItem) => void }) {
  if (!items.length) return null;
  const visibleItems = items.slice(0, 4);
  return <section className={`acervo-home-section${compact ? " acervo-home-section--compact" : ""}`} data-section={filter}><header className="acervo-home__section-heading acervo-home-section__header"><div><h3 className="text-lg font-semibold text-foreground">{title}</h3><p className="mt-1 text-xs text-muted-foreground">{items.length} {items.length === 1 ? "item" : "itens"} preservados</p></div>{items.length > visibleItems.length ? <button type="button" onClick={onShowMore} className="acervo-home-section__more">Mostrar mais</button> : null}</header><div className="acervo-home-section__grid">{visibleItems.map((item) => <LibraryItemCard key={item.id} item={item} canWrite={canWrite} onOpen={() => onOpen(item)} onArchive={() => onArchive(item)} onDelete={() => onDelete(item)} />)}</div></section>;
}

function groupHomeSections(sections: Array<{ filter: Exclude<AcervoFilter, "ALL">; title: string; items: LibraryItem[] }>) {
  const groups: Array<typeof sections> = [];
  for (let index = 0; index < sections.length; index += 1) {
    const current = sections[index];
    const next = sections[index + 1];
    const canPair = current.filter !== "IDENTITY" && current.items.length <= 2 && next && next.filter !== "IDENTITY" && next.items.length <= 2;
    if (canPair) { groups.push([current, next]); index += 1; } else { groups.push([current]); }
  }
  return groups;
}

function AcervoGithubHighlights({ workspaceId, onOpenRepositories }: { workspaceId: string; onOpenRepositories: () => void }) {
  const [repositories, setRepositories] = useState<GithubAppRepository[]>([]);
  const [installationAvailable, setInstallationAvailable] = useState<boolean | null>(null);
  useEffect(() => { let active = true; setInstallationAvailable(null); setRepositories([]); void getWorkspaceGithubAppStatus(workspaceId).then(async (installation) => { if (!installation.configured) return { configured: false, items: [] as GithubAppRepository[] }; const page = await listGithubAppRepositories(workspaceId, 1, 4); return { configured: true, items: page.items }; }).then((result) => { if (!active) return; setInstallationAvailable(result.configured); setRepositories(result.items); }).catch(() => { if (active) setInstallationAvailable(false); }); return () => { active = false; }; }, [workspaceId]);
  if (installationAvailable === null) return null;
  return <section className="acervo-home-section acervo-github-section" data-section="CODE"><header className="acervo-home__section-heading acervo-home-section__header acervo-home-section__header--github"><div><h3 className="text-lg font-semibold text-foreground">Repositórios GitHub</h3><p className="mt-1 text-xs text-muted-foreground">Ativos técnicos autorizados para o workspace.</p></div>{installationAvailable ? <button type="button" onClick={onOpenRepositories} className="acervo-home-section__more">Ver repositórios</button> : null}</header>{!installationAvailable ? <p className="text-sm text-muted-foreground">A GitHub App ainda não está configurada neste workspace.</p> : repositories.length ? <div className="acervo-github-section__grid">{repositories.map((repository) => <a key={repository.repositoryId} href={repository.htmlUrl} target="_blank" rel="noopener noreferrer" className="acervo-github-repository"><span className="acervo-github-repository__visibility">{repository.private ? "Privado" : "Público"}</span><span className="acervo-github-repository__title">{repository.fullName}</span><span className="acervo-github-repository__meta">{repository.language || "Sem linguagem definida"}{repository.archived ? " · Arquivado" : ""}</span>{repository.topics.length ? <span className="acervo-github-repository__topics">{repository.topics.slice(0, 2).map((topic) => <span key={topic}>{topic}</span>)}</span> : null}</a>)}</div> : <p className="text-sm text-muted-foreground">Nenhum repositório autorizado nesta instalação.</p>}</section>;
}

function ArchivedLibraryItems({ items, canWrite, actionId, onRestore, onDelete }: { items: LibraryItem[]; canWrite: boolean; actionId: string | null; onRestore: (item: LibraryItem) => void; onDelete: (item: LibraryItem) => void }) { return <ul className="divide-y divide-border rounded-xl border border-border bg-card">{items.map((item) => <li key={item.id} className="grid gap-3 px-4 py-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"><div className="min-w-0"><p className="break-words text-sm font-semibold text-foreground">{item.title}</p><p className="mt-1 text-xs text-muted-foreground">{getTypeLabel(item.type)} · Arquivado em {item.archivedAt ? formatDate(item.archivedAt) : "data indisponível"}</p>{item.description ? <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">{item.description}</p> : null}</div>{canWrite ? <div className="flex flex-wrap gap-2"><button type="button" className="rounded-md border border-border px-3 py-2 text-sm font-medium" disabled={actionId === item.id} onClick={() => onRestore(item)}>{actionId === item.id ? "Restaurando..." : "Restaurar"}</button><button type="button" className="rounded-md bg-destructive px-3 py-2 text-sm font-medium text-destructive-foreground" disabled={actionId === item.id} onClick={() => onDelete(item)}>Excluir permanentemente</button></div> : null}</li>)}</ul>; }

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
