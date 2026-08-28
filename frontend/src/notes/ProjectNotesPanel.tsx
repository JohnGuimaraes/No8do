import { FormEvent, useEffect, useState } from "react";
import { NotePencil, Plus, PencilSimple, Trash } from "@phosphor-icons/react";
import { ConfirmationDialog } from "@/components/ConfirmationDialog";
import { Button } from "@/components/ui/button";
import {
  createProjectNote,
  listProjectNotes,
  type ProjectNote,
  updateProjectNote, deleteProjectNote,
} from "@/notes/noteApi";

type ProjectNotesPanelProps = {
  workspaceId: string;
  projectId: string;
};

const MAX_NOTE_CONTENT_LENGTH = 10_000;

export function ProjectNotesPanel({ workspaceId, projectId }: ProjectNotesPanelProps) {
  const [notes, setNotes] = useState<ProjectNote[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [content, setContent] = useState("");
  const [type, setType] = useState<ProjectNote["type"]>("NOTE");
  const [createFormOpen, setCreateFormOpen] = useState(false);
  const [editing, setEditing] = useState<ProjectNote | null>(null);
  const [deleting, setDeleting] = useState<ProjectNote | null>(null);
  const [deletingNote, setDeletingNote] = useState(false);

  useEffect(() => {
    let active = true;
    setCreateFormOpen(false);
    cancelEditing();
    setDeleting(null);

    async function loadNotes() {
      setLoading(true);
      setError(null);

      try {
        const response = await listProjectNotes(workspaceId, projectId);
        if (active) {
          setNotes(response);
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Nao foi possivel carregar as anotacoes.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadNotes();

    return () => {
      active = false;
    };
  }, [workspaceId, projectId]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedContent = content.trim();

    if (!normalizedContent) {
      setFormError("Informe o conteudo da anotacao.");
      return;
    }
    if (normalizedContent.length > MAX_NOTE_CONTENT_LENGTH) {
      setFormError("A anotacao deve ter no maximo 10.000 caracteres.");
      return;
    }

    if (creating) return;
    setCreating(true);
    setFormError(null);

    try {
      const saved = editing ? await updateProjectNote(workspaceId, projectId, editing.id, { content: normalizedContent, type }) : await createProjectNote(workspaceId, projectId, { content: normalizedContent, type });
      setNotes((current) => editing ? current.map((note) => note.id === saved.id ? saved : note) : [saved, ...current]);
      cancelEditing();
      setCreateFormOpen(false);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Nao foi possivel criar a anotacao.");
    } finally {
      setCreating(false);
    }
  }
  function cancelEditing() { setContent(""); setType("NOTE"); setEditing(null); setFormError(null); }
  function startEditing(note: ProjectNote) { setEditing(note); setContent(note.content); setType(note.type); setFormError(null); setCreateFormOpen(true); }
  function closeForm() { cancelEditing(); setCreateFormOpen(false); }
  async function confirmDelete() { if (!deleting || deletingNote) return; setDeletingNote(true); setError(null); try { await deleteProjectNote(workspaceId, projectId, deleting.id); setNotes((current) => current.filter((note) => note.id !== deleting.id)); if (editing?.id === deleting.id) cancelEditing(); setDeleting(null); } catch (err) { setError(err instanceof Error ? err.message : "Não foi possível excluir a anotação."); } finally { setDeletingNote(false); } }

  return (
    <div className="min-w-0 rounded-md border border-border bg-background p-3">
      <div className="mb-3 flex min-w-0 flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-foreground">Anotacoes</p>
          <p className="mt-1 text-xs text-muted-foreground">
            Guarde observacoes, comandos, links e referencias deste projeto.
          </p>
        </div>
        {!createFormOpen ? <Button type="button" size="sm" onClick={() => setCreateFormOpen(true)}><Plus className="h-4 w-4" />Adicionar nota</Button> : null}
      </div>

      {createFormOpen ? <form className="grid min-w-0 gap-2" onSubmit={handleSubmit}>
        <select className="h-9 rounded-md border border-input bg-card px-3 text-sm" value={type} onChange={(event) => setType(noteType(event.target.value))}><option value="NOTE">Anotação</option><option value="DECISION">Decisão</option><option value="CONTEXT">Contexto</option></select><textarea
          className="min-h-28 w-full min-w-0 resize-y rounded-md border border-input bg-card px-3 py-2 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={content}
          onChange={(event) => setContent(event.target.value)}
          maxLength={MAX_NOTE_CONTENT_LENGTH}
          placeholder="Escreva uma observacao, comando, instrucao, link ou referencia tecnica."
          aria-label="Conteudo da anotacao"
        />
        <p className="text-right text-[11px] text-muted-foreground">
          {content.length}/{MAX_NOTE_CONTENT_LENGTH}
        </p>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
        <div className="flex flex-wrap justify-stretch gap-2 sm:justify-end">
          <Button type="button" variant="ghost" size="sm" disabled={creating} onClick={closeForm}>Cancelar</Button>
          <Button type="submit" size="sm" className="w-full sm:w-auto" disabled={creating}>
            <Plus className="h-4 w-4" />
            {creating ? "Salvando..." : editing ? "Salvar alteração" : "Salvar anotacao"}
          </Button>
        </div>
      </form> : null}

      <div className="mt-4 min-w-0">
        {loading ? <p className="text-sm text-muted-foreground">Carregando anotacoes...</p> : null}
        {error ? <p className="text-sm text-destructive">{error}</p> : null}
        {!loading && !error && notes.length === 0 ? (
          <div className="rounded-md border border-dashed border-border px-3 py-6 text-center">
            <p className="text-sm font-medium text-muted-foreground">Sem anotacoes ainda</p>
            <p className="mt-1 text-xs text-muted-foreground">
              Use este espaco para memoria livre do projeto, sem credenciais ou segredos.
            </p>
          </div>
        ) : null}
        {!loading && !error && notes.length > 0 ? (
          <ol className="min-w-0 space-y-3">
            {notes.map((note) => (
              <li key={note.id} className="min-w-0 rounded-md border border-border bg-card px-3 py-3">
                <div className="mb-2 flex min-w-0 flex-wrap items-center gap-2">
                  <span className="inline-flex items-center gap-1.5 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700 dark:border-amber-800 dark:bg-amber-950/55 dark:text-amber-200">
                    <NotePencil className="h-3.5 w-3.5" />
                    {note.type === "DECISION" ? "Decisão" : note.type === "CONTEXT" ? "Contexto" : "Anotação"}
                  </span>
                  <span className="break-words text-[11px] text-muted-foreground">
                    Autor: {note.createdByName}
                  </span>
                  <time className="break-words text-[11px] text-muted-foreground">
                    {formatDate(note.createdAt)}
                  </time>
                </div>
                <p className="whitespace-pre-wrap break-words text-sm leading-6 text-card-foreground">
                  {note.content}
                </p><div className="mt-3 flex gap-2"><Button type="button" variant="ghost" size="sm" onClick={() => startEditing(note)}><PencilSimple className="h-4 w-4" />Editar</Button><Button type="button" variant="ghost" size="sm" className="text-destructive" onClick={() => setDeleting(note)}><Trash className="h-4 w-4" />Excluir</Button></div>
              </li>
            ))}
          </ol>
        ) : null}
      </div>
      <ConfirmationDialog open={Boolean(deleting)} title="Excluir anotação?" message="Esta ação não poderá ser desfeita." itemName={deleting?.content.slice(0, 80)} confirmLabel="Excluir" loadingLabel="Excluindo..." destructive loading={deletingNote} onCancel={() => setDeleting(null)} onConfirm={() => void confirmDelete()} /></div>
  );
}

function noteType(value: string): ProjectNote["type"] { return value === "DECISION" || value === "CONTEXT" ? value : "NOTE"; }

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
