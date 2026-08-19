import { type ReactNode, useMemo } from "react";
import { type Client } from "@/clients/clientApi";
import { type Idea, type IdeaStatus, type IdeaType } from "@/ideas/ideaApi";
import { type LibraryItem, type LibraryItemType } from "@/library/libraryApi";
import { type Project, type ProjectStatus } from "@/projects/projectApi";
import { getProjectStatusLabel } from "@/projects/projectStatus";
import { type Workspace } from "@/workspaces/workspaceApi";

type WorkspaceDashboardProps = {
  workspace: Workspace;
  projects: Project[];
  clients: Client[];
  libraryItems: LibraryItem[];
  ideas: Idea[];
  onOpenSection: (section: "development" | "projects" | "clients" | "library" | "ideas") => void;
  onOpenProject: (project: Project) => void;
  onOpenLibraryItem: (itemId: string) => void;
  onOpenIdea: (ideaId: string) => void;
};

const DEVELOPMENT_ORDER: Record<ProjectStatus, number> = {
  ACTIVE: 0,
  PLANNING: 1,
  IDEA: 2,
  BLOCKED: 3,
  PAUSED: 4,
  DONE: 5,
};

export function WorkspaceDashboard({
  workspace,
  projects,
  clients,
  libraryItems,
  ideas,
  onOpenSection,
  onOpenProject,
  onOpenLibraryItem,
  onOpenIdea,
}: WorkspaceDashboardProps) {
  const metrics = useMemo(() => {
    const developmentProjects = projects.filter((project) => project.status !== "DONE");
    return {
      development: developmentProjects.length,
      blocked: projects.filter((project) => project.status === "BLOCKED").length,
      completed: projects.filter((project) => project.status === "DONE").length,
      clients: clients.length,
      activeIdeas: ideas.filter((idea) => idea.status !== "ARCHIVED" && idea.status !== "CONVERTED").length,
      library: libraryItems.length,
    };
  }, [clients.length, ideas, libraryItems.length, projects]);

  const attentionProjects = useMemo(
    () =>
      projects
        .filter((project) => project.status === "BLOCKED" || project.status === "PAUSED")
        .sort((left, right) => {
          const statusDiff = DEVELOPMENT_ORDER[left.status] - DEVELOPMENT_ORDER[right.status];
          return statusDiff === 0 ? Date.parse(right.updatedAt) - Date.parse(left.updatedAt) : statusDiff;
        })
        .slice(0, 5),
    [projects],
  );

  const currentProjects = useMemo(
    () =>
      projects
        .filter((project) => project.status === "ACTIVE" || project.status === "PLANNING" || project.status === "IDEA")
        .sort((left, right) => {
          const statusDiff = DEVELOPMENT_ORDER[left.status] - DEVELOPMENT_ORDER[right.status];
          return statusDiff === 0 ? Date.parse(right.updatedAt) - Date.parse(left.updatedAt) : statusDiff;
        })
        .slice(0, 5),
    [projects],
  );

  const recentIdeas = useMemo(
    () =>
      ideas
        .filter((idea) => idea.status !== "ARCHIVED")
        .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
        .slice(0, 5),
    [ideas],
  );

  const recentLibraryItems = useMemo(
    () =>
      [...libraryItems]
        .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
        .slice(0, 5),
    [libraryItems],
  );

  const recentCompletedProjects = useMemo(
    () =>
      projects
        .filter((project) => project.status === "DONE")
        .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
        .slice(0, 5),
    [projects],
  );

  return (
    <section className="grid min-w-0 gap-6">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <MetricCard
          label="Projetos em desenvolvimento"
          value={metrics.development}
          detail="status diferente de DONE"
          onClick={() => onOpenSection("development")}
        />
        <MetricCard
          label="Bloqueados"
          value={metrics.blocked}
          detail="projetos em BLOCKED"
          onClick={() => onOpenSection("development")}
        />
        <MetricCard
          label="Projetos concluidos"
          value={metrics.completed}
          detail="memoria operacional"
          onClick={() => onOpenSection("projects")}
        />
        <MetricCard label="Clientes" value={metrics.clients} detail="clientes cadastrados" onClick={() => onOpenSection("clients")} />
        <MetricCard label="Ideias" value={metrics.activeIdeas} detail="nao arquivadas nem convertidas" onClick={() => onOpenSection("ideas")} />
        <MetricCard label="Biblioteca" value={metrics.library} detail="itens reutilizaveis" onClick={() => onOpenSection("library")} />
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <DashboardSection title="Projetos que exigem atencao">
          {attentionProjects.length === 0 ? (
            <EmptyState text="Nenhum projeto bloqueado ou pausado." />
          ) : (
            attentionProjects.map((project) => (
              <ProjectRow key={project.id} project={project} onClick={() => onOpenProject(project)} />
            ))
          )}
        </DashboardSection>

        <DashboardSection title="Em desenvolvimento">
          {currentProjects.length === 0 ? (
            <EmptyState text="Nenhum projeto ativo, em planejamento ou ideia." />
          ) : (
            currentProjects.map((project) => (
              <ProjectRow key={project.id} project={project} onClick={() => onOpenProject(project)} />
            ))
          )}
        </DashboardSection>

        <DashboardSection title="Ideias recentes">
          {recentIdeas.length === 0 ? (
            <EmptyState text="Nenhuma ideia registrada para este workspace." />
          ) : (
            recentIdeas.map((idea) => (
              <button
                key={idea.id}
                type="button"
                className="grid min-w-0 gap-1 rounded-md border border-border bg-card px-3 py-2 text-left shadow-sm hover:bg-accent"
                onClick={() => onOpenIdea(idea.id)}
              >
                <span className="flex min-w-0 flex-wrap items-center gap-2">
                  <span className="min-w-0 break-words text-sm font-medium text-card-foreground">{idea.title}</span>
                  <Badge>{getIdeaTypeLabel(idea.type)}</Badge>
                  <Badge>{getIdeaStatusLabel(idea.status)}</Badge>
                </span>
                {idea.status === "CONVERTED" ? (
                  <span className="text-xs text-muted-foreground">Transformada em projeto</span>
                ) : null}
              </button>
            ))
          )}
        </DashboardSection>

        <DashboardSection title="Biblioteca recente">
          {recentLibraryItems.length === 0 ? (
            <EmptyState text="Nenhum item recente na biblioteca." />
          ) : (
            recentLibraryItems.map((item) => (
              <button
                key={item.id}
                type="button"
                className="grid min-w-0 gap-1 rounded-md border border-border bg-card px-3 py-2 text-left shadow-sm hover:bg-accent"
                onClick={() => onOpenLibraryItem(item.id)}
              >
                <span className="flex min-w-0 flex-wrap items-center gap-2">
                  <span className="min-w-0 break-words text-sm font-medium text-card-foreground">{item.title}</span>
                  <Badge>{getLibraryTypeLabel(item.type)}</Badge>
                </span>
                <span className="truncate text-xs text-muted-foreground">
                  {item.description || item.url || "Sem descricao cadastrada."}
                </span>
              </button>
            ))
          )}
        </DashboardSection>
      </div>

      <DashboardSection title="Projetos concluidos">
        {recentCompletedProjects.length === 0 ? (
          <EmptyState text="Nenhum projeto concluido ainda." />
        ) : (
          recentCompletedProjects.map((project) => (
            <ProjectRow key={project.id} project={project} onClick={() => onOpenProject(project)} />
          ))
        )}
      </DashboardSection>

      <p className="text-xs text-muted-foreground">Workspace: {workspace.name}</p>
    </section>
  );
}

