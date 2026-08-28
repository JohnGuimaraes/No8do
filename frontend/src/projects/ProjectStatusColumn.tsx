import { useDroppable } from "@dnd-kit/core";
import { Lightbulb, MapTrifold, PauseCircle, RocketLaunch, WarningCircle } from "@phosphor-icons/react";
import { type Project, type ProjectStatus } from "@/projects/projectApi";
import { ProjectCard } from "@/projects/ProjectCard";

type ProjectStatusColumnProps = {
  status: ProjectStatus;
  label: string;
  projects: Project[];
  editingProjectId: string | null;
  savingProjectId: string | null;
  editName: string;
  editDescription: string;
  editCurrentState: string;
  editRepositoryUrl: string;
  editStatus: ProjectStatus;
  completingProjectId: string | null;
  canWrite: boolean;
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

export function ProjectStatusColumn({
  status,
  label,
  projects,
  editingProjectId,
  savingProjectId,
  editName,
  editDescription,
  editCurrentState,
  editRepositoryUrl,
  editStatus,
  completingProjectId,
  canWrite,
  onStartEditing,
  onCancelEditing,
  onSave,
  onEditNameChange,
  onEditDescriptionChange,
  onEditCurrentStateChange,
  onEditRepositoryUrlChange,
  onEditStatusChange,
  onOpenDetails,
}: ProjectStatusColumnProps) {
  const { isOver, setNodeRef } = useDroppable({
    id: status,
    data: {
      status,
      type: "status-column",
    },
  });

  return (
    <section
      ref={setNodeRef}
      data-status={status}
      className={`project-status-column flex min-h-36 w-[min(82vw,320px)] min-w-[280px] max-w-[320px] flex-col rounded-xl border p-3 transition-colors sm:min-w-[300px] ${
        isOver ? "border-primary bg-primary/10 shadow-[0_18px_42px_-34px_hsl(var(--primary))]" : "border-transparent bg-transparent"
      }`}
    >
      <div className="project-status-column__header mb-3">
        <span className="project-status-column__icon" aria-hidden="true">{getColumnIcon(status)}</span>
        <div className="min-w-0">
          <p className="project-status-column__step">{getColumnMeta(status).step}</p>
          <h3 className="project-status-column__title min-w-0 break-words text-sm font-semibold text-foreground">{label}</h3>
          <p className="project-status-column__helper">{getColumnMeta(status).helper}</p>
        </div>
        <span className="project-status-column__count shrink-0 text-xs font-medium text-muted-foreground">
          {projects.length} {projects.length === 1 ? "card" : "cards"}
        </span>
      </div>

      {projects.length === 0 ? (
        <div className="project-status-column__empty flex min-h-32 flex-1 items-center justify-center px-3 py-8 text-center">
          <div>
            <p className="text-sm font-medium text-muted-foreground">Sem projetos</p>
            <p className="mt-1 text-xs text-muted-foreground">Esta etapa ainda está livre.</p>
          </div>
        </div>
      ) : (
        <div className="flex min-w-0 flex-col gap-3">
          {projects.map((project) => (
            <ProjectCard
              key={project.id}
              project={project}
              editing={editingProjectId === project.id}
              saving={savingProjectId === project.id}
              editName={editName}
              editDescription={editDescription}
              editCurrentState={editCurrentState}
              editRepositoryUrl={editRepositoryUrl}
              editStatus={editStatus}
              completing={completingProjectId === project.id}
              canWrite={canWrite}
              onStartEditing={onStartEditing}
              onCancelEditing={onCancelEditing}
              onSave={onSave}
              onEditNameChange={onEditNameChange}
              onEditDescriptionChange={onEditDescriptionChange}
              onEditCurrentStateChange={onEditCurrentStateChange}
              onEditRepositoryUrlChange={onEditRepositoryUrlChange}
              onEditStatusChange={onEditStatusChange}
              onOpenDetails={onOpenDetails}
            />
          ))}
        </div>
      )}
    </section>
  );
}

function getColumnMeta(status: ProjectStatus) {
  const metadata: Record<Exclude<ProjectStatus, "DONE">, { step: string; helper: string }> = {
    IDEA: { step: "Etapa 01", helper: "Ponto de partida" },
    PLANNING: { step: "Etapa 02", helper: "Organize o proximo movimento" },
    ACTIVE: { step: "Em fluxo", helper: "Trabalho em andamento" },
    BLOCKED: { step: "Atenção", helper: "Precisa ser destravado" },
    PAUSED: { step: "Em espera", helper: "Pausa intencional" },
  };

  return metadata[status as Exclude<ProjectStatus, "DONE">];
}

function getColumnIcon(status: ProjectStatus) {
  const iconClassName = "h-4 w-4";

  switch (status) {
    case "IDEA": return <Lightbulb className={iconClassName} weight="duotone" />;
    case "PLANNING": return <MapTrifold className={iconClassName} weight="duotone" />;
    case "ACTIVE": return <RocketLaunch className={iconClassName} weight="duotone" />;
    case "BLOCKED": return <WarningCircle className={iconClassName} weight="duotone" />;
    case "PAUSED": return <PauseCircle className={iconClassName} weight="duotone" />;
    default: return null;
  }
}
