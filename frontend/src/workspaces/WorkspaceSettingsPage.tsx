import { type FormEvent, useEffect, useState } from "react";
import { ArrowLeft, Copy, GearSix, GithubLogo, Plug, Plus, Trash } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { createWorkspaceInvite, deleteWorkspace, getWorkspaceGithubAppInstallation, getWorkspaceGithubAppInstallUrl, listWorkspaceInvites, listWorkspaceMembersForManagement, revokeWorkspaceInvite, type Workspace, type WorkspaceGithubAppInstallation, type WorkspaceInvite, type WorkspaceInviteRole, type WorkspaceMemberManagement } from "@/workspaces/workspaceApi";

export function WorkspaceSettingsPage({ workspace, onReturnToWorkspace, onWorkspaceDeleted }: { workspace: Workspace; onReturnToWorkspace: () => void; onWorkspaceDeleted: (workspaceId: string) => void }) {
  const [githubInstallation, setGithubInstallation] = useState<WorkspaceGithubAppInstallation | null>(null);
  const [githubError, setGithubError] = useState<string | null>(null);
  const [githubSuccess, setGithubSuccess] = useState<string | null>(null);
  const [installingGithubApp, setInstallingGithubApp] = useState(false);
  const [members, setMembers] = useState<WorkspaceMemberManagement[]>([]);
  const [invites, setInvites] = useState<WorkspaceInvite[]>([]);
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<WorkspaceInviteRole>("VIEWER");
  const [createdInviteUrl, setCreatedInviteUrl] = useState<string | null>(null);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [savingInvite, setSavingInvite] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [confirmationName, setConfirmationName] = useState("");
  const [deletingWorkspace, setDeletingWorkspace] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setGithubInstallation(null);
    setGithubError(null);
    void getWorkspaceGithubAppInstallation(workspace.id)
      .then((installation) => { if (active) setGithubInstallation(installation); })
      .catch((error) => { if (active) setGithubError(error instanceof Error ? error.message : "Não foi possível carregar a integração GitHub."); });
    return () => { active = false; };
  }, [workspace.id]);

  useEffect(() => { let active = true; void Promise.all([listWorkspaceMembersForManagement(workspace.id), listWorkspaceInvites(workspace.id)]).then(([memberItems, inviteItems]) => { if (active) { setMembers(memberItems); setInvites(inviteItems); } }).catch((error) => { if (active) setInviteError(error instanceof Error ? error.message : "Não foi possível carregar membros e convites."); }); return () => { active = false; }; }, [workspace.id]);

  useEffect(() => {
    const result = new URLSearchParams(window.location.search).get("githubApp");
    if (!result) return;
    window.history.replaceState({}, "", window.location.pathname);
    if (result === "installed") setGithubSuccess("GitHub App instalada com sucesso.");
    if (result === "error") setGithubError("Não foi possível concluir a instalação da GitHub App.");
  }, []);

  function handleInstallGithubApp() {
    setInstallingGithubApp(true);
    setGithubError(null);
    setGithubSuccess(null);
    window.location.assign(getWorkspaceGithubAppInstallUrl(workspace.id));
  }

  async function handleInvite(event: FormEvent<HTMLFormElement>) { event.preventDefault(); setSavingInvite(true); setInviteError(null); setCreatedInviteUrl(null); try { const invite = await createWorkspaceInvite(workspace.id, { email: inviteEmail, role: inviteRole }); setInvites((current) => [invite, ...current]); setInviteEmail(""); setCreatedInviteUrl(invite.inviteUrl); } catch (error) { setInviteError(error instanceof Error ? error.message : "Não foi possível criar o convite."); } finally { setSavingInvite(false); } }
  async function handleRevoke(inviteId: string) { setInviteError(null); try { await revokeWorkspaceInvite(workspace.id, inviteId); setInvites((current) => current.map((invite) => invite.id === inviteId ? { ...invite, revokedAt: new Date().toISOString() } : invite)); } catch (error) { setInviteError(error instanceof Error ? error.message : "Não foi possível revogar o convite."); } }
  async function copyInviteUrl() { if (!createdInviteUrl) return; try { await navigator.clipboard.writeText(createdInviteUrl); } catch { setInviteError("Não foi possível copiar o link."); } }
  function closeDeleteWorkspace() { setDeleteOpen(false); setConfirmationName(""); setDeleteError(null); }
  async function handleDeleteWorkspace(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setDeletingWorkspace(true);
    setDeleteError(null);
    try {
      await deleteWorkspace(workspace.id, confirmationName);
      onWorkspaceDeleted(workspace.id);
    } catch (error) {
      setDeleteError(error instanceof Error ? error.message : "Não foi possível excluir o workspace.");
    } finally {
      setDeletingWorkspace(false);
    }
  }

  return (
    <section className="mx-auto grid w-full max-w-4xl gap-8">
      <header className="grid gap-5 border-b border-border/75 pb-7 sm:flex sm:items-end sm:justify-between">
        <div className="grid max-w-2xl gap-3"><span className="flex items-center gap-2 text-xs font-medium uppercase tracking-[.12em] text-muted-foreground"><GearSix className="h-4 w-4 text-primary" />Workspace</span><div><h1 className="break-words text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">Configurações</h1><p className="mt-2 text-sm leading-6 text-muted-foreground">Contexto e administração de <span className="font-medium text-foreground">{workspace.name}</span>.</p></div></div>
        <Button type="button" variant="ghost" size="sm" className="w-fit" onClick={onReturnToWorkspace}><ArrowLeft className="h-4 w-4" />Voltar ao workspace</Button>
      </header>
      <div className="grid gap-7">
        <section className="grid gap-4 rounded-lg border border-border bg-card p-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end sm:p-5" aria-labelledby="workspace-context-title">
          <div className="min-w-0"><p id="workspace-context-title" className="text-xs font-medium uppercase tracking-[.1em] text-muted-foreground">Contexto do workspace</p><h2 className="mt-1 break-words text-xl font-semibold tracking-tight text-card-foreground">{workspace.name}</h2></div>
          <div className="min-w-0 sm:text-right"><p className="text-xs font-medium uppercase tracking-[.1em] text-muted-foreground">Seu papel</p><p className="mt-1 text-sm font-semibold text-card-foreground">{workspace.role}</p></div>
          <p className="border-t border-border/70 pt-3 text-sm leading-6 text-muted-foreground sm:col-span-2">Administradores podem convidar pessoas para colaborar ou apenas visualizar este workspace.</p>
        </section>

        <section className="grid gap-4" aria-labelledby="workspace-members-title"><div><h2 id="workspace-members-title" className="text-lg font-semibold text-foreground">Membros e convites</h2><p className="mt-1 text-sm text-muted-foreground">Compartilhe acesso sem expor dados sensíveis do workspace.</p></div><article className="grid gap-4 rounded-lg border border-border bg-card p-4 sm:p-5"><div className="grid gap-2">{members.map((member) => <div key={member.userId} className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-border/70 bg-background/45 px-3 py-2"><div className="min-w-0"><p className="break-words text-sm font-medium text-card-foreground">{member.name}</p><p className="break-all text-xs text-muted-foreground">{member.email}</p></div><span className="rounded-md border border-border bg-card px-2 py-1 text-xs text-muted-foreground">{member.role}</span></div>)}</div><form className="grid gap-3 border-t border-border/70 pt-4" onSubmit={handleInvite}><div className="flex flex-wrap items-center justify-between gap-2"><h3 className="text-sm font-semibold text-foreground">Convidar membro</h3><span className="text-xs text-muted-foreground">Expira em 7 dias</span></div><div className="grid gap-2 lg:grid-cols-[minmax(0,1fr)_minmax(180px,240px)_auto]"><input className="h-9 min-w-0 rounded-md border border-input bg-background px-3 text-sm" type="email" value={inviteEmail} onChange={(event) => setInviteEmail(event.target.value)} placeholder="email@exemplo.com" required /><select className="h-9 min-w-0 rounded-md border border-input bg-background px-3 text-sm" value={inviteRole} onChange={(event) => setInviteRole(event.target.value as WorkspaceInviteRole)}><option value="ADMIN">Administrador — pode visualizar e alterar o conteúdo</option><option value="VIEWER">Somente leitura — pode visualizar o workspace</option></select><Button type="submit" size="sm" className="w-full lg:w-fit" disabled={savingInvite}><Plus className="h-4 w-4" />{savingInvite ? "Criando..." : "Convidar"}</Button></div></form>{createdInviteUrl ? <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-primary/25 bg-primary/5 p-3 text-sm"><span className="min-w-0 break-all text-muted-foreground">Link criado. Copie e envie ao convidado.</span><Button type="button" variant="ghost" size="sm" onClick={() => void copyInviteUrl()}><Copy className="h-4 w-4" />Copiar link</Button></div> : null}{inviteError ? <p className="rounded-md border border-destructive/25 bg-destructive/5 px-3 py-2 text-sm text-destructive" role="alert">{inviteError}</p> : null}<div className="grid gap-2 border-t border-border/70 pt-4"><h3 className="text-sm font-semibold text-foreground">Convites</h3>{invites.length ? invites.map((invite) => <div key={invite.id} className="flex flex-wrap items-center justify-between gap-3 rounded-md px-1 py-2 text-sm"><div className="min-w-0"><p className="break-all font-medium text-card-foreground">{invite.email}</p><p className="text-xs text-muted-foreground">{invite.role} · expira em {new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium" }).format(new Date(invite.expiresAt))}</p></div>{!invite.acceptedAt && !invite.revokedAt ? <Button type="button" variant="ghost" size="sm" className="text-destructive" onClick={() => void handleRevoke(invite.id)}><Trash className="h-4 w-4" />Revogar</Button> : <span className="text-xs text-muted-foreground">{invite.acceptedAt ? "Aceito" : "Revogado"}</span>}</div>) : <p className="text-sm text-muted-foreground">Nenhum convite criado.</p>}</div></article></section>

        <section className="grid gap-4" aria-labelledby="workspace-integrations-title">
          <div className="flex items-center gap-2"><Plug className="h-4 w-4 text-primary" aria-hidden="true" /><div><h2 id="workspace-integrations-title" className="text-lg font-semibold text-foreground">Integrações</h2><p className="mt-1 text-sm text-muted-foreground">Conexões compartilhadas para o workspace.</p></div></div>
          <article className="grid gap-3 rounded-lg border border-border bg-card p-4 sm:grid-cols-[auto_minmax(0,1fr)_auto] sm:items-center sm:p-5">
            <div className="grid h-10 w-10 place-items-center rounded-lg border border-border bg-muted text-foreground"><GithubLogo className="h-5 w-5" weight="bold" aria-hidden="true" /></div>
            <div className="min-w-0"><h3 className="text-base font-semibold text-card-foreground">GitHub App</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">Autorize este workspace em uma conta pessoal ou organização.</p>{githubInstallation?.installed ? <p className="mt-1 text-sm text-muted-foreground">Conta instalada: <span className="font-medium text-card-foreground">{githubInstallation.accountLogin}</span> ({githubInstallation.accountType === "ORGANIZATION" ? "organização" : "pessoal"})</p> : null}{githubError ? <p className="mt-2 text-sm text-destructive" role="alert">{githubError}</p> : null}{githubSuccess ? <p className="mt-2 text-sm text-emerald-700 dark:text-emerald-300" role="status">{githubSuccess}</p> : null}</div>
            <div className="flex w-fit flex-wrap items-center gap-2"><span className={`rounded-full border px-2.5 py-1 text-xs font-medium ${githubInstallation?.installed ? "border-primary/25 bg-primary/10 text-primary" : "border-border bg-muted text-muted-foreground"}`}>{githubInstallation?.installed ? "Instalado" : githubInstallation ? "Não instalado" : "Carregando..."}</span>{githubInstallation && !githubInstallation.installed ? <Button type="button" size="sm" onClick={handleInstallGithubApp} disabled={installingGithubApp}>{installingGithubApp ? "Redirecionando..." : "Instalar GitHub App"}</Button> : null}</div>
          </article>
        </section>
        {workspace.role === "OWNER" ? <section className="grid gap-4 border-t border-destructive/25 pt-6" aria-labelledby="workspace-danger-title">
          <div><h2 id="workspace-danger-title" className="text-lg font-semibold text-foreground">Zona de perigo</h2><p className="mt-1 text-sm text-muted-foreground">A exclusão remove permanentemente os projetos, dados operacionais e integrações deste workspace.</p></div>
          <article className="grid gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4 sm:p-5"><div><h3 className="text-base font-semibold text-card-foreground">Excluir workspace</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">Esta ação não pode ser desfeita. Capas vinculadas aos projetos também serão removidas.</p></div>{!deleteOpen ? <Button type="button" variant="outline" size="sm" className="w-fit border-destructive/40 text-destructive hover:bg-destructive/10 hover:text-destructive" onClick={() => setDeleteOpen(true)}><Trash className="h-4 w-4" />Excluir workspace</Button> : <form className="grid gap-3 border-t border-destructive/20 pt-3" onSubmit={handleDeleteWorkspace}><label className="grid gap-2 text-sm font-medium text-foreground">Digite <span className="break-words font-semibold">{workspace.name}</span> para confirmar<input className="h-9 rounded-md border border-input bg-background px-3 text-sm font-normal" value={confirmationName} onChange={(event) => setConfirmationName(event.target.value)} autoComplete="off" /></label>{deleteError ? <p className="rounded-md border border-destructive/25 bg-background/70 px-3 py-2 text-sm text-destructive" role="alert">{deleteError}</p> : null}<div className="flex flex-wrap gap-2"><Button type="submit" size="sm" variant="destructive" disabled={deletingWorkspace || confirmationName !== workspace.name}>{deletingWorkspace ? "Excluindo..." : "Excluir definitivamente"}</Button><Button type="button" size="sm" variant="ghost" onClick={closeDeleteWorkspace} disabled={deletingWorkspace}>Cancelar</Button></div></form>}</article>
        </section> : null}
      </div>
    </section>
  );
}
