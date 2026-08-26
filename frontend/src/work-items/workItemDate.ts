export function formatWorkItemDueDate(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(new Date(year, month - 1, day));
}

export function isWorkItemDueDateOverdue(value: string) {
  const today = new Date();
  const localToday = [
    today.getFullYear(),
    String(today.getMonth() + 1).padStart(2, "0"),
    String(today.getDate()).padStart(2, "0"),
  ].join("-");

  return value < localToday;
}

export function getWorkItemDueDateLabel(dueDate: string, status: "OPEN" | "DONE") {
  return `Prazo: ${formatWorkItemDueDate(dueDate)}${status === "OPEN" && isWorkItemDueDateOverdue(dueDate) ? " · vencido" : ""}`;
}
