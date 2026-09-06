import { ArrowRight, CheckCircle, GithubLogo, NotePencil, WarningCircle } from "@phosphor-icons/react";
import { Children, type ReactNode, useEffect, useId, useMemo, useState } from "react";
import { listWorkspaceProjectActivities, type WorkspaceProjectActivity } from "@/activities/activityApi";
import { MetricGlyph } from "@/dashboard/MetricGlyph";
import no8doLogo from "@/assets/logo/no8do-logo.png";
import { ConnectedNodeGraphic } from "@/components/visual/ConnectedNodeGraphic";
import { type Idea, type IdeaStatus, type IdeaType } from "@/ideas/ideaApi";
import { listGithubAppRepositories, type GithubAppRepository } from "@/github/githubAppApi";
import { type LibraryItem, type LibraryItemType } from "@/library/libraryApi";
import { type Project, type ProjectStatus } from "@/projects/projectApi";
import { getProjectStatusLabel } from "@/projects/projectStatus";
import { listReplays, type Replay } from "@/replays/replayApi";
import { ReplayRecentSpotlight } from "@/replays/ReplaysPanel";
import { listWorkspaceWorkItems, type WorkspaceWorkItem } from "@/work-items/workItemApi";
import { getWorkItemDueDateLabel, isWorkItemDueDateOverdue } from "@/work-items/workItemDate";
import { getWorkspaceGithubAppStatus, type Workspace } from "@/workspaces/workspaceApi";

type WorkspaceDashboardProps = {
  workspace: Workspace;
  projects: Project[];
  libraryItems: LibraryItem[];
  ideas: Idea[];
  onOpenSection: (section: "development" | "work-items" | "projects" | "library" | "ideas" | "replays") => void;
  onOpenProject: (project: Project) => void;
  onOpenLibraryItem: (itemId: string) => void;
  onOpenIdea: (ideaId: string) => void;
  onOpenReplays: () => void;
};

