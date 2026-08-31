import { useRef, useState } from "react";
import { Code, List, ListChecks, Quotes, TextB, TextItalic, TextT } from "@phosphor-icons/react";
import { MarkdownRenderer } from "@/components/MarkdownRenderer";

const tools = [
  { label: "Negrito", Icon: TextB, before: "**", after: "**" }, { label: "Itálico", Icon: TextItalic, before: "*", after: "*" },
  { label: "Título", Icon: TextT, before: "## ", after: "" }, { label: "Lista", Icon: List, before: "- ", after: "" },
  { label: "Checklist", Icon: ListChecks, before: "- [ ] ", after: "" }, { label: "Citação", Icon: Quotes, before: "> ", after: "" }, { label: "Código", Icon: Code, before: "`", after: "`" },
] as const;

export function MarkdownEditor({ value, onChange, maxLength, placeholder, ariaLabel }: { value: string; onChange: (value: string) => void; maxLength: number; placeholder: string; ariaLabel: string }) {
  const [preview, setPreview] = useState(false);
  const textarea = useRef<HTMLTextAreaElement>(null);
  function insert(before: string, after: string) { const el = textarea.current; const start = el?.selectionStart ?? value.length; const end = el?.selectionEnd ?? value.length; const selected = value.slice(start, end) || "texto"; onChange(value.slice(0, start) + before + selected + after + value.slice(end)); requestAnimationFrame(() => { el?.focus(); el?.setSelectionRange(start + before.length, start + before.length + selected.length); }); }
  return <div className="grid gap-2"><div className="flex flex-wrap items-center justify-between gap-2"><span className="text-xs text-muted-foreground">Markdown suportado</span><div className="flex rounded-md border border-border p-0.5"><button type="button" className={`rounded px-2 py-1 text-xs ${!preview ? "bg-muted text-foreground" : "text-muted-foreground"}`} onClick={() => setPreview(false)}>Editar</button><button type="button" className={`rounded px-2 py-1 text-xs ${preview ? "bg-muted text-foreground" : "text-muted-foreground"}`} onClick={() => setPreview(true)}>Visualizar</button></div></div>{!preview ? <><div className="flex flex-wrap gap-1" aria-label="Ferramentas de Markdown">{tools.map(({ label, Icon, before, after }) => <button key={label} type="button" className="grid h-8 w-8 place-items-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={() => insert(before, after)} aria-label={label} title={label}><Icon className="h-4 w-4" /></button>)}</div><textarea ref={textarea} className="min-h-32 w-full resize-y rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring" value={value} onChange={(event) => onChange(event.target.value)} maxLength={maxLength} placeholder={placeholder} aria-label={ariaLabel} /></> : <div className="min-h-32 rounded-md border border-border bg-muted/25 p-3"><MarkdownRenderer content={value || "Nada para visualizar."} /></div>}</div>;
}
