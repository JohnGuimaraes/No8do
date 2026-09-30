# AGENTS.md - No8do

## Projeto

No8do é um workspace privado colaborativo e memória operacional para projetos, ideias, pendências, biblioteca, conhecimento e contexto de trabalho.

Direção visual:

- canvas técnico sutil;
- tipografia editorial;
- informação limpa;
- objetos visuais diferentes por contexto;
- movimento funcional;
- base off-white/grafite;
- azul/ciano como identidade;
- âmbar/laranja e cores contextuais preservadas;
- sem neon, cyberpunk, glassmorphism pesado ou animações contínuas.

## Stack

Frontend: React, Vite, TypeScript, Tailwind, shadcn/ui, Phosphor e dnd-kit.

Backend: Java, Spring Boot, Spring Security, JPA/Hibernate, Validation, Flyway e PostgreSQL.

Arquitetura: monolito modular.

## Regras

- Preservar sempre o working tree existente.
- Inspecionar a arquitetura real antes de alterar.
- Não usar `git reset`, `git restore`, `git checkout`, `git clean`, `git pull` ou `git rebase` sem autorização.
- Não alterar arquitetura, stack ou dependências sem autorização.
- Não implementar funcionalidades fora do escopo.
- Não misturar backend em tarefas frontend-only.
- Não adicionar dependências sem autorização.
- Não configurar deploy/produção nesta fase.
- Não usar Supabase, Base44 ou Next.js.
- Preservar responsividade e acessibilidade.
- Usar Phosphor Icons.
- Evitar `transition-all`, `will-change` global, animações infinitas, `console.log` e `debugger`.
- Não fazer `npm audit fix` nem atualizar dependências em lote sem autorização.
- Não fazer commit, push, merge, PR ou deploy sem pedido explícito.
- Não commitar `.env`, `node_modules/`, `frontend/dist/` ou `backend/target/`.

## No8do Replays / Knowledge Layer

O MCP `No8do` de produção é a memória técnica canônica compartilhada do projeto. Para tarefas técnicas não triviais, consulte `docs/REPLAYS_AGENT_PROTOCOL.md`.

- Consultar conhecimento existente antes de reinventar solução técnica não trivial.
- Não criar Replays para alterações rotineiras ou sem valor reutilizável; registrar somente após evidência adequada.
- Pesquisar equivalentes antes de criar e atualizar existentes somente quando houver melhoria real.
- Registrar ReplayUsage somente quando um Replay influenciar materialmente a solução.
- Indisponibilidade do MCP não bloqueia desenvolvimento local; produção não é ambiente para testes destrutivos ou dados artificiais.

## Segurança backend

- Autorização sempre no backend.
- Validar membership do workspace e ownership dos recursos.
- Evitar IDOR/BOLA.
- Nunca expor dados entre workspaces.
- Não confiar apenas no frontend.

## Banco

- Usar Flyway.
- Não alterar banco manualmente sem migration.
- Migrations em `backend/src/main/resources/db/migration/`.

## Validações

Frontend:
```powershell
cd frontend
npm run typecheck
npm run build

## Uso controlado do navegador

O navegador local controlado via `mcp__cua_repl` / `unified-computer-use` deve ser usado somente quando realmente necessário para validar comportamento visual ou interação que não possa ser confirmada com segurança por código, testes, logs, API ou terminal.

Prioridade de validação:

1. inspeção de código;
2. testes automatizados;
3. logs, terminal ou chamadas HTTP;
4. DOM ou Playwright pontual;
5. CUA visual;
6. screenshots somente quando ajudarem a diagnosticar algo específico.

Antes de usar o navegador, avaliar: "Isso realmente exige interação visual?" Se a resposta for não, não usar navegador.

Não usar navegador apenas para confirmar algo já comprovado por typecheck, build, testes automatizados, resposta da API, inspeção do código, logs ou DOM determinístico.

Quando o navegador for necessário:

- Reutilizar o Chrome atual conectado e a aba atual sempre que possível.
- Não abrir outro navegador, usar outro perfil ou abrir novas abas sem necessidade.
- Executar somente o fluxo mínimo necessário e parar assim que a hipótese estiver validada.
- Evitar exploração visual ampla, screenshots repetitivas e navegação por telas sem relação direta com a tarefa.

Uso recomendado:

- CUA visual: layout, modais, foco, formulários, drag and drop, navegação real e comportamentos que dependem da experiência do usuário.
- Playwright ou DOM: presença de elementos, atributos, textos, estados e validações determinísticas da interface.
- CDP ou DevTools: console, network, requests, erros de browser e diagnóstico técnico específico.
- Terminal, testes ou API: backend, autorização, persistência, banco, MCP, CSRF, versionamento, regras de negócio e respostas HTTP.

O navegador não deve ser usado como ferramenta padrão de validação. Ao final de uma tarefa que utilizou navegador, relatar brevemente que foi usado, por que era necessário e qual verificação mínima foi realizada.

Esta regra reduz consumo de tokens e torna o fluxo de desenvolvimento mais eficiente, sem impedir testes visuais quando realmente necessários.

## Revisão adaptativa

Dimensione a revisão independente pelo risco: mudanças triviais não exigem subagentes; mudanças pequenas podem usar um reviewer; alterações relevantes de segurança, autenticação, banco ou protocolo podem exigir três, e PRs críticas podem exigir nova revisão somente do delta. Consulte `.agents/skills/no8do-independent-review/SKILL.md`. Reviewers são somente leitura; o root valida findings. Revisão independente não é substituída por testes ou CodeQL verdes.