type AttentionEntry =
  | { kind: "work-item"; item: WorkspaceWorkItem; overdue: boolean }
  | { kind: "project"; project: Project };

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
  libraryItems,
  ideas,
  onOpenSection,
  onOpenProject,
  onOpenLibraryItem,
  onOpenIdea,
  onOpenReplays,
}: WorkspaceDashboardProps) {
  const [motionPaused, setMotionPaused] = useState(false);
  const metrics = useMemo(() => {
    const developmentProjects = projects.filter((project) => project.status !== "DONE");
    return {
      development: developmentProjects.length,
      blocked: projects.filter((project) => project.status === "BLOCKED").length,
      completed: projects.filter((project) => project.status === "DONE").length,
      activeIdeas: ideas.filter((idea) => idea.status !== "ARCHIVED" && idea.status !== "CONVERTED").length,
      library: libraryItems.length,
    };
  }, [ideas, libraryItems.length, projects]);

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

  const identityItems = useMemo(
    () => libraryItems.filter((item) => item.type === "IDENTITY").slice(0, 2),
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

  const [openWorkItems, setOpenWorkItems] = useState<WorkspaceWorkItem[]>([]);
  const [workItemsLoading, setWorkItemsLoading] = useState(true);
  const [workItemsError, setWorkItemsError] = useState<string | null>(null);
  const [recentActivities, setRecentActivities] = useState<WorkspaceProjectActivity[]>([]);
  const [activitiesLoading, setActivitiesLoading] = useState(true);
  const [activitiesError, setActivitiesError] = useState<string | null>(null);
  const [replays, setReplays] = useState<Replay[]>([]);

  const replayMetrics = useMemo(() => ({
    total: replays.length,
    validated: replays.filter((replay) => replay.status === "VALIDATED").length,
  }), [replays]);

  const recentReplays = useMemo(
    () => [...replays].sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt)).slice(0, 5),
    [replays],
  );

  useEffect(() => { let active = true; void listReplays(workspace.id).then((items) => { if (active) setReplays(items); }).catch(() => { if (active) setReplays([]); }); return () => { active = false; }; }, [workspace.id]);

  useEffect(() => {
    let active = true;

    setOpenWorkItems([]);
    setWorkItemsLoading(true);
    setWorkItemsError(null);

    void listWorkspaceWorkItems(workspace.id, { status: "OPEN" })
      .then((items) => {
        if (active) {
          setOpenWorkItems(items);
        }
      })
      .catch((error) => {
        if (active) {
          setWorkItemsError(error instanceof Error ? error.message : "Não foi possível carregar o trabalho atual.");
        }
      })
      .finally(() => {
        if (active) {
          setWorkItemsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [workspace.id]);

  useEffect(() => {
    let active = true;

    setRecentActivities([]);
    setActivitiesLoading(true);
    setActivitiesError(null);

    void listWorkspaceProjectActivities(workspace.id, 8)
      .then((activities) => {
        if (active) {
          setRecentActivities(activities);
        }
      })
      .catch((error) => {
        if (active) {
          setActivitiesError(error instanceof Error ? error.message : "Não foi possível carregar as atividades recentes.");
        }
      })
      .finally(() => {
        if (active) {
          setActivitiesLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [workspace.id]);

  const attentionEntries = useMemo<AttentionEntry[]>(() => {
    const overdueItems = openWorkItems
      .filter((item) => item.dueDate && isWorkItemDueDateOverdue(item.dueDate))
      .map((item) => ({ kind: "work-item" as const, item, overdue: true }));
    const blockers = openWorkItems
      .filter((item) => item.type === "BLOCKER" && !overdueItems.some(({ item: overdueItem }) => overdueItem.id === item.id))
      .map((item) => ({ kind: "work-item" as const, item, overdue: false }));
    const blockedProjects = projects
      .filter((project) => project.status === "BLOCKED")
      .map((project) => ({ kind: "project" as const, project }));
    const pausedProjects = projects
      .filter((project) => project.status === "PAUSED")
      .map((project) => ({ kind: "project" as const, project }));

    return [...overdueItems, ...blockers, ...blockedProjects, ...pausedProjects].slice(0, 6);
  }, [openWorkItems, projects]);

  const currentWorkItems = useMemo(
    () =>
      openWorkItems
        .filter((item) => item.type === "NEXT_STEP" || item.type === "PENDING")
        .sort((left, right) => {
          if (left.type !== right.type) return left.type === "NEXT_STEP" ? -1 : 1;
          return Date.parse(right.updatedAt) - Date.parse(left.updatedAt);
        })
        .slice(0, 6),
    [openWorkItems],
  );

  return (
    <section className="workspace-dashboard grid min-w-0 gap-10">
      <div className="dashboard-overview-grid">
        <div className="dashboard-metrics-grid">
          <MetricCard
            label="Projetos em desenvolvimento"
            value={metrics.development}
            detail="trabalho em curso"
            glyph="development"
            onClick={() => onOpenSection("development")}
          />
          <MetricCard
            label="Bloqueados"
            value={metrics.blocked}
            detail="pedem movimento"
            glyph="blocked"
            tone="warning"
            onClick={() => onOpenSection("development")}
          />
          <MetricCard
            label="Projetos concluidos"
            value={metrics.completed}
            detail="memória operacional"
            glyph="completed"
            onClick={() => onOpenSection("projects")}
          />
          <MetricCard
            label="Ideias"
            value={metrics.activeIdeas}
            detail="em exploração"
            glyph="ideas"
            onClick={() => onOpenSection("ideas")}
          />
          <MetricCard
            label="Acervo"
            value={metrics.library}
            detail="memória compartilhada"
            glyph="library"
            onClick={() => onOpenSection("library")}
          />
        </div>

        <section className="dashboard-identity-field" aria-label="Identidade No8do">
          <ConnectedNodeGraphic wordmark paused={motionPaused} />
        </section>

        <aside className="dashboard-context-stack">
          <section className="workspace-node-card" aria-labelledby="workspace-node-title">
            <header className="workspace-node-card__lead">
              <p className="text-xs font-medium uppercase text-muted-foreground">Workspace Node</p>
              <h2 id="workspace-node-title" className="mt-1 break-words text-lg font-semibold text-card-foreground">{workspace.name}</h2>
              <p className="mt-1 text-sm text-muted-foreground">Contexto operacional conectado.</p>
            </header>
            <ConnectedNodeGraphic className="workspace-node-card__graphic" paused={motionPaused} />
            <button className="workspace-node-card__motion" type="button" aria-pressed={motionPaused} onClick={() => setMotionPaused((value) => !value)}>{motionPaused ? "Retomar movimento" : "Pausar movimento"}</button>
            <div className="workspace-node-card__map">
              <div><p className="replay-section-label">Leitura do workspace</p><h3>Mapa do conhecimento</h3></div>
              <dl><div><dt>Replays</dt><dd>{replayMetrics.total}</dd></div><div><dt>Acervo</dt><dd>{metrics.library}</dd></div><div><dt>Ideias</dt><dd>{metrics.activeIdeas}</dd></div><div><dt>Em curso</dt><dd>{metrics.development}</dd></div><div><dt>Concluídos</dt><dd>{metrics.completed}</dd></div></dl>
            </div>
          </section>
        </aside>

        <section className="dashboard-replays-section dashboard-replays-section--top" aria-labelledby="dashboard-replays-title">
          <header className="dashboard-replays-section__header">
            <div><p className="replay-section-label">Memória técnica reutilizável</p><h3 id="dashboard-replays-title">Replays recentes</h3><p>{replayMetrics.total} no workspace{replayMetrics.validated ? ` · ${replayMetrics.validated} validados` : ""}</p></div>
            <button type="button" className="dashboard-replays-section__action" onClick={onOpenReplays}>Ver todos<ArrowRight className="h-4 w-4" /></button>
          </header>
          {recentReplays.length === 0 ? <CompactState text="Nenhum Replay registrado ainda." /> : <ReplayRecentSpotlight recent={recentReplays} onAccess={onOpenReplays} motionPaused={motionPaused} />}
        </section>
      </div>

      <div className="dashboard-operational-grid">
        <DashboardSection title="Atenção agora" tone="attention">
          {workItemsLoading ? (
            <CompactState text="Carregando pontos de atenção..." />
          ) : attentionEntries.length === 0 ? (
            <CompactState text="Nada crítico agora." />
          ) : (
            attentionEntries.map((entry) => entry.kind === "work-item" ? (
              <WorkItemRow
                key={entry.item.id}
                item={entry.item}
                variant="attention"
                showOverdue={entry.overdue}
                onClick={() => {
                  const project = projects.find((value) => value.id === entry.item.projectId);
                  if (project) onOpenProject(project);
                }}
              />
            ) : (
              <ProjectRow key={entry.project.id} project={entry.project} variant="attention" onClick={() => onOpenProject(entry.project)} />
            ))
          )}
        </DashboardSection>

        <DashboardSection title="Trabalho atual" tone="development" actionLabel="Ver todas as pendências" onAction={() => onOpenSection("work-items")}>
          {workItemsLoading ? (
            <CompactState text="Carregando trabalho atual..." />
          ) : workItemsError ? (
            <CompactState text={workItemsError} />
          ) : currentWorkItems.length === 0 ? (
            <CompactState text="Nenhum próximo passo ou pendência aberto." />
          ) : (
            currentWorkItems.map((item) => (
              <WorkItemRow
                key={item.id}
                item={item}
                variant="development"
                showOverdue={Boolean(item.dueDate && isWorkItemDueDateOverdue(item.dueDate))}
                onClick={() => {
                  const project = projects.find((value) => value.id === item.projectId);
                  if (project) onOpenProject(project);
                }}
              />
            ))
          )}
        </DashboardSection>

        <DashboardSection title="Em desenvolvimento" tone="development">
          {currentProjects.length === 0 ? (
            <EmptyState text="Nenhum projeto ativo, em planejamento ou ideia." />
          ) : (
            currentProjects.map((project) => (
              <ProjectRow key={project.id} project={project} variant="development" onClick={() => onOpenProject(project)} />
            ))
          )}
        </DashboardSection>

        <DashboardSection title="Atividade recente" tone="activity">
          {activitiesLoading ? (
            <CompactState text="Carregando atividades recentes..." />
          ) : activitiesError ? (
            <CompactState text={activitiesError} />
          ) : recentActivities.length === 0 ? (
            <CompactState text="Nenhuma atividade recente." />
          ) : (
            recentActivities.map((activity) => (
              <ActivityRow
                key={activity.activityId}
                activity={activity}
                onClick={() => {
                  const project = projects.find((value) => value.id === activity.projectId);
                  if (project) onOpenProject(project);
                }}
              />
            ))
          )}
        </DashboardSection>

        <DashboardSection title="Acervo do workspace" tone="library" actionLabel="Abrir Acervo" onAction={() => onOpenSection("library")}>
          <p className="text-sm text-muted-foreground">Memória compartilhada, conhecimento e ativos técnicos.</p>
          {recentLibraryItems.length === 0 ? (
            <EmptyState text="Nenhum conteúdo recente no Acervo." />
          ) : (
            recentLibraryItems.map((item) => (
              <button
                key={item.id}
                type="button"
                className="dashboard-library-row"
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
          {identityItems.length ? <div className="grid gap-2 border-t border-border/70 pt-3"><span className="text-xs font-medium uppercase tracking-[.1em] text-muted-foreground">Identidade</span>{identityItems.map((item) => <button key={item.id} type="button" className="dashboard-library-row" onClick={() => onOpenLibraryItem(item.id)}><span className="min-w-0 break-words text-sm font-medium text-card-foreground">{item.title}</span><span className="truncate text-xs text-muted-foreground">{item.description || "Ativo visual do workspace"}</span></button>)}</div> : null}
          <GithubAcervoHighlights workspaceId={workspace.id} />
        </DashboardSection>

        <DashboardSection title="Ideias recentes" tone="ideas">
          {recentIdeas.length === 0 ? (
            <EmptyState text="Nenhuma ideia registrada para este workspace." />
          ) : (
            recentIdeas.map((idea) => (
              <button
                key={idea.id}
                type="button"
                className="dashboard-idea-row"
                onClick={() => onOpenIdea(idea.id)}
              >
                <span className="flex min-w-0 flex-wrap items-center gap-2">
                  <span className="min-w-0 break-words text-sm font-medium text-card-foreground">{idea.title}</span>
                  <Badge>{getIdeaTypeLabel(idea.type)}</Badge>
                  <Badge>{getIdeaStatusLabel(idea.status)}</Badge>
                </span>
              </button>
            ))
          )}
        </DashboardSection>

      </div>

      <DashboardSection title="Projetos concluídos" tone="completed">
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

function GithubAcervoHighlights({ workspaceId }: { workspaceId: string }) {
  const [repositories, setRepositories] = useState<GithubAppRepository[]>([]);
  const [accountLogin, setAccountLogin] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setAccountLogin(null);
    setRepositories([]);
    void getWorkspaceGithubAppStatus(workspaceId)
      .then(async (installation) => {
        if (!installation.configured) return null;
        const page = await listGithubAppRepositories(workspaceId, 1, 3);
        return { accountLogin: installation.accountLogin, items: page.items };
      })
      .then((result) => {
        if (!active || !result) return;
        setAccountLogin(result.accountLogin ?? null);
        setRepositories(result.items);
      })
      .catch(() => {
        if (!active) return;
        setAccountLogin(null);
        setRepositories([]);
      });
    return () => { active = false; };
  }, [workspaceId]);

  if (!accountLogin || repositories.length === 0) return null;

  return <div className="mt-2 grid gap-2 border-t border-border/70 pt-3"><span className="flex items-center gap-2 text-xs font-medium text-muted-foreground"><GithubLogo className="h-3.5 w-3.5 text-primary" weight="duotone" />Código disponível em {accountLogin}</span>{repositories.map((repository) => <a key={repository.repositoryId} href={repository.htmlUrl} target="_blank" rel="noopener noreferrer" className="dashboard-library-row"><span className="min-w-0 break-words text-sm font-medium text-card-foreground">{repository.fullName}</span><span className="truncate text-xs text-muted-foreground">{repository.description || repository.language || "Repositório GitHub"}</span></a>)}</div>;
}

function MetricCard({
  label,
  value,
  detail,
  glyph,
  tone = "default",
  onClick,
}: {
  label: string;
  value: number;
  detail: string;
  glyph: "development" | "blocked" | "completed" | "ideas" | "library" | "pending";
  tone?: "default" | "warning";
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={`dashboard-metric dashboard-metric--${glyph} ${tone === "warning" ? "dashboard-metric--warning" : ""}`}
      onClick={onClick}
    >
      <img className="dashboard-metric__brand" src={no8doLogo} alt="" aria-hidden="true" />
      <span className="grid gap-2"><span className="text-xs font-medium uppercase text-muted-foreground">{label}</span><span className="text-3xl font-semibold tracking-tight text-card-foreground">{value}</span><span className="text-xs text-muted-foreground">{detail}</span></span>
      <MetricGlyph type={glyph} />
    </button>
  );
}

function DashboardSection({ title, tone, children, actionLabel, onAction }: { title: string; tone: "attention" | "development" | "activity" | "ideas" | "library" | "completed"; children: ReactNode; actionLabel?: string; onAction?: () => void }) {
  const [expanded, setExpanded] = useState(false);
  const entries = Children.toArray(children);
  const regionId = useId();
  const visibleEntries = expanded ? entries : entries.slice(0, 2);
  const hasMore = entries.length > 2;
  return (
    <section className={`dashboard-section dashboard-section--${tone}`}>
      <header className="dashboard-section__header"><div><h3>{title}</h3></div><div className="dashboard-section__actions">{hasMore ? <button type="button" aria-expanded={expanded} aria-controls={regionId} onClick={() => setExpanded((current) => !current)}>{expanded ? "Recolher" : "Ver mais"}</button> : null}{actionLabel && onAction ? <button type="button" onClick={onAction}>{actionLabel}</button> : null}</div></header>
      <div id={regionId} className="dashboard-section__rows">{visibleEntries}</div>
    </section>
  );
}

function ProjectRow({ project, variant = "default", onClick }: { project: Project; variant?: "default" | "attention" | "development"; onClick: () => void }) {
  return (
    <button
      type="button"
      className={`dashboard-project-row dashboard-project-row--${variant}`}
      onClick={onClick}
    >
      <span className="flex min-w-0 flex-wrap items-center gap-2">
        <span className="min-w-0 break-words text-sm font-medium text-card-foreground">{project.name}</span>
        <Badge>{getProjectStatusLabel(project.status)}</Badge>
      </span>
      <span className="flex flex-wrap gap-2 text-xs text-muted-foreground">
        <span>atualizado em {formatDate(project.updatedAt)}</span>
      </span>
    </button>
  );
}

function WorkItemRow({ item, variant, showOverdue, onClick }: { item: WorkspaceWorkItem; variant: "attention" | "development"; showOverdue: boolean; onClick: () => void }) {
  return (
    <button type="button" className={`dashboard-project-row dashboard-project-row--${variant}`} onClick={onClick}>
      <span className="flex min-w-0 flex-wrap items-center gap-2">
        <span className="min-w-0 break-words text-sm font-medium text-card-foreground">{item.title}</span>
        <Badge>{getWorkItemTypeLabel(item.type)}</Badge>
        {showOverdue ? <Badge>Vencido</Badge> : null}
      </span>
      <span className="grid gap-1 text-xs text-muted-foreground">
        <span className="truncate">Projeto: {item.projectName}</span>
        <span>{item.assigneeName ? `Responsável: ${item.assigneeName}` : "Sem responsável"}{item.dueDate ? ` · ${getWorkItemDueDateLabel(item.dueDate, item.status)}` : ""}</span>
      </span>
    </button>
  );
}

function ActivityRow({ activity, onClick }: { activity: WorkspaceProjectActivity; onClick: () => void }) {
  const Icon = getActivityIcon(activity.type);

  return (
    <button type="button" className="dashboard-activity-row" onClick={onClick}>
      <span className="flex min-w-0 items-start gap-2">
        <Icon className="mt-0.5 h-4 w-4 shrink-0 text-primary" weight="duotone" aria-hidden="true" />
        <span className="grid min-w-0 gap-1">
          <span className="break-words text-sm font-medium text-card-foreground">{activity.content}</span>
          <span className="truncate text-xs font-medium text-foreground/80">Projeto: {activity.projectName}</span>
          <span className="text-xs text-muted-foreground">{activity.createdByName} · {formatDate(activity.createdAt)}</span>
        </span>
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

function CompactState({ text }: { text: string }) {
  return <p className="rounded-md border border-dashed border-border px-3 py-3 text-sm text-muted-foreground">{text}</p>;
}

function getWorkItemTypeLabel(type: WorkspaceWorkItem["type"]) {
  const labels: Record<WorkspaceWorkItem["type"], string> = {
    NEXT_STEP: "Próximo passo",
    PENDING: "Pendência",
    BLOCKER: "Bloqueio",
  };
  return labels[type];
}

function getActivityIcon(type: WorkspaceProjectActivity["type"]) {
  if (type === "DECISION") return CheckCircle;
  if (type === "BLOCKER") return WarningCircle;
  if (type === "NEXT_STEP") return ArrowRight;
  return NotePencil;
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

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
