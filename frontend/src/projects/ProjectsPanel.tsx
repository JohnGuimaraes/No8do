import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { DndContext, useDroppable, type DragEndEvent } from "@dnd-kit/core";
import { Circle, MagnifyingGlass, Plus } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { ClientsPanel } from "@/clients/ClientsPanel";
import { listClients, type Client } from "@/clients/clientApi";
import { WorkspaceDashboard } from "@/dashboard/WorkspaceDashboard";
import { IdeasPanel } from "@/ideas/IdeasPanel";
import { listIdeas, type Idea, type IdeaStatus, type IdeaType } from "@/ideas/ideaApi";
import { LibraryPanel } from "@/library/LibraryPanel";
import { listLibraryItems, type LibraryItem, type LibraryItemType } from "@/library/libraryApi";
import {
  createProject,
  listProjects,
  updateProject,
  type CreateProjectInput,
  type Project,
  type ProjectStatus,
  type UpdateProjectInput,
} from "@/projects/projectApi";
import { ProjectCreateForm } from "@/projects/ProjectCreateForm";
import { ProjectDetailsPanel } from "@/projects/ProjectDetailsPanel";
import { ProjectStatusColumn } from "@/projects/ProjectStatusColumn";
import { DEVELOPMENT_PROJECT_STATUS_COLUMNS, getProjectStatusLabel } from "@/projects/projectStatus";
import { WorkspaceWorkItemsPanel } from "@/work-items/WorkspaceWorkItemsPanel";
import { type Workspace } from "@/workspaces/workspaceApi";

type WorkspaceSection = "overview" | "development" | "work-items" | "projects" | "clients" | "library" | "ideas";
type SearchDomain = "project" | "client" | "library" | "idea";
type SearchResult = {
  id: string;
  domain: SearchDomain;
  title: string;
  description: string;
  badge?: string;
  item: Project | Client | LibraryItem | Idea;
};

const WORKSPACE_SECTIONS: Array<{ id: WorkspaceSection; label: string; description: string }> = [
  {
    id: "overview",
    label: "Visão Geral",
    description: "Resumo operacional do workspace e atalhos para continuar.",
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
    label: "Projetos",
    description: "Projetos concluidos e memoria operacional.",
  },
  {
    id: "clients",
    label: "Clientes",
    description: "Clientes e projetos relacionados.",
  },
  {
    id: "library",
    label: "Biblioteca",
    description: "Conhecimento, ferramentas e referencias reutilizaveis.",
  },
  {
    id: "ideas",
    label: "Ideias",
    description: "Ideias que podem evoluir para novos projetos.",
  },
];

