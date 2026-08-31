import { useEffect, useState, type FormEvent, type MouseEvent } from "react";
import { ArrowLeft, Desktop, GithubLogo, IdentificationCard, LockKey, Moon, SignOut, SlidersHorizontal, Sun, Trash, UserCircle, WarningCircle } from "@phosphor-icons/react";
import { type AuthUser, useAuth } from "@/auth/AuthContext";
import { Button } from "@/components/ui/button";
import { disconnectUserGithub, getUserGithubConnection, type UserGithubConnection } from "@/account/githubApi";
import { getApiUrl } from "@/lib/api";

type AccountPageProps = {
  user: AuthUser | null;
  onReturnToWorkspace: () => void;
};

type ThemePreference = "system" | "light" | "dark";

const THEME_STORAGE_KEY = "no8do-theme";

function getThemePreference(): ThemePreference {
  const value = localStorage.getItem(THEME_STORAGE_KEY);
  return value === "light" || value === "dark" || value === "system" ? value : "system";
}

function applyTheme(preference: ThemePreference) {
  const dark = preference === "dark" || (preference === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.classList.toggle("dark", dark);
  document.documentElement.style.colorScheme = dark ? "dark" : "light";
}

function PageHeader({ icon: Icon, eyebrow, title, description, onReturnToWorkspace }: {
  icon: typeof IdentificationCard;
  eyebrow: string;
  title: string;
  description: string;
  onReturnToWorkspace: () => void;
}) {
  return (
    <header className="grid gap-5 border-b border-border/75 pb-7 sm:flex sm:items-end sm:justify-between">
      <div className="grid max-w-2xl gap-3">
        <span className="flex items-center gap-2 text-xs font-medium uppercase tracking-[.12em] text-muted-foreground"><Icon className="h-4 w-4 text-primary" />{eyebrow}</span>
        <div><h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">{title}</h1><p className="mt-2 text-sm leading-6 text-muted-foreground">{description}</p></div>
      </div>
      <Button type="button" variant="ghost" size="sm" className="w-fit" onClick={onReturnToWorkspace}><ArrowLeft className="h-4 w-4" />Voltar ao workspace</Button>
    </header>
  );
}

export function ProfilePage({ user, onReturnToWorkspace, onLogout, onDeleteAccount, loggingOut }: AccountPageProps & { onLogout: () => void; onDeleteAccount: (input: { confirmationEmail: string; confirmationText: string }) => Promise<void>; loggingOut: boolean }) {
  const { updateProfile } = useAuth();
  const initial = user?.name.trim().charAt(0).toLocaleUpperCase("pt-BR") || "N";
  const [name, setName] = useState(user?.name ?? "");
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileSuccess, setProfileSuccess] = useState<string | null>(null);
  const [githubConnection, setGithubConnection] = useState<UserGithubConnection | null>(null);
  const [githubError, setGithubError] = useState<string | null>(null);
  const [githubSuccess, setGithubSuccess] = useState<string | null>(null);
  const [disconnectingGithub, setDisconnectingGithub] = useState(false);
  const [confirmationEmail, setConfirmationEmail] = useState("");
  const [confirmationText, setConfirmationText] = useState("");
  const [deletingAccount, setDeletingAccount] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setGithubConnection(null);
    setGithubError(null);
    setGithubSuccess(null);
    void getUserGithubConnection()
      .then((connection) => { if (active) setGithubConnection(connection); })
      .catch((error) => { if (active) setGithubError(error instanceof Error ? error.message : "Não foi possível carregar a conexão GitHub."); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    const result = new URLSearchParams(window.location.search).get("github");
    if (!result) return;
    window.history.replaceState({}, "", window.location.pathname);
    if (result === "error") setGithubError("Não foi possível concluir a conexão com o GitHub.");
  }, []);

  useEffect(() => {
    setName(user?.name ?? "");
  }, [user?.name]);

  async function handleDisconnectGithub(event: MouseEvent<HTMLButtonElement>) {
    event.preventDefault();
    event.stopPropagation();
    setDisconnectingGithub(true);
    setGithubError(null);
    setGithubSuccess(null);
    try {
      await disconnectUserGithub();
      setGithubConnection({ connected: false, login: null, avatarUrl: null, connectedAt: null });
      setGithubSuccess("GitHub desconectado com sucesso.");
    } catch (error) {
      setGithubError(error instanceof Error ? error.message : "Não foi possível desconectar o GitHub.");
    } finally {
      setDisconnectingGithub(false);
    }
  }

  async function handleDeleteAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (deletingAccount) return;
    setDeletingAccount(true);
    setDeleteError(null);
    try {
      await onDeleteAccount({ confirmationEmail, confirmationText });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Não foi possível excluir a conta.";
      setDeleteError(message === "Delete owned workspaces before deleting your account" ? "Exclua seus próprios workspaces antes de excluir sua conta." : message);
    } finally {
      setDeletingAccount(false);
    }
  }

  async function handleUpdateProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedName = name.trim();
    if (savingProfile || !normalizedName || normalizedName === user?.name) return;
    setSavingProfile(true);
    setProfileError(null);
    setProfileSuccess(null);
    try {
      await updateProfile({ name: normalizedName });
      setProfileSuccess("Nome atualizado com sucesso.");
    } catch (error) {
      setProfileError(error instanceof Error ? error.message : "Não foi possível atualizar seu nome.");
    } finally {
      setSavingProfile(false);
    }
  }

  const emailMatches = confirmationEmail.trim().toLocaleLowerCase("pt-BR") === (user?.email ?? "").toLocaleLowerCase("pt-BR");
  const deleteDisabled = deletingAccount || !emailMatches || confirmationText !== "EXCLUIR";
  const profileSaveDisabled = savingProfile || !name.trim() || name.trim() === user?.name;

  return (
    <section className="mx-auto grid w-full max-w-4xl gap-8">
      <PageHeader icon={IdentificationCard} eyebrow="Conta" title="Perfil" description="Sua identidade e acesso ao workspace No8do." onReturnToWorkspace={onReturnToWorkspace} />

      <section className="relative overflow-hidden rounded-xl border border-border bg-card p-5 shadow-[0_24px_70px_-48px_hsl(var(--foreground))] sm:p-7">
        <div className="absolute right-0 top-0 h-32 w-32 border-b border-l border-sky-500/15 bg-[linear-gradient(135deg,transparent_48%,rgba(14,165,233,0.08)_49%,transparent_50%)] dark:border-cyan-400/20 dark:bg-[linear-gradient(135deg,transparent_48%,rgba(34,211,238,0.1)_49%,transparent_50%)]" aria-hidden="true" />
        <div className="relative flex min-w-0 flex-col gap-5 sm:flex-row sm:items-center">
          <div className="grid h-20 w-20 shrink-0 place-items-center rounded-2xl border border-cyan-600/25 bg-[linear-gradient(145deg,rgba(224,242,254,0.95),rgba(236,253,245,0.9))] text-3xl font-semibold text-sky-800 shadow-[0_18px_34px_-26px_rgba(3,105,161,0.75)] dark:border-cyan-400/35 dark:bg-[linear-gradient(145deg,rgba(21,57,75,0.95),rgba(24,54,57,0.9))] dark:text-cyan-100 dark:shadow-[0_18px_34px_-26px_rgba(34,211,238,0.35)]">{initial}</div>
          <div className="min-w-0"><p className="flex items-center gap-2 text-xs font-medium uppercase tracking-[.12em] text-muted-foreground"><UserCircle className="h-4 w-4 text-primary" />Conta No8do</p><h2 className="mt-2 break-words text-2xl font-semibold tracking-tight text-card-foreground">{user?.name ?? "Usuário"}</h2><p className="mt-1 break-all text-sm text-muted-foreground">{user?.email ?? "E-mail não disponível"}</p></div>
        </div>
      </section>

      <section className="grid gap-4" aria-labelledby="profile-account-title">
        <header><p className="text-xs font-medium uppercase tracking-[.12em] text-primary">Identidade</p><h2 id="profile-account-title" className="mt-1 text-xl font-semibold tracking-tight text-foreground">Conta</h2></header>
        <div className="grid gap-px overflow-hidden rounded-xl border border-border bg-border sm:grid-cols-2">
          <form className="bg-card p-5" onSubmit={(event) => void handleUpdateProfile(event)}><label className="grid gap-2 text-xs font-medium uppercase tracking-[.1em] text-muted-foreground">Nome<input className="min-h-10 rounded-md border border-input bg-background px-3 py-2 text-sm font-normal normal-case tracking-normal text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring" value={name} onChange={(event) => setName(event.target.value)} autoComplete="name" disabled={savingProfile} maxLength={160} /></label>{profileError ? <p className="mt-2 text-sm normal-case tracking-normal text-destructive" role="alert">{profileError}</p> : null}{profileSuccess ? <p className="mt-2 text-sm normal-case tracking-normal text-emerald-700 dark:text-emerald-300" role="status">{profileSuccess}</p> : null}<Button type="submit" size="sm" className="mt-3" disabled={profileSaveDisabled}>{savingProfile ? "Salvando..." : "Salvar alterações"}</Button></form>
          <div className="bg-card p-5"><p className="text-xs font-medium uppercase tracking-[.1em] text-muted-foreground">E-mail</p><p className="mt-2 break-all text-base font-semibold text-card-foreground">{user?.email ?? "E-mail não disponível"}</p></div>
        </div>
      </section>

      <section className="grid gap-4" aria-labelledby="profile-security-title">
        <header><p className="text-xs font-medium uppercase tracking-[.12em] text-primary">Proteção</p><h2 id="profile-security-title" className="mt-1 text-xl font-semibold tracking-tight text-foreground">Segurança</h2></header>
        <div className="grid overflow-hidden rounded-xl border border-border bg-card">
          <SecurityUnavailable title="Alterar senha" description="A alteração de senha será disponibilizada quando houver suporte seguro no backend." />
          <SecurityUnavailable title="Sessões" description="O gerenciamento de sessões será disponibilizado quando houver suporte no backend." />
        </div>
      </section>

      <section className="grid gap-4" aria-labelledby="profile-github-title">
        <header><p className="text-xs font-medium uppercase tracking-[.12em] text-primary">Integrações</p><h2 id="profile-github-title" className="mt-1 text-xl font-semibold tracking-tight text-foreground">GitHub</h2></header>
        <article className="grid gap-4 rounded-xl border border-border bg-card p-5 shadow-[0_18px_52px_-42px_hsl(var(--foreground))] sm:grid-cols-[auto_minmax(0,1fr)_auto] sm:items-center sm:p-6">
          {githubConnection?.connected && githubConnection.avatarUrl ? <img src={githubConnection.avatarUrl} alt="" className="h-11 w-11 rounded-lg border border-border bg-muted object-cover" referrerPolicy="no-referrer" /> : <span className="grid h-11 w-11 place-items-center rounded-lg border border-border bg-muted text-foreground"><GithubLogo className="h-6 w-6" weight="bold" aria-hidden="true" /></span>}
          <div className="min-w-0"><h3 className="text-base font-semibold text-card-foreground">{githubConnection?.connected ? githubConnection.login : "GitHub não conectado"}</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">{githubConnection?.connected ? "Sua conta pode ser vinculada aos workspaces em que você administra." : "Conecte sua conta uma única vez para usá-la em workspaces autorizados."}</p>{githubError ? <p className="mt-2 text-sm text-destructive" role="alert">{githubError}</p> : null}{githubSuccess ? <p className="mt-2 text-sm text-emerald-700 dark:text-emerald-300" role="status">{githubSuccess}</p> : null}</div>
          <div className="flex w-fit items-center gap-2">{githubConnection?.connected ? <Button type="button" variant="ghost" size="sm" onClick={(event) => void handleDisconnectGithub(event)} disabled={disconnectingGithub}>{disconnectingGithub ? "Desconectando..." : "Desconectar"}</Button> : githubConnection ? <Button type="button" size="sm" onClick={() => window.location.assign(getApiUrl("/api/account/integrations/github/connect"))}>Conectar GitHub</Button> : null}</div>
        </article>
      </section>

      <section className="grid gap-4 border-t border-border/75 pt-6" aria-labelledby="profile-danger-title">
        <header><p className="flex items-center gap-2 text-xs font-medium uppercase tracking-[.12em] text-destructive"><WarningCircle className="h-4 w-4" />Zona de perigo</p><h2 id="profile-danger-title" className="mt-1 text-xl font-semibold tracking-tight text-foreground">Excluir minha conta</h2></header>
        <form className="grid gap-4 rounded-xl border border-destructive/25 bg-card p-5 shadow-[0_18px_52px_-42px_hsl(var(--foreground))] sm:p-6" onSubmit={(event) => void handleDeleteAccount(event)}>
          <p className="text-sm leading-6 text-muted-foreground">Esta ação remove sua conta, seus vínculos pessoais e seu acesso aos workspaces. Conteúdos compartilhados permanecem no workspace com autoria histórica removida.</p>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="grid gap-2 text-sm font-medium text-card-foreground">
              E-mail da conta
              <input className="min-h-10 rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring" value={confirmationEmail} onChange={(event) => setConfirmationEmail(event.target.value)} autoComplete="email" disabled={deletingAccount} />
            </label>
            <label className="grid gap-2 text-sm font-medium text-card-foreground">
              Confirmação
              <input className="min-h-10 rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring" value={confirmationText} onChange={(event) => setConfirmationText(event.target.value)} autoComplete="off" disabled={deletingAccount} />
            </label>
          </div>
          {deleteError ? <p className="text-sm text-destructive" role="alert">{deleteError}</p> : null}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-xs text-muted-foreground">Digite seu e-mail e EXCLUIR para confirmar.</p>
            <Button type="submit" className="border-destructive/35 bg-destructive text-destructive-foreground hover:bg-destructive/90" disabled={deleteDisabled}><Trash className="h-4 w-4" />{deletingAccount ? "Excluindo..." : "Excluir minha conta"}</Button>
          </div>
        </form>
      </section>

      <section className="flex flex-wrap items-center justify-between gap-4 border-t border-border/75 pt-6"><p className="text-sm text-muted-foreground">Encerre esta sessão neste dispositivo.</p><Button type="button" variant="outline" className="border-destructive/35 text-destructive hover:bg-destructive/10 hover:text-destructive" onClick={onLogout} disabled={loggingOut}><SignOut className="h-4 w-4" />{loggingOut ? "Saindo..." : "Sair"}</Button></section>
    </section>
  );
}

