import { ProjectsPanel, type WorkspaceSection } from "@/projects/ProjectsPanel";
import { type Workspace } from "@/workspaces/workspaceApi";

type WorkspacePanelProps = {
  workspace: Workspace | null;
  activeSection: WorkspaceSection;
  selectedProjectId: string | null;
  selectedReplayId: string | null;
  onNavigateSection: (section: WorkspaceSection) => void;
  onOpenProject: (projectId: string, section: WorkspaceSection) => void;
  onOpenReplay: (replayId: string) => void;
  onBackToReplayCatalog: () => void;
};

export function WorkspacePanel({ workspace, activeSection, selectedProjectId, selectedReplayId, onNavigateSection, onOpenProject, onOpenReplay, onBackToReplayCatalog }: WorkspacePanelProps) {
  if (!workspace) {
    return (
      <section className="editorial-empty-state">
        <p className="text-sm font-medium text-foreground">Seu primeiro espaço começa aqui.</p>
        <p className="mt-1 max-w-md text-sm leading-6 text-muted-foreground">Crie ou selecione um workspace na barra superior para reunir projetos, decisões e conhecimento.</p>
      </section>
    );
  }

  return <ProjectsPanel workspace={workspace} activeSection={activeSection} selectedProjectId={selectedProjectId} selectedReplayId={selectedReplayId} onNavigateSection={onNavigateSection} onOpenProject={onOpenProject} onOpenReplay={onOpenReplay} onBackToReplayCatalog={onBackToReplayCatalog} />;
}