export function ProjectsPanel({ workspace }: { workspace: Workspace }) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [libraryItems, setLibraryItems] = useState<LibraryItem[]>([]);
  const [ideas, setIdeas] = useState<Idea[]>([]);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [currentState, setCurrentState] = useState("");
  const [status, setStatus] = useState<ProjectStatus | "">("");
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [savingProjectId, setSavingProjectId] = useState<string | null>(null);
  const [editingProjectId, setEditingProjectId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editCurrentState, setEditCurrentState] = useState("");
  const [editStatus, setEditStatus] = useState<ProjectStatus>("IDEA");
  const [selectedProject, setSelectedProject] = useState<Project | null>(null);
  const [selectedClientId, setSelectedClientId] = useState<string | null>(null);
  const [selectedLibraryItemId, setSelectedLibraryItemId] = useState<string | null>(null);
  const [selectedIdeaId, setSelectedIdeaId] = useState<string | null>(null);
  const [activeSection, setActiveSection] = useState<WorkspaceSection>("overview");
  const [searchTerm, setSearchTerm] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
  const [createFormOpen, setCreateFormOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [boardError, setBoardError] = useState<string | null>(null);

  const developmentProjects = projects.filter((project) => project.status !== "DONE");
  const completedProjects = projects.filter((project) => project.status === "DONE");
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
              project.clientName,
            ]),
          )
          .map<SearchResult>((project) => ({
            id: project.id,
            domain: "project",
            title: project.name,
            description: project.description || project.currentState || project.clientName || "Projeto do workspace",
            badge: getProjectStatusLabel(project.status),
            item: project,
          })),
      },
      {
        domain: "client" as const,
        title: "Clientes",
        results: clients
          .filter((client) =>
            matchesSearch(normalizedSearchTerm, [client.name, client.companyName, client.email, client.phone]),
          )
          .map<SearchResult>((client) => ({
            id: client.id,
            domain: "client",
            title: client.name,
            description: client.companyName || client.email || client.phone || "Cliente",
            badge: "Cliente",
            item: client,
          })),
      },
      {
        domain: "library" as const,
        title: "Biblioteca",
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
            description: item.description || item.url || item.content || "Item da biblioteca",
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
  }, [clients, ideas, libraryItems, normalizedSearchTerm, projects]);
  const totalSearchResults = searchResults.reduce((total, group) => total + group.results.length, 0);

  useEffect(() => {
    let cancelled = false;

    async function loadProjects() {
      setLoading(true);
      setBoardError(null);
      setEditingProjectId(null);

      try {
        const items = await listProjects(workspace.id);
        const clientItems = await listClients(workspace.id);
        const libraryItems = await listLibraryItems(workspace.id);
        const ideaItems = await listIdeas(workspace.id);

        if (!cancelled) {
          setProjects(items);
          setClients(clientItems);
          setLibraryItems(libraryItems);
          setIdeas(ideaItems);
          setSelectedProject(null);
          setSelectedClientId(null);
          setSelectedLibraryItemId(null);
          setSelectedIdeaId(null);
          setActiveSection("overview");
          resetCreateForm();
          setSearchTerm("");
          setSearchOpen(false);
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

  const handleLibraryItemsChange = useCallback((items: LibraryItem[]) => {
    setLibraryItems(items);
  }, []);

  const handleIdeasChange = useCallback((items: Idea[]) => {
    setIdeas(items);
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

    setCreating(true);
    setFormError(null);

    try {
      const project = await createProject(workspace.id, input);
      setProjects((current) => [project, ...current]);
      setName("");
      setDescription("");
      setCurrentState("");
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
    setEditStatus(project.status);
    setBoardError(null);
  }

  function cancelEditing() {
    setEditingProjectId(null);
    setSavingProjectId(null);
    setEditName("");
    setEditDescription("");
    setEditCurrentState("");
    setEditStatus("IDEA");
    setBoardError(null);
  }

  function applyProjectUpdate(updatedProject: Project) {
    setProjects((current) =>
      current.map((item) => (item.id === updatedProject.id ? updatedProject : item)),
    );
    setSelectedProject((current) => (current?.id === updatedProject.id ? updatedProject : current));
  }

  async function handleProjectClientChange(project: Project, clientId: string | null) {
    setBoardError(null);

    try {
      const updatedProject = await updateProject(workspace.id, project.id, {
        name: project.name,
        description: project.description ?? undefined,
        currentState: project.currentState ?? undefined,
        status: project.status,
        clientId,
      });
      applyProjectUpdate(updatedProject);
    } catch (err) {
      setBoardError(err instanceof Error ? err.message : "Nao foi possivel vincular o cliente.");
    }
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
      clientId: project.clientId,
    };
    const normalizedDescription = editDescription.trim();
    const normalizedCurrentState = editCurrentState.trim();

    if (normalizedDescription) {
      input.description = normalizedDescription;
    }
    if (normalizedCurrentState) {
      input.currentState = normalizedCurrentState;
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
      return;
    }

    const previousProjects = projects;
    setBoardError(null);
    setProjects((current) =>
      current.map((item) => (item.id === project.id ? { ...item, status: nextStatus } : item)),
    );

    try {
      const updatedProject = await updateProject(workspace.id, project.id, {
        name: project.name,
        description: project.description ?? undefined,
        currentState: project.currentState ?? undefined,
        status: nextStatus,
        clientId: project.clientId,
      });
      applyProjectUpdate(updatedProject);
    } catch (err) {
      setProjects(previousProjects);
      setBoardError(err instanceof Error ? err.message : "Nao foi possivel mover o projeto.");
    }
  }

  function handleClientCreated(client: Client) {
    setClients((current) => [client, ...current]);
  }

  function handleClientUpdated(client: Client) {
    setClients((current) => current.map((item) => (item.id === client.id ? client : item)));
    setProjects((current) =>
      current.map((project) =>
        project.clientId === client.id ? { ...project, clientName: client.name } : project,
      ),
    );
    setSelectedProject((current) =>
      current?.clientId === client.id ? { ...current, clientName: client.name } : current,
    );
  }

  function handleIdeaConverted(project: Project) {
    setProjects((current) => [project, ...current.filter((item) => item.id !== project.id)]);
    setSelectedProject(project);
    setActiveSection("development");
  }

  function handleSectionChange(sectionId: WorkspaceSection) {
    setActiveSection(sectionId);
    setEditingProjectId(null);
    setBoardError(null);
    if (sectionId !== "development") {
      handleCancelCreate();
    }
    setSelectedProject(null);
    setSelectedClientId(null);
    setSelectedLibraryItemId(null);
    setSelectedIdeaId(null);
  }

  function handleSearchSelect(result: SearchResult) {
    setSearchTerm("");
    setSearchOpen(false);
    setEditingProjectId(null);
    setBoardError(null);
    setSelectedProject(null);
    setSelectedClientId(null);
    setSelectedLibraryItemId(null);
    setSelectedIdeaId(null);

    if (result.domain === "project") {
      const project = result.item as Project;
      if (project.status === "DONE") {
        handleCancelCreate();
      }
      setActiveSection(project.status === "DONE" ? "projects" : "development");
      setSelectedProject(project);
      return;
    }

    handleCancelCreate();

    if (result.domain === "client") {
      setActiveSection("clients");
      setSelectedClientId(result.id);
      return;
    }

    if (result.domain === "library") {
      setActiveSection("library");
      setSelectedLibraryItemId(result.id);
      return;
    }

    setActiveSection("ideas");
    setSelectedIdeaId(result.id);
  }

  function openProjectFromDashboard(project: Project) {
    setActiveSection(project.status === "DONE" ? "projects" : "development");
    setSelectedProject(project);
  }

  function openProjectFromWorkItems(projectId: string) {
    const project = projects.find((item) => item.id === projectId);
    if (!project) {
      return;
    }

    setActiveSection(project.status === "DONE" ? "projects" : "development");
    setSelectedProject(project);
  }

  function openSectionFromDashboard(sectionId: Exclude<WorkspaceSection, "overview" | "work-items">) {
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
    <div className="flex min-w-0 flex-col gap-6">
      <header className="grid min-w-0 gap-3 border-b border-border pb-4">
        <nav
          className="flex min-w-0 gap-1 overflow-x-auto rounded-md border border-border bg-muted/40 p-1"
          aria-label="Navegacao principal do workspace"
        >
          {WORKSPACE_SECTIONS.map((section) => {
            const isActive = activeSection === section.id;
            return (
              <button
                key={section.id}
                type="button"
                aria-current={isActive ? "page" : undefined}
                className={`shrink-0 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                  isActive
                    ? "bg-background text-foreground shadow-sm ring-1 ring-border"
                    : "text-muted-foreground hover:bg-background/70 hover:text-foreground"
                }`}
                onClick={() => handleSectionChange(section.id)}
              >
                {section.label}
              </button>
            );
          })}
        </nav>

        <div className="grid min-w-0 gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(260px,420px)] lg:items-start">
          <div className="min-w-0">
            <h2 className="text-lg font-semibold leading-tight text-foreground">{activeSectionInfo.label}</h2>
            <p className="mt-1 text-sm text-muted-foreground">{activeSectionInfo.description}</p>
          </div>

          <div className="relative min-w-0">
            <label className="sr-only" htmlFor="workspace-search">
              Buscar no workspace
            </label>
            <div className="relative">
              <MagnifyingGlass className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                id="workspace-search"
                className="h-10 w-full rounded-md border border-input bg-background pl-9 pr-3 text-sm outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                value={searchTerm}
                onChange={(event) => {
                  setSearchTerm(event.target.value);
                  setSearchOpen(true);
                }}
                onFocus={() => setSearchOpen(true)}
                onKeyDown={(event) => {
                  if (event.key === "Escape") {
                    setSearchOpen(false);
                  }
                }}
                placeholder="Buscar no workspace..."
                autoComplete="off"
              />
            </div>

            {searchOpen && normalizedSearchTerm.length >= 2 ? (
              <div className="absolute right-0 z-40 mt-2 max-h-[min(70vh,520px)] w-full overflow-y-auto rounded-md border border-border bg-card p-2 shadow-xl">
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
                          onSelect={handleSearchSelect}
                        />
                      ) : null,
                    )}
                  </div>
                )}
              </div>
            ) : null}
          </div>
        </div>
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
              clients={clients}
              libraryItems={libraryItems}
              ideas={ideas}
              onOpenSection={openSectionFromDashboard}
              onOpenProject={openProjectFromDashboard}
              onOpenLibraryItem={openLibraryItemFromDashboard}
              onOpenIdea={openIdeaFromDashboard}
            />
          ) : null}

          {activeSection === "development" ? (
            <DevelopmentSection
              name={name}
              description={description}
              currentState={currentState}
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
              editStatus={editStatus}
              onNameChange={setName}
              onDescriptionChange={setDescription}
              onCurrentStateChange={setCurrentState}
              onStatusChange={setStatus}
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
              onEditStatusChange={setEditStatus}
              onOpenDetails={setSelectedProject}
            />
          ) : null}

          {activeSection === "projects" ? (
            <CompletedProjectsSection projects={completedProjects} onOpenDetails={setSelectedProject} />
          ) : null}

          {activeSection === "work-items" ? (
            <WorkspaceWorkItemsPanel workspaceId={workspace.id} onOpenProject={openProjectFromWorkItems} />
          ) : null}

          {activeSection === "clients" ? (
            <ClientsPanel
              workspaceId={workspace.id}
              clients={clients}
              projects={projects}
              onClientCreated={handleClientCreated}
              onClientUpdated={handleClientUpdated}
              onOpenProject={setSelectedProject}
              selectedClientId={selectedClientId}
            />
          ) : null}

          {activeSection === "library" ? (
            <LibraryPanel
              workspaceId={workspace.id}
              selectedItemId={selectedLibraryItemId}
              onItemsChange={handleLibraryItemsChange}
            />
          ) : null}

          {activeSection === "ideas" ? (
            <IdeasPanel
              workspaceId={workspace.id}
              projects={projects}
              onProjectCreated={handleIdeaConverted}
              onOpenProject={setSelectedProject}
              selectedIdeaId={selectedIdeaId}
              onIdeasChange={handleIdeasChange}
            />
          ) : null}
        </>
      )}

      {selectedProject ? (
        <ProjectDetailsPanel
          project={selectedProject}
          clients={clients}
          onClientChange={(clientId) => void handleProjectClientChange(selectedProject, clientId)}
          onClose={() => setSelectedProject(null)}
        />
      ) : null}
    </div>
  );
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
  name: string;
  description: string;
  currentState: string;
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
  editStatus: ProjectStatus;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onCurrentStateChange: (value: string) => void;
  onStatusChange: (value: ProjectStatus | "") => void;
  onOpenCreateForm: () => void;
  onCancelCreate: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onDragEnd: (event: DragEndEvent) => void;
  onStartEditing: (project: Project) => void;
  onCancelEditing: () => void;
  onSave: (project: Project) => void;
  onEditNameChange: (value: string) => void;
  onEditDescriptionChange: (value: string) => void;
  onEditCurrentStateChange: (value: string) => void;
  onEditStatusChange: (value: ProjectStatus) => void;
  onOpenDetails: (project: Project) => void;
};

