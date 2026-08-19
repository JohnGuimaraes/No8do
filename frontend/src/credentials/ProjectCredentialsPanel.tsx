import { FormEvent, useEffect, useState } from "react";
import { Eye, EyeSlash, Key, Plus } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import {
  createProjectCredential,
  listProjectCredentials,
  revealProjectCredential,
  type ProjectCredential,
  type ProjectCredentialType,
} from "@/credentials/credentialApi";

type ProjectCredentialsPanelProps = {
  workspaceId: string;
  projectId: string;
};

const CREDENTIAL_TYPES: Array<{ value: ProjectCredentialType; label: string }> = [
  { value: "PASSWORD", label: "Senha" },
  { value: "API_KEY", label: "API key" },
  { value: "TOKEN", label: "Token" },
  { value: "OTHER", label: "Outro" },
];

const MAX_LABEL_LENGTH = 180;
const MAX_USERNAME_LENGTH = 255;
const MAX_SECRET_LENGTH = 20_000;
const MAX_NOTES_LENGTH = 500;

export function ProjectCredentialsPanel({ workspaceId, projectId }: ProjectCredentialsPanelProps) {
  const [credentials, setCredentials] = useState<ProjectCredential[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [revealingId, setRevealingId] = useState<string | null>(null);
  const [revealedCredentialId, setRevealedCredentialId] = useState<string | null>(null);
  const [revealedSecret, setRevealedSecret] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [label, setLabel] = useState("");
  const [type, setType] = useState<ProjectCredentialType>("PASSWORD");
  const [username, setUsername] = useState("");
  const [secret, setSecret] = useState("");
  const [notes, setNotes] = useState("");

  useEffect(() => {
    let active = true;
    clearRevealedSecret();

    async function loadCredentials() {
      setLoading(true);
      setError(null);

      try {
        const response = await listProjectCredentials(workspaceId, projectId);
        if (active) {
          setCredentials(response);
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Nao foi possivel carregar o cofre.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadCredentials();

    return () => {
      active = false;
      clearRevealedSecret();
    };
  }, [workspaceId, projectId]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedLabel = label.trim();
    const normalizedUsername = username.trim();
    const normalizedNotes = notes.trim();

    if (!normalizedLabel) {
      setFormError("Informe um nome para a credencial.");
      return;
    }
    if (!secret) {
      setFormError("Informe o segredo da credencial.");
      return;
    }
    if (normalizedLabel.length > MAX_LABEL_LENGTH) {
      setFormError("O nome deve ter no maximo 180 caracteres.");
      return;
    }
    if (normalizedUsername.length > MAX_USERNAME_LENGTH) {
      setFormError("O usuario deve ter no maximo 255 caracteres.");
      return;
    }
    if (secret.length > MAX_SECRET_LENGTH) {
      setFormError("O segredo deve ter no maximo 20.000 caracteres.");
      return;
    }
    if (normalizedNotes.length > MAX_NOTES_LENGTH) {
      setFormError("A observacao deve ter no maximo 500 caracteres.");
      return;
    }

    setCreating(true);
    setFormError(null);
    clearRevealedSecret();

    try {
      const created = await createProjectCredential(workspaceId, projectId, {
        label: normalizedLabel,
        type,
        secret,
        ...(normalizedUsername ? { username: normalizedUsername } : {}),
        ...(normalizedNotes ? { notes: normalizedNotes } : {}),
      });
      setCredentials((current) => [created, ...current]);
      setLabel("");
      setType("PASSWORD");
      setUsername("");
      setSecret("");
      setNotes("");
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Nao foi possivel criar a credencial.");
    } finally {
      setCreating(false);
    }
  }

  async function handleReveal(credentialId: string) {
    clearRevealedSecret();
    setRevealingId(credentialId);
    setError(null);

    try {
      const response = await revealProjectCredential(workspaceId, projectId, credentialId);
      setRevealedCredentialId(response.id);
      setRevealedSecret(response.secret);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel revelar a credencial.");
    } finally {
      setRevealingId(null);
    }
  }

  function clearRevealedSecret() {
    setRevealedCredentialId(null);
    setRevealedSecret("");
  }

  return (
    <div className="min-w-0 rounded-md border border-border bg-background p-3">
      <div className="mb-3 min-w-0">
        <p className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <Key className="h-4 w-4" />
          Cofre
        </p>
        <p className="mt-1 text-xs text-muted-foreground">
          Guarde credenciais do projeto. O segredo so aparece quando voce pedir.
        </p>
      </div>

      <form className="grid min-w-0 gap-2" onSubmit={handleSubmit}>
        <input
          className="h-9 w-full min-w-0 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={label}
          onChange={(event) => setLabel(event.target.value)}
          maxLength={MAX_LABEL_LENGTH}
          placeholder="Nome da credencial"
          aria-label="Nome da credencial"
        />
        <select
          className="h-9 w-full min-w-0 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={type}
          onChange={(event) => setType(event.target.value as ProjectCredentialType)}
          aria-label="Tipo da credencial"
        >
          {CREDENTIAL_TYPES.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <input
          className="h-9 w-full min-w-0 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          maxLength={MAX_USERNAME_LENGTH}
          placeholder="Usuario opcional"
          aria-label="Usuario da credencial"
        />
        <input
          className="h-9 w-full min-w-0 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={secret}
          onChange={(event) => setSecret(event.target.value)}
          maxLength={MAX_SECRET_LENGTH}
          type="password"
          placeholder="Segredo"
          aria-label="Segredo da credencial"
          autoComplete="off"
        />
        <textarea
          className="min-h-20 w-full min-w-0 resize-y rounded-md border border-input bg-card px-3 py-2 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
          maxLength={MAX_NOTES_LENGTH}
          placeholder="Observacao curta opcional"
          aria-label="Observacao da credencial"
        />
        <p className="text-xs text-muted-foreground">
          Observacao nao deve conter senhas, tokens ou API keys.
        </p>
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
        <div className="flex justify-stretch sm:justify-end">
          <Button type="submit" size="sm" className="w-full sm:w-auto" disabled={creating}>
            <Plus className="h-4 w-4" />
            {creating ? "Salvando..." : "Salvar credencial"}
          </Button>
        </div>
      </form>

      <div className="mt-4 min-w-0">
        {loading ? <p className="text-sm text-muted-foreground">Carregando cofre...</p> : null}
        {error ? <p className="text-sm text-destructive">{error}</p> : null}
        {!loading && !error && credentials.length === 0 ? (
          <div className="rounded-md border border-dashed border-border px-3 py-6 text-center">
            <p className="text-sm font-medium text-muted-foreground">Sem credenciais ainda</p>
            <p className="mt-1 text-xs text-muted-foreground">
              Adicione senhas, tokens ou chaves tecnicas somente quando forem necessarias ao projeto.
            </p>
          </div>
        ) : null}
        {!loading && credentials.length > 0 ? (
          <ol className="min-w-0 space-y-3">
            {credentials.map((credential) => {
              const revealed = revealedCredentialId === credential.id;

              return (
                <li key={credential.id} className="min-w-0 rounded-md border border-border bg-card px-3 py-3">
                  <div className="flex min-w-0 flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="break-words text-sm font-semibold text-card-foreground">
                        {credential.label}
                      </p>
                      <div className="mt-1 flex min-w-0 flex-wrap items-center gap-2 text-[11px] text-muted-foreground">
                        <span>{getCredentialTypeLabel(credential.type)}</span>
                        <span>Autor: {credential.createdByName}</span>
                        <time>{formatDate(credential.createdAt)}</time>
                      </div>
                    </div>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => (revealed ? clearRevealedSecret() : void handleReveal(credential.id))}
                      disabled={revealingId === credential.id}
                    >
                      {revealed ? <EyeSlash className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      {revealed ? "Ocultar" : revealingId === credential.id ? "Abrindo..." : "Mostrar"}
                    </Button>
                  </div>
                  {credential.username ? (
                    <p className="mt-2 break-words text-xs text-muted-foreground">
                      Usuario: {credential.username}
                    </p>
                  ) : null}
                  {credential.notes ? (
                    <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-muted-foreground">
                      {credential.notes}
                    </p>
                  ) : null}
                  {revealed ? (
                    <div className="mt-3 rounded-md border border-border bg-background px-3 py-2">
                      <p className="mb-1 text-[11px] font-medium uppercase text-muted-foreground">Segredo</p>
                      <p className="whitespace-pre-wrap break-words font-mono text-sm text-card-foreground">
                        {revealedSecret}
                      </p>
                    </div>
                  ) : null}
                </li>
              );
            })}
          </ol>
        ) : null}
      </div>
    </div>
  );
}

function getCredentialTypeLabel(type: ProjectCredentialType) {
  return CREDENTIAL_TYPES.find((option) => option.value === type)?.label ?? "Outro";
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