function MetricCard({
  label,
  value,
  detail,
  onClick,
}: {
  label: string;
  value: number;
  detail: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className="grid min-w-0 gap-2 rounded-md border border-border bg-card p-4 text-left shadow-sm hover:bg-accent"
      onClick={onClick}
    >
      <span className="text-xs font-medium uppercase text-muted-foreground">{label}</span>
      <span className="text-2xl font-semibold text-card-foreground">{value}</span>
      <span className="text-xs text-muted-foreground">{detail}</span>
    </button>
  );
}

function DashboardSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="grid min-w-0 gap-3 rounded-md border border-border bg-background/60 p-4">
      <h3 className="text-sm font-semibold text-foreground">{title}</h3>
      <div className="grid min-w-0 gap-2">{children}</div>
    </section>
  );
}

function ProjectRow({ project, onClick }: { project: Project; onClick: () => void }) {
  return (
    <button
      type="button"
      className="grid min-w-0 gap-1 rounded-md border border-border bg-card px-3 py-2 text-left shadow-sm hover:bg-accent"
      onClick={onClick}
    >
      <span className="flex min-w-0 flex-wrap items-center gap-2">
        <span className="min-w-0 break-words text-sm font-medium text-card-foreground">{project.name}</span>
        <Badge>{getProjectStatusLabel(project.status)}</Badge>
      </span>
      <span className="flex flex-wrap gap-2 text-xs text-muted-foreground">
        {project.clientName ? <span>cliente: {project.clientName}</span> : null}
        <span>atualizado em {formatDate(project.updatedAt)}</span>
      </span>
    </button>
  );
}

function Badge({ children }: { children: ReactNode }) {
  return (
    <span className="shrink-0 rounded-full border border-border bg-background px-2 py-0.5 text-[11px] text-muted-foreground">
      {children}
    </span>
  );
}

function EmptyState({ text }: { text: string }) {
  return <p className="rounded-md border border-dashed border-border px-3 py-6 text-center text-sm text-muted-foreground">{text}</p>;
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

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