function SecurityUnavailable({ title, description }: { title: string; description: string }) {
  return <article className="flex min-w-0 items-start justify-between gap-4 border-b border-border/75 p-5 last:border-b-0"><div className="flex min-w-0 gap-3"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg border border-border bg-muted/60 text-muted-foreground"><LockKey className="h-4 w-4" /></span><div className="min-w-0"><h3 className="text-sm font-semibold text-card-foreground">{title}</h3><p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">{description}</p></div></div><span className="shrink-0 rounded-full border border-border bg-muted/55 px-2.5 py-1 text-[11px] font-medium text-muted-foreground">Indisponível</span></article>;
}

export function PreferencesPage({ onReturnToWorkspace }: AccountPageProps) {
  const [theme, setTheme] = useState<ThemePreference>(getThemePreference);

  useEffect(() => {
    applyTheme(theme);
    if (theme !== "system") return;
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    const handleChange = () => applyTheme("system");
    mediaQuery.addEventListener("change", handleChange);
    return () => mediaQuery.removeEventListener("change", handleChange);
  }, [theme]);

  function selectTheme(nextTheme: ThemePreference) {
    localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
    setTheme(nextTheme);
  }

  return (
    <section className="mx-auto grid w-full max-w-4xl gap-8">
      <PageHeader icon={SlidersHorizontal} eyebrow="Conta" title="Preferências" description="Escolha como o No8do acompanha a aparência do seu dispositivo." onReturnToWorkspace={onReturnToWorkspace} />
      <section className="rounded-lg border border-border bg-card p-4 shadow-[0_14px_42px_-38px_hsl(var(--foreground))] sm:p-5">
        <div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
          <div><p className="text-xs font-medium uppercase tracking-[.12em] text-primary">Aparência</p><h2 className="mt-1 text-lg font-semibold tracking-tight text-card-foreground">Tema</h2></div>
          <p className="text-sm text-muted-foreground">Salvo neste navegador.</p>
        </div>
        <div className="mt-4 grid gap-2 sm:grid-cols-3" role="radiogroup" aria-label="Tema do aplicativo">
          <ThemeOption icon={Desktop} title="Sistema" description="Acompanha o dispositivo" selected={theme === "system"} onSelect={() => selectTheme("system")} />
          <ThemeOption icon={Sun} title="Claro" description="Base off-white" selected={theme === "light"} onSelect={() => selectTheme("light")} />
          <ThemeOption icon={Moon} title="Escuro" description="Grafite técnico" selected={theme === "dark"} onSelect={() => selectTheme("dark")} />
        </div>
      </section>
    </section>
  );
}

function ThemeOption({ icon: Icon, title, description, selected, onSelect }: { icon: typeof Sun; title: string; description: string; selected: boolean; onSelect: () => void }) {
  return <button type="button" role="radio" aria-checked={selected} className={`theme-option ${selected ? "theme-option--selected" : ""}`} onClick={onSelect}><Icon className="h-4 w-4 shrink-0 text-primary" /><span className="min-w-0"><span className="block text-sm font-semibold">{title}</span><span className="mt-0.5 block text-xs leading-4 text-muted-foreground">{description}</span></span></button>;
}
