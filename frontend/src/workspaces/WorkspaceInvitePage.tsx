import { type FormEvent, useEffect, useState } from "react";
import { ArrowRight } from "@phosphor-icons/react";
import { AuthShell, Field } from "@/auth/LoginPage";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/auth/AuthContext";
import { acceptWorkspaceInvite, getWorkspaceInvite, type WorkspaceInviteDetails } from "@/workspaces/workspaceApi";

export function WorkspaceInvitePage({ token, onAccepted, onShowLogin }: { token: string; onAccepted: (workspaceId: string) => void; onShowLogin: () => void }) {
  const { status, user, registerWorkspaceInvite } = useAuth();
  const [invite, setInvite] = useState<WorkspaceInviteDetails | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => { let active = true; setInvite(null); setError(null); if (!token) { setError("Convite inválido ou incompleto."); return; } void getWorkspaceInvite(token).then((value) => { if (active) setInvite(value); }).catch((reason) => { if (active) setError(reason instanceof Error ? reason.message : "Convite inválido."); }); return () => { active = false; }; }, [token]);
  async function accept() { if (!token) return; setSubmitting(true); setError(null); try { const result = await acceptWorkspaceInvite(token); onAccepted(result.workspaceId); } catch (reason) { setError(reason instanceof Error ? reason.message : "Não foi possível aceitar o convite."); } finally { setSubmitting(false); } }
  async function register(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (!token) return; setSubmitting(true); setError(null); try { const result = await registerWorkspaceInvite(token, { name, password }); onAccepted(result.workspaceId); } catch (reason) { setError(reason instanceof Error ? reason.message : "Não foi possível criar a conta."); } finally { setSubmitting(false); } }
  const emailMatches = invite && user?.email.toLowerCase() === invite.email.toLowerCase();
  return <AuthShell eyebrow="No8do" title="Convite para workspace">{error ? <p className="text-sm text-destructive" role="alert">{error}</p> : null}{invite ? <div className="flex flex-col gap-5"><div className="rounded-md border border-border bg-card p-4 text-sm"><p className="font-semibold text-foreground">{invite.workspaceName}</p><p className="mt-1 text-muted-foreground">Permissão: {invite.role === "ADMIN" ? "Administrador" : "Somente leitura"}</p><p className="mt-1 text-muted-foreground">Convite para {invite.email}</p></div>{status === "authenticated" ? <>{emailMatches ? <Button type="button" className="auth-submit-button w-full" disabled={submitting} onClick={() => void accept()}>{submitting ? "Aceitando..." : "Aceitar convite"}<ArrowRight className="h-4 w-4" /></Button> : <p className="text-sm text-destructive">Este convite foi enviado para outro e-mail. Entre com a conta correta.</p>}</> : <><form className="flex flex-col gap-4" onSubmit={register}><Field label="E-mail" name="email" type="email" value={invite.email} onChange={() => undefined} autoComplete="email" readOnly /><Field label="Nome" name="name" type="text" value={name} onChange={setName} autoComplete="name" /><Field label="Senha" name="password" type="password" value={password} onChange={setPassword} autoComplete="new-password" /><Button type="submit" className="auth-submit-button w-full" disabled={submitting}>{submitting ? "Criando..." : "Criar conta e aceitar"}<ArrowRight className="h-4 w-4" /></Button></form><Button type="button" variant="ghost" onClick={onShowLogin}>Já tenho uma conta</Button></>}</div> : null}</AuthShell>;
}
