import { FormEvent, useState } from "react";
import { CaretDown, Check, Plus } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { type Workspace } from "@/workspaces/workspaceApi";

type WorkspaceSwitcherProps = { workspaces: Workspace[]; activeWorkspaceId: string | null; loading: boolean; creating: boolean; error: string | null; onSelect: (workspaceId: string) => void; onCreate: (name: string) => Promise<void> };

export function WorkspaceSwitcher({ workspaces, activeWorkspaceId, loading, creating, error, onSelect, onCreate }: WorkspaceSwitcherProps) {
  const [open, setOpen] = useState(false);
  const [creatingWorkspace, setCreatingWorkspace] = useState(false);
  const [name, setName] = useState("");
  const activeWorkspace = workspaces.find((workspace) => workspace.id === activeWorkspaceId);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedName = name.trim();
    if (!normalizedName) return;
    await onCreate(normalizedName);
    setName("");
    setCreatingWorkspace(false);
    setOpen(false);
  }

  return <div className="relative min-w-0">
    <button type="button" className="workspace-switcher-trigger" aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen((current) => !current)}>
      <span className="workspace-switcher-mark">N</span>
      <span className="min-w-0 text-left"><span className="block truncate text-sm font-medium text-foreground">{loading ? "Carregando workspace..." : activeWorkspace?.name ?? "Selecione um workspace"}</span>{activeWorkspace ? <span className="block text-[11px] text-muted-foreground">{activeWorkspace.role}</span> : null}</span>
      <CaretDown className="h-4 w-4 shrink-0 text-muted-foreground" />
    </button>
    {open ? <div className="workspace-switcher-menu" role="dialog" aria-label="Selecionar workspace">
      <div className="grid gap-1 p-2">{workspaces.map((workspace) => <button key={workspace.id} type="button" className="workspace-switcher-option" onClick={() => { onSelect(workspace.id); setOpen(false); }}><span className="min-w-0"><span className="block truncate text-sm font-medium">{workspace.name}</span><span className="block text-xs text-muted-foreground">{workspace.role}</span></span>{workspace.id === activeWorkspaceId ? <Check className="h-4 w-4 shrink-0 text-primary" /> : null}</button>)}</div>
      <div className="border-t border-border/70 p-2">{creatingWorkspace ? <form className="grid gap-2" onSubmit={(event) => void handleSubmit(event)}><input autoFocus className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring" value={name} onChange={(event) => setName(event.target.value)} placeholder="Nome do workspace" aria-label="Nome do novo workspace" />{error ? <p className="text-xs text-destructive">{error}</p> : null}<div className="flex justify-end gap-2"><Button type="button" variant="ghost" size="sm" onClick={() => setCreatingWorkspace(false)}>Cancelar</Button><Button type="submit" size="sm" disabled={creating}>{creating ? "Criando..." : "Criar"}</Button></div></form> : <Button type="button" variant="ghost" size="sm" className="w-full justify-start" onClick={() => setCreatingWorkspace(true)}><Plus className="h-4 w-4" />Novo workspace</Button>}</div>
    </div> : null}
  </div>;
}
