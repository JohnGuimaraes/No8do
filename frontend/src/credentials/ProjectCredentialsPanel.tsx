import { FormEvent, useEffect, useRef, useState } from "react";
import { Copy, Eye, EyeSlash, Key, Plus } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { createProjectCredential, listProjectCredentials, revealProjectCredential, type ProjectCredential, type ProjectCredentialType } from "@/credentials/credentialApi";

type ProjectCredentialsPanelProps = {
  workspaceId: string;
  projectId: string;
  revealedCredentials: Record<string, string>;
  onRevealedCredentialsChange: (credentials: Record<string, string>) => void;
};

const CREDENTIAL_TYPES: Array<{ value: ProjectCredentialType; label: string }> = [
  { value: "PASSWORD", label: "Senha" }, { value: "API_KEY", label: "Chave" },
  { value: "TOKEN", label: "Token" }, { value: "OTHER", label: "Outro" },
];
const MAX_LABEL_LENGTH = 180;
const MAX_USERNAME_LENGTH = 255;
const MAX_SECRET_LENGTH = 20_000;
const MAX_NOTES_LENGTH = 500;

export function ProjectCredentialsPanel({ workspaceId, projectId, revealedCredentials, onRevealedCredentialsChange }: ProjectCredentialsPanelProps) {
  const [credentials, setCredentials] = useState<ProjectCredential[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [revealingId, setRevealingId] = useState<string | null>(null);
  const [copiedCredentialId, setCopiedCredentialId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [label, setLabel] = useState("");
  const [type, setType] = useState<ProjectCredentialType>("PASSWORD");
  const [username, setUsername] = useState("");
  const [secret, setSecret] = useState("");
  const [notes, setNotes] = useState("");
  const [showFormSecret, setShowFormSecret] = useState(true);
  const copyFeedbackTimer = useRef<number | null>(null);

  useEffect(() => {
    let active = true;
    async function loadCredentials() {
      setLoading(true); setError(null);
      try { const response = await listProjectCredentials(workspaceId, projectId); if (active) setCredentials(response); }
      catch (err) { if (active) setError(err instanceof Error ? err.message : "Não foi possível carregar o cofre."); }
      finally { if (active) setLoading(false); }
    }
    void loadCredentials();
    return () => { active = false; };
  }, [projectId, workspaceId]);

  useEffect(() => () => { if (copyFeedbackTimer.current) window.clearTimeout(copyFeedbackTimer.current); }, []);

  function resetForm() { setLabel(""); setType("PASSWORD"); setUsername(""); setSecret(""); setNotes(""); setShowFormSecret(true); setFormError(null); }
  function hideCredential(credentialId: string) { onRevealedCredentialsChange(Object.fromEntries(Object.entries(revealedCredentials).filter(([id]) => id !== credentialId))); setCopiedCredentialId(null); }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedLabel = label.trim();
    const normalizedUsername = username.trim();
    const normalizedNotes = notes.trim();
    if (!normalizedLabel) { setFormError("Informe um nome para a credencial."); return; }
    if (!secret) { setFormError("Informe a senha ou segredo da credencial."); return; }
    if (normalizedLabel.length > MAX_LABEL_LENGTH) { setFormError("O nome deve ter no máximo 180 caracteres."); return; }
    if (normalizedUsername.length > MAX_USERNAME_LENGTH) { setFormError("O usuário deve ter no máximo 255 caracteres."); return; }
    if (secret.length > MAX_SECRET_LENGTH) { setFormError("O segredo deve ter no máximo 20.000 caracteres."); return; }
    if (normalizedNotes.length > MAX_NOTES_LENGTH) { setFormError("A observação deve ter no máximo 500 caracteres."); return; }
    setCreating(true); setFormError(null);
    try {
      const created = await createProjectCredential(workspaceId, projectId, { label: normalizedLabel, type, secret, ...(normalizedUsername ? { username: normalizedUsername } : {}), ...(normalizedNotes ? { notes: normalizedNotes } : {}) });
      setCredentials((current) => [created, ...current]);
      resetForm();
    } catch (err) { setFormError(err instanceof Error ? err.message : "Não foi possível criar a credencial."); }
    finally { setCreating(false); }
  }

  async function reveal(credentialId: string) {
    if (revealedCredentials[credentialId]) return;
    setRevealingId(credentialId); setError(null);
    try { const response = await revealProjectCredential(workspaceId, projectId, credentialId); onRevealedCredentialsChange({ ...revealedCredentials, [response.id]: response.secret }); }
    catch (err) { setError(err instanceof Error ? err.message : "Não foi possível revelar a credencial."); }
    finally { setRevealingId(null); }
  }

  async function copyCredential(credentialId: string) {
    let value = revealedCredentials[credentialId];
    if (!value) {
      setRevealingId(credentialId); setError(null);
      try { const response = await revealProjectCredential(workspaceId, projectId, credentialId); value = response.secret; onRevealedCredentialsChange({ ...revealedCredentials, [response.id]: response.secret }); }
      catch (err) { setError(err instanceof Error ? err.message : "Não foi possível copiar."); return; }
      finally { setRevealingId(null); }
    }
    try {
      await navigator.clipboard.writeText(value);
      setCopiedCredentialId(credentialId);
      if (copyFeedbackTimer.current) window.clearTimeout(copyFeedbackTimer.current);
      copyFeedbackTimer.current = window.setTimeout(() => setCopiedCredentialId(null), 1000);
    } catch { setError("Não foi possível copiar."); }
  }

  return <div className="min-w-0"><header className="mb-6"><p className="flex items-center gap-2 text-sm font-semibold text-foreground"><Key className="h-4 w-4 text-primary" />Cofre</p><p className="mt-1 text-sm text-muted-foreground">Credenciais operacionais do projeto, disponíveis quando você precisar.</p></header><form className="grid min-w-0 gap-3 border-b border-border/70 pb-7" onSubmit={handleSubmit}><div className="grid gap-3 sm:grid-cols-2"><Field label="Nome"><input className={inputClassName} value={label} onChange={(event) => setLabel(event.target.value)} maxLength={MAX_LABEL_LENGTH} placeholder="Ex.: GitHub Produção" required /></Field><Field label="Tipo"><select className={inputClassName} value={type} onChange={(event) => setType(event.target.value as ProjectCredentialType)}>{CREDENTIAL_TYPES.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></Field></div><Field label="Usuário / identificador"><input className={inputClassName} value={username} onChange={(event) => setUsername(event.target.value)} maxLength={MAX_USERNAME_LENGTH} placeholder="nome@empresa.com" /></Field><Field label="Senha / segredo"><div className="flex gap-2"><input className={inputClassName} value={secret} onChange={(event) => setSecret(event.target.value)} maxLength={MAX_SECRET_LENGTH} type={showFormSecret ? "text" : "password"} placeholder="Informe o valor" autoComplete="off" required /><Button type="button" variant="outline" size="sm" onClick={() => setShowFormSecret((current) => !current)}>{showFormSecret ? "Ocultar" : "Mostrar"}</Button></div></Field><Field label="Observação"><textarea className="min-h-20 w-full min-w-0 resize-y rounded-md border border-input bg-card px-3 py-2 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2" value={notes} onChange={(event) => setNotes(event.target.value)} maxLength={MAX_NOTES_LENGTH} placeholder="Observação curta opcional" /></Field><p className="text-xs text-muted-foreground">A observação não deve conter senhas, tokens ou chaves.</p>{formError ? <p className="text-sm text-destructive">{formError}</p> : null}<div className="flex flex-wrap justify-stretch gap-2 sm:justify-end"><Button type="button" variant="ghost" onClick={resetForm} disabled={creating}>Cancelar</Button><Button type="submit" className="w-full sm:w-auto" disabled={creating}><Plus className="h-4 w-4" />{creating ? "Salvando..." : "Salvar credencial"}</Button></div></form><section className="mt-7 min-w-0">{loading ? <p className="text-sm text-muted-foreground">Carregando cofre...</p> : null}{error ? <VaultMessage error={error} /> : null}{!loading && !error && credentials.length === 0 ? <div className="py-8 text-center"><p className="text-sm font-medium text-muted-foreground">Sem credenciais ainda</p><p className="mt-1 text-xs text-muted-foreground">Adicione senhas, tokens ou chaves somente quando forem necessários ao projeto.</p></div> : null}{!loading && credentials.length > 0 ? <ol className="grid min-w-0 gap-4 lg:grid-cols-2">{credentials.map((credential) => { const revealedSecret = revealedCredentials[credential.id]; const revealed = Boolean(revealedSecret); return <li key={credential.id} className="min-w-0 rounded-lg border border-border/80 bg-card p-4"><div className="flex min-w-0 flex-wrap items-start justify-between gap-3"><div className="min-w-0"><p className="break-words text-sm font-semibold text-card-foreground">{credential.label}</p><p className="mt-1 text-xs text-muted-foreground">{getCredentialTypeLabel(credential.type)}</p></div></div>{credential.username ? <CredentialValue label="Usuário">{credential.username}</CredentialValue> : null}<CredentialValue label={getSecretLabel(credential.type)}>{revealed ? <span className="break-all font-mono text-sm text-card-foreground">{revealedSecret}</span> : <span className="tracking-[0.18em] text-muted-foreground">••••••••••</span>}</CredentialValue>{credential.notes ? <p className="mt-4 whitespace-pre-wrap break-words text-sm leading-6 text-muted-foreground">{credential.notes}</p> : null}<div className="mt-4 flex flex-wrap gap-2"><Button type="button" variant="outline" size="sm" onClick={() => (revealed ? hideCredential(credential.id) : void reveal(credential.id))} disabled={revealingId === credential.id}>{revealed ? <EyeSlash className="h-4 w-4" /> : <Eye className="h-4 w-4" />}{revealed ? "Ocultar" : revealingId === credential.id ? "Mostrando..." : "Mostrar"}</Button><Button type="button" variant="ghost" size="sm" onClick={() => void copyCredential(credential.id)} disabled={revealingId === credential.id}><Copy className="h-4 w-4" />{copiedCredentialId === credential.id ? "Copiado" : "Copiar"}</Button></div></li>; })}</ol> : null}</section></div>;
}

const inputClassName = "h-9 w-full min-w-0 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2";
function Field({ label, children }: { label: string; children: React.ReactNode }) { return <label className="grid min-w-0 gap-1.5 text-sm font-medium text-foreground"><span>{label}</span>{children}</label>; }
function CredentialValue({ label, children }: { label: string; children: React.ReactNode }) { return <div className="mt-4 min-w-0"><p className="text-[11px] font-medium uppercase text-muted-foreground">{label}</p><div className="mt-1 min-w-0 break-words text-sm text-card-foreground">{children}</div></div>; }
function getCredentialTypeLabel(type: ProjectCredentialType) { return CREDENTIAL_TYPES.find((option) => option.value === type)?.label ?? "Outro"; }
function getSecretLabel(type: ProjectCredentialType) { if (type === "API_KEY") return "Chave"; if (type === "TOKEN") return "Token"; return "Senha"; }
function VaultMessage({ error }: { error: string }) { if (error.includes("Credential vault is not configured")) return <div className="rounded-lg border border-primary/25 bg-primary/[0.04] p-4"><p className="font-medium text-foreground">Cofre indisponível</p><p className="mt-1 text-sm text-muted-foreground">A chave de criptografia do servidor ainda não foi configurada.</p><code className="mt-3 block break-all text-xs text-foreground">NO8DO_CREDENTIALS_MASTER_KEY</code></div>; return <p className="text-sm text-destructive">{error}</p>; }
