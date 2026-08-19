import { FormEvent, useEffect, useState } from "react";
import { DndContext, type DragEndEvent } from "@dnd-kit/core";
import { Circle } from "@phosphor-icons/react";
import { ClientsPanel } from "@/clients/ClientsPanel";
import { listClients, type Client } from "@/clients/clientApi";
import { IdeasPanel } from "@/ideas/IdeasPanel";
import { LibraryPanel } from "@/library/LibraryPanel";
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
import { type Workspace } from "@/workspaces/workspaceApi";

type WorkspaceSection = "development" | "projects" | "clients" | "library" | "ideas";

const WORKSPACE_SECTIONS: Array<{ id: WorkspaceSection; label: string; description: string }> = [
  {
    id: "development",
    label: "Desenvolvimento",
    description: "Projetos que ainda estao sendo construidos.",
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
  const [activeSection, setActiveSection] = useState<WorkspaceSection>("development");
  const [formError, setFormError] = useState<string | null>(null);
  const [boardError, setBoardError] = useState<string | null>(null);

  const developmentProjects = projects.filter((project) => project.status !== "DONE");
  const completedProjects = projects.filter((project) => project.status === "DONE");
  const activeSectionInfo = WORKSPACE_SECTIONS.find((section) => section.id === activeSection) ?? WORKSPACE_SECTIONS[0];

  useEffect(() => {
    let cancelled = false;

    async function loadProjects() {
      setLoading(true);
      setBoardError(null);
      setEditingProjectId(null);

      try {
        const items = await listProjects(workspace.id);
        const clientItems = await listClients(workspace.id);

        if (!cancelled) {
          setProjects(items);
          setClients(clientItems);
          setSelectedProject(null);
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
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Nao foi possivel criar o projeto.");
    } finally {
      setCreating(false);
    }
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
    setSelectedProject(null);
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

        <div className="min-w-0">
          <h2 className="text-lg font-semibold leading-tight text-foreground">{activeSectionInfo.label}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{activeSectionInfo.description}</p>
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
          {activeSection === "development" ? (
            <DevelopmentSection
              name={name}
              description={description}
              currentState={currentState}
              status={status}
              creating={creating}
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

          {activeSection === "clients" ? (
            <ClientsPanel
              workspaceId={workspace.id}
              clients={clients}
              projects={projects}
              onClientCreated={handleClientCreated}
              onClientUpdated={handleClientUpdated}
              onOpenProject={setSelectedProject}
            />
          ) : null}

          {activeSection === "library" ? (
            <LibraryPanel workspaceId={workspace.id} />
          ) : null}

          {activeSection === "ideas" ? (
            <IdeasPanel
              workspaceId={workspace.id}
              projects={projects}
              onProjectCreated={handleIdeaConverted}
              onOpenProject={setSelectedProject}
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

type DevelopmentSectionProps = {
  name: string;
  description: string;
  currentState: string;
  status: ProjectStatus | "";
  creating: boolean;
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
      />

      {projects.length === 0 ? (
        <div className="rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground">
          <p className="font-medium text-foreground">Nenhum projeto em desenvolvimento.</p>
          <p className="mt-1">Use o formulario acima para criar o primeiro card.</p>
        </div>
      ) : (
        <DndContext onDragEnd={(event) => onDragEnd(event)}>
          <div className="w-full min-w-0 overflow-x-auto pb-3">
            <div className="flex min-w-max gap-4 px-1">
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
            </div>
          </div>
        </DndContext>
      )}
    </div>
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
