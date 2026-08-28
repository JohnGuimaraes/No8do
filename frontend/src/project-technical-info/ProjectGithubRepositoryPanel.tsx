import { useEffect, useMemo, useState } from "react";
import { ArrowSquareOut, ArrowsClockwise, GithubLogo, LinkSimple, Trash } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import { ConfirmationDialog } from "@/components/ConfirmationDialog";
import {
  associateProjectGithubRepository,
  dissociateProjectGithubRepository,
  getProjectGithubRepository,
  listGithubAppRepositories,
  type GithubAppRepository,
  type ProjectGithubRepository,
} from "@/github/githubAppApi";

type ProjectGithubRepositoryPanelProps = {
  workspaceId: string;
  projectId: string;
  canWrite: boolean;
};

const CATALOG_PAGE_SIZE = 50;

export function ProjectGithubRepositoryPanel({ workspaceId, projectId, canWrite }: ProjectGithubRepositoryPanelProps) {
  const [association, setAssociation] = useState<ProjectGithubRepository | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectorOpen, setSelectorOpen] = useState(false);
  const [catalog, setCatalog] = useState<GithubAppRepository[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(false);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [pendingRepository, setPendingRepository] = useState<GithubAppRepository | null>(null);
  const [dissociatePending, setDissociatePending] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    setSelectorOpen(false);
    setCatalog([]);

    void getProjectGithubRepository(workspaceId, projectId)
      .then((response) => {
        if (active) setAssociation(response);
      })
      .catch((requestError) => {
        if (active) setError(messageFor(requestError, "Não foi possível carregar o repositório GitHub."));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [projectId, workspaceId]);

  useEffect(() => {
    if (!selectorOpen) return;
    let active = true;
    setCatalogLoading(true);
    setCatalogError(null);

    void listGithubAppRepositories(workspaceId, 1, CATALOG_PAGE_SIZE)
      .then((response) => {
        if (active) setCatalog(response.items);
      })
      .catch((requestError) => {
        if (active) setCatalogError(catalogMessageFor(requestError));
      })
      .finally(() => {
        if (active) setCatalogLoading(false);
      });

    return () => {
      active = false;
    };
  }, [selectorOpen, workspaceId]);

  const filteredCatalog = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase("pt-BR");
    if (!normalizedQuery) return catalog;
    return catalog.filter((repository) =>
      [repository.name, repository.fullName, repository.ownerLogin, repository.description]
        .filter(Boolean)
        .some((value) => value!.toLocaleLowerCase("pt-BR").includes(normalizedQuery)),
    );
  }, [catalog, query]);

  async function confirmAssociation() {
    if (!pendingRepository) return;
    setSaving(true);
    setError(null);
    try {
      const response = await associateProjectGithubRepository(workspaceId, projectId, pendingRepository.repositoryId);
      setAssociation(response);
      setSelectorOpen(false);
      setQuery("");
      setPendingRepository(null);
    } catch (requestError) {
      setError(messageFor(requestError, "Não foi possível associar o repositório GitHub."));
    } finally {
      setSaving(false);
    }
  }

  async function confirmDissociation() {
    setSaving(true);
    setError(null);
    try {
      await dissociateProjectGithubRepository(workspaceId, projectId);
      setAssociation({ state: "NOT_ASSOCIATED", repositoryId: null, repository: null });
      setDissociatePending(false);
    } catch (requestError) {
      setError(messageFor(requestError, "Não foi possível desassociar o repositório GitHub."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="mt-5 border-t border-border/70 pt-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <GithubLogo className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
            <h3 className="text-sm font-semibold text-foreground">Repositório GitHub</h3>
          </div>
          <p className="mt-1 text-xs leading-5 text-muted-foreground">
            {association?.state === "INACCESSIBLE"
              ? "A associação precisa ser revisada: a instalação do GitHub App não possui mais acesso a este repositório."
              : "Use um repositório autorizado pela GitHub App deste workspace."}
          </p>
        </div>
        {canWrite && !loading && association?.state !== "ASSOCIATED" ? (
          <Button type="button" size="sm" variant="outline" disabled={saving} onClick={() => setSelectorOpen((open) => !open)}>
            <LinkSimple className="h-4 w-4" />
            Associar repositório
          </Button>
        ) : null}
      </div>

      {loading ? <p className="mt-3 text-sm text-muted-foreground">Carregando repositório GitHub...</p> : null}
      {error ? <p className="mt-3 text-sm text-destructive">{error}</p> : null}

      {!loading && association?.state === "NOT_ASSOCIATED" && !selectorOpen ? (
        <p className="mt-3 text-sm text-muted-foreground">Nenhum repositório associado.</p>
      ) : null}

      {!loading && association?.state === "ASSOCIATED" && association.repository ? (
        <AssociatedRepository
          repository={association.repository}
          disabled={saving || !canWrite}
          canWrite={canWrite}
          onReplace={() => setSelectorOpen((open) => !open)}
          onDissociate={() => setDissociatePending(true)}
        />
      ) : null}

      {canWrite && !loading && association?.state === "INACCESSIBLE" ? (
        <div className="mt-3 flex flex-wrap gap-2">
          <Button type="button" size="sm" variant="outline" disabled={saving} onClick={() => setSelectorOpen((open) => !open)}>
            <ArrowsClockwise className="h-4 w-4" />
            Trocar repositório
          </Button>
          <Button type="button" size="sm" variant="ghost" className="text-destructive hover:bg-destructive/10 hover:text-destructive" disabled={saving} onClick={() => setDissociatePending(true)}>
            <Trash className="h-4 w-4" />
            Desassociar
          </Button>
        </div>
      ) : null}

      {selectorOpen ? (
        <div className="mt-4 grid gap-3 rounded-md border border-border bg-card p-3">
          <label className="grid gap-1 text-sm font-medium text-foreground">
            Buscar repositório
            <input
              className="h-9 rounded-md border border-input bg-background px-3 text-sm font-normal outline-none ring-offset-background transition-shadow placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="owner ou nome"
            />
          </label>
          {catalogLoading ? <p className="text-sm text-muted-foreground">Carregando repositórios autorizados...</p> : null}
          {catalogError ? <p className="text-sm text-destructive">{catalogError}</p> : null}
          {!catalogLoading && !catalogError && filteredCatalog.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhum repositório autorizado encontrado.</p>
          ) : null}
          {!catalogLoading && !catalogError ? (
            <ul className="grid max-h-72 gap-2 overflow-y-auto" aria-label="Repositórios autorizados">
              {filteredCatalog.map((repository) => (
                <li key={repository.repositoryId}>
                  <button
                    type="button"
                    className="grid w-full gap-1 rounded-md border border-border bg-background px-3 py-2 text-left hover:border-primary/35"
                    onClick={() => setPendingRepository(repository)}
                  >
                    <span className="flex flex-wrap items-center justify-between gap-2 text-sm font-medium text-foreground">
                      {repository.fullName}
                      <span className="text-[11px] font-medium uppercase text-muted-foreground">
                        {repository.private ? "Privado" : "Público"}
                      </span>
                    </span>
                    {repository.description ? <span className="line-clamp-2 text-xs leading-5 text-muted-foreground">{repository.description}</span> : null}
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}

      <ConfirmationDialog
        open={pendingRepository !== null}
        title={association?.state === "ASSOCIATED" ? "Trocar repositório GitHub?" : "Associar repositório GitHub?"}
        message="O repositório será validado pela GitHub App deste workspace antes de ser associado ao projeto."
        itemName={pendingRepository?.fullName}
        confirmLabel={association?.state === "ASSOCIATED" ? "Trocar repositório" : "Associar"}
        loadingLabel="Associando..."
        loading={saving}
        onCancel={() => setPendingRepository(null)}
        onConfirm={() => void confirmAssociation()}
      />
      <ConfirmationDialog
        open={dissociatePending}
        title="Desassociar repositório GitHub?"
        message="O link manual do repositório, se existir, será mantido no projeto."
        confirmLabel="Desassociar"
        loadingLabel="Desassociando..."
        destructive
        loading={saving}
        onCancel={() => setDissociatePending(false)}
        onConfirm={() => void confirmDissociation()}
      />
    </section>
  );
}

function AssociatedRepository({ repository, disabled, canWrite, onReplace, onDissociate }: {
  repository: GithubAppRepository;
  disabled: boolean;
  canWrite: boolean;
  onReplace: () => void;
  onDissociate: () => void;
}) {
  return (
    <div className="mt-4 grid gap-3 rounded-md border border-border bg-card px-3 py-3">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <p className="break-words text-sm font-semibold text-card-foreground">{repository.fullName}</p>
          <span className="text-[11px] font-medium uppercase text-muted-foreground">{repository.private ? "Privado" : "Público"}</span>
        </div>
        {repository.description ? <p className="mt-1 text-sm leading-6 text-muted-foreground">{repository.description}</p> : null}
        <p className="mt-2 text-xs text-muted-foreground">
          {[repository.language, repository.topics.length ? repository.topics.join(" · ") : null].filter(Boolean).join(" · ") || "Sem metadados adicionais"}
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        <a href={repository.htmlUrl} target="_blank" rel="noopener noreferrer" className="inline-flex h-8 items-center gap-1.5 rounded-md px-2 text-sm font-medium text-muted-foreground hover:bg-accent hover:text-accent-foreground">
          <ArrowSquareOut className="h-4 w-4" />
          Abrir no GitHub
        </a>
        {canWrite ? <Button type="button" size="sm" variant="outline" disabled={disabled} onClick={onReplace}>
          <ArrowsClockwise className="h-4 w-4" />
          Trocar repositório
        </Button> : null}
        {canWrite ? <Button type="button" size="sm" variant="ghost" className="text-destructive hover:bg-destructive/10 hover:text-destructive" disabled={disabled} onClick={onDissociate}>
          <Trash className="h-4 w-4" />
          Desassociar
        </Button> : null}
      </div>
    </div>
  );
}

function messageFor(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function catalogMessageFor(error: unknown) {
  const message = messageFor(error, "Não foi possível carregar os repositórios autorizados.");
  return message === "GitHub App is not installed for this workspace"
    ? "Conecte o GitHub nas configurações do workspace para associar um repositório."
    : message;
}
