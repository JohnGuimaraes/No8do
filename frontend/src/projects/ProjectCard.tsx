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
    <article className="rounded-lg border border-border bg-background p-4 shadow-sm">
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
        <>
          <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <h3 className="break-words text-base font-semibold text-foreground">{project.name}</h3>
              <p className="mt-1 text-xs text-muted-foreground">
                Atualizado em {formatDate(project.updatedAt)}
              </p>
            </div>
            <span className="rounded-md border border-border px-2 py-1 text-xs font-medium text-muted-foreground">
              {getProjectStatusLabel(project.status)}
            </span>
          </div>

          {project.description ? (
            <p className="mb-3 whitespace-pre-wrap text-sm text-muted-foreground">{project.description}</p>
          ) : null}

          {project.currentState ? (
            <div className="mb-4 rounded-md border border-border bg-card px-3 py-2 text-sm text-card-foreground">
              {project.currentState}
            </div>
          ) : null}

          <Button type="button" variant="outline" size="sm" onClick={() => onStartEditing(project)}>
            Editar
          </Button>
        </>
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
