import { Fragment, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  Archive,
  ArrowLeft,
  ArrowSquareOut,
  CaretLeft,
  CaretRight,
  CircleNotch,
  File,
  GitFork,
  GithubLogo,
  Globe,
  LockKey,
  MagnifyingGlass,
  WarningCircle,
} from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";
import {
  listGithubAppRepositories,
  previewGithubAppRepository,
  type GithubAppRepository,
  type GithubAppRepositoryPage,
  type GithubAppRepositoryPreview,
} from "@/github/githubAppApi";
import { getWorkspaceGithubAppStatus, type WorkspaceGithubAppStatus } from "@/workspaces/workspaceApi";

type RepositoryFilter = "all" | "public" | "private";

const PER_PAGE = 24;

export function GithubRepositoriesPanel({ workspaceId }: { workspaceId: string }) {
  const [catalog, setCatalog] = useState<GithubAppRepositoryPage | null>(null);
  const [installation, setInstallation] = useState<WorkspaceGithubAppStatus | null>(null);
  const [page, setPage] = useState(1);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<RepositoryFilter>("all");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedRepositoryId, setSelectedRepositoryId] = useState<number | null>(null);

  useEffect(() => {
    setPage(1);
    setQuery("");
    setFilter("all");
    setCatalog(null);
    setSelectedRepositoryId(null);
  }, [workspaceId]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    void listGithubAppRepositories(workspaceId, page, PER_PAGE)
      .then((result) => { if (active) setCatalog(result); })
      .catch((requestError) => {
        if (active) setError(requestError instanceof Error ? requestError.message : "Não foi possível carregar os repositórios GitHub.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [workspaceId, page]);

  useEffect(() => {
    let active = true;
    void getWorkspaceGithubAppStatus(workspaceId)
      .then((result) => { if (active) setInstallation(result); })
      .catch(() => { if (active) setInstallation(null); });
    return () => { active = false; };
  }, [workspaceId]);

  const visibleRepositories = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    return (catalog?.items ?? []).filter((repository) => {
      const visibilityMatches = filter === "all" || (filter === "private" ? repository.private : !repository.private);
      const queryMatches = !normalizedQuery || [repository.name, repository.fullName, repository.ownerLogin, repository.description, repository.language, ...repository.topics]
        .some((value) => value?.toLocaleLowerCase().includes(normalizedQuery));
      return visibilityMatches && queryMatches;
    });
  }, [catalog?.items, filter, query]);

  if (selectedRepositoryId !== null) {
    return <GithubRepositoryPreview workspaceId={workspaceId} repositoryId={selectedRepositoryId} onBack={() => setSelectedRepositoryId(null)} />;
  }

  const notInstalled = Boolean(error && /not installed/i.test(error));
  const totalPages = catalog ? Math.max(1, Math.ceil(catalog.totalCount / catalog.perPage)) : 1;

  return (
    <section className="grid min-w-0 gap-5" aria-labelledby="github-repositories-title">
      <header className="grid gap-3 border-b border-border/70 pb-5 sm:flex sm:items-end sm:justify-between">
        <div className="min-w-0"><span className="flex items-center gap-2 text-xs font-medium uppercase tracking-[.1em] text-muted-foreground"><GithubLogo className="h-4 w-4 text-primary" weight="duotone" />Código / GitHub App</span><h3 id="github-repositories-title" className="mt-2 text-xl font-semibold text-foreground">Repositórios GitHub</h3><p className="mt-1 text-sm text-muted-foreground">Ativos técnicos disponíveis para o workspace, sem importar ou criar projetos.</p></div>
        {installation?.configured && installation.accountLogin ? <span className="flex w-fit items-center gap-2 rounded-md border border-border bg-card px-3 py-2 text-xs text-muted-foreground"><GithubLogo className="h-4 w-4 text-foreground" weight="bold" />{installation.accountLogin}<span className="text-border">/</span>{installation.accountType === "ORGANIZATION" ? "organização" : "pessoal"}</span> : null}
      </header>

      {loading ? <PanelState icon={<CircleNotch className="h-5 w-5 text-primary" />} title="Carregando repositórios" detail="Consultando a página autorizada pela GitHub App." /> : null}
      {!loading && notInstalled ? <PanelState icon={<GithubLogo className="h-5 w-5 text-muted-foreground" />} title="GitHub App não instalada" detail="Instale a GitHub App em Configurações do Workspace para consultar os repositórios autorizados." /> : null}
      {!loading && error && !notInstalled ? <PanelState icon={<WarningCircle className="h-5 w-5 text-destructive" />} title="Não foi possível carregar os repositórios" detail={error} /> : null}

      {!loading && !error && catalog ? <>
        <div className="grid gap-3 rounded-lg border border-border bg-card p-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
          <label className="relative block"><MagnifyingGlass className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" /><span className="sr-only">Buscar na página atual</span><input value={query} onChange={(event) => setQuery(event.target.value)} className="h-9 w-full rounded-md border border-input bg-background pl-9 pr-3 text-sm text-foreground outline-none placeholder:text-muted-foreground focus:border-ring focus:ring-2 focus:ring-ring/25" placeholder="Buscar nesta página" /></label>
          <div className="flex items-center gap-1 rounded-md border border-border bg-muted/55 p-1" role="group" aria-label="Filtrar repositórios por visibilidade">
            {(["all", "public", "private"] as const).map((value) => <button key={value} type="button" onClick={() => setFilter(value)} className={`rounded px-2.5 py-1.5 text-xs font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${filter === value ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}>{value === "all" ? "Todos" : value === "public" ? "Públicos" : "Privados"}</button>)}
          </div>
        </div>

        {catalog.items.length === 0 ? <PanelState icon={<GithubLogo className="h-5 w-5 text-muted-foreground" />} title="Nenhum repositório autorizado" detail="A GitHub App ainda não possui repositórios acessíveis nesta instalação." /> : visibleRepositories.length === 0 ? <PanelState icon={<MagnifyingGlass className="h-5 w-5 text-muted-foreground" />} title="Nenhum resultado nesta página" detail="A busca e os filtros são aplicados somente aos itens já carregados." /> : <div className="grid min-w-0 gap-3 md:grid-cols-2 xl:grid-cols-3">{visibleRepositories.map((repository) => <RepositoryCard key={repository.repositoryId} repository={repository} onOpen={() => setSelectedRepositoryId(repository.repositoryId)} />)}</div>}

        <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-border/70 pt-4 text-xs text-muted-foreground"><span>{catalog.totalCount} repositórios autorizados · Página {catalog.page} de {totalPages}</span><div className="flex items-center gap-2"><Button type="button" variant="outline" size="sm" disabled={catalog.page <= 1} onClick={() => setPage((current) => Math.max(1, current - 1))}><CaretLeft className="h-4 w-4" />Anterior</Button><Button type="button" variant="outline" size="sm" disabled={catalog.page >= totalPages} onClick={() => setPage((current) => Math.min(totalPages, current + 1))}>Próxima<CaretRight className="h-4 w-4" /></Button></div></footer>
      </> : null}
    </section>
  );
}

function GithubRepositoryPreview({ workspaceId, repositoryId, onBack }: { workspaceId: string; repositoryId: number; onBack: () => void }) {
  const [preview, setPreview] = useState<GithubAppRepositoryPreview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    void previewGithubAppRepository(workspaceId, repositoryId)
      .then((result) => { if (active) setPreview(result); })
      .catch((requestError) => { if (active) setError(requestError instanceof Error ? requestError.message : "Não foi possível abrir o preview do repositório."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [repositoryId, workspaceId]);

  return <section className="grid min-w-0 gap-5" aria-live="polite">
    <Button type="button" variant="ghost" size="sm" className="w-fit" onClick={onBack}><ArrowLeft className="h-4 w-4" />Repositórios GitHub</Button>
    {loading ? <PanelState icon={<CircleNotch className="h-5 w-5 text-primary" />} title="Carregando preview" detail="Consultando os dados autorizados pela GitHub App." /> : null}
    {!loading && error ? <PanelState icon={<WarningCircle className="h-5 w-5 text-destructive" />} title="Não foi possível abrir o preview" detail={error} /> : null}
    {!loading && preview ? <RepositoryPreviewContent preview={preview} /> : null}
  </section>;
}

function RepositoryCard({ repository, onOpen }: { repository: GithubAppRepository; onOpen: () => void }) {
  return <article className="acervo-github-catalog-card grid min-w-0 gap-3 rounded-lg border border-border bg-card p-4"><button type="button" onClick={onOpen} className="grid min-w-0 gap-3 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><div className="flex min-w-0 items-start justify-between gap-3"><div className="min-w-0"><p className="truncate text-sm font-semibold text-card-foreground">{repository.name}</p><p className="mt-0.5 truncate text-xs text-muted-foreground">{repository.fullName}</p></div><VisibilityBadge privateRepository={repository.private} /></div><p className="line-clamp-2 min-h-10 text-sm leading-5 text-muted-foreground">{repository.description || "Sem descrição informada."}</p><div className="flex flex-wrap gap-1.5">{repository.language ? <Tag>{repository.language}</Tag> : null}{repository.topics.slice(0, 3).map((topic) => <Tag key={topic}>{topic}</Tag>)}{repository.fork ? <Tag icon={<GitFork className="h-3 w-3" />}>Fork</Tag> : null}{repository.archived ? <Tag icon={<Archive className="h-3 w-3" />}>Arquivado</Tag> : null}</div></button><div className="flex items-center justify-between gap-3 border-t border-border/60 pt-3 text-[11px] text-muted-foreground"><span>Atualizado {formatDate(repository.updatedAt)}</span><a href={repository.htmlUrl} target="_blank" rel="noopener noreferrer" onClick={(event) => event.stopPropagation()} className="inline-flex items-center gap-1 text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">GitHub<ArrowSquareOut className="h-3.5 w-3.5" /></a></div></article>;
}

function RepositoryPreviewContent({ preview }: { preview: GithubAppRepositoryPreview }) {
  const { repository } = preview;
  return <div className="acervo-github-preview grid min-w-0 gap-5"><header className="acervo-github-preview__header grid gap-4 rounded-lg border border-border bg-card p-5 sm:flex sm:items-start sm:justify-between"><div className="min-w-0"><span className="flex items-center gap-2 text-xs font-medium uppercase tracking-[.1em] text-muted-foreground"><GithubLogo className="h-4 w-4 text-primary" weight="duotone" />Preview GitHub</span><h3 className="mt-2 break-words text-2xl font-semibold text-card-foreground">{repository.fullName}</h3><p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">{repository.description || "Sem descrição informada."}</p></div><div className="flex shrink-0 items-center gap-2"><VisibilityBadge privateRepository={repository.private} /><a href={repository.htmlUrl} target="_blank" rel="noopener noreferrer" className="inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-background px-3 text-xs font-medium text-foreground hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">Abrir no GitHub<ArrowSquareOut className="h-3.5 w-3.5" /></a></div></header>
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(260px,.42fr)]"><section className="min-w-0 rounded-lg border border-border bg-card p-4 sm:p-5"><h4 className="text-sm font-semibold text-card-foreground">README</h4>{preview.readme ? <SafeMarkdown content={preview.readme} /> : <p className="mt-3 text-sm text-muted-foreground">Este repositório não possui README acessível.</p>}</section><aside className="grid content-start gap-4"><section className="rounded-lg border border-border bg-card p-4"><h4 className="text-sm font-semibold text-card-foreground">Detalhes</h4><dl className="mt-3 grid gap-3 text-sm"><Detail label="Branch padrão" value={repository.defaultBranch || "Não informada"} /><Detail label="Linguagem" value={repository.language || "Não informada"} /><Detail label="Atualizado" value={formatDate(repository.updatedAt)} /></dl></section><section className="rounded-lg border border-border bg-card p-4"><h4 className="text-sm font-semibold text-card-foreground">Stacks detectadas</h4>{preview.detectedStacks.length ? <div className="mt-3 flex flex-wrap gap-1.5">{preview.detectedStacks.map((stack) => <Tag key={stack}>{formatStack(stack)}</Tag>)}</div> : <p className="mt-3 text-sm text-muted-foreground">Nenhuma stack identificada pelos arquivos raiz.</p>}</section><section className="rounded-lg border border-border bg-card p-4"><h4 className="text-sm font-semibold text-card-foreground">Topics</h4>{repository.topics.length ? <div className="mt-3 flex flex-wrap gap-1.5">{repository.topics.map((topic) => <Tag key={topic}>{topic}</Tag>)}</div> : <p className="mt-3 text-sm text-muted-foreground">Sem topics informados.</p>}</section><section className="rounded-lg border border-border bg-card p-4"><h4 className="text-sm font-semibold text-card-foreground">Arquivos raiz</h4>{preview.rootFiles.length ? <ul className="mt-3 grid gap-1.5 text-xs text-muted-foreground">{preview.rootFiles.slice(0, 16).map((file) => <li key={file} className="flex min-w-0 items-center gap-2"><File className="h-3.5 w-3.5 shrink-0 text-primary" /><span className="truncate">{file}</span></li>)}</ul> : <p className="mt-3 text-sm text-muted-foreground">Nenhum arquivo raiz disponível.</p>}</section></aside></div>
  </div>;
}

function SafeMarkdown({ content }: { content: string }) {
  const blocks: ReactNode[] = [];
  const lines = content.replace(/\r\n/g, "\n").split("\n");
  let codeLines: string[] = [];
  let inCodeBlock = false;

  lines.forEach((line, index) => {
    if (line.startsWith("```")) {
      if (inCodeBlock) blocks.push(<pre key={`code-${index}`} className="mt-4 overflow-x-auto rounded-md border border-border bg-muted/65 p-3 text-xs leading-5 text-card-foreground"><code>{codeLines.join("\n")}</code></pre>);
      inCodeBlock = !inCodeBlock;
      codeLines = [];
      return;
    }
    if (inCodeBlock) { codeLines.push(line); return; }
    if (!line.trim()) return;
    const heading = line.match(/^(#{1,3})\s+(.+)$/);
    if (heading) { const Tag = heading[1].length === 1 ? "h1" : heading[1].length === 2 ? "h2" : "h3"; blocks.push(<Tag key={`heading-${index}`} className={Tag === "h1" ? "mt-6 text-xl font-semibold text-card-foreground" : "mt-5 text-base font-semibold text-card-foreground"}>{renderInline(heading[2])}</Tag>); return; }
    if (/^\s*[-*+]\s+/.test(line)) { blocks.push(<div key={`item-${index}`} className="mt-1 flex gap-2 text-sm leading-6 text-muted-foreground"><span aria-hidden="true">•</span><span>{renderInline(line.replace(/^\s*[-*+]\s+/, ""))}</span></div>); return; }
    blocks.push(<p key={`paragraph-${index}`} className="mt-3 text-sm leading-6 text-muted-foreground">{renderInline(line)}</p>);
  });
  if (inCodeBlock && codeLines.length) blocks.push(<pre key="code-final" className="mt-4 overflow-x-auto rounded-md border border-border bg-muted/65 p-3 text-xs leading-5 text-card-foreground"><code>{codeLines.join("\n")}</code></pre>);
  return <div className="mt-3 min-w-0 break-words">{blocks}</div>;
}

function renderInline(value: string) {
  const parts = value.split(/(`[^`]+`|\[[^\]]+\]\(https?:\/\/[^)\s]+\))/g);
  return parts.map((part, index) => {
    if (part.startsWith("`") && part.endsWith("`")) return <code key={index} className="rounded bg-muted px-1 py-0.5 text-[.85em] text-card-foreground">{part.slice(1, -1)}</code>;
    const link = part.match(/^\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)$/);
    if (link) return <a key={index} href={link[2]} target="_blank" rel="noopener noreferrer" className="text-primary underline decoration-primary/45 underline-offset-2 hover:text-primary/80">{link[1]}</a>;
    return <Fragment key={index}>{part}</Fragment>;
  });
}

function PanelState({ icon, title, detail }: { icon: ReactNode; title: string; detail: string }) {
  return <div className="grid min-h-56 place-items-center rounded-lg border border-dashed border-border bg-card/55 px-6 py-10 text-center"><div className="grid max-w-md justify-items-center gap-3">{icon}<div><p className="text-sm font-medium text-foreground">{title}</p><p className="mt-1 text-sm leading-6 text-muted-foreground">{detail}</p></div></div></div>;
}

function VisibilityBadge({ privateRepository }: { privateRepository: boolean }) {
  return <span className="acervo-github-visibility-badge inline-flex shrink-0 items-center gap-1 text-[10px] font-medium uppercase leading-none tracking-[.05em]">{privateRepository ? <LockKey className="h-3 w-3" /> : <Globe className="h-3 w-3" />}{privateRepository ? "Privado" : "Público"}</span>;
}

function Tag({ children, icon }: { children: ReactNode; icon?: ReactNode }) {
  return <span className="acervo-github-tag inline-flex max-w-full items-center gap-1 text-[10px] leading-none text-muted-foreground">{icon}{children}</span>;
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-[10px] font-medium uppercase tracking-[.08em] text-muted-foreground">{label}</dt><dd className="mt-1 break-words text-sm text-card-foreground">{value}</dd></div>;
}

function formatStack(stack: string) {
  const labels: Record<string, string> = { NODE_JS: "Node.js", JAVA_MAVEN: "Java / Maven", JAVA_GRADLE: "Java / Gradle", PYTHON: "Python", RUBY: "Ruby", GO: "Go", RUST: "Rust", PHP: "PHP", DOTNET: ".NET" };
  return labels[stack] ?? stack;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium" }).format(new Date(value));
}
