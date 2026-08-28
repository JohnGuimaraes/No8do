import { FormEvent, useEffect, useMemo, useState } from "react";
import { CheckCircle, PencilSimple, Plus, X } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { ConfirmationDialog } from "@/components/ConfirmationDialog";
import { ToastNotification } from "@/components/ToastNotification";
import { ViewModeToggle, type ViewMode } from "@/components/ViewModeToggle";
import { IdeaTypeGlyph } from "@/ideas/IdeaTypeGlyph";
import {
  archiveIdea,
  convertIdeaToProject,
  createIdea,
  deleteIdea,
  listArchivedIdeas,
  listIdeas,
  restoreIdea,
  updateIdea,
  type Idea,
  type IdeaInput,
  type IdeaStatus,
  type IdeaType,
} from "@/ideas/ideaApi";
import { type Project } from "@/projects/projectApi";

type StatusFilter = IdeaStatus | "ALL";
type TypeFilter = IdeaType | "ALL";
type EditableStatus = Exclude<IdeaStatus, "CONVERTED" | "ARCHIVED">;

const TYPE_OPTIONS: Array<{ value: IdeaType; label: string }> = [
  { value: "PROJECT", label: "Sistema" }, { value: "FEATURE", label: "Funcionalidade" },
  { value: "IMPROVEMENT", label: "Melhoria" }, { value: "RESEARCH", label: "Pesquisa" },
  { value: "PRODUCT", label: "Produto" }, { value: "OTHER", label: "Outro" },
];
const STATUS_OPTIONS: Array<{ value: EditableStatus; label: string }> = [
  { value: "INBOX", label: "Caixa de entrada" }, { value: "PLANNED", label: "Planejada" },
];
const STATUS_FILTERS: Array<{ value: StatusFilter; label: string }> = [
  { value: "ALL", label: "Todas" }, { value: "INBOX", label: "Caixa de entrada" }, { value: "PLANNED", label: "Planejadas" },
];
const TYPE_FILTERS: Array<{ value: TypeFilter; label: string }> = [{ value: "ALL", label: "Todos os tipos" }, ...TYPE_OPTIONS];
const EMPTY_FORM: IdeaInput = { title: "", description: "", type: "PROJECT", status: "INBOX" };

