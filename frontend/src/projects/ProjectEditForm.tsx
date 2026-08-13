import { Button } from "@/components/ui/button";
import { type ProjectStatus } from "@/projects/projectApi";
import { getProjectStatusLabel, PROJECT_STATUS_OPTIONS } from "@/projects/projectStatus";

type ProjectEditFormProps = {
  name: string;
  description: string;
  currentState: string;
  status: ProjectStatus;
  saving: boolean;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onCurrentStateChange: (value: string) => void;
  onStatusChange: (value: ProjectStatus) => void;
  onSave: () => void;
  onCancel: () => void;
};

export function ProjectEditForm({
  name,
  description,
  currentState,
  status,
  saving,
  onNameChange,
  onDescriptionChange,
  onCurrentStateChange,
  onStatusChange,
  onSave,
  onCancel,
}: ProjectEditFormProps) {
  return (
    <div className="flex flex-col gap-3">
      <div>
        <p className="text-xs font-medium uppercase text-muted-foreground">Editando projeto</p>
      </div>

      <div className="grid gap-3">
        <input
          className="h-10 rounded-md border border-input bg-background px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={name}
          onChange={(event) => onNameChange(event.target.value)}
          aria-label="Nome do projeto"
        />
        <select
          className="h-10 rounded-md border border-input bg-background px-3 text-sm outline-none ring-offset-background transition-shadow focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={status}
          onChange={(event) => onStatusChange(event.target.value as ProjectStatus)}
          aria-label="Status do projeto"
        >
          {PROJECT_STATUS_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {getProjectStatusLabel(option)}
            </option>
          ))}
        </select>
        <textarea
          className="min-h-24 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={description}
          onChange={(event) => onDescriptionChange(event.target.value)}
          placeholder="Descrição"
          aria-label="Descrição do projeto"
        />
        <input
          className="h-10 rounded-md border border-input bg-background px-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={currentState}
          onChange={(event) => onCurrentStateChange(event.target.value)}
          placeholder="Estado atual"
          aria-label="Estado atual do projeto"
        />
      </div>

      <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="button" onClick={onSave} disabled={saving}>
          {saving ? "Salvando..." : "Salvar"}
        </Button>
      </div>
    </div>
  );
}
