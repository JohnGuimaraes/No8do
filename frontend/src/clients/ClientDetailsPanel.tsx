import { FormEvent, useState } from "react";
import { X } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { type Client, updateClient } from "@/clients/clientApi";
import { type Project } from "@/projects/projectApi";
import { getProjectStatusLabel } from "@/projects/projectStatus";

type ClientDetailsPanelProps = {
  client: Client;
  projects: Project[];
  onClose: () => void;
  onClientUpdated: (client: Client) => void;
  onOpenProject: (project: Project) => void;
};

export function ClientDetailsPanel({
  client,
  projects,
  onClose,
  onClientUpdated,
  onOpenProject,
}: ClientDetailsPanelProps) {
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState(client.name);
  const [companyName, setCompanyName] = useState(client.companyName ?? "");
  const [email, setEmail] = useState(client.email ?? "");
  const [phone, setPhone] = useState(client.phone ?? "");
  const [notes, setNotes] = useState(client.notes ?? "");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedName = name.trim();
    if (!normalizedName) {
      setError("Informe o nome do cliente.");
      return;
    }

    setSaving(true);
    setError(null);
    try {
      const updated = await updateClient(client.workspaceId, client.id, {
        name: normalizedName,
        companyName: companyName.trim(),
        email: email.trim(),
        phone: phone.trim(),
        notes: notes.trim(),
      });
      onClientUpdated(updated);
      setEditing(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel salvar o cliente.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end bg-foreground/30 px-3 py-4 backdrop-blur-sm sm:items-center sm:justify-center sm:px-6"
      role="dialog"
      aria-modal="true"
      aria-labelledby="client-details-title"
      onClick={onClose}
    >
      <section
        className="max-h-[92vh] w-full min-w-0 overflow-y-auto rounded-lg border border-border bg-card shadow-xl sm:max-w-3xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="sticky top-0 z-10 flex items-start justify-between gap-3 border-b border-border bg-card/95 px-4 py-4 backdrop-blur sm:px-6">
          <div className="min-w-0">
            <h2 id="client-details-title" className="break-words text-xl font-semibold text-card-foreground">
              {client.name}
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">atualizado em {formatDate(client.updatedAt)}</p>
          </div>
          <Button type="button" variant="ghost" size="icon" onClick={onClose} aria-label="Fechar cliente">
            <X className="h-5 w-5" />
          </Button>
        </div>

        <div className="grid gap-5 px-4 py-5 sm:px-6 lg:grid-cols-[minmax(0,1fr)_260px]">
          <div className="min-w-0 space-y-4">
            {editing ? (
              <form className="grid gap-3 rounded-md border border-border bg-background/70 p-4" onSubmit={handleSubmit}>
                <Field label="Nome" value={name} maxLength={180} onChange={setName} />
                <Field label="Empresa" value={companyName} maxLength={255} onChange={setCompanyName} />
                <Field label="E-mail" value={email} maxLength={320} onChange={setEmail} />
                <Field label="Telefone" value={phone} maxLength={50} onChange={setPhone} />
                <label className="grid gap-1 text-sm font-medium text-foreground">
                  Observacoes
                  <textarea
                    className="min-h-28 rounded-md border border-input bg-card px-3 py-2 text-sm font-normal outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                    value={notes}
                    onChange={(event) => setNotes(event.target.value)}
                    maxLength={5000}
                  />
                </label>
                <p className="text-xs text-muted-foreground">
                  Credenciais, senhas, tokens e chaves devem ficar no Cofre do projeto.
                </p>
                {error ? <p className="text-sm text-destructive">{error}</p> : null}
                <div className="grid gap-2 sm:flex sm:justify-end">
                  <Button type="button" variant="outline" onClick={() => setEditing(false)} disabled={saving}>
                    Cancelar
                  </Button>
                  <Button type="submit" disabled={saving}>
                    {saving ? "Salvando..." : "Salvar cliente"}
                  </Button>
                </div>
              </form>
            ) : (
              <section className="rounded-md border border-border bg-background/70 p-4">
                <div className="mb-3 flex justify-end">
                  <Button type="button" size="sm" variant="outline" onClick={() => setEditing(true)}>
                    Editar
                  </Button>
                </div>
                <dl className="grid gap-3">
                  <Item label="Empresa" value={client.companyName ?? "Nao informada"} />
                  <Item label="E-mail" value={client.email ?? "Nao informado"} />
                  <Item label="Telefone" value={client.phone ?? "Nao informado"} />
                  <Item label="Observacoes" value={client.notes ?? "Sem observacoes"} multiline />
                </dl>
              </section>
            )}

            <section className="rounded-md border border-border bg-background/70 p-4">
              <h3 className="mb-3 text-sm font-semibold text-foreground">Projetos relacionados</h3>
              {projects.length === 0 ? (
                <p className="text-sm text-muted-foreground">Nenhum projeto vinculado a este cliente.</p>
              ) : (
                <div className="grid gap-2">
                  {projects.map((project) => (
                    <button
                      key={project.id}
                      type="button"
                      className="rounded-md border border-border bg-card px-3 py-2 text-left text-sm hover:bg-accent"
                      onClick={() => onOpenProject(project)}
                    >
                      <span className="block font-medium text-card-foreground">{project.name}</span>
                      <span className="text-xs text-muted-foreground">{getProjectStatusLabel(project.status)}</span>
                    </button>
                  ))}
                </div>
              )}
            </section>
          </div>

          <aside className="rounded-md border border-border bg-background/70 p-4">
            <dl className="grid gap-4">
              <Item label="Criado em" value={formatDate(client.createdAt)} />
              <Item label="Atualizado em" value={formatDate(client.updatedAt)} />
            </dl>
          </aside>
        </div>
      </section>
    </div>
  );
}

function Field({
  label,
  value,
  maxLength,
  onChange,
}: {
  label: string;
  value: string;
  maxLength: number;
  onChange: (value: string) => void;
}) {
  return (
    <label className="grid gap-1 text-sm font-medium text-foreground">
      {label}
      <input
        className="h-10 rounded-md border border-input bg-card px-3 text-sm font-normal outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        maxLength={maxLength}
      />
    </label>
  );
}

function Item({ label, value, multiline = false }: { label: string; value: string; multiline?: boolean }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] font-medium uppercase text-muted-foreground">{label}</dt>
      <dd className={`mt-1 break-words text-sm text-card-foreground ${multiline ? "whitespace-pre-wrap leading-6" : ""}`}>
        {value}
      </dd>
    </div>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