export function IdeasPanel({ workspaceId, projects, onProjectCreated, onOpenProject, selectedIdeaId, onIdeasChange, onIdeaRestored, canWrite }: {
  workspaceId: string; projects: Project[]; onProjectCreated: (project: Project) => void;
  onOpenProject: (project: Project) => void; selectedIdeaId?: string | null; onIdeasChange?: (ideas: Idea[]) => void; onIdeaRestored?: (idea: Idea) => void; canWrite: boolean;
}) {
  const [ideas, setIdeas] = useState<Idea[]>([]);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [typeFilter, setTypeFilter] = useState<TypeFilter>("ALL");
  const [form, setForm] = useState<IdeaInput>(EMPTY_FORM);
  const [editForm, setEditForm] = useState<IdeaInput>(EMPTY_FORM);
  const [selectedIdea, setSelectedIdea] = useState<Idea | null>(null);
  const [editingIdeaId, setEditingIdeaId] = useState<string | null>(null);
  const [createFormOpen, setCreateFormOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [convertingIdeaId, setConvertingIdeaId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [conversionFeedback, setConversionFeedback] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>("visual");
  const [archivedMode, setArchivedMode] = useState(false);
  const [actionIdeaId, setActionIdeaId] = useState<string | null>(null);
  const [pendingConversion, setPendingConversion] = useState<Idea | null>(null);
  const [pendingDeletion, setPendingDeletion] = useState<Idea | null>(null);
  const [toast, setToast] = useState<{ title: string; message: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function loadIdeas() {
      setLoading(true); setError(null); setSelectedIdea(null); setEditingIdeaId(null); setStatusFilter("ALL"); setTypeFilter("ALL"); setConversionFeedback(null);
      try {
        const items = await (archivedMode ? listArchivedIdeas(workspaceId) : listIdeas(workspaceId));
        if (!cancelled) {
          const visibleIdeas = archivedMode ? items : items.filter((idea) => idea.status !== "CONVERTED");
          setIdeas(visibleIdeas);
          if (!archivedMode) onIdeasChange?.(visibleIdeas);
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Nao foi possivel carregar ideias.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void loadIdeas();
    return () => { cancelled = true; };
  }, [archivedMode, onIdeasChange, workspaceId]);

  useEffect(() => {
    if (!selectedIdeaId) return;
    const idea = ideas.find((item) => item.id === selectedIdeaId);
    if (idea) { setSelectedIdea(idea); setEditingIdeaId(null); }
  }, [ideas, selectedIdeaId]);

  useEffect(() => {
    if (!conversionFeedback) return;
    const timeout = window.setTimeout(() => setConversionFeedback(null), 3500);
    return () => window.clearTimeout(timeout);
  }, [conversionFeedback]);

  const filteredIdeas = useMemo(() => ideas.filter((idea) => (statusFilter === "ALL" || idea.status === statusFilter) && (typeFilter === "ALL" || idea.type === typeFilter)), [ideas, statusFilter, typeFilter]);
  const hasActiveFilters = statusFilter !== "ALL" || typeFilter !== "ALL";

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const input = normalizeInput(form);
    if (!input.title) { setError("Informe um titulo para a ideia."); return; }
    setSaving(true); setError(null);
    try {
      const createdIdea = await createIdea(workspaceId, input);
      setIdeas((current) => { const next = [createdIdea, ...current]; onIdeasChange?.(next); return next; });
      setSelectedIdea(createdIdea); setForm(EMPTY_FORM); setCreateFormOpen(false);
    } catch (err) { setError(err instanceof Error ? err.message : "Nao foi possivel criar a ideia."); } finally { setSaving(false); }
  }
  function handleCancelCreate() { setForm(EMPTY_FORM); setError(null); setCreateFormOpen(false); }
  function startEditing(idea: Idea) {
    setSelectedIdea(idea); setEditingIdeaId(idea.id);
    setEditForm({ title: idea.title, description: idea.description ?? "", type: idea.type, status: idea.status === "CONVERTED" ? "PLANNED" : idea.status });
    setError(null);
  }
  async function handleUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selectedIdea || editingIdeaId !== selectedIdea.id) return;
    const input = normalizeInput(editForm); if (!input.title) { setError("Informe um titulo para a ideia."); return; }
    setSaving(true); setError(null);
    try { const updatedIdea = await updateIdea(workspaceId, selectedIdea.id, input); applyIdeaUpdate(updatedIdea); setEditingIdeaId(null); }
    catch (err) { setError(err instanceof Error ? err.message : "Nao foi possivel salvar a ideia."); } finally { setSaving(false); }
  }
  async function handleConvert(idea: Idea) {
    setConvertingIdeaId(idea.id); setError(null);
    try {
      const result = await convertIdeaToProject(workspaceId, idea.id);
      setIdeas((current) => {
        const next = current.filter((item) => item.id !== result.idea.id);
        onIdeasChange?.(next);
        return next;
      });
      onProjectCreated(result.project);
      setSelectedIdea(null);
      setConversionFeedback(`Convertida em projeto: ${result.project.name}`);
    } catch (err) { setError(err instanceof Error ? err.message : "Nao foi possivel transformar a ideia em projeto."); } finally { setConvertingIdeaId(null); }
  }
  function showToast(title: string, message: string) { setToast({ title, message }); window.setTimeout(() => setToast(null), 3000); }
  async function handleArchive(idea: Idea) { setActionIdeaId(idea.id); setError(null); try { const archived = await archiveIdea(workspaceId, idea.id); setIdeas((current) => current.filter((item) => item.id !== archived.id)); onIdeasChange?.(ideas.filter((item) => item.id !== archived.id)); setSelectedIdea(null); showToast("Ideia arquivada", `${archived.title} foi movida para Arquivadas.`); } catch (err) { setError(err instanceof Error ? err.message : "Nao foi possivel arquivar a ideia."); } finally { setActionIdeaId(null); } }
  async function handleRestore(idea: Idea) { setActionIdeaId(idea.id); setError(null); try { const restored = await restoreIdea(workspaceId, idea.id); setIdeas((current) => current.filter((item) => item.id !== restored.id)); onIdeaRestored?.(restored); setSelectedIdea(null); showToast("Ideia restaurada", `${restored.title} foi restaurada.`); } catch (err) { setError(err instanceof Error ? err.message : "Nao foi possivel restaurar a ideia."); } finally { setActionIdeaId(null); } }
  async function handleDelete() { const idea = pendingDeletion; if (!idea) return; setActionIdeaId(idea.id); setError(null); try { await deleteIdea(workspaceId, idea.id); setIdeas((current) => current.filter((item) => item.id !== idea.id)); if (!archivedMode) onIdeasChange?.(ideas.filter((item) => item.id !== idea.id)); setSelectedIdea(null); setPendingDeletion(null); showToast("Ideia excluída", `${idea.title} foi excluída permanentemente.`); } catch (err) { setError(err instanceof Error ? err.message : "Nao foi possivel excluir a ideia."); } finally { setActionIdeaId(null); } }
  function applyIdeaUpdate(updatedIdea: Idea) {
    setIdeas((current) => {
      const next = current.map((idea) => (idea.id === updatedIdea.id ? updatedIdea : idea)).sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt));
      onIdeasChange?.(next); return next;
    });
    setSelectedIdea((current) => (current?.id === updatedIdea.id ? updatedIdea : current));
  }
  function findConvertedProject(idea: Idea) { return idea.convertedProjectId ? projects.find((project) => project.id === idea.convertedProjectId) ?? null : null; }

  return <section className="grid min-w-0 gap-6">
    <header className="grid min-w-0 gap-4">
      <div className="flex min-w-0 flex-wrap items-start justify-between gap-4">
        <div className="min-w-0"><h2 className="text-xl font-semibold text-foreground">Ideias</h2><p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">Capture possibilidades, melhorias, pesquisas, produtos e funcionalidades antes de virarem trabalho.</p></div>
        <div className="flex flex-wrap gap-2"><Button type="button" variant="outline" onClick={() => setArchivedMode((current) => !current)}>{archivedMode ? "Voltar às ideias" : "Arquivadas"}</Button>{canWrite && !createFormOpen && !archivedMode ? <Button type="button" onClick={() => setCreateFormOpen(true)}><Plus className="h-4 w-4" aria-hidden="true" />Nova ideia</Button> : null}</div>
      </div>
      {canWrite && createFormOpen && !archivedMode ? <IdeaCreateForm form={form} saving={saving} onChange={setForm} onCancel={handleCancelCreate} onSubmit={handleCreate} /> : null}
      <div className="grid min-w-0 gap-4 border-y border-border py-4 lg:grid-cols-[minmax(0,1fr)_224px_auto] lg:items-end lg:gap-6">
        <StatusFilterControl value={statusFilter} onChange={setStatusFilter} />
        <label className="grid min-w-0 gap-1.5 text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground">Tipo
          <select className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm font-normal normal-case tracking-normal text-foreground" value={typeFilter} onChange={(event) => setTypeFilter(event.target.value as TypeFilter)}>{TYPE_FILTERS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select>
        </label>
        <ViewModeToggle value={viewMode} onChange={setViewMode} />
      </div>
    </header>
    <div className="grid min-w-0 gap-4">
      <div className="text-sm text-muted-foreground" aria-live="polite">{filteredIdeas.length} {filteredIdeas.length === 1 ? "ideia" : "ideias"}{archivedMode ? " arquivadas" : ""}</div>
      {error ? <p className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p> : null}
      {conversionFeedback ? <p className="flex items-center gap-2 rounded-md border border-primary/20 bg-primary/10 px-3 py-2 text-sm text-primary" role="status"><CheckCircle className="h-4 w-4" aria-hidden="true" />{conversionFeedback}</p> : null}
      {loading ? <div className="no8do-panel rounded-xl px-4 py-6 text-sm text-muted-foreground">Carregando ideias...</div> : filteredIdeas.length === 0 ? <EmptyIdeasState filtered={hasActiveFilters} /> : viewMode === "visual" ?
        <div className="grid min-w-0 gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:max-w-[1480px] 2xl:grid-cols-4">{filteredIdeas.map((idea) => <IdeaCard key={idea.id} idea={idea} convertedProjectName={findConvertedProject(idea)?.name ?? idea.convertedProjectName} onClick={() => { setSelectedIdea(idea); setEditingIdeaId(null); }} />)}</div> :
        <IdeaList ideas={filteredIdeas} projects={projects} onOpen={(idea) => { setSelectedIdea(idea); setEditingIdeaId(null); }} />}
    </div>
    {selectedIdea ? <IdeaDetails idea={selectedIdea} canWrite={canWrite} editForm={editForm} editing={editingIdeaId === selectedIdea.id} saving={saving} converting={convertingIdeaId === selectedIdea.id} actionLoading={actionIdeaId === selectedIdea.id} project={findConvertedProject(selectedIdea)} onClose={() => { setSelectedIdea(null); setEditingIdeaId(null); }} onEdit={() => startEditing(selectedIdea)} onFormChange={setEditForm} onCancelEdit={() => setEditingIdeaId(null)} onUpdate={handleUpdate} onConvert={() => setPendingConversion(selectedIdea)} onArchive={() => void handleArchive(selectedIdea)} onRestore={() => void handleRestore(selectedIdea)} onDelete={() => setPendingDeletion(selectedIdea)} onOpenProject={onOpenProject} /> : null}
    <ConfirmationDialog open={Boolean(pendingConversion)} title="Transformar em projeto?" message="A ideia será preservada e vinculada ao novo projeto." itemName={pendingConversion?.title} confirmLabel="Transformar" loadingLabel="Transformando..." loading={pendingConversion !== null && convertingIdeaId === pendingConversion.id} onCancel={() => setPendingConversion(null)} onConfirm={() => { if (!pendingConversion) return; void handleConvert(pendingConversion).finally(() => setPendingConversion(null)); }} />
    <ConfirmationDialog open={Boolean(pendingDeletion)} title="Excluir ideia permanentemente?" message={pendingDeletion?.convertedProjectId ? "Esta ação não poderá ser desfeita. O projeto criado a partir desta ideia não será excluído." : "Esta ação não poderá ser desfeita."} itemName={pendingDeletion?.title} confirmLabel="Excluir permanentemente" loadingLabel="Excluindo..." destructive loading={pendingDeletion !== null && actionIdeaId === pendingDeletion.id} onCancel={() => setPendingDeletion(null)} onConfirm={() => void handleDelete()} />
    {toast ? <ToastNotification {...toast} onDismiss={() => setToast(null)} /> : null}
  </section>;
}

