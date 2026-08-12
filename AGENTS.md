# AGENTS.md - No8do

## Projeto

O No8do e um workspace colaborativo privado para organizar projetos, ideias, clientes, pendencias, links, decisoes e estado atual dos projetos.

O foco e ser uma memoria operacional do trabalho: mostrar onde cada projeto parou, o que ja foi feito, o que falta, quem e responsavel e qual e o proximo passo.

Visual desejado: organizacao em cards/boards estilo Trello, com uma energia mais leve e criativa inspirada no Excalidraw.

## Fase Atual

O projeto esta em desenvolvimento local.

Usar:
- Frontend no VS Code
- Backend no IntelliJ IDEA
- PostgreSQL via Docker Compose
- Tudo rodando em localhost

Nao configurar agora:
- VPS
- Oracle Cloud
- Coolify
- dominio
- HTTPS
- Dockerfile das aplicacoes
- deploy
- producao

## Stack

Frontend:
- React
- Vite
- TypeScript
- Tailwind CSS
- shadcn/ui
- Phosphor Icons
- dnd-kit

Backend:
- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- Hibernate
- Spring Security
- Validation
- Flyway
- PostgreSQL Driver

Banco:
- PostgreSQL

Infra local:
- Docker Compose apenas para PostgreSQL

## Estrutura

```txt
no8do/
|-- frontend/
|-- backend/
|-- docker-compose.yml
|-- .gitignore
|-- README.md
`-- AGENTS.md
```

## Regras

- Nao implementar funcionalidades fora do escopo pedido.
- Nao alterar arquitetura sem autorizacao.
- Nao adicionar dependencias sem justificar.
- Nao configurar deploy nesta fase.
- Nao usar Supabase.
- Nao usar Base44.
- Nao usar Next.js.
- Nao misturar frontend e backend.
- Nao commitar `.env` real.
- Pode commitar `.env.example`.
- Nao commitar `node_modules/`.
- Nao commitar `frontend/dist/`.
- Nao commitar `backend/target/`.
- Nao fazer `npm audit fix` sem autorizacao.
- Nao atualizar dependencias em lote sem autorizacao.
- Nao fazer commit, push, merge, PR ou deploy sem pedido explicito.

## Validacoes

Quando alterar frontend:

```powershell
cd frontend
npm run build
```

Quando alterar backend:

```powershell
cd backend
.\mvnw.cmd test
```

Quando alterar Docker Compose:

```powershell
docker compose config
```

Quando alterar mais de uma camada:

```powershell
docker compose config
cd frontend
npm run build
cd ../backend
.\mvnw.cmd test
```

## Desenvolvimento Local

PostgreSQL:

```powershell
docker compose up -d
```

Frontend:

```powershell
cd frontend
npm run dev
```

URL:

```txt
http://localhost:5173
```

Backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

URL:

```txt
http://localhost:8080
```

Health check:

```txt
GET http://localhost:8080/api/health
```

## Backend

Manter como monolito modular.

Dominios previstos:

```txt
auth/
user/
workspace/
project/
task/
idea/
client/
link/
decision/
activity/
comment/
shared/
```

Regras importantes:
- autorizacao sempre no backend;
- validar se o usuario pertence ao workspace;
- validar ownership dos recursos;
- evitar IDOR/BOLA;
- nao expor dados entre workspaces;
- nao confiar apenas no frontend.

## Banco

Usar Flyway para migrations.

Nao alterar banco manualmente sem migration.

Pasta:

```txt
backend/src/main/resources/db/migration/
```

Padrao:

```txt
V1__baseline.sql
V2__create_workspaces.sql
V3__create_projects.sql
```

## Git

Repositorio:

```txt
https://github.com/JohnGuimaraes/No8do.git
```

Branch principal:

```txt
main
```

Regras:
- mostrar `git status` antes de commit;
- nao fazer commit/push sem autorizacao;
- informar hash apos commit.

Mensagens exemplo:

```txt
chore: estrutura inicial do No8do
docs: adiciona instrucoes do agente
feat: cria entidades iniciais de projeto
fix: corrige configuracao do Flyway
```

## Resposta Esperada

Ao final de cada tarefa, informar:

1. resumo do que foi feito;
2. arquivos alterados;
3. comandos executados;
4. validacoes realizadas;
5. erros ou warnings;
6. status final;
7. proximo passo recomendado.
