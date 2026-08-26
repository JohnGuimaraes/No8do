import { Kanban, ListBullets, SquaresFour } from "@phosphor-icons/react";

export type ViewMode = "visual" | "list";

export function ViewModeToggle({
  value,
  onChange,
  visualLabel = "Cards",
}: {
  value: ViewMode;
  onChange: (value: ViewMode) => void;
  visualLabel?: "Cards" | "Board" | "Agrupado";
}) {
  const VisualIcon = visualLabel === "Board" ? Kanban : SquaresFour;

  return (
    <div className="inline-flex shrink-0 rounded-md border border-border bg-background p-0.5" aria-label="Modo de visualizacao">
      <button
        type="button"
        aria-label={`Exibir ${visualLabel}`}
        aria-pressed={value === "visual"}
        className={`inline-flex h-8 items-center gap-1.5 rounded px-2.5 text-xs font-medium transition-[background-color,color] ${value === "visual" ? "bg-secondary text-secondary-foreground" : "text-muted-foreground hover:text-foreground"}`}
        onClick={() => onChange("visual")}
      >
        <VisualIcon className="h-4 w-4" aria-hidden="true" />
        {visualLabel}
      </button>
      <button
        type="button"
        aria-label="Exibir lista"
        aria-pressed={value === "list"}
        className={`inline-flex h-8 items-center gap-1.5 rounded px-2.5 text-xs font-medium transition-[background-color,color] ${value === "list" ? "bg-secondary text-secondary-foreground" : "text-muted-foreground hover:text-foreground"}`}
        onClick={() => onChange("list")}
      >
        <ListBullets className="h-4 w-4" aria-hidden="true" />
        Lista
      </button>
    </div>
  );
}
