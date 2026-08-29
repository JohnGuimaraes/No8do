import { type FormEvent, useEffect, useRef, useState } from "react";
import { ArrowRight, EnvelopeSimple, ShieldCheck, UserPlus } from "@phosphor-icons/react";
import { AuthShell, Field } from "@/auth/LoginPage";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/auth/AuthContext";
import { ApiRequestError } from "@/lib/api";
import { acceptWorkspaceInvite, getWorkspaceInvite, type WorkspaceInviteDetails } from "@/workspaces/workspaceApi";

const roleLabel = (role: WorkspaceInviteDetails["role"]) => role === "ADMIN" ? "Administrador" : "Somente leitura";

export function WorkspaceInvitePage({ token, onAccepted, onShowLogin }: { token: string; onAccepted: (workspaceId: string) => void; onShowLogin: () => void }) {
  const { status, user, registerWorkspaceInvite } = useAuth();
  const [invite, setInvite] = useState<WorkspaceInviteDetails | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [existingAccount, setExistingAccount] = useState(false);
  const activeToken = useRef(token);

  useEffect(() => {
    let active = true;
    activeToken.current = token;
    setInvite(null);
    setError(null);
    setName("");
    setPassword("");
    setSubmitting(false);
    setExistingAccount(false);

    if (!token) {
      setError("Convite inválido ou incompleto.");
      return () => { active = false; };
    }

    void getWorkspaceInvite(token)
      .then((value) => { if (active) setInvite(value); })
      .catch((reason) => { if (active) setError(reason instanceof Error ? reason.message : "Convite inválido."); });

    return () => { active = false; };
  }, [token]);

  async function accept() {
    if (!token) return;
    const requestToken = token;
    setSubmitting(true);
    setError(null);

    try {
      const result = await acceptWorkspaceInvite(requestToken);
      if (activeToken.current === requestToken) onAccepted(result.workspaceId);
    } catch (reason) {
      if (activeToken.current === requestToken) setError(reason instanceof Error ? reason.message : "Não foi possível aceitar o convite.");
    } finally {
      if (activeToken.current === requestToken) setSubmitting(false);
    }
  }

  async function register(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;
    const requestToken = token;
    setSubmitting(true);
    setError(null);

    try {
      const result = await registerWorkspaceInvite(requestToken, { name, password });
      if (activeToken.current === requestToken) onAccepted(result.workspaceId);
    } catch (reason) {
      if (activeToken.current !== requestToken) return;
      if (reason instanceof ApiRequestError && reason.status === 409) {
        setExistingAccount(true);
        setName("");
        setPassword("");
        return;
      }
      setError(reason instanceof Error ? reason.message : "Não foi possível criar a conta.");
    } finally {
      if (activeToken.current === requestToken) setSubmitting(false);
    }
  }

  const emailMatches = invite && user?.email.toLowerCase() === invite.email.toLowerCase();
  return <AuthShell eyebrow="No8do" title="Convite para workspace">{error ? <p className="rounded-md border border-destructive/25 bg-destructive/5 px-3 py-2 text-sm text-destructive" role="alert">{error}</p> : null}{invite ? <div className="grid gap-5"><section className="grid gap-4 border-y border-border/75 py-5" aria-labelledby="invite-workspace-title"><div className="flex items-start gap-3"><div className="grid h-9 w-9 shrink-0 place-items-center rounded-md border border-primary/20 bg-primary/10 text-primary"><UserPlus className="h-5 w-5" weight="duotone" aria-hidden="true" /></div><div className="min-w-0"><p className="text-xs font-medium uppercase tracking-[.1em] text-muted-foreground">Você foi convidado para participar de</p><h2 id="invite-workspace-title" className="mt-1 break-words text-xl font-semibold text-foreground">{invite.workspaceName}</h2></div></div><div className="grid gap-3 text-sm sm:grid-cols-2"><div className="flex items-center gap-2"><ShieldCheck className="h-4 w-4 shrink-0 text-primary" /><div><p className="text-xs text-muted-foreground">Permissão concedida</p><span className="mt-1 inline-flex rounded-full border border-primary/25 bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary">{roleLabel(invite.role)}</span></div></div><div className="min-w-0 border-t border-border/70 pt-3 sm:border-l sm:border-t-0 sm:pl-4 sm:pt-0"><div className="flex gap-2"><EnvelopeSimple className="h-4 w-4 shrink-0 text-muted-foreground" /><div className="min-w-0"><p className="text-xs text-muted-foreground">Convidado</p><p className="mt-1 break-all font-medium text-foreground">{invite.email}</p></div></div></div></div><p className="text-xs leading-5 text-muted-foreground">Sua conta será vinculada a este workspace. O e-mail do convite é fixo e a permissão indicada será aplicada ao aceitar.</p></section>{status === "authenticated" ? emailMatches ? <div className="grid gap-3"><p className="text-sm text-muted-foreground">Você está conectado com o e-mail convidado. Confirme para entrar no workspace.</p><Button type="button" className="auth-submit-button w-full" disabled={submitting} onClick={() => void accept()}>{submitting ? "Aceitando..." : "Aceitar convite"}<ArrowRight className="h-4 w-4" /></Button></div> : <p className="rounded-md border border-destructive/25 bg-destructive/5 px-3 py-3 text-sm text-destructive" role="alert">Este convite foi enviado para outro e-mail. Entre com a conta correta para aceitá-lo.</p> : existingAccount ? <div className="grid gap-4"><div className="rounded-md border border-border bg-muted/50 px-3 py-3 text-sm text-muted-foreground"><p className="font-medium text-foreground">Este e-mail já possui uma conta no No8do.</p><p className="mt-1">Entre para aceitar o convite.</p></div><Button type="button" className="auth-submit-button w-full" onClick={onShowLogin}>Entrar e aceitar convite<ArrowRight className="h-4 w-4" /></Button></div> : <div className="grid gap-4"><div><h3 className="text-base font-semibold text-foreground">Criar conta e aceitar convite</h3><p className="mt-1 text-sm text-muted-foreground">Use os dados abaixo para ativar seu acesso ao workspace.</p></div><form className="grid gap-3" onSubmit={register}><Field label="E-mail do convite" name="email" type="email" value={invite.email} onChange={() => undefined} autoComplete="email" readOnly /><Field label="Nome" name="name" type="text" value={name} onChange={setName} autoComplete="name" /><Field label="Senha" name="password" type="password" value={password} onChange={setPassword} autoComplete="new-password" /><Button type="submit" className="auth-submit-button mt-1 w-full" disabled={submitting}>{submitting ? "Criando..." : "Criar conta e aceitar convite"}<ArrowRight className="h-4 w-4" /></Button></form><div className="flex flex-wrap items-center gap-x-2 gap-y-1 border-t border-border/75 pt-4"><p className="text-sm text-muted-foreground">Já possui uma conta?</p><Button type="button" variant="link" size="sm" className="h-auto px-0" onClick={onShowLogin}>Entrar e aceitar convite</Button></div></div>}</div> : null}</AuthShell>;
}
