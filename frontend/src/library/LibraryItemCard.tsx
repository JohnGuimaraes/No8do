import { BookOpen, Code, DotsThreeVertical, FileText, Fingerprint, FlowArrow, GearSix, Globe, HardDrives, ImageSquare, LinkSimple, SealCheck, SquaresFour } from "@phosphor-icons/react";
import { useState, type ReactNode } from "react";
import { type LibraryItem } from "@/library/libraryApi";

export function LibraryItemCard({ item, onOpen, onArchive, onDelete }: { item: LibraryItem; onOpen: () => void; onArchive: () => void; onDelete: () => void }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const link = getLink(item.url);
  const visualAsset = item.type === "IDENTITY" && link?.hostname ? item.url : null;
  return <article className={`acervo-item-card group relative flex min-h-0 min-w-0 flex-col rounded-lg border border-border/80 bg-card/85 p-3 text-left focus-within:ring-2 focus-within:ring-ring ${item.type === "IDENTITY" ? "acervo-identity-card" : ""}`} data-acervo-type={item.type}>
    <button type="button" className="absolute inset-0 z-0 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={onOpen} aria-label={`Abrir ${item.title}`} />
    <header className="acervo-item-card__header pointer-events-none relative z-10 flex min-w-0 items-start justify-between gap-2"><div className="min-w-0">{renderTypeHeader(item, link)}</div><div className="pointer-events-auto relative z-20 shrink-0"><button type="button" className="grid h-8 w-8 place-items-center rounded-md text-muted-foreground hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label="Ações do item" aria-expanded={menuOpen} onClick={(event) => { event.stopPropagation(); setMenuOpen((current) => !current); }}><DotsThreeVertical className="h-4 w-4" weight="bold" /></button>{menuOpen ? <div className="absolute right-0 top-9 z-20 grid w-44 rounded-md border border-border bg-popover p-1 shadow-lg"><button type="button" className="rounded px-3 py-2 text-left text-sm hover:bg-accent" onClick={() => { setMenuOpen(false); onOpen(); }}>Editar</button><button type="button" className="rounded px-3 py-2 text-left text-sm hover:bg-accent" onClick={() => { setMenuOpen(false); onArchive(); }}>Arquivar</button><button type="button" className="rounded px-3 py-2 text-left text-sm text-destructive hover:bg-destructive/10" onClick={() => { setMenuOpen(false); onDelete(); }}>Excluir</button></div> : null}</div></header>
    <div className="acervo-item-card__content pointer-events-none relative z-10 flex min-h-0 min-w-0 flex-1 flex-col gap-2">
      {item.type === "IDENTITY" ? visualAsset ? <div className="acervo-identity-card__preview"><img src={visualAsset} alt="" loading="lazy" decoding="async" referrerPolicy="no-referrer" onError={(event) => { event.currentTarget.style.display = "none"; }} /></div> : <div className="acervo-identity-card__fallback"><Fingerprint className="h-6 w-6" weight="duotone" /><span>Identidade</span></div> : null}
      <div className="grid min-w-0 gap-0.5"><h3 className="break-words text-[15px] font-semibold leading-5 text-card-foreground">{item.title}</h3><p className={`max-h-14 overflow-hidden whitespace-pre-wrap break-words text-sm leading-5 text-muted-foreground ${item.type === "COMMAND" || item.type === "SNIPPET" ? "font-mono" : ""}`}>{item.description || item.content || item.url || "Sem descrição cadastrada."}</p></div>
      <footer className="mt-auto flex min-w-0 items-center justify-between gap-2 pt-1 text-xs text-muted-foreground">{item.url ? <span className="min-w-0 truncate">{link?.hostname ?? item.url}</span> : <span /> }<span className="shrink-0">Atualizado em {formatDate(item.updatedAt)}</span></footer>
    </div>
  </article>;
}

function TypeHeader({ icon, label }: { icon: ReactNode; label: string }) { return <div className="acervo-item-card__type flex items-center gap-1.5 text-[10px] font-medium uppercase leading-none text-muted-foreground"><span className="text-primary">{icon}</span>{label}</div>; }

function renderTypeHeader(item: LibraryItem, link: ReturnType<typeof getLink>) {
  if (item.type === "LINK") return <div className="acervo-item-card__type flex min-w-0 items-center gap-1.5 text-[10px] leading-none text-muted-foreground">{link?.favicon ? <img className="h-3.5 w-3.5 shrink-0 rounded" src={link.favicon} alt="" loading="lazy" decoding="async" referrerPolicy="no-referrer" onError={(event) => { event.currentTarget.style.display = "none"; }} /> : <Globe className="h-3.5 w-3.5 shrink-0 text-primary" />}<span className="truncate">{link?.hostname ?? "Link"}</span><LinkSimple className="h-3 w-3 shrink-0" /></div>;
  if (item.type === "COMMAND") return <div className="acervo-command-strip acervo-item-card__type rounded-md border border-border font-mono text-[10px] leading-none"><span className="mr-1">● ● ●</span>COMMAND</div>;
  if (item.type === "DOCUMENT") return <div className="acervo-document-mark"><TypeHeader icon={<FileText className="h-4 w-4" />} label="Documento" /><span aria-hidden="true" /><span aria-hidden="true" /></div>;
  if (item.type === "TOOL") return <TypeHeader icon={<GearSix className="h-4 w-4" />} label="Ferramenta" />;
  if (item.type === "SNIPPET") return <TypeHeader icon={<Code className="h-4 w-4" />} label="Snippet" />;
  if (item.type === "REFERENCE") return <TypeHeader icon={<BookOpen className="h-4 w-4" />} label="Referência" />;
  if (item.type === "TEMPLATE") return <TypeHeader icon={<SquaresFour className="h-4 w-4" />} label="Template" />;
  if (item.type === "NOTE") return <TypeHeader icon={<FileText className="h-4 w-4" />} label="Nota" />;
  if (item.type === "IDENTITY") return <TypeHeader icon={<Fingerprint className="h-4 w-4" />} label="Identidade" />;
  if (item.type === "DECISION") return <TypeHeader icon={<SealCheck className="h-4 w-4" />} label="Decisão" />;
  if (item.type === "PROCESS") return <TypeHeader icon={<FlowArrow className="h-4 w-4" />} label="Processo" />;
  if (item.type === "INFRASTRUCTURE") return <TypeHeader icon={<HardDrives className="h-4 w-4" />} label="Infraestrutura" />;
  return <TypeHeader icon={<ImageSquare className="h-4 w-4" />} label="Material" />;
}

function getLink(value: string | null) { try { if (!value) return null; const url = new URL(value); if (!/^https?:$/.test(url.protocol)) return null; return { hostname: url.hostname, favicon: `${url.origin}/favicon.ico` }; } catch { return null; } }
function formatDate(value: string) { return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(new Date(value)); }
