import { type FormEvent, type ReactNode, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { DndContext, PointerSensor, useDroppable, useSensor, useSensors, type DragEndEvent, type DragStartEvent } from "@dnd-kit/core";
import {
  ArrowsClockwise,
  ArrowLeft,
  Books,
  CheckSquare,
  Circle,
  Folders,
  GithubLogo,
  Kanban,
  Lightbulb,
  MagnifyingGlass,
  Plus,
  SquaresFour,
} from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { ViewModeToggle, type ViewMode } from "@/components/ViewModeToggle";
import { WorkspaceDashboard } from "@/dashboard/WorkspaceDashboard";
import { IdeasPanel } from "@/ideas/IdeasPanel";
import { listIdeas, type Idea, type IdeaStatus, type IdeaType } from "@/ideas/ideaApi";
import { AcervoLibraryScene, animateAcervoEntry } from "@/library/AcervoEditorialBackground";
import { LibraryPanel } from "@/library/LibraryPanel";
import { ReplaysPanel } from "@/replays/ReplaysPanel";
import { listLibraryItems, type LibraryItem, type LibraryItemType } from "@/library/libraryApi";
import {
  archiveProject,
  createProject,
  deleteProject,
  getProjectCoverUrl,
  listProjects,
  restoreProject,
  updateProject,
  type CreateProjectInput,
  type Project,
  type ProjectStatus,
  type UpdateProjectInput,
} from "@/projects/projectApi";
import { ProjectCreateForm } from "@/projects/ProjectCreateForm";
import { ProjectDetailsPanel } from "@/projects/ProjectDetailsPanel";
import { ProjectStatusColumn } from "@/projects/ProjectStatusColumn";
import { ToastNotification } from "@/components/ToastNotification";
import { ConfirmationDialog } from "@/components/ConfirmationDialog";
import { ProjectCompletionPopup } from "@/projects/ProjectCompletionPopup";
import { DEVELOPMENT_PROJECT_STATUS_COLUMNS, getProjectStatusLabel } from "@/projects/projectStatus";
import { WorkspaceWorkItemsPanel } from "@/work-items/WorkspaceWorkItemsPanel";
import { type Workspace } from "@/workspaces/workspaceApi";
import { ProjectMedia } from "@/components/visual/ProjectMedia";
import { gsap } from "gsap";
import no8doLogo from "@/assets/logo/no8do-logo.png";

export type WorkspaceSection = "overview" | "development" | "work-items" | "projects" | "library" | "ideas" | "replays";
type SearchDomain = "project" | "library" | "idea";
type SearchResult = {
  id: string;
  domain: SearchDomain;
  title: string;
  description: string;
  badge?: string;
  item: Project | LibraryItem | Idea;
};

const WORKSPACE_SECTIONS: Array<{ id: WorkspaceSection; label: string; description: string }> = [
  {
    id: "overview",
    label: "Visão Geral",
    description: "Resumo operacional do workspace e atalhos para continuar.",
  },
  {
    id: "library",
    label: "Acervo",
    description: "Conhecimento, referências e ativos compartilhados do workspace.",
  },
  { id: "replays", label: "Replays", description: "Soluções e conhecimento técnico reutilizável deste workspace." },
  {
    id: "ideas",
    label: "Ideias",
    description: "Ideias que podem evoluir para novos projetos.",
  },
  {
    id: "development",
    label: "Desenvolvimento",
    description: "Projetos que ainda estao sendo construidos.",
  },
  {
    id: "work-items",
    label: "Pendências",
    description: "Bloqueios, pendencias e proximos passos dos projetos.",
  },
  {
    id: "projects",
    label: "Projetos finalizados",
    description: "Projetos concluidos e memoria operacional.",
  },
];

type ProjectsPanelProps = {
  workspace: Workspace;
  activeSection: WorkspaceSection;
  selectedProjectId: string | null;
  selectedReplayId: string | null;
  onNavigateSection: (section: WorkspaceSection) => void;
  onOpenProject: (projectId: string, section: WorkspaceSection) => void;
  onOpenReplay: (replayId: string) => void;
  onBackToReplayCatalog: () => void;
};

export function ProjectsPanel({ workspace, activeSection, selectedProjectId, selectedReplayId, onNavigateSection, onOpenProject, onOpenReplay, onBackToReplayCatalog }: ProjectsPanelProps) {
  const canWrite = workspace.role !== "VIEWER";
  const [projects, setProjects] = useState<Project[]>([]);
  const [libraryItems, setLibraryItems] = useState<LibraryItem[]>([]);
  const [ideas, setIdeas] = useState<Idea[]>([]);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [currentState, setCurrentState] = useState("");
  const [repositoryUrl, setRepositoryUrl] = useState("");
  const [status, setStatus] = useState<ProjectStatus | "">("");
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [savingProjectId, setSavingProjectId] = useState<string | null>(null);
  const [editingProjectId, setEditingProjectId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editCurrentState, setEditCurrentState] = useState("");
  const [editRepositoryUrl, setEditRepositoryUrl] = useState("");
  const [editStatus, setEditStatus] = useState<ProjectStatus>("IDEA");
  const [selectedLibraryItemId, setSelectedLibraryItemId] = useState<string | null>(null);
  const [selectedIdeaId, setSelectedIdeaId] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
  const [createFormOpen, setCreateFormOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [boardError, setBoardError] = useState<string | null>(null);
  const [completingProjectId, setCompletingProjectId] = useState<string | null>(null);
  const [recentlyCompletedProjectId, setRecentlyCompletedProjectId] = useState<string | null>(null);
  const [completionToast, setCompletionToast] = useState<{ title: string; message: string } | null>(null);
  const [completionPopupProjectName, setCompletionPopupProjectName] = useState<string | null>(null);
  const [archivedProjects, setArchivedProjects] = useState<Project[]>([]);
  const [archivedProjectsOpen, setArchivedProjectsOpen] = useState(false);
  const [projectActionId, setProjectActionId] = useState<string | null>(null);
  const [projectPendingDeletion, setProjectPendingDeletion] = useState<Project | null>(null);
  const [replaysImmersive, setReplaysImmersive] = useState(false);
  const completionTimeoutRef = useRef<number | null>(null);
  const toastTimeoutRef = useRef<number | null>(null);
  const popupTimeoutRef = useRef<number | null>(null);

  const developmentProjects = projects.filter((project) => project.status !== "DONE");
  const completedProjects = projects.filter((project) => project.status === "DONE");
  const selectedProject = selectedProjectId ? projects.find((project) => project.id === selectedProjectId) ?? null : null;
  const activeSectionInfo = WORKSPACE_SECTIONS.find((section) => section.id === activeSection) ?? WORKSPACE_SECTIONS[0];
  const normalizedSearchTerm = searchTerm.trim().toLowerCase();
  const searchResults = useMemo(() => {
    if (normalizedSearchTerm.length < 2) {
      return [];
    }

    return [
      {
        domain: "project" as const,
        title: "Projetos",
        results: projects
          .filter((project) =>
            matchesSearch(normalizedSearchTerm, [
              project.name,
              project.description,
              project.status,
              getProjectStatusLabel(project.status),
            ]),
          )
          .map<SearchResult>((project) => ({
            id: project.id,
            domain: "project",
            title: project.name,
            description: project.description || project.currentState || "Projeto do workspace",
            badge: getProjectStatusLabel(project.status),
            item: project,
          })),
      },
      {
        domain: "library" as const,
        title: "Acervo",
        results: libraryItems
          .filter((item) =>
            matchesSearch(normalizedSearchTerm, [
              item.title,
              item.description,
              item.content,
              item.url,
              item.type,
              getLibraryTypeLabel(item.type),
            ]),
          )
          .map<SearchResult>((item) => ({
            id: item.id,
            domain: "library",
            title: item.title,
            description: item.description || item.url || item.content || "Item do Acervo",
            badge: getLibraryTypeLabel(item.type),
            item,
          })),
      },
      {
        domain: "idea" as const,
        title: "Ideias",
        results: ideas
          .filter((idea) =>
            matchesSearch(normalizedSearchTerm, [
              idea.title,
              idea.description,
              idea.type,
              getIdeaTypeLabel(idea.type),
              idea.status,
              getIdeaStatusLabel(idea.status),
              idea.convertedProjectName,
            ]),
          )
          .map<SearchResult>((idea) => ({
            id: idea.id,
            domain: "idea",
            title: idea.title,
            description: idea.description || idea.convertedProjectName || "Ideia do workspace",
            badge: getIdeaStatusLabel(idea.status),
            item: idea,
          })),
      },
    ];
  }, [ideas, libraryItems, normalizedSearchTerm, projects]);
  const totalSearchResults = searchResults.reduce((total, group) => total + group.results.length, 0);

  useEffect(() => () => {
    if (completionTimeoutRef.current) window.clearTimeout(completionTimeoutRef.current);
    if (toastTimeoutRef.current) window.clearTimeout(toastTimeoutRef.current);
    if (popupTimeoutRef.current) window.clearTimeout(popupTimeoutRef.current);
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function loadProjects() {
      setLoading(true);
      setBoardError(null);
      setEditingProjectId(null);

      try {
        const [items, libraryItems, ideaItems] = await Promise.all([
          listProjects(workspace.id),
          listLibraryItems(workspace.id),
          listIdeas(workspace.id),
        ]);

        if (!cancelled) {
          setProjects(items);
          setLibraryItems(libraryItems);
          setIdeas(ideaItems.filter((idea) => idea.status !== "CONVERTED"));
          setSelectedLibraryItemId(null);
          setSelectedIdeaId(null);
          resetCreateForm();
          setSearchTerm("");
          setSearchOpen(false);
          setCompletingProjectId(null);
          setRecentlyCompletedProjectId(null);
          setCompletionToast(null);
          setCompletionPopupProjectName(null);
        }
      } catch (err) {
        if (!cancelled) {
          setBoardError(err instanceof Error ? err.message : "Nao foi possivel carregar projetos.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadProjects();

    return () => {
      cancelled = true;
    };
  }, [workspace.id]);

  useEffect(() => {
    if (!loading && !boardError && selectedProjectId && !selectedProject) {
      onNavigateSection(activeSection);
    }
  }, [activeSection, boardError, loading, onNavigateSection, selectedProject, selectedProjectId]);

  const handleLibraryItemsChange = useCallback((items: LibraryItem[]) => {
    setLibraryItems(items);
  }, []);

  const handleIdeasChange = useCallback((items: Idea[]) => {
    setIdeas(items.filter((idea) => idea.status !== "CONVERTED"));
  }, []);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const normalizedName = name.trim();
    if (!normalizedName) {
      setFormError("Informe um nome para o projeto.");
      return;
    }

    const input: CreateProjectInput = {
      name: normalizedName,
    };
    const normalizedDescription = description.trim();
    const normalizedCurrentState = currentState.trim();

    if (normalizedDescription) {
      input.description = normalizedDescription;
    }
    if (normalizedCurrentState) {
      input.currentState = normalizedCurrentState;
    }
    if (status) {
      input.status = status;
    }
    if (repositoryUrl.trim()) {
      input.repositoryUrl = repositoryUrl.trim();
    }

    setCreating(true);
    setFormError(null);

    try {
      const project = await createProject(workspace.id, input);
      setProjects((current) => [project, ...current]);
      setName("");
      setDescription("");
      setCurrentState("");
      setRepositoryUrl("");
      setStatus("");
      setCreateFormOpen(false);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Nao foi possivel criar o projeto.");
    } finally {
      setCreating(false);
    }
  }

  function resetCreateForm() {
    setName("");
    setDescription("");
    setCurrentState("");
    setRepositoryUrl("");
    setStatus("");
    setFormError(null);
    setCreating(false);
  }

  function handleCancelCreate() {
    resetCreateForm();
    setCreateFormOpen(false);
  }

  function startEditing(project: Project) {
    setEditingProjectId(project.id);
    setEditName(project.name);
    setEditDescription(project.description ?? "");
    setEditCurrentState(project.currentState ?? "");
    setEditRepositoryUrl(project.repositoryUrl ?? "");
    setEditStatus(project.status);
    setBoardError(null);
  }

  function cancelEditing() {
    setEditingProjectId(null);
    setSavingProjectId(null);
    setEditName("");
    setEditDescription("");
    setEditCurrentState("");
    setEditRepositoryUrl("");
    setEditStatus("IDEA");
    setBoardError(null);
  }

  function applyProjectUpdate(updatedProject: Project) {
    setProjects((current) =>
      current.map((item) => (item.id === updatedProject.id ? updatedProject : item)),
    );
  }

  async function handleUpdate(project: Project) {
    const normalizedName = editName.trim();
    if (!normalizedName) {
      setBoardError("Informe um nome para o projeto.");
      return;
    }

    const input: UpdateProjectInput = {
      name: normalizedName,
      status: editStatus,
    };
    const normalizedDescription = editDescription.trim();
    const normalizedCurrentState = editCurrentState.trim();

    if (normalizedDescription) {
      input.description = normalizedDescription;
    }
    if (normalizedCurrentState) {
      input.currentState = normalizedCurrentState;
    }
    if (editRepositoryUrl.trim()) {
      input.repositoryUrl = editRepositoryUrl.trim();
    }

    setSavingProjectId(project.id);
    setBoardError(null);

    try {
      const updatedProject = await updateProject(workspace.id, project.id, input);
      applyProjectUpdate(updatedProject);
      cancelEditing();
    } catch (err) {
      setBoardError(err instanceof Error ? err.message : "Nao foi possivel salvar o projeto.");
    } finally {
      setSavingProjectId(null);
    }
  }

  async function handleDragEnd(event: DragEndEvent) {
    const project = event.active.data.current?.project as Project | undefined;
    const nextStatus = event.over?.data.current?.status as ProjectStatus | undefined;

    if (!project || !nextStatus || nextStatus === project.status || editingProjectId === project.id) {
      return false;
    }

    const previousProjects = projects;
    const isCompleting = nextStatus === "DONE";
    setBoardError(null);
    if (!isCompleting) {
      setProjects((current) =>
        current.map((item) => (item.id === project.id ? { ...item, status: nextStatus } : item)),
      );
    }

    try {
      const updatedProject = await updateProject(workspace.id, project.id, {
        name: project.name,
        description: project.description ?? undefined,
        currentState: project.currentState ?? undefined,
        status: nextStatus,
        repositoryUrl: project.repositoryUrl ?? undefined,
      });
      if (isCompleting) {
        setCompletingProjectId(project.id);
        await waitForCompletionAnimation();
        applyProjectUpdate(updatedProject);
        setCompletingProjectId(null);
        setRecentlyCompletedProjectId(updatedProject.id);
        showCompletionPopup(updatedProject);
        showCompletionToast(updatedProject);
        return true;
      }

      applyProjectUpdate(updatedProject);
      return false;
    } catch (err) {
      setCompletingProjectId(null);
      setProjects(previousProjects);
      setBoardError(err instanceof Error ? err.message : "Nao foi possivel mover o projeto.");
      return false;
    }
  }

  function waitForCompletionAnimation() {
    return new Promise<void>((resolve) => {
      completionTimeoutRef.current = window.setTimeout(() => {
        completionTimeoutRef.current = null;
        resolve();
      }, 220);
    });
  }

  function showCompletionToast(project: Project) {
    if (toastTimeoutRef.current) window.clearTimeout(toastTimeoutRef.current);
    setCompletionToast({
      title: "Projeto concluído",
      message: `${project.name} foi movido para Projetos finalizados.`,
    });
    toastTimeoutRef.current = window.setTimeout(() => {
      toastTimeoutRef.current = null;
      setCompletionToast(null);
    }, 3000);
  }

  function showCompletionPopup(project: Project) {
    if (popupTimeoutRef.current) window.clearTimeout(popupTimeoutRef.current);
    setCompletionPopupProjectName(project.name);
    popupTimeoutRef.current = window.setTimeout(() => {
      popupTimeoutRef.current = null;
      setCompletionPopupProjectName(null);
    }, 1300);
  }

  function dismissCompletionToast() {
    if (toastTimeoutRef.current) window.clearTimeout(toastTimeoutRef.current);
    toastTimeoutRef.current = null;
    setCompletionToast(null);
  }

  function showLifecycleToast(title: string, message: string) {
    if (toastTimeoutRef.current) window.clearTimeout(toastTimeoutRef.current);
    setCompletionToast({ title, message });
    toastTimeoutRef.current = window.setTimeout(() => { toastTimeoutRef.current = null; setCompletionToast(null); }, 3000);
  }

  async function openArchivedProjects() {
    setArchivedProjectsOpen(true);
    setBoardError(null);
    try { setArchivedProjects(await listProjects(workspace.id, true)); }
    catch (err) { setBoardError(err instanceof Error ? err.message : "Nao foi possivel carregar projetos arquivados."); }
  }

  async function handleArchiveProject(project: Project) {
    setProjectActionId(project.id); setBoardError(null);
    try {
      const archived = await archiveProject(workspace.id, project.id);
      setProjects((current) => current.filter((item) => item.id !== archived.id));
      setArchivedProjects((current) => [archived, ...current.filter((item) => item.id !== archived.id)]);
      onNavigateSection(activeSection);
      showLifecycleToast("Projeto arquivado", `${archived.name} foi movido para Arquivados.`);
    } catch (err) { setBoardError(err instanceof Error ? err.message : "Nao foi possivel arquivar o projeto."); }
    finally { setProjectActionId(null); }
  }

  async function handleRestoreProject(project: Project) {
    setProjectActionId(project.id); setBoardError(null);
    try {
      const restored = await restoreProject(workspace.id, project.id);
      setArchivedProjects((current) => current.filter((item) => item.id !== restored.id));
      setProjects((current) => [restored, ...current.filter((item) => item.id !== restored.id)]);
      showLifecycleToast("Projeto restaurado", `${restored.name} foi restaurado.`);
    } catch (err) { setBoardError(err instanceof Error ? err.message : "Nao foi possivel restaurar o projeto."); }
    finally { setProjectActionId(null); }
  }

  async function handleDeleteProject() {
    const project = projectPendingDeletion;
    if (!project) return;
    setProjectActionId(project.id); setBoardError(null);
    try {
      await deleteProject(workspace.id, project.id);
      setProjects((current) => current.filter((item) => item.id !== project.id));
      setArchivedProjects((current) => current.filter((item) => item.id !== project.id));
      onNavigateSection(activeSection); setProjectPendingDeletion(null);
      showLifecycleToast("Projeto excluído", `${project.name} foi excluído permanentemente.`);
    } catch (err) { setBoardError(err instanceof Error ? err.message : "Nao foi possivel excluir o projeto."); }
    finally { setProjectActionId(null); }
  }

  function handleIdeaConverted(project: Project) {
    setProjects((current) => [project, ...current.filter((item) => item.id !== project.id)]);
    onOpenProject(project.id, "development");
  }

  function handleSectionChange(sectionId: WorkspaceSection) {
    onNavigateSection(sectionId);
    if (sectionId !== "replays") setReplaysImmersive(false);
    setEditingProjectId(null);
    setBoardError(null);
    if (sectionId !== "development") {
      handleCancelCreate();
    }
    setSelectedLibraryItemId(null);
    setSelectedIdeaId(null);
  }

  function openReplaysFromDashboard() {
    onNavigateSection("replays");
  }

  function handleSearchSelect(result: SearchResult) {
    setSearchTerm("");
    setSearchOpen(false);
    setEditingProjectId(null);
    setBoardError(null);
    setSelectedLibraryItemId(null);
    setSelectedIdeaId(null);

    if (result.domain === "project") {
      const project = result.item as Project;
      if (project.status === "DONE") {
        handleCancelCreate();
      }
      onOpenProject(project.id, project.status === "DONE" ? "projects" : "development");
      return;
    }

    handleCancelCreate();


    if (result.domain === "library") {
      onNavigateSection("library");
      setSelectedLibraryItemId(result.id);
      return;
    }

    onNavigateSection("ideas");
    setSelectedIdeaId(result.id);
  }

  function openProjectFromDashboard(project: Project) {
    onOpenProject(project.id, project.status === "DONE" ? "projects" : "development");
  }

  function openProjectFromWorkItems(projectId: string) {
    const project = projects.find((item) => item.id === projectId);
    if (!project) {
      return;
    }

    onOpenProject(project.id, project.status === "DONE" ? "projects" : "development");
  }

  function openSectionFromDashboard(sectionId: Exclude<WorkspaceSection, "overview">) {
    handleSectionChange(sectionId);
  }

  function openLibraryItemFromDashboard(itemId: string) {
    handleSectionChange("library");
    setSelectedLibraryItemId(itemId);
  }

  function openIdeaFromDashboard(ideaId: string) {
    handleSectionChange("ideas");
    setSelectedIdeaId(ideaId);
  }

  return (
    <div className="flex min-w-0 flex-col gap-8">
      <aside className="hidden">
        <div className="sticky top-6 grid gap-4">
          <div className="rounded-xl border border-border/80 bg-background/70 p-4">
            <p className="text-[11px] font-medium uppercase text-muted-foreground">No8do / Workspace</p>
            <h2 className="mt-1 break-words text-lg font-semibold tracking-tight text-foreground">{workspace.name}</h2>
          </div>
          <nav className="grid gap-1 rounded-xl border border-border/80 bg-background/70 p-2" aria-label="Navegacao principal do workspace">
            {WORKSPACE_SECTIONS.map((section) => (
              <WorkspaceNavButton
                key={section.id}
                section={section}
                active={activeSection === section.id}
                onClick={() => handleSectionChange(section.id)}
              />
            ))}
          </nav>
        </div>
      </aside>

      <div className={`workspace-content-shell flex min-w-0 flex-col gap-8 ${replaysImmersive || activeSection === "library" || activeSection === "replays" ? "workspace-content-shell--knowledge-immersive" : ""}`}>
        <header className="workspace-shell-header grid min-w-0 gap-5 border-b border-border/80 pb-5">
          <div className="grid min-w-0 gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(300px,460px)] xl:items-start">
            <div className="min-w-0">
              <p className="text-xs font-medium uppercase text-muted-foreground">No8do / {workspace.name}</p>
              <h1 className="mt-1 text-2xl font-semibold leading-tight tracking-tight text-foreground">{activeSectionInfo.label}</h1>
              <p className="mt-1 text-sm text-muted-foreground">{activeSectionInfo.description}</p>
            </div>

            <GlobalSearch
              searchTerm={searchTerm}
              searchOpen={searchOpen}
              normalizedSearchTerm={normalizedSearchTerm}
              totalSearchResults={totalSearchResults}
              searchResults={searchResults}
              onSearchTermChange={setSearchTerm}
              onSearchOpenChange={setSearchOpen}
              onSelect={handleSearchSelect}
            />
          </div>

          <nav
            className="workspace-tabs"
            aria-label="Navegacao principal do workspace"
          >
            <WorkspaceTabGroup label="Visão geral" sections={WORKSPACE_SECTIONS.filter((section) => section.id === "overview")} activeSection={activeSection} onChange={handleSectionChange} />
            <WorkspaceTabGroup label="Desenvolvimento" sections={WORKSPACE_SECTIONS.filter((section) => ["ideas", "development", "work-items", "projects"].includes(section.id))} activeSection={activeSection} onChange={handleSectionChange} />
            <WorkspaceTabGroup label="Conhecimento" sections={WORKSPACE_SECTIONS.filter((section) => ["library", "replays"].includes(section.id))} activeSection={activeSection} onChange={handleSectionChange} />
          </nav>
        </header>

      {boardError ? <p className="text-sm text-destructive">{boardError}</p> : null}

      {loading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Circle weight="fill" className="h-2 w-2 animate-pulse" />
          Carregando projetos...
        </div>
      ) : (
        <>
          {activeSection === "overview" ? (
            <WorkspaceDashboard
              workspace={workspace}
              projects={projects}
              libraryItems={libraryItems}
              ideas={ideas}
              onOpenSection={openSectionFromDashboard}
              onOpenProject={openProjectFromDashboard}
              onOpenLibraryItem={openLibraryItemFromDashboard}
              onOpenIdea={openIdeaFromDashboard}
              onOpenReplays={openReplaysFromDashboard}
            />
          ) : null}

          {activeSection === "development" ? (
            <DevelopmentSection
              name={name}
              description={description}
              currentState={currentState}
              repositoryUrl={repositoryUrl}
              status={status}
              creating={creating}
              createFormOpen={createFormOpen}
              formError={formError}
              projects={developmentProjects}
              editingProjectId={editingProjectId}
              savingProjectId={savingProjectId}
              editName={editName}
              editDescription={editDescription}
              editCurrentState={editCurrentState}
              editRepositoryUrl={editRepositoryUrl}
              editStatus={editStatus}
              completingProjectId={completingProjectId}
              onNameChange={setName}
              onDescriptionChange={setDescription}
              onCurrentStateChange={setCurrentState}
              onRepositoryUrlChange={setRepositoryUrl}
              onStatusChange={setStatus}
              canWrite={canWrite}
              onOpenCreateForm={() => setCreateFormOpen(true)}
              onCancelCreate={handleCancelCreate}
              onSubmit={handleCreate}
              onDragEnd={handleDragEnd}
              onStartEditing={startEditing}
              onCancelEditing={cancelEditing}
              onSave={handleUpdate}
              onEditNameChange={setEditName}
              onEditDescriptionChange={setEditDescription}
              onEditCurrentStateChange={setEditCurrentState}
              onEditRepositoryUrlChange={setEditRepositoryUrl}
              onEditStatusChange={setEditStatus}
              onOpenDetails={openProjectFromDashboard}
            />
          ) : null}

          {activeSection === "projects" ? (
            <CompletedProjectsSection projects={completedProjects} archivedProjects={archivedProjects} archivedOpen={archivedProjectsOpen} actionId={projectActionId} recentlyCompletedProjectId={recentlyCompletedProjectId} canWrite={canWrite} onOpenDetails={openProjectFromDashboard} onOpenArchived={() => void openArchivedProjects()} onCloseArchived={() => setArchivedProjectsOpen(false)} onRestore={(project) => void handleRestoreProject(project)} onDelete={setProjectPendingDeletion} name={name} description={description} currentState={currentState} repositoryUrl={repositoryUrl} creating={creating} createFormOpen={createFormOpen} formError={formError} onOpenCreateForm={() => { setStatus("DONE"); setCreateFormOpen(true); }} onCancelCreate={handleCancelCreate} onSubmit={handleCreate} onNameChange={setName} onDescriptionChange={setDescription} onCurrentStateChange={setCurrentState} onRepositoryUrlChange={setRepositoryUrl} />
          ) : null}

          {activeSection === "work-items" ? (
            <WorkspaceWorkItemsPanel workspaceId={workspace.id} onOpenProject={openProjectFromWorkItems} canWrite={canWrite} />
          ) : null}

          {activeSection === "library" ? (
            <KnowledgeFullscreenEnvironment workspaceName={workspace.name} onExit={() => handleSectionChange("overview")}>
              <LibraryPanel
                workspaceId={workspace.id}
                selectedItemId={selectedLibraryItemId}
                canWrite={canWrite}
                onItemsChange={handleLibraryItemsChange}
                onItemRestored={(item) => setLibraryItems((current) => [item, ...current.filter((value) => value.id !== item.id)])}
              />
            </KnowledgeFullscreenEnvironment>
          ) : null}

          {activeSection === "ideas" ? (
            <IdeasPanel
              workspaceId={workspace.id}
              projects={projects}
              canWrite={canWrite}
              onProjectCreated={handleIdeaConverted}
              onOpenProject={openProjectFromDashboard}
              selectedIdeaId={selectedIdeaId}
              onIdeasChange={handleIdeasChange}
              onIdeaRestored={(idea) => setIdeas((current) => [idea, ...current.filter((item) => item.id !== idea.id)])}
            />
          ) : null}
          {activeSection === "replays" ? <ReplaysPanel workspaceId={workspace.id} canWrite={canWrite} onImmersiveChange={setReplaysImmersive} onExitWorkspace={() => handleSectionChange("overview")} replayId={selectedReplayId} onOpenReplay={onOpenReplay} onBackToCatalog={onBackToReplayCatalog} /> : null}
        </>
      )}

      {selectedProject ? (
        <ProjectDetailsPanel
          project={selectedProject}
          canWrite={canWrite}
          onClose={() => onNavigateSection(activeSection)}
          onProjectUpdated={applyProjectUpdate}
          onArchive={(project) => void handleArchiveProject(project)}
          onDelete={setProjectPendingDeletion}
          actionLoading={projectActionId === selectedProject.id}
        />
      ) : null}
      {completionToast ? <ToastNotification {...completionToast} onDismiss={dismissCompletionToast} /> : null}
      <ProjectCompletionPopup projectName={completionPopupProjectName} />
      <ConfirmationDialog open={Boolean(projectPendingDeletion)} title="Excluir projeto permanentemente?" message="Esta ação removerá o projeto e os dados associados e não poderá ser desfeita." itemName={projectPendingDeletion?.name} confirmLabel="Excluir permanentemente" loadingLabel="Excluindo..." destructive loading={projectPendingDeletion !== null && projectActionId === projectPendingDeletion.id} onCancel={() => setProjectPendingDeletion(null)} onConfirm={() => void handleDeleteProject()} />
      </div>
    </div>
  );
}

function KnowledgeFullscreenEnvironment({ workspaceName, onExit, children }: { workspaceName: string; onExit: () => void; children: ReactNode }) {
  const rootRef = useRef<HTMLElement>(null); const [revealed, setRevealed] = useState(false); const [exiting, setExiting] = useState(false);
  useLayoutEffect(() => {
    if (!rootRef.current) return;
    return animateAcervoEntry(rootRef.current, () => setRevealed(true));
  }, []);
  useEffect(() => { const element = rootRef.current; return () => { if (element) gsap.killTweensOf(element); }; }, []);
  const leave = () => { if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) { onExit(); return; } setExiting(true); gsap.to(rootRef.current, { autoAlpha: 0, y: -8, duration: .24, ease: "power2.inOut", onComplete: onExit }); };
  return <section ref={rootRef} className={`knowledge-environment ${exiting ? "knowledge-environment--exiting" : ""}`} aria-label="Acervo do workspace">
    <header className="knowledge-environment__header">
      <Button type="button" variant="ghost" size="sm" className="knowledge-environment__back" onClick={leave}><ArrowLeft className="h-4 w-4" />Workspace</Button>
      <div className="knowledge-environment__identity"><p><Books className="h-4 w-4" weight="duotone" />Acervo</p><span>{workspaceName} / conhecimento compartilhado</span></div>
      <span className="knowledge-environment__rule" aria-hidden="true" />
    </header>
    <div ref={(element) => element?.toggleAttribute("inert", !revealed)} className={`knowledge-environment__content ${revealed ? "" : "knowledge-environment__content--entering"}`}>{children}</div>
    {!revealed ? <LibraryEntryOverlay /> : null}
  </section>;
}

function LibraryEntryOverlay() {
  return <div className="library-entry-overlay" aria-hidden="true"><AcervoLibraryScene /><div className="library-entry-overlay__title"><span>MEMÓRIA DO WORKSPACE</span><strong>Acervo</strong><p>Conhecimento preservado.</p></div></div>;
}

function WorkspaceTabGroup({ label, sections, activeSection, onChange }: { label: string; sections: Array<{ id: WorkspaceSection; label: string; description: string }>; activeSection: WorkspaceSection; onChange: (section: WorkspaceSection) => void }) {
  const overview = sections[0]?.id === "overview";
  return <div className={`workspace-tabs__group ${overview ? "workspace-tabs__group--overview" : ""}`} aria-label={label}>{overview ? null : <span className="workspace-tabs__group-label">{label}</span>}<span className="workspace-tabs__group-items">{sections.map((section) => <button key={section.id} type="button" aria-current={activeSection === section.id ? "page" : undefined} className={`workspace-tabs__item ${activeSection === section.id ? "workspace-tabs__item--active" : ""}`} onClick={() => onChange(section.id)}>{getSectionIcon(section.id, "h-4 w-4")}{section.label}</button>)}</span></div>;
}

function WorkspaceNavButton({
  section,
  active,
  onClick,
}: {
  section: { id: WorkspaceSection; label: string; description: string };
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-current={active ? "page" : undefined}
      className={`group flex min-w-0 items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm font-medium transition-[background-color,color,box-shadow] ${
        active
          ? "bg-primary text-primary-foreground shadow-[0_14px_32px_-24px_hsl(var(--primary))]"
          : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
      }`}
      onClick={onClick}
    >
      <span className={`grid h-8 w-8 shrink-0 place-items-center rounded-md ${active ? "bg-primary-foreground/15" : "bg-background"}`}>
        {getSectionIcon(section.id, "h-4 w-4")}
      </span>
      <span className="min-w-0">
        <span className="block truncate">{section.label}</span>
      </span>
    </button>
  );
}

function GlobalSearch({
  searchTerm,
  searchOpen,
  normalizedSearchTerm,
  totalSearchResults,
  searchResults,
  onSearchTermChange,
  onSearchOpenChange,
  onSelect,
}: {
  searchTerm: string;
  searchOpen: boolean;
  normalizedSearchTerm: string;
  totalSearchResults: number;
  searchResults: Array<{ domain: SearchDomain; title: string; results: SearchResult[] }>;
  onSearchTermChange: (value: string) => void;
  onSearchOpenChange: (value: boolean) => void;
  onSelect: (result: SearchResult) => void;
}) {
  return (
    <div className="relative min-w-0">
      <label className="sr-only" htmlFor="workspace-search">
        Buscar no workspace
      </label>
      <div className="relative">
        <MagnifyingGlass className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <input
          id="workspace-search"
          className="h-11 w-full rounded-lg border border-input bg-card pl-9 pr-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={searchTerm}
          onChange={(event) => {
            onSearchTermChange(event.target.value);
            onSearchOpenChange(true);
          }}
          onFocus={() => onSearchOpenChange(true)}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              onSearchOpenChange(false);
            }
          }}
            placeholder="Buscar projetos, Acervo, ideias..."
          autoComplete="off"
        />
      </div>

      {searchOpen && normalizedSearchTerm.length >= 2 ? (
        <div className="absolute right-0 z-40 mt-2 max-h-[min(70vh,520px)] w-full overflow-y-auto rounded-xl border border-border bg-card p-2 shadow-[0_28px_80px_-42px_hsl(var(--foreground))]">
          {totalSearchResults === 0 ? (
            <p className="px-3 py-6 text-center text-sm text-muted-foreground">Nenhum resultado encontrado.</p>
          ) : (
            <div className="grid gap-3">
              {searchResults.map((group) =>
                group.results.length > 0 ? (
                  <SearchResultGroup
                    key={group.domain}
                    title={group.title}
                    results={group.results}
                    onSelect={onSelect}
                  />
                ) : null,
              )}
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}

function getSectionIcon(sectionId: WorkspaceSection, className: string) {
  const props = { className, weight: "duotone" as const };
  if (sectionId === "overview") {
    return <SquaresFour {...props} />;
  }
  if (sectionId === "development") {
    return <Kanban {...props} />;
  }
  if (sectionId === "work-items") {
    return <CheckSquare {...props} />;
  }
  if (sectionId === "projects") {
    return <Folders {...props} />;
  }
  if (sectionId === "library") {
    return <Books {...props} />;
  }
  if (sectionId === "replays") {
    return <ArrowsClockwise {...props} />;
  }
  return <Lightbulb {...props} />;
}

function SearchResultGroup({
  title,
  results,
  onSelect,
}: {
  title: string;
  results: SearchResult[];
  onSelect: (result: SearchResult) => void;
}) {
  const visibleResults = results.slice(0, 5);
  const hiddenCount = results.length - visibleResults.length;

  return (
    <section className="grid gap-1">
      <h3 className="px-2 text-[11px] font-medium uppercase text-muted-foreground">{title}</h3>
      {visibleResults.map((result) => (
        <button
          key={`${result.domain}-${result.id}`}
          type="button"
          className="grid min-w-0 gap-1 rounded-md px-2 py-2 text-left hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          onClick={() => onSelect(result)}
        >
          <span className="flex min-w-0 items-center gap-2">
            <span className="min-w-0 truncate text-sm font-medium text-card-foreground">{result.title}</span>
            {result.badge ? (
              <span className="shrink-0 rounded-full border border-border bg-background px-2 py-0.5 text-[11px] text-muted-foreground">
                {result.badge}
              </span>
            ) : null}
          </span>
          <span className="truncate text-xs text-muted-foreground">{result.description}</span>
        </button>
      ))}
      {hiddenCount > 0 ? <p className="px-2 py-1 text-xs text-muted-foreground">+ {hiddenCount} resultados</p> : null}
    </section>
  );
}

type DevelopmentSectionProps = {
  canWrite: boolean;
  name: string;
  description: string;
  currentState: string;
  repositoryUrl: string;
  status: ProjectStatus | "";
  creating: boolean;
  createFormOpen: boolean;
  formError: string | null;
  projects: Project[];
  editingProjectId: string | null;
  savingProjectId: string | null;
  editName: string;
  editDescription: string;
  editCurrentState: string;
  editRepositoryUrl: string;
  editStatus: ProjectStatus;
  completingProjectId: string | null;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onCurrentStateChange: (value: string) => void;
  onRepositoryUrlChange: (value: string) => void;
  onStatusChange: (value: ProjectStatus | "") => void;
  onOpenCreateForm: () => void;
  onCancelCreate: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onDragEnd: (event: DragEndEvent) => Promise<boolean>;
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

function DevelopmentSection({
  canWrite,
  name,
  description,
  currentState,
  repositoryUrl,
  status,
  creating,
  createFormOpen,
  formError,
  projects,
  editingProjectId,
  savingProjectId,
  editName,
  editDescription,
  editCurrentState,
  editRepositoryUrl,
  editStatus,
  completingProjectId,
  onNameChange,
  onDescriptionChange,
  onCurrentStateChange,
  onRepositoryUrlChange,
  onStatusChange,
  onOpenCreateForm,
  onCancelCreate,
  onSubmit,
  onDragEnd,
  onStartEditing,
  onCancelEditing,
  onSave,
  onEditNameChange,
  onEditDescriptionChange,
  onEditCurrentStateChange,
  onEditRepositoryUrlChange,
  onEditStatusChange,
  onOpenDetails,
}: DevelopmentSectionProps) {
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }));
  const railRef = useRef<HTMLDivElement>(null);
  const [dragging, setDragging] = useState(false);
  const [completionFeedback, setCompletionFeedback] = useState<string | null>(null);
  useEffect(() => { if (!completionFeedback) return; const timeout = window.setTimeout(() => setCompletionFeedback(null), 800); return () => window.clearTimeout(timeout); }, [completionFeedback]);
  useEffect(() => {
    const rail = railRef.current;
    if (!rail) return;

    const handleWheel = (event: WheelEvent) => {
      if (dragging || event.ctrlKey || event.shiftKey || Math.abs(event.deltaX) > Math.abs(event.deltaY)) return;

      const delta = event.deltaMode === WheelEvent.DOM_DELTA_LINE
        ? event.deltaY * 16
        : event.deltaMode === WheelEvent.DOM_DELTA_PAGE
          ? event.deltaY * rail.clientWidth
          : event.deltaY;
      const maxScrollLeft = rail.scrollWidth - rail.clientWidth;
      const nextScrollLeft = Math.max(0, Math.min(maxScrollLeft, rail.scrollLeft + delta));

      if (maxScrollLeft <= 0 || nextScrollLeft === rail.scrollLeft) return;

      event.preventDefault();
      rail.scrollLeft = nextScrollLeft;
    };

    rail.addEventListener("wheel", handleWheel, { passive: false });
    return () => rail.removeEventListener("wheel", handleWheel);
  }, [dragging]);
  async function handleBoardDragEnd(event: DragEndEvent) { setDragging(false); const project = event.active.data.current?.project as Project | undefined; if (await onDragEnd(event) && project) setCompletionFeedback(project.name); }
  return (
    <div className="flex min-w-0 flex-col gap-7">
      <div className="flex min-w-0 flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-base font-semibold text-foreground">Desenvolvimento</h3>
          <p className="mt-1 text-sm text-muted-foreground">Acompanhe o fluxo dos projetos ate a conclusao.</p>
        </div>
        {canWrite && !createFormOpen ? (
          <Button type="button" onClick={onOpenCreateForm}>
            <Plus className="h-4 w-4" />
            Novo projeto
          </Button>
        ) : null}
      </div>

      {canWrite && createFormOpen ? (
        <ProjectCreateForm
          name={name}
          description={description}
          currentState={currentState}
          repositoryUrl={repositoryUrl}
          status={status}
          creating={creating}
          error={formError}
          onNameChange={onNameChange}
          onDescriptionChange={onDescriptionChange}
          onCurrentStateChange={onCurrentStateChange}
          onRepositoryUrlChange={onRepositoryUrlChange}
          onStatusChange={onStatusChange}
          onSubmit={onSubmit}
          onCancel={onCancelCreate}
        />
      ) : null}

      <DndContext sensors={canWrite ? sensors : []} onDragStart={(_event: DragStartEvent) => setDragging(true)} onDragCancel={() => setDragging(false)} onDragEnd={canWrite ? handleBoardDragEnd : undefined}>
        {projects.length === 0 ? (
          <div className="rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground">
            <p className="font-medium text-foreground">Nenhum projeto em desenvolvimento.</p>
            <p className="mt-1">Use Novo projeto para criar o primeiro card.</p>
          </div>
        ) : (
          <div ref={railRef} className="kanban-rail w-full min-w-0 overflow-x-auto pb-2">
            <div className="flex min-w-max items-start gap-5 px-1" data-dragging={dragging || undefined}>
              {DEVELOPMENT_PROJECT_STATUS_COLUMNS.map((column) => (
                <ProjectStatusColumn
                  key={column.status}
                  status={column.status}
                  label={column.label}
                  projects={projects.filter((project) => project.status === column.status)}
                  editingProjectId={editingProjectId}
                  savingProjectId={savingProjectId}
                  editName={editName}
                  editDescription={editDescription}
                  editCurrentState={editCurrentState}
                  editRepositoryUrl={editRepositoryUrl}
                  editStatus={editStatus}
                  completingProjectId={completingProjectId}
                  canWrite={canWrite}
                  onStartEditing={onStartEditing}
                  onCancelEditing={onCancelEditing}
                  onSave={(project) => onSave(project)}
                  onEditNameChange={onEditNameChange}
                  onEditDescriptionChange={onEditDescriptionChange}
                  onEditCurrentStateChange={onEditCurrentStateChange}
                  onEditRepositoryUrlChange={onEditRepositoryUrlChange}
                  onEditStatusChange={onEditStatusChange}
                  onOpenDetails={onOpenDetails}
                />
              ))}
              {canWrite ? <CompleteProjectDropTarget feedback={completionFeedback} /> : null}
            </div>
          </div>
        )}
      </DndContext>
    </div>
  );
}

function CompleteProjectDropTarget({ feedback }: { feedback: string | null }) {
  const { isOver, setNodeRef } = useDroppable({
    id: "complete-project-drop-target",
    data: {
      status: "DONE" satisfies ProjectStatus,
      type: "completion-target",
    },
  });

  return (
    <section
      ref={setNodeRef}
      className={`complete-project-target flex w-[min(82vw,320px)] min-w-[280px] max-w-[320px] flex-col p-3 transition-[border-color,background-color,transform,opacity] sm:min-w-[300px] ${
        isOver ? "complete-project-target--over" : ""
      }`}
      aria-label="Concluir projeto"
    >
      <div className="complete-project-target__header">
        <span className="complete-project-target__header-icon" aria-hidden="true"><CheckSquare className="h-4 w-4" weight="duotone" /></span>
        <div className="min-w-0">
          <p className="complete-project-target__step">Destino</p>
          <h3 className="complete-project-target__title">Concluir</h3>
          <p className="complete-project-target__helper">Finaliza o projeto</p>
        </div>
      </div>
      <div className="complete-project-target__drop flex min-h-32 flex-1 items-center justify-center px-3 py-8 text-center">
        <div>
          <CheckSquare className="complete-project-target__drop-icon mx-auto mb-2 h-4 w-4" weight="duotone" aria-hidden="true" />
          <p className="text-sm font-medium">{feedback ? `${feedback} concluído` : "Solte aqui para concluir"}</p>
          {!feedback ? <p className="mt-1 text-xs">O projeto será movido para finalizados.</p> : null}
        </div>
      </div>
    </section>
  );
}

function CompletedProjectsSection({
  projects,
  archivedProjects,
  archivedOpen,
  actionId,
  recentlyCompletedProjectId,
  canWrite,
  onOpenDetails,
  onOpenArchived,
  onCloseArchived,
  onRestore,
  onDelete,
  name,
  description,
  currentState,
  repositoryUrl,
  creating,
  createFormOpen,
  formError,
  onOpenCreateForm,
  onCancelCreate,
  onSubmit,
  onNameChange,
  onDescriptionChange,
  onCurrentStateChange,
  onRepositoryUrlChange,
}: {
  projects: Project[];
  archivedProjects: Project[];
  archivedOpen: boolean;
  actionId: string | null;
  recentlyCompletedProjectId: string | null;
  canWrite: boolean;
  onOpenDetails: (project: Project) => void;
  onOpenArchived: () => void;
  onCloseArchived: () => void;
  onRestore: (project: Project) => void;
  onDelete: (project: Project) => void;
  name: string;
  description: string;
  currentState: string;
  repositoryUrl: string;
  creating: boolean;
  createFormOpen: boolean;
  formError: string | null;
  onOpenCreateForm: () => void;
  onCancelCreate: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onCurrentStateChange: (value: string) => void;
  onRepositoryUrlChange: (value: string) => void;
}) {
  const [viewMode, setViewMode] = useState<ViewMode>("visual");
  return <div className="grid min-w-0 gap-6">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h3 className="text-lg font-medium text-foreground">Projetos finalizados</h3><p className="mt-1 text-sm text-muted-foreground">Registre projetos concluídos ou entregas já existentes sem passar pelo fluxo de desenvolvimento.</p></div><div className="flex flex-wrap items-center gap-2">{canWrite ? <Button type="button" variant="outline" onClick={archivedOpen ? onCloseArchived : onOpenArchived}>{archivedOpen ? "Fechar arquivados" : "Arquivados"}</Button> : null}<ViewModeToggle value={viewMode} onChange={setViewMode} />{canWrite && !createFormOpen ? <Button type="button" onClick={onOpenCreateForm}><Plus className="h-4 w-4" />Adicionar projeto finalizado</Button> : null}</div></div>
    {canWrite && createFormOpen ? <ProjectCreateForm name={name} description={description} currentState={currentState} repositoryUrl={repositoryUrl} status="DONE" creating={creating} error={formError} onNameChange={onNameChange} onDescriptionChange={onDescriptionChange} onCurrentStateChange={onCurrentStateChange} onRepositoryUrlChange={onRepositoryUrlChange} onStatusChange={() => undefined} onSubmit={onSubmit} onCancel={onCancelCreate} /> : null}
    {archivedOpen ? <ArchivedProjectsList projects={archivedProjects} actionId={actionId} onRestore={onRestore} onDelete={onDelete} /> : projects.length === 0 ? (
      <div className="rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground">
        <p className="font-medium text-foreground">Nenhum projeto concluido ainda.</p>
        <p className="mt-1">Adicione uma entrega concluída ou finalize um projeto do fluxo de desenvolvimento.</p>
      </div>
    ) : viewMode === "visual" ? <div className="grid min-w-0 max-w-[1720px] gap-5 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
      {projects.map((project) => <CompletedProjectCard key={project.id} project={project} recentlyCompleted={project.id === recentlyCompletedProjectId} onOpenDetails={onOpenDetails} />)}
    </div> : <CompletedProjectList projects={projects} recentlyCompletedProjectId={recentlyCompletedProjectId} onOpenDetails={onOpenDetails} />}
  </div>;
}

function ArchivedProjectsList({ projects, actionId, onRestore, onDelete }: { projects: Project[]; actionId: string | null; onRestore: (project: Project) => void; onDelete: (project: Project) => void }) {
  return <div className="grid gap-3"><div><h4 className="text-base font-semibold text-foreground">Arquivados</h4><p className="mt-1 text-sm text-muted-foreground">Itens fora das listas normais, preservando o status original.</p></div>{projects.length === 0 ? <EmptyArchivedProjects /> : <ul className="divide-y divide-border rounded-xl border border-border bg-card">{projects.map((project) => <li key={project.id} className="grid gap-3 px-4 py-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"><div className="min-w-0"><p className="break-words text-sm font-semibold text-foreground">{project.name}</p><p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{project.description || project.currentState || "Sem descrição cadastrada."}</p><p className="mt-2 text-xs text-muted-foreground">{getProjectStatusLabel(project.status)} · Arquivado em {project.archivedAt ? formatDate(project.archivedAt) : "data indisponível"}</p></div><div className="flex flex-wrap gap-2"><Button type="button" variant="outline" disabled={actionId === project.id} onClick={() => onRestore(project)}>{actionId === project.id ? "Restaurando..." : "Restaurar"}</Button><Button type="button" variant="destructive" disabled={actionId === project.id} onClick={() => onDelete(project)}>Excluir permanentemente</Button></div></li>)}</ul>}</div>;
}

function EmptyArchivedProjects() { return <div className="rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground"><p className="font-medium text-foreground">Nenhum projeto arquivado.</p></div>; }

function CompletedProjectCard({ project, recentlyCompleted, onOpenDetails }: { project: Project; recentlyCompleted: boolean; onOpenDetails: (project: Project) => void }) {
  const coverUrl = getProjectCoverUrl(project.workspaceId, project);
  const [backgroundSource, setBackgroundSource] = useState(coverUrl ?? no8doLogo);

  useEffect(() => {
    setBackgroundSource(coverUrl ?? no8doLogo);
  }, [coverUrl]);

  const hasFallbackBackground = backgroundSource === no8doLogo;

  return <article className={`editorial-card project-catalog-card motion-sensitive group grid min-w-0 overflow-hidden p-0 ${hasFallbackBackground ? "project-catalog-card--fallback" : ""} ${recentlyCompleted ? "recently-completed-project" : ""}`}>
    <img className="project-catalog-card__background" src={backgroundSource} alt="" aria-hidden="true" loading="lazy" decoding="async" onError={() => setBackgroundSource(no8doLogo)} />
    <button type="button" className="w-full min-w-0 text-left" onClick={() => onOpenDetails(project)}>
      <span className="project-catalog-card__content grid min-w-0 gap-2 p-3 sm:p-3.5">
        <span className="status-chip">Concluído</span>
        <span className="block break-words text-[15px] font-semibold leading-5 text-card-foreground">{project.name}</span>
        <span className="line-clamp-2 text-xs leading-5 text-muted-foreground">{project.description || project.currentState || "Sem descricao cadastrada."}</span>
        <span className="grid gap-1 text-[11px] text-muted-foreground"><span className="break-words">Criado por {project.createdByName || "Usuário"}</span><span>Atualizado {formatDate(project.updatedAt)}</span></span>
      </span>
    </button>
    {project.repositoryUrl ? <a href={project.repositoryUrl} target="_blank" rel="noopener noreferrer" className="absolute right-2 top-2 z-10 grid h-8 w-8 place-items-center rounded-md border border-border/70 bg-card/90 text-muted-foreground hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={`Abrir repositório de ${project.name} no GitHub`} title="Abrir repositório no GitHub" onClick={(event) => event.stopPropagation()}><GithubLogo className="h-4 w-4" weight="bold" aria-hidden="true" /></a> : null}
  </article>;
}

function CompletedProjectList({ projects, recentlyCompletedProjectId, onOpenDetails }: { projects: Project[]; recentlyCompletedProjectId: string | null; onOpenDetails: (project: Project) => void }) {
  return <ul className="divide-y divide-border rounded-xl border border-border bg-card">{projects.map((project) => <li key={project.id} className="relative"><button type="button" className={`grid w-full min-w-0 grid-cols-[64px_minmax(0,1fr)] gap-3 px-3 py-3 pr-12 text-left transition-[background-color] hover:bg-muted/55 sm:grid-cols-[72px_minmax(0,1fr)_auto] sm:items-center sm:px-4 sm:pr-14 ${project.id === recentlyCompletedProjectId ? "recently-completed-project" : ""}`} onClick={() => onOpenDetails(project)}><span className="overflow-hidden rounded-md [&_.project-media]:aspect-auto [&_.project-media]:h-14 [&_.project-media]:w-16 sm:[&_.project-media]:h-16"><ProjectMedia workspaceId={project.workspaceId} project={project} alt={`Capa do projeto ${project.name}`} /></span><span className="grid min-w-0 gap-1"><span className="break-words text-sm font-semibold text-card-foreground">{project.name}</span><span className="line-clamp-1 text-xs text-muted-foreground">{project.description || project.currentState || "Sem descricao cadastrada."}</span><span className="text-[11px] text-muted-foreground">Criado por {project.createdByName || "Usuário"}</span></span><span className="text-xs text-muted-foreground">Concluído<br />Atualizado {formatDate(project.updatedAt)}</span></button>{project.repositoryUrl ? <a href={project.repositoryUrl} target="_blank" rel="noopener noreferrer" className="absolute right-2 top-2 grid h-8 w-8 place-items-center rounded-md text-muted-foreground hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={`Abrir repositório de ${project.name} no GitHub`} title="Abrir repositório no GitHub" onClick={(event) => event.stopPropagation()}><GithubLogo className="h-4 w-4" weight="bold" aria-hidden="true" /></a> : null}</li>)}</ul>;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

function matchesSearch(term: string, values: Array<string | null | undefined>) {
  return values.some((value) => value?.toLowerCase().includes(term));
}

function getLibraryTypeLabel(type: LibraryItemType) {
  const labels: Record<LibraryItemType, string> = {
    DOCUMENT: "Documento",
    IDENTITY: "Identidade",
    LINK: "Link",
    TOOL: "Ferramenta",
    COMMAND: "Comando",
    SNIPPET: "Snippet",
    REFERENCE: "Referencia",
    TEMPLATE: "Template",
    NOTE: "Nota",
    DECISION: "Decisão",
    PROCESS: "Processo",
    INFRASTRUCTURE: "Infraestrutura",
    MATERIAL: "Material",
  };
  return labels[type];
}

function getIdeaTypeLabel(type: IdeaType) {
  const labels: Record<IdeaType, string> = {
    PROJECT: "Sistema",
    FEATURE: "Funcionalidade",
    IMPROVEMENT: "Melhoria",
    RESEARCH: "Pesquisa",
    PRODUCT: "Produto",
    OTHER: "Outro",
  };
  return labels[type];
}

function getIdeaStatusLabel(status: IdeaStatus) {
  const labels: Record<IdeaStatus, string> = {
    INBOX: "Caixa de entrada",
    PLANNED: "Planejada",
    CONVERTED: "Convertida",
    ARCHIVED: "Arquivada",
  };
  return labels[status];
}
