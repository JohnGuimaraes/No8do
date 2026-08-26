import { ArrowsOut } from "@phosphor-icons/react";
import { useDraggable } from "@dnd-kit/core";
import { Button } from "@/components/ui/button";
import { type Project, type ProjectStatus } from "@/projects/projectApi";
import { ProjectEditForm } from "@/projects/ProjectEditForm";
import { getProjectStatusLabel } from "@/projects/projectStatus";
import { StickyNoteSurface } from "@/projects/StickyNoteSurface";

type ProjectCardProps = {
  project: Project;
  editing: boolean;
  saving: boolean;
  editName: string;
  editDescription: string;
  editCurrentState: string;
  editRepositoryUrl: string;
  editStatus: ProjectStatus;
  completing: boolean;
  onStartEditing: (project: Project) => void;
  onCancelEditing: () => void;
  onSave: (project: Project) => void;
  onEditNameChange: (value: string) => void;
  onEditDescriptionChange: (value: string) => void;
  onEditCurrentStateChange: (value: string) => void;
  onEditRepositoryUrlChange: (value: string) => void;
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
  editRepositoryUrl,
  editStatus,
  completing,
  onStartEditing,
  onCancelEditing,
  onSave,
  onEditNameChange,
  onEditDescriptionChange,
  onEditCurrentStateChange,
  onEditRepositoryUrlChange,
  onEditStatusChange,
  onOpenDetails,
}: ProjectCardProps) {
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
      {...attributes}
      {...listeners}
      data-project-status={project.status}
      className={`kanban-card ${!editing ? "kanban-card--note p-0" : "p-4"} min-w-0 ${completing ? "kanban-card--completing" : ""} ${
        isDragging ? "relative z-10 scale-[1.01] opacity-75 shadow-xl" : ""
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
          repositoryUrl={editRepositoryUrl}
          status={editStatus}
          saving={saving}
          onNameChange={onEditNameChange}
          onDescriptionChange={onEditDescriptionChange}
          onCurrentStateChange={onEditCurrentStateChange}
          onRepositoryUrlChange={onEditRepositoryUrlChange}
          onStatusChange={onEditStatusChange}
          onSave={() => onSave(project)}
          onCancel={onCancelEditing}
        />
      ) : (
        <div className="kanban-card__content flex h-full min-w-0 flex-col">
          <StickyNoteSurface status={project.status} noteId={project.id} />
          <div className="min-w-0">
            <h3 className="kanban-card__title break-words">
              {project.name}
            </h3>
          </div>

          {project.currentState ? (
            <div className="kanban-card__state">
              <p className="kanban-card__annotation-label text-[11px] font-medium">Agora</p>
              <p className="kanban-card__current-state whitespace-pre-wrap">{project.currentState}</p>
            </div>
          ) : null}

          {project.description ? (
            <p className="kanban-card__description line-clamp-3 whitespace-pre-wrap text-sm leading-6">
              {project.description}
            </p>
          ) : null}

          <div className="kanban-card__footer mt-auto flex flex-wrap items-center justify-between gap-2">
            <div className="kanban-card__metadata">
              <span className="kanban-card__status">{getProjectStatusLabel(project.status)}</span>
              <span className="kanban-card__updated">atualizado em {formatDate(project.updatedAt)}</span>
            </div>
            <div className="kanban-card__actions flex flex-wrap items-center justify-end gap-3">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="w-auto"
                onClick={(event) => {
                  event.stopPropagation();
                  event.preventDefault();
                  onStartEditing(project);
                }}
              >
                Editar
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="w-auto"
                onClick={(event) => {
                  event.stopPropagation();
                  event.preventDefault();
                  onOpenDetails(project);
                }}
              >
                <ArrowsOut className="h-4 w-4" />
                Expandir
              </Button>
            </div>
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
