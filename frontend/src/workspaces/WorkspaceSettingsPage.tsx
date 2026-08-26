import { useEffect, useState } from "react";
import { ArrowLeft, GearSix, GithubLogo, Plug } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { getWorkspaceGithubAppInstallation, getWorkspaceGithubAppInstallUrl, type Workspace, type WorkspaceGithubAppInstallation } from "@/workspaces/workspaceApi";

export function WorkspaceSettingsPage({ workspace, onReturnToWorkspace }: { workspace: Workspace; onReturnToWorkspace: () => void }) {
  const [githubInstallation, setGithubInstallation] = useState<WorkspaceGithubAppInstallation | null>(null);
  const [githubError, setGithubError] = useState<string | null>(null);
  const [githubSuccess, setGithubSuccess] = useState<string | null>(null);
  const [installingGithubApp, setInstallingGithubApp] = useState(false);

  useEffect(() => {
    let active = true;
    setGithubInstallation(null);
    setGithubError(null);
    void getWorkspaceGithubAppInstallation(workspace.id)
      .then((installation) => { if (active) setGithubInstallation(installation); })
      .catch((error) => { if (active) setGithubError(error instanceof Error ? error.message : "Não foi possível carregar a integração GitHub."); });
    return () => { active = false; };
  }, [workspace.id]);

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

  return (
    <section className="mx-auto grid w-full max-w-4xl gap-8">
      <header className="grid gap-5 border-b border-border/75 pb-7 sm:flex sm:items-end sm:justify-between">
        <div className="grid max-w-2xl gap-3"><span className="flex items-center gap-2 text-xs font-medium uppercase tracking-[.12em] text-muted-foreground"><GearSix className="h-4 w-4 text-primary" />Workspace</span><div><h1 className="break-words text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">Configurações</h1><p className="mt-2 text-sm leading-6 text-muted-foreground">Contexto e administração de <span className="font-medium text-foreground">{workspace.name}</span>.</p></div></div>
        <Button type="button" variant="ghost" size="sm" className="w-fit" onClick={onReturnToWorkspace}><ArrowLeft className="h-4 w-4" />Voltar ao workspace</Button>
      </header>
      <div className="grid gap-8">
        <section className="grid gap-5 rounded-xl border border-border bg-card p-5 shadow-[0_18px_52px_-42px_hsl(var(--foreground))] sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] sm:p-6" aria-labelledby="workspace-context-title">
          <div className="sm:col-span-2"><p id="workspace-context-title" className="text-xs font-medium uppercase tracking-[.1em] text-muted-foreground">Contexto do workspace</p><p className="mt-2 text-sm leading-6 text-muted-foreground">Informações e permissões disponíveis nesta área.</p></div>
          <div><p className="text-xs font-medium uppercase tracking-[.1em] text-muted-foreground">Workspace</p><p className="mt-2 break-words text-lg font-semibold text-card-foreground">{workspace.name}</p></div>
          <div><p className="text-xs font-medium uppercase tracking-[.1em] text-muted-foreground">Seu papel</p><p className="mt-2 text-lg font-semibold text-card-foreground">{workspace.role}</p></div>
          <p className="sm:col-span-2 border-t border-border/70 pt-4 text-sm leading-6 text-muted-foreground">Alterações de nome, membros e permissões dependem de suporte administrativo no backend e não estão disponíveis nesta etapa.</p>
        </section>

        <section className="grid gap-4" aria-labelledby="workspace-integrations-title">
          <div className="flex items-center gap-2"><Plug className="h-4 w-4 text-primary" aria-hidden="true" /><div><h2 id="workspace-integrations-title" className="text-lg font-semibold text-foreground">Integrações</h2><p className="mt-1 text-sm text-muted-foreground">Conexões compartilhadas para o workspace.</p></div></div>
          <article className="grid gap-4 rounded-xl border border-border bg-card p-5 shadow-[0_18px_52px_-42px_hsl(var(--foreground))] sm:grid-cols-[auto_minmax(0,1fr)_auto] sm:items-center sm:p-6">
            <div className="grid h-11 w-11 place-items-center rounded-lg border border-border bg-muted text-foreground"><GithubLogo className="h-6 w-6" weight="bold" aria-hidden="true" /></div>
            <div className="min-w-0"><h3 className="text-base font-semibold text-card-foreground">GitHub</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">Instale a GitHub App para autorizar este workspace em uma conta pessoal ou organização. Repositórios não são listados nem importados nesta etapa.</p>{githubInstallation?.installed ? <p className="mt-2 text-sm text-muted-foreground">Conta instalada: <span className="font-medium text-card-foreground">{githubInstallation.accountLogin}</span> ({githubInstallation.accountType === "ORGANIZATION" ? "organização" : "pessoal"})</p> : null}{githubError ? <p className="mt-2 text-sm text-destructive" role="alert">{githubError}</p> : null}{githubSuccess ? <p className="mt-2 text-sm text-emerald-700 dark:text-emerald-300" role="status">{githubSuccess}</p> : null}</div>
            <div className="flex w-fit flex-wrap items-center gap-2"><span className={`rounded-full border px-2.5 py-1 text-xs font-medium ${githubInstallation?.installed ? "border-primary/25 bg-primary/10 text-primary" : "border-border bg-muted text-muted-foreground"}`}>{githubInstallation?.installed ? "Instalado" : githubInstallation ? "Não instalado" : "Carregando..."}</span>{githubInstallation && !githubInstallation.installed ? <Button type="button" size="sm" onClick={handleInstallGithubApp} disabled={installingGithubApp}>{installingGithubApp ? "Redirecionando..." : "Instalar GitHub App"}</Button> : null}</div>
          </article>
        </section>
      </div>
    </section>
  );
}
