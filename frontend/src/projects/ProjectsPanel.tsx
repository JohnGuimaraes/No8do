import { FormEvent, useEffect, useState } from "react";
import { Circle } from "@phosphor-icons/react";
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
import { ProjectStatusColumn } from "@/projects/ProjectStatusColumn";
import { PROJECT_STATUS_COLUMNS } from "@/projects/projectStatus";
import { type Workspace } from "@/workspaces/workspaceApi";

export function ProjectsPanel({ workspace }: { workspace: Workspace }) {
  const [projects, setProjects] = useState<Project[]>([]);
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
  const [formError, setFormError] = useState<string | null>(null);
  const [boardError, setBoardError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadProjects() {
      setLoading(true);
      setBoardError(null);
      setEditingProjectId(null);

      try {
        const items = await listProjects(workspace.id);

        if (!cancelled) {
          setProjects(items);
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

    setSavingProjectId(project.id);
    setBoardError(null);

    try {
      const updatedProject = await updateProject(workspace.id, project.id, input);
      setProjects((current) =>
        current.map((item) => (item.id === updatedProject.id ? updatedProject : item)),
      );
      cancelEditing();
    } catch (err) {
      setBoardError(err instanceof Error ? err.message : "Nao foi possivel salvar o projeto.");
    } finally {
      setSavingProjectId(null);
    }
  }

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <ProjectCreateForm
        name={name}
        description={description}
        currentState={currentState}
        status={status}
        creating={creating}
        error={formError}
        onNameChange={setName}
        onDescriptionChange={setDescription}
        onCurrentStateChange={setCurrentState}
        onStatusChange={setStatus}
        onSubmit={handleCreate}
      />

      {boardError ? <p className="text-sm text-destructive">{boardError}</p> : null}

      {loading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Circle weight="fill" className="h-2 w-2 animate-pulse" />
          Carregando projetos...
        </div>
      ) : projects.length === 0 ? (
        <div className="rounded-md border border-dashed border-border px-6 py-10 text-center text-sm text-muted-foreground">
          Nenhum projeto neste workspace
        </div>
      ) : (
        <div className="w-full min-w-0 overflow-x-auto pb-3">
          <div className="flex min-w-max gap-4 px-1">
            {PROJECT_STATUS_COLUMNS.map((column) => (
              <ProjectStatusColumn
                key={column.status}
                label={column.label}
                projects={projects.filter((project) => project.status === column.status)}
                editingProjectId={editingProjectId}
                savingProjectId={savingProjectId}
                editName={editName}
                editDescription={editDescription}
                editCurrentState={editCurrentState}
                editStatus={editStatus}
                onStartEditing={startEditing}
                onCancelEditing={cancelEditing}
                onSave={(project) => void handleUpdate(project)}
                onEditNameChange={setEditName}
                onEditDescriptionChange={setEditDescription}
                onEditCurrentStateChange={setEditCurrentState}
                onEditStatusChange={setEditStatus}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
