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
