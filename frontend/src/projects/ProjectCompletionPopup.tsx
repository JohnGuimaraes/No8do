import { Check } from "@phosphor-icons/react";

export function ProjectCompletionPopup({ projectName }: { projectName: string | null }) {
  if (!projectName) return null;
  return <div className="project-completion-popup" aria-hidden="true">
    <span className="project-completion-popup__check"><Check className="h-5 w-5" weight="bold" /></span>
    <p className="project-completion-popup__title">Projeto concluído</p>
    <p className="project-completion-popup__name">{projectName}</p>
    <p className="project-completion-popup__message">Movido para Projetos finalizados</p>
  </div>;
}