function DevelopmentSection({
  name,
  description,
  currentState,
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
  editStatus,
  onNameChange,
  onDescriptionChange,
  onCurrentStateChange,
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
  onEditStatusChange,
  onOpenDetails,
}: DevelopmentSectionProps) {
  return (
    <div className="flex min-w-0 flex-col gap-6">
      <div className="flex min-w-0 flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-base font-semibold text-foreground">Desenvolvimento</h3>
          <p className="mt-1 text-sm text-muted-foreground">Acompanhe o fluxo dos projetos ate a conclusao.</p>
        </div>
        {!createFormOpen ? (
          <Button type="button" onClick={onOpenCreateForm}>
            <Plus className="h-4 w-4" />
            Novo projeto
          </Button>
        ) : null}
      </div>

      {createFormOpen ? (
        <ProjectCreateForm
          name={name}
          description={description}
          currentState={currentState}
          status={status}
          creating={creating}
          error={formError}
          onNameChange={onNameChange}
          onDescriptionChange={onDescriptionChange}
          onCurrentStateChange={onCurrentStateChange}
          onStatusChange={onStatusChange}
          onSubmit={onSubmit}
          onCancel={onCancelCreate}
        />
      ) : null}

      <DndContext onDragEnd={(event) => onDragEnd(event)}>
        {projects.length === 0 ? (
          <div className="rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground">
            <p className="font-medium text-foreground">Nenhum projeto em desenvolvimento.</p>
            <p className="mt-1">Use Novo projeto para criar o primeiro card.</p>
          </div>
        ) : (
          <div className="w-full min-w-0 overflow-x-auto pb-3">
            <div className="flex min-w-max items-stretch gap-4 px-1">
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
                  editStatus={editStatus}
                  onStartEditing={onStartEditing}
                  onCancelEditing={onCancelEditing}
                  onSave={(project) => onSave(project)}
                  onEditNameChange={onEditNameChange}
                  onEditDescriptionChange={onEditDescriptionChange}
                  onEditCurrentStateChange={onEditCurrentStateChange}
                  onEditStatusChange={onEditStatusChange}
                  onOpenDetails={onOpenDetails}
                />
              ))}
              <CompleteProjectDropTarget />
            </div>
          </div>
        )}
      </DndContext>
    </div>
  );
}

