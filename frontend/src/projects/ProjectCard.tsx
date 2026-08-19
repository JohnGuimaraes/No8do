import { useState } from "react";
import { DotsSixVertical } from "@phosphor-icons/react";
import { useDraggable } from "@dnd-kit/core";
import { Button } from "@/components/ui/button";
import { ProjectActivityPanel } from "@/activities/ProjectActivityPanel";
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
  onOpenDetails: (project: Project) => void;
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
  onOpenDetails,
}: ProjectCardProps) {
  const [showActivities, setShowActivities] = useState(false);
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: project.id,
    data: {
      project,
      type: "project",
    },
    disabled: editing,
  });
  const style = transform
    ? {
        transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
      }
    : undefined;

  return (
    <article
      ref={setNodeRef}
      style={style}
      className={`min-w-0 rounded-lg border border-border bg-card p-4 shadow-sm transition-shadow hover:shadow-md ${
        isDragging ? "relative z-10 opacity-80 shadow-lg" : ""
      }`}
      onClick={() => {
        if (!editing) {
          onOpenDetails(project);
        }
      }}
    >
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
        <div className="flex h-full min-w-0 flex-col gap-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0 flex-1">
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

          <button
            type="button"
            className="flex w-full cursor-grab items-center justify-center gap-2 rounded-md border border-dashed border-border bg-background/70 px-3 py-2 text-xs font-medium text-muted-foreground transition-colors hover:bg-accent active:cursor-grabbing"
            aria-label={`Arrastar projeto ${project.name}`}
            onClick={(event) => event.stopPropagation()}
            {...attributes}
            {...listeners}
          >
            <DotsSixVertical className="h-4 w-4" />
            Arrastar para mudar status
          </button>

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

          {showActivities ? (
            <div onClick={(event) => event.stopPropagation()}>
              <ProjectActivityPanel workspaceId={project.workspaceId} projectId={project.id} />
            </div>
          ) : null}

          <div className="mt-auto grid gap-2 sm:flex sm:flex-wrap sm:justify-end">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="w-full sm:w-auto"
              onClick={(event) => {
                event.stopPropagation();
                setShowActivities((current) => !current);
              }}
            >
              {showActivities ? "Ocultar histórico" : "Histórico"}
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="w-full sm:w-auto"
              onClick={(event) => {
                event.stopPropagation();
                onStartEditing(project);
              }}
            >
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
