import { type ProjectStatus } from "@/projects/projectApi";

export const PROJECT_STATUS_OPTIONS: ProjectStatus[] = [
  "IDEA",
  "PLANNING",
  "ACTIVE",
  "BLOCKED",
  "PAUSED",
  "DONE",
];

export const PROJECT_STATUS_COLUMNS: Array<{ status: ProjectStatus; label: string }> = [
  { status: "IDEA", label: "Ideia" },
  { status: "PLANNING", label: "Planejamento" },
  { status: "ACTIVE", label: "Em desenvolvimento" },
  { status: "BLOCKED", label: "Bloqueado" },
  { status: "PAUSED", label: "Pausado" },
  { status: "DONE", label: "Concluído" },
];

export const DEVELOPMENT_PROJECT_STATUS_COLUMNS = PROJECT_STATUS_COLUMNS.filter(
  (column) => column.status !== "DONE",
);

export function getProjectStatusLabel(status: ProjectStatus) {
  return PROJECT_STATUS_COLUMNS.find((column) => column.status === status)?.label ?? status;
}