function IdeaCreateForm({ form, saving, onChange, onCancel, onSubmit }: FormProps) {
  return <form className="no8do-panel grid gap-4 rounded-xl p-4 sm:p-5" onSubmit={onSubmit}>
    <div className="flex items-center justify-between gap-3"><h3 className="text-base font-semibold text-card-foreground">Nova ideia</h3><Plus className="h-5 w-5 text-muted-foreground" aria-hidden="true" /></div>
    <p className="rounded-md border border-amber-300/80 bg-amber-50 px-3 py-2 text-xs text-amber-950 dark:border-amber-800 dark:bg-amber-950/55 dark:text-amber-100">Nao armazene senhas, tokens ou chaves em ideias.</p>
    <IdeaFields form={form} onChange={onChange} includeStatus={false} />
    <div className="flex flex-wrap justify-end gap-2"><Button type="button" variant="outline" disabled={saving} onClick={onCancel}>Cancelar</Button><Button type="submit" disabled={saving}>{saving ? "Salvando..." : "Criar ideia"}</Button></div>
  </form>;
}

type FormProps = { form: IdeaInput; saving: boolean; onChange: (form: IdeaInput) => void; onCancel: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void };
function IdeaFields({ form, onChange, includeStatus }: Pick<FormProps, "form" | "onChange"> & { includeStatus: boolean }) {
  return <><label className="grid gap-1.5 text-sm font-medium text-foreground">Titulo<input className="h-10 rounded-md border border-input bg-background px-3 text-sm font-normal" value={form.title} onChange={(event) => onChange({ ...form, title: event.target.value })} maxLength={180} placeholder="Ex.: Sistema financeiro pessoal" /></label>
    <label className="grid gap-1.5 text-sm font-medium text-foreground">Descricao<textarea className="min-h-28 rounded-md border border-input bg-background px-3 py-2 text-sm font-normal" value={form.description} onChange={(event) => onChange({ ...form, description: event.target.value })} maxLength={10000} placeholder="Contexto, problema ou possibilidade" /></label>
    <div className={includeStatus ? "grid gap-4 sm:grid-cols-2" : "max-w-sm"}><label className="grid gap-1.5 text-sm font-medium text-foreground">Tipo<select className="h-10 rounded-md border border-input bg-background px-3 text-sm font-normal" value={form.type} onChange={(event) => onChange({ ...form, type: event.target.value as IdeaType })}>{TYPE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
    {includeStatus ? <label className="grid gap-1.5 text-sm font-medium text-foreground">Status<select className="h-10 rounded-md border border-input bg-background px-3 text-sm font-normal" value={form.status} onChange={(event) => onChange({ ...form, status: event.target.value as EditableStatus })}>{STATUS_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label> : null}</div></>;
}

function StatusFilterControl({ value, onChange }: { value: StatusFilter; onChange: (value: StatusFilter) => void }) {
  return <div className="grid min-w-0 gap-1.5"><span className="text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground">Status</span><div className="flex min-w-0 gap-2 overflow-x-auto pb-1" aria-label="Filtrar por status">{STATUS_FILTERS.map((option) => <button key={option.value} type="button" aria-pressed={value === option.value} className={`shrink-0 rounded-md border px-3 py-2 text-xs font-medium transition-[background-color,color,border-color] ${value === option.value ? "border-primary bg-primary text-primary-foreground" : "border-border bg-background text-muted-foreground hover:border-primary/40 hover:text-foreground"}`} onClick={() => onChange(option.value)}>{option.label}</button>)}</div></div>;
}

function IdeaCard({ idea, convertedProjectName, onClick }: { idea: Idea; convertedProjectName: string | null; onClick: () => void }) {
  const isConverted = idea.status === "CONVERTED"; const isArchived = idea.status === "ARCHIVED";
  return <button type="button" className={`group grid min-w-0 gap-4 rounded-xl border bg-card p-4 text-left shadow-sm transition-[border-color,box-shadow,transform] hover:-translate-y-px hover:border-primary/40 hover:shadow-md ${isConverted ? "border-primary/35" : "border-border"} ${isArchived ? "bg-muted/45" : ""}`} onClick={onClick}>
    <span className="flex min-w-0 items-start justify-between gap-3"><span className="flex min-w-0 items-center gap-2.5"><IdeaTypeGlyph type={idea.type} /><span className="text-xs font-semibold uppercase tracking-[0.1em] text-muted-foreground">{getTypeLabel(idea.type)}</span></span><StatusChip status={idea.status} /></span>
    <span className="grid min-w-0 gap-2"><span className="line-clamp-2 break-words text-base font-semibold leading-6 text-card-foreground">{idea.title}</span><span className="line-clamp-3 text-sm leading-6 text-muted-foreground">{idea.description || "Sem descricao cadastrada."}</span></span>
    <span className="grid gap-2 text-xs leading-5 text-muted-foreground">{isConverted ? <span className="font-medium text-foreground">Convertida em projeto{convertedProjectName ? `: ${convertedProjectName}` : ""}</span> : null}<span>atualizada em {formatDate(idea.updatedAt)}</span></span>
  </button>;
}
function IdeaList({ ideas, projects, onOpen }: { ideas: Idea[]; projects: Project[]; onOpen: (idea: Idea) => void }) {
  return <ul className="divide-y divide-border rounded-xl border border-border bg-card">{ideas.map((idea) => {
    const projectName = projects.find((project) => project.id === idea.convertedProjectId)?.name ?? idea.convertedProjectName;
    return <li key={idea.id}><button type="button" className="grid w-full min-w-0 grid-cols-[auto_minmax(0,1fr)_auto] items-start gap-3 px-3 py-3 text-left transition-[background-color] hover:bg-muted/55 sm:grid-cols-[auto_minmax(0,1fr)_auto_auto] sm:px-4" onClick={() => onOpen(idea)}><IdeaTypeGlyph type={idea.type} /><span className="grid min-w-0 gap-1"><span className="break-words text-sm font-semibold text-card-foreground">{idea.title}</span><span className="line-clamp-1 text-xs text-muted-foreground">{getTypeLabel(idea.type)} · {idea.description || "Sem descricao cadastrada."}{idea.status === "CONVERTED" && projectName ? ` · Convertida em projeto: ${projectName}` : ""}</span><span className="text-[11px] text-muted-foreground">Atualizada em {formatDate(idea.updatedAt)}</span></span><StatusChip status={idea.status} /></button></li>;
  })}</ul>;
}
function StatusChip({ status }: { status: IdeaStatus }) { const tone = status === "CONVERTED" ? "border-primary/30 bg-primary/10 text-primary" : status === "ARCHIVED" ? "border-border bg-muted text-muted-foreground" : "border-border bg-background text-muted-foreground"; return <span className={`shrink-0 rounded-full border px-2 py-1 text-[11px] font-medium ${tone}`}>{getStatusLabel(status)}</span>; }
function EmptyIdeasState({ filtered }: { filtered: boolean }) { return <div className="rounded-xl border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground"><p className="font-medium text-foreground">{filtered ? "Nenhuma ideia encontrada com estes filtros." : "Nenhuma ideia registrada."}</p></div>; }

function IdeaDetails({ idea, canWrite, editForm, editing, saving, converting, actionLoading, project, onClose, onEdit, onFormChange, onCancelEdit, onUpdate, onConvert, onArchive, onRestore, onDelete, onOpenProject }: { idea: Idea; canWrite: boolean; editForm: IdeaInput; editing: boolean; saving: boolean; converting: boolean; actionLoading: boolean; project: Project | null; onClose: () => void; onEdit: () => void; onFormChange: (form: IdeaInput) => void; onCancelEdit: () => void; onUpdate: (event: FormEvent<HTMLFormElement>) => void; onConvert: () => void; onArchive: () => void; onRestore: () => void; onDelete: () => void; onOpenProject: (project: Project) => void }) {
  return <div className="fixed inset-0 z-50 flex items-end bg-foreground/40 px-3 py-4 sm:items-center sm:justify-center sm:px-6" role="dialog" aria-modal="true" aria-labelledby="idea-details-title"><div className="flex max-h-[92vh] w-full max-w-3xl flex-col overflow-hidden rounded-xl border border-border bg-card shadow-xl">
    <div className="flex items-start justify-between gap-3 border-b border-border p-4"><div className="min-w-0"><p className="text-xs font-medium uppercase tracking-[0.1em] text-muted-foreground">{getTypeLabel(idea.type)} / {getStatusLabel(idea.status)}</p><h2 id="idea-details-title" className="mt-1 break-words text-lg font-semibold text-foreground">{idea.title}</h2></div><div className="flex shrink-0 gap-2">{canWrite && idea.status !== "CONVERTED" && idea.status !== "ARCHIVED" ? <Button type="button" variant="outline" size="icon" onClick={onEdit} aria-label="Editar ideia" title="Editar ideia"><PencilSimple className="h-4 w-4" aria-hidden="true" /></Button> : null}{canWrite ? <><Button type="button" variant="outline" disabled={actionLoading} onClick={idea.status === "ARCHIVED" ? onRestore : onArchive}>{actionLoading ? "Processando..." : idea.status === "ARCHIVED" ? "Restaurar" : "Arquivar"}</Button><Button type="button" variant="destructive" size="icon" disabled={actionLoading} onClick={onDelete} aria-label="Excluir ideia permanentemente" title="Excluir ideia permanentemente"><X className="h-4 w-4" aria-hidden="true" /></Button></> : null}<Button type="button" variant="outline" size="icon" onClick={onClose} aria-label="Fechar" title="Fechar"><X className="h-4 w-4" aria-hidden="true" /></Button></div></div>
    <div className="min-h-0 overflow-y-auto p-4 sm:p-5">{editing ? <form className="grid gap-4" onSubmit={onUpdate}><IdeaFields form={editForm} onChange={onFormChange} includeStatus /><div className="flex flex-wrap justify-end gap-2"><Button type="button" variant="outline" onClick={onCancelEdit}>Cancelar</Button><Button type="submit" disabled={saving}>{saving ? "Salvando..." : "Salvar"}</Button></div></form> : <div className="grid gap-4"><p className="whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{idea.description || "Sem descricao cadastrada."}</p>{idea.status === "ARCHIVED" ? <p className="text-sm text-muted-foreground">Restaure esta ideia para continuar o fluxo.</p> : idea.status === "CONVERTED" ? <ConvertedProjectAction idea={idea} project={project} onOpenProject={onOpenProject} /> : canWrite ? <Button type="button" className="w-fit" onClick={onConvert} disabled={converting}>{converting ? "Transformando..." : "Transformar em projeto"}</Button> : null}<dl className="grid gap-3 border-t border-border pt-4 text-xs text-muted-foreground sm:grid-cols-3"><div><dt className="font-medium text-foreground">Autor</dt><dd>{idea.createdByName}</dd></div><div><dt className="font-medium text-foreground">Criada em</dt><dd>{formatDate(idea.createdAt)}</dd></div><div><dt className="font-medium text-foreground">Atualizada em</dt><dd>{formatDate(idea.updatedAt)}</dd></div></dl></div>}</div>
  </div></div>;
}
function ConvertedProjectAction({ idea, project, onOpenProject }: { idea: Idea; project: Project | null; onOpenProject: (project: Project) => void }) { const projectName = project?.name ?? idea.convertedProjectName; return project ? <Button type="button" variant="outline" className="w-fit" onClick={() => onOpenProject(project)}>Abrir projeto: {project.name}</Button> : <p className="rounded-md border border-primary/20 bg-primary/10 px-3 py-2 text-sm text-foreground">Convertida em projeto{projectName ? `: ${projectName}` : ""}.</p>; }
function normalizeInput(input: IdeaInput): IdeaInput { return { title: input.title.trim(), description: normalizeOptional(input.description), type: input.type, status: input.status }; }
function normalizeOptional(value: string | undefined) { const normalized = value?.trim(); return normalized ? normalized : undefined; }
function getTypeLabel(type: IdeaType) { return TYPE_OPTIONS.find((option) => option.value === type)?.label ?? type; }
function getStatusLabel(status: IdeaStatus) { return status === "CONVERTED" ? "Convertida" : STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status; }
function formatDate(value: string) { return new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium" }).format(new Date(value)); }
