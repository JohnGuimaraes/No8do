import { X } from "@phosphor-icons/react";
import { type ReactNode } from "react";
import { ProjectActivityPanel } from "@/activities/ProjectActivityPanel";
import { type Client } from "@/clients/clientApi";
import { Button } from "@/components/ui/button";
import { ProjectCredentialsPanel } from "@/credentials/ProjectCredentialsPanel";
import { ProjectNotesPanel } from "@/notes/ProjectNotesPanel";
import { ProjectTechnicalInfoPanel } from "@/project-technical-info/ProjectTechnicalInfoPanel";
import { type Project } from "@/projects/projectApi";
import { getProjectStatusLabel } from "@/projects/projectStatus";
import { ProjectWorkItemsPanel } from "@/work-items/ProjectWorkItemsPanel";

type ProjectDetailsPanelProps = {
  project: Project;
  clients: Client[];
  onClientChange: (clientId: string | null) => void;
  onClose: () => void;
};

export function ProjectDetailsPanel({ project, clients, onClientChange, onClose }: ProjectDetailsPanelProps) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-end bg-foreground/30 px-3 py-4 backdrop-blur-sm sm:items-center sm:justify-center sm:px-6"
      role="dialog"
      aria-modal="true"
      aria-labelledby="project-details-title"
      onClick={onClose}
    >
      <section
        className="max-h-[92vh] w-full min-w-0 overflow-y-auto rounded-lg border border-border bg-card shadow-xl sm:max-w-3xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="sticky top-0 z-10 flex items-start justify-between gap-3 border-b border-border bg-card/95 px-4 py-4 backdrop-blur sm:px-6">
          <div className="min-w-0">
            <h2
              id="project-details-title"
              className="break-words text-xl font-semibold leading-tight text-card-foreground"
            >
              {project.name}
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              atualizado em {formatDate(project.updatedAt)}
            </p>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="shrink-0"
            onClick={onClose}
            aria-label="Fechar detalhes do projeto"
          >
            <X className="h-5 w-5" />
          </Button>
        </div>

        <div className="grid min-w-0 gap-5 px-4 py-5 sm:px-6 lg:grid-cols-[minmax(0,1fr)_260px]">
          <div className="min-w-0 space-y-4">
            <DetailSection title="Descricao">
              {project.description ? (
                <p className="whitespace-pre-wrap break-words text-sm leading-6 text-card-foreground">
                  {project.description}
                </p>
              ) : (
                <p className="text-sm text-muted-foreground">Sem descricao cadastrada.</p>
              )}
            </DetailSection>

            <DetailSection title="Estado atual">
              {project.currentState ? (
                <p className="whitespace-pre-wrap break-words text-sm leading-6 text-card-foreground">
                  {project.currentState}
                </p>
              ) : (
                <p className="text-sm text-muted-foreground">Sem estado atual cadastrado.</p>
              )}
            </DetailSection>

            <ProjectWorkItemsPanel workspaceId={project.workspaceId} projectId={project.id} />

            <ProjectTechnicalInfoPanel workspaceId={project.workspaceId} projectId={project.id} />

            <ProjectCredentialsPanel workspaceId={project.workspaceId} projectId={project.id} />

            <ProjectNotesPanel workspaceId={project.workspaceId} projectId={project.id} />

            <ProjectActivityPanel workspaceId={project.workspaceId} projectId={project.id} />
          </div>

          <aside className="min-w-0 rounded-md border border-border bg-background/70 p-4">
            <dl className="grid gap-4">
              <DetailItem label="Status" value={getProjectStatusLabel(project.status)} />
              <div className="min-w-0">
                <dt className="text-[11px] font-medium uppercase text-muted-foreground">Cliente</dt>
                <dd className="mt-1">
                  <select
                    className="h-10 w-full min-w-0 rounded-md border border-input bg-card px-3 text-sm outline-none ring-offset-background transition-shadow focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                    value={project.clientId ?? ""}
                    onChange={(event) => onClientChange(event.target.value || null)}
                    aria-label="Cliente vinculado ao projeto"
                  >
                    <option value="">Sem cliente vinculado</option>
                    {clients.map((client) => (
                      <option key={client.id} value={client.id}>
                        {client.name}
                      </option>
                    ))}
                  </select>
                </dd>
              </div>
              <DetailItem label="Criado em" value={formatDate(project.createdAt)} />
              <DetailItem label="Atualizado em" value={formatDate(project.updatedAt)} />
            </dl>
          </aside>
        </div>
      </section>
    </div>
  );
}

function DetailSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="min-w-0 rounded-md border border-border bg-background/70 p-4">
      <h3 className="mb-2 text-sm font-semibold text-foreground">{title}</h3>
      {children}
    </section>
  );
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] font-medium uppercase text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-words text-sm font-medium text-card-foreground">{value}</dd>
    </div>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
