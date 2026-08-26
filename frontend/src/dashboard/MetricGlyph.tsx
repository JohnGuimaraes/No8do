import { type ReactNode } from "react";

type MetricGlyphType = "development" | "blocked" | "completed" | "ideas" | "library" | "pending";

export function MetricGlyph({ type }: { type: MetricGlyphType }) {
  const content: Record<MetricGlyphType, ReactNode> = {
    development: <><i className="absolute h-7 w-10 rounded border border-current/50" /><i className="absolute h-7 w-10 translate-x-1.5 -translate-y-1.5 rounded border border-current/70" /><i className="absolute h-7 w-10 translate-x-3 -translate-y-3 rounded border border-current bg-current/10" /></>,
    blocked: <><i className="h-8 w-3 rounded-sm bg-current" /><i className="h-5 w-7 rounded-sm border-2 border-current" /><i className="h-8 w-3 rounded-sm bg-current" /></>,
    completed: <span className="grid h-10 w-10 place-items-center rounded-full border-2 border-current border-r-transparent text-lg">✓</span>,
    ideas: <><i className="h-3 w-3 rounded-full bg-current" /><i className="h-px w-7 bg-current/60" /><i className="h-2 w-2 rounded-full border border-current" /></>,
    library: <><i className="absolute h-8 w-7 -translate-x-2 rounded border border-current/40" /><i className="absolute h-8 w-7 translate-x-2 rounded border border-current/80 bg-current/10" /></>,
    pending: <span className="grid gap-1"><i className="h-px w-9 bg-current" /><i className="h-px w-7 bg-current" /><i className="h-px w-8 bg-current" /></span>,
  };
  return <span className="metric-glyph relative flex h-12 w-14 items-center justify-center gap-1 text-primary" aria-hidden="true">{content[type]}</span>;
}
