import { Button } from "@/components/ui/button";
import { type Project, type ProjectStatus } from "@/projects/projectApi";
import { ProjectEditForm } from "@/projects/ProjectEditForm";
import { getProjectStatusLabel } from "@/projects/projectStatus";

type ProjectCardProps = {
  project: Project;
  editing: boolean;
  saving: boolean;
  editName: string;
  editDescription: string;
  editCurrentState: string;
  editStatus: ProjectStatus;
  onStartEditing: (project: Project) => void;
  onCancelEditing: () => void;
  onSave: (project: Project) => void;
  onEditNameChange: (value: string) => void;
  onEditDescriptionChange: (value: string) => void;
  onEditCurrentStateChange: (value: string) => void;
  onEditStatusChange: (value: ProjectStatus) => void;
};

export function ProjectCard({
  project,
  editing,
  saving,
  editName,
  editDescription,
  editCurrentState,
  editStatus,
  onStartEditing,
  onCancelEditing,
  onSave,
  onEditNameChange,
  onEditDescriptionChange,
  onEditCurrentStateChange,
  onEditStatusChange,
}: ProjectCardProps) {
  return (
    <article className="rounded-lg border border-border bg-card p-4 shadow-sm transition-shadow hover:shadow-md">
      {editing ? (
        <ProjectEditForm
          name={editName}
          description={editDescription}
          currentState={editCurrentState}
          status={editStatus}
          saving={saving}
          onNameChange={onEditNameChange}
          onDescriptionChange={onEditDescriptionChange}
          onCurrentStateChange={onEditCurrentStateChange}
          onStatusChange={onEditStatusChange}
          onSave={() => onSave(project)}
          onCancel={onCancelEditing}
        />
      ) : (
        <div className="flex h-full flex-col gap-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <h3 className="break-words text-base font-semibold leading-snug text-card-foreground">
                {project.name}
              </h3>
              <p className="mt-1 text-xs text-muted-foreground">
                atualizado em {formatDate(project.updatedAt)}
              </p>
            </div>
            <span className="rounded-full border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground">
              {getProjectStatusLabel(project.status)}
            </span>
          </div>

          {project.description ? (
            <p className="whitespace-pre-wrap rounded-md bg-background/70 px-3 py-2 text-sm leading-6 text-muted-foreground">
              {project.description}
            </p>
          ) : null}

          {project.currentState ? (
            <div className="rounded-md border border-border bg-background px-3 py-2">
              <p className="mb-1 text-[11px] font-medium uppercase text-muted-foreground">Estado atual</p>
              <p className="whitespace-pre-wrap text-sm leading-6 text-card-foreground">{project.currentState}</p>
            </div>
          ) : null}

          <div className="mt-auto flex justify-end">
            <Button type="button" variant="outline" size="sm" onClick={() => onStartEditing(project)}>
              Editar
            </Button>
          </div>
        </div>
      )}
    </article>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
