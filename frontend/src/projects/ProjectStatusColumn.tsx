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
    <section className="flex min-h-80 flex-col rounded-lg border border-border bg-muted/30 p-3">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h3 className="text-sm font-semibold text-foreground">{label}</h3>
        <span className="rounded-full border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground">
          {projects.length} {projects.length === 1 ? "card" : "cards"}
        </span>
      </div>

      {projects.length === 0 ? (
        <div className="flex min-h-32 flex-1 items-center justify-center rounded-md border border-dashed border-border bg-background/60 px-3 py-8 text-center">
          <div>
            <p className="text-sm font-medium text-muted-foreground">Sem projetos</p>
            <p className="mt-1 text-xs text-muted-foreground">Esta etapa ainda está livre.</p>
          </div>
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
