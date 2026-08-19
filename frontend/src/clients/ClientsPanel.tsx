import { FormEvent, useState } from "react";
import { Plus } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { ClientDetailsPanel } from "@/clients/ClientDetailsPanel";
import { createClient, type Client } from "@/clients/clientApi";
import { type Project } from "@/projects/projectApi";

type ClientsPanelProps = {
  workspaceId: string;
  clients: Client[];
  projects: Project[];
  onClientCreated: (client: Client) => void;
  onClientUpdated: (client: Client) => void;
  onOpenProject: (project: Project) => void;
};

export function ClientsPanel({
  workspaceId,
  clients,
  projects,
  onClientCreated,
  onClientUpdated,
  onOpenProject,
}: ClientsPanelProps) {
  const [selectedClient, setSelectedClient] = useState<Client | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [companyName, setCompanyName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [notes, setNotes] = useState("");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedName = name.trim();
    if (!normalizedName) {
      setError("Informe o nome do cliente.");
      return;
    }

    setCreating(true);
    setError(null);
    try {
      const client = await createClient(workspaceId, {
        name: normalizedName,
        companyName: companyName.trim(),
        email: email.trim(),
        phone: phone.trim(),
        notes: notes.trim(),
      });
      onClientCreated(client);
      setName("");
      setCompanyName("");
      setEmail("");
      setPhone("");
      setNotes("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Nao foi possivel criar o cliente.");
    } finally {
      setCreating(false);
    }
  }

  function relatedProjects(clientId: string) {
    return projects.filter((project) => project.clientId === clientId);
  }

  return (
    <div className="grid min-w-0 gap-6">
      <form className="grid gap-4 rounded-lg border border-border bg-card p-4 shadow-sm" onSubmit={handleSubmit}>
        <div>
          <h3 className="text-sm font-semibold text-card-foreground">Novo cliente</h3>
          <p className="mt-1 text-xs text-muted-foreground">Cadastre dados basicos e vincule projetos existentes.</p>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <Input label="Nome" value={name} maxLength={180} onChange={setName} />
          <Input label="Empresa" value={companyName} maxLength={255} onChange={setCompanyName} />
          <Input label="E-mail" value={email} maxLength={320} onChange={setEmail} />
          <Input label="Telefone" value={phone} maxLength={50} onChange={setPhone} />
        </div>
        <textarea
          className="min-h-20 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
          maxLength={5000}
          placeholder="Observacoes comuns. Nao armazene credenciais aqui."
          aria-label="Observacoes do cliente"
        />
        {error ? <p className="text-sm text-destructive">{error}</p> : null}
        <div className="flex justify-end">
          <Button type="submit" disabled={creating}>
            <Plus className="h-4 w-4" />
            {creating ? "Criando..." : "Criar cliente"}
          </Button>
        </div>
      </form>

      {clients.length === 0 ? (
        <div className="rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground">
          Nenhum cliente cadastrado
        </div>
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {clients.map((client) => (
            <button
              key={client.id}
              type="button"
              className="rounded-md border border-border bg-card p-4 text-left shadow-sm hover:bg-accent"
              onClick={() => setSelectedClient(client)}
            >
              <span className="block break-words text-base font-semibold text-card-foreground">{client.name}</span>
              <span className="mt-1 block text-sm text-muted-foreground">
                {client.companyName ?? client.email ?? "Sem empresa ou e-mail"}
              </span>
              <span className="mt-3 inline-flex rounded-full border border-border bg-background px-2.5 py-1 text-xs text-muted-foreground">
                {relatedProjects(client.id).length} projeto(s)
              </span>
            </button>
          ))}
        </div>
      )}

      {selectedClient ? (
        <ClientDetailsPanel
          client={selectedClient}
          projects={relatedProjects(selectedClient.id)}
          onClose={() => setSelectedClient(null)}
          onClientUpdated={(client) => {
            onClientUpdated(client);
            setSelectedClient(client);
          }}
          onOpenProject={onOpenProject}
        />
      ) : null}
    </div>
  );
}

function Input({
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
        className="h-10 rounded-md border border-input bg-background px-3 text-sm font-normal outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        maxLength={maxLength}
      />
    </label>
  );
}
