import { ArrowLeft, Question } from "@phosphor-icons/react";
import { Button } from "@/components/ui/button";

export function HelpPage({ onReturnToWorkspace }: { onReturnToWorkspace: () => void }) {
  return (
    <section className="mx-auto grid w-full max-w-4xl gap-8">
      <header className="grid gap-5 border-b border-border/75 pb-7 sm:flex sm:items-end sm:justify-between">
        <div className="grid max-w-2xl gap-3"><span className="flex items-center gap-2 text-xs font-medium uppercase tracking-[.12em] text-muted-foreground"><Question className="h-4 w-4 text-primary" />Ajuda</span><div><h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">Como o No8do organiza o trabalho</h1><p className="mt-2 text-sm leading-6 text-muted-foreground">Uma referência breve para os fluxos já disponíveis no workspace.</p></div></div>
        <Button type="button" variant="ghost" size="sm" className="w-fit" onClick={onReturnToWorkspace}><ArrowLeft className="h-4 w-4" />Voltar ao workspace</Button>
      </header>
      <div className="grid gap-4 sm:grid-cols-2">
        <section className="rounded-xl border border-border bg-card p-5 shadow-[0_18px_52px_-42px_hsl(var(--foreground))]"><h2 className="text-base font-semibold text-card-foreground">Fluxo de projetos</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">Acompanhe projetos em desenvolvimento, registre pendências e mantenha os concluídos como memória operacional.</p></section>
        <section className="rounded-xl border border-border bg-card p-5 shadow-[0_18px_52px_-42px_hsl(var(--foreground))]"><h2 className="text-base font-semibold text-card-foreground">Conhecimento do workspace</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">Use Ideias e Acervo para guardar referências e contextos que podem evoluir para trabalho ativo.</p></section>
      </div>
    </section>
  );
}