function CompleteProjectDropTarget() {
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
      className={`flex min-h-80 w-[min(72vw,220px)] min-w-[200px] max-w-[220px] flex-col justify-center rounded-lg border border-dashed p-3 text-center transition-colors ${
        isOver ? "border-primary bg-primary/10 text-primary" : "border-border bg-background/70 text-muted-foreground"
      }`}
      aria-label="Concluir projeto"
    >
      <div className="rounded-md bg-card/80 px-3 py-4 shadow-sm">
        <p className="text-sm font-semibold">Concluir projeto</p>
        <p className="mt-1 text-xs">Arraste aqui para concluir.</p>
      </div>
    </section>
  );
}

function CompletedProjectsSection({
  projects,
  onOpenDetails,
}: {
  projects: Project[];
  onOpenDetails: (project: Project) => void;
}) {
  if (projects.length === 0) {
    return (
      <div className="rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground">
        <p className="font-medium text-foreground">Nenhum projeto concluido ainda.</p>
        <p className="mt-1">Projetos com status DONE aparecem aqui como memoria operacional.</p>
      </div>
    );
  }

  return (
    <div className="grid min-w-0 gap-3">
      {projects.map((project) => (
        <button
          key={project.id}
          type="button"
          className="grid min-w-0 gap-3 rounded-md border border-border bg-card p-4 text-left shadow-sm transition-colors hover:bg-accent sm:grid-cols-[minmax(0,1fr)_auto]"
          onClick={() => onOpenDetails(project)}
        >
          <span className="min-w-0">
            <span className="block break-words text-base font-semibold text-card-foreground">{project.name}</span>
            <span className="mt-1 block text-sm text-muted-foreground">
              {project.description || project.currentState || "Sem descricao cadastrada."}
            </span>
          </span>
          <span className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground sm:justify-end">
            <span className="rounded-full border border-border bg-background px-2.5 py-1 font-medium">
              {getProjectStatusLabel(project.status)}
            </span>
            {project.clientName ? <span>cliente: {project.clientName}</span> : null}
            <span>atualizado em {formatDate(project.updatedAt)}</span>
          </span>
        </button>
      ))}
    </div>
  );
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
    LINK: "Link",
    TOOL: "Ferramenta",
    COMMAND: "Comando",
    SNIPPET: "Snippet",
    REFERENCE: "Referencia",
    TEMPLATE: "Template",
    NOTE: "Nota",
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
