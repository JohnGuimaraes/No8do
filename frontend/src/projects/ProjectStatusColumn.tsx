import { type Project, type ProjectStatus } from "@/projects/projectApi";
import { ProjectCard } from "@/projects/ProjectCard";

type ProjectStatusColumnProps = {
  label: string;
  projects: Project[];
  editingProjectId: string | null;
  savingProjectId: string | null;
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

export function ProjectStatusColumn({
  label,
  projects,
  editingProjectId,
  savingProjectId,
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
}: ProjectStatusColumnProps) {
  return (
    <section className="rounded-lg border border-border bg-background p-3">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h3 className="text-sm font-semibold text-foreground">{label}</h3>
        <span className="rounded-md border border-border px-2 py-1 text-xs font-medium text-muted-foreground">
          {projects.length}
        </span>
      </div>

      {projects.length === 0 ? (
        <div className="rounded-md border border-dashed border-border px-3 py-8 text-center text-sm text-muted-foreground">
          Sem projetos
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {projects.map((project) => (
            <ProjectCard
              key={project.id}
              project={project}
              editing={editingProjectId === project.id}
              saving={savingProjectId === project.id}
              editName={editName}
              editDescription={editDescription}
              editCurrentState={editCurrentState}
              editStatus={editStatus}
              onStartEditing={onStartEditing}
              onCancelEditing={onCancelEditing}
              onSave={onSave}
              onEditNameChange={onEditNameChange}
              onEditDescriptionChange={onEditDescriptionChange}
              onEditCurrentStateChange={onEditCurrentStateChange}
              onEditStatusChange={onEditStatusChange}
            />
          ))}
        </div>
      )}
    </section>
  );
}
