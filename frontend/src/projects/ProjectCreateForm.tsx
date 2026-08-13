import { FormEvent } from "react";
import { Plus } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { type ProjectStatus } from "@/projects/projectApi";
import { getProjectStatusLabel, PROJECT_STATUS_OPTIONS } from "@/projects/projectStatus";

type ProjectCreateFormProps = {
  name: string;
  description: string;
  currentState: string;
  status: ProjectStatus | "";
  creating: boolean;
  error: string | null;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onCurrentStateChange: (value: string) => void;
  onStatusChange: (value: ProjectStatus | "") => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function ProjectCreateForm({
  name,
  description,
  currentState,
  status,
  creating,
  error,
  onNameChange,
  onDescriptionChange,
  onCurrentStateChange,
  onStatusChange,
  onSubmit,
}: ProjectCreateFormProps) {
  return (
    <form className="grid gap-4 rounded-lg border border-border bg-card p-4 shadow-sm" onSubmit={onSubmit}>
      <div>
        <h3 className="text-sm font-semibold text-card-foreground">Novo projeto</h3>
        <p className="mt-1 text-xs text-muted-foreground">Crie um card no workspace ativo.</p>
      </div>

      <div className="grid gap-3 md:grid-cols-[1fr_180px]">
        <input
          className="h-10 rounded-md border border-input bg-background px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={name}
          onChange={(event) => onNameChange(event.target.value)}
          placeholder="Nome do projeto, ideia ou entrega"
          aria-label="Nome do projeto"
        />
        <select
          className="h-10 rounded-md border border-input bg-background px-3 text-sm outline-none ring-offset-background transition-shadow focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={status}
          onChange={(event) => onStatusChange(event.target.value as ProjectStatus | "")}
          aria-label="Status do projeto"
        >
          <option value="">Status padrão</option>
          {PROJECT_STATUS_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {getProjectStatusLabel(option)}
            </option>
          ))}
        </select>
      </div>

      <textarea
        className="min-h-20 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        value={description}
        onChange={(event) => onDescriptionChange(event.target.value)}
        placeholder="Descrição curta do que precisa acontecer"
        aria-label="Descrição do projeto"
      />

      <div className="flex flex-col gap-3 md:flex-row">
        <input
          className="h-10 min-w-0 flex-1 rounded-md border border-input bg-background px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={currentState}
          onChange={(event) => onCurrentStateChange(event.target.value)}
          placeholder="Onde parou agora"
          aria-label="Estado atual do projeto"
        />
        <Button type="submit" className="md:min-w-36" disabled={creating}>
          <Plus className="h-4 w-4" />
          {creating ? "Criando..." : "Criar projeto"}
        </Button>
      </div>

      {error ? (
        <p className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}
    </form>
  );
}
