import { ProjectsPanel, type WorkspaceSection } from "@/projects/ProjectsPanel";
import { type Workspace } from "@/workspaces/workspaceApi";

type WorkspacePanelProps = {
  workspace: Workspace | null;
  activeSection: WorkspaceSection;
  selectedProjectId: string | null;
  onNavigateSection: (section: WorkspaceSection) => void;
  onOpenProject: (projectId: string, section: WorkspaceSection) => void;
};

export function WorkspacePanel({ workspace, activeSection, selectedProjectId, onNavigateSection, onOpenProject }: WorkspacePanelProps) {
  if (!workspace) {
    return (
      <section className="editorial-empty-state">
        <p className="text-sm font-medium text-foreground">Seu primeiro espaço começa aqui.</p>
        <p className="mt-1 max-w-md text-sm leading-6 text-muted-foreground">Crie ou selecione um workspace na barra superior para reunir projetos, decisões e conhecimento.</p>
      </section>
    );
  }

  return <ProjectsPanel workspace={workspace} activeSection={activeSection} selectedProjectId={selectedProjectId} onNavigateSection={onNavigateSection} onOpenProject={onOpenProject} />;
}
