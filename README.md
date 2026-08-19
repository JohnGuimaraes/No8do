# No8do

Workspace visual e privado para organizar projetos, ideias, clientes, pendências, links, decisões e o estado atual de cada projeto.

> **Status atual:** fase inicial de estrutura. Ambiente de desenvolvimento 100% local. Nenhuma funcionalidade de negócio, tela avançada ou autenticação real foi implementada ainda.

---

## Stack

| Camada     | Tecnologia |
|------------|------------|
| Frontend   | React + Vite + TypeScript |
| UI         | Tailwind CSS + shadcn/ui |
| Ícones     | Phosphor Icons |
| Drag&Drop  | dnd-kit |
| Backend    | Java 21 + Spring Boot 3.5.16 |
| Banco      | PostgreSQL 16 |
| Infra local| Docker Compose (somente PostgreSQL) |

## Estrutura do repositório

```
no8do/
├── frontend/           # React + Vite + TS (aberto no VS Code)
├── backend/             # Spring Boot + Maven (aberto no IntelliJ IDEA)
├── docker-compose.yml   # PostgreSQL local
├── .gitignore
└── README.md
```

---

## Pré-requisitos

- Node.js 24.x (ou compatível) + npm
- Java 21+ (JDK) — testado com Java 25
- Docker Desktop (com Docker Compose)
- VS Code (frontend)
- IntelliJ IDEA (backend)

---

## 1. Subir o PostgreSQL local

Na raiz do projeto:

```bash
docker compose up -d
```

Isso sobe um container `no8do-postgres` com:

- **database:** `no8do`
- **user:** `no8do`
- **password:** `no8do_dev`
- **porta:** `5432`
- **volume persistente:** `no8do_postgres_data`

Para conferir se subiu corretamente:

```bash
docker compose ps
```

Para derrubar:

```bash
docker compose down
```

(os dados continuam no volume; para apagar tudo, use `docker compose down -v`)

---

## 2. Rodar o backend (Spring Boot)

Abra a pasta `backend/` no **IntelliJ IDEA** e rode a classe `No8doApiApplication` diretamente, **ou** via terminal:

```bash
cd backend

# Linux/macOS
./mvnw spring-boot:run

# Windows
.\mvnw.cmd spring-boot:run
```

O backend sobe em **http://localhost:8080**.

Endpoint de health check:

```
GET http://localhost:8080/api/health
```

Resposta esperada:

```json
{ "status": "ok", "app": "No8do API" }
```

> ⚠️ O PostgreSQL (passo 1) precisa estar rodando **antes** de iniciar o backend, pois o Flyway executa as migrations na inicialização.

### Configuração

O `backend/src/main/resources/application.yml` usa variáveis de ambiente com valores padrão de desenvolvimento local (veja `backend/.env.example`):

| Variável       | Padrão local                                  |
|----------------|------------------------------------------------|
| `DB_URL`       | `jdbc:postgresql://localhost:5432/no8do`       |
| `DB_USER`      | `no8do`                                         |
| `DB_PASSWORD`  | `no8do_dev`                                     |
| `SERVER_PORT`  | `8080`                                          |
| `NO8DO_CREDENTIALS_MASTER_KEY` | sem padrao; Base64 de exatamente 32 bytes para o cofre |

### Spring Security

O starter `spring-boot-starter-security` está incluído na dependência, mas **sem fluxo de autenticação implementado**. Em `SecurityConfig.java` todas as rotas estão liberadas (`permitAll`) e o CORS está aberto para `http://localhost:5173` (frontend local). Isso é temporário e está sinalizado com `TODO` no código.

---

## 3. Rodar o frontend (React + Vite)

Abra a pasta `frontend/` no **VS Code**.

```bash
cd frontend
npm install
npm run dev
```

O frontend sobe em **http://localhost:5173**.

A página inicial mostra o nome **No8do**, o texto "Workspace visual para projetos, ideias e decisões." e um indicador simples e opcional de conexão com o backend (chama `GET /api/health`; se o backend estiver offline, a página continua funcionando normalmente).

### Configuração

`frontend/.env.example` documenta a variável usada para apontar para o backend:

```
VITE_API_URL=http://localhost:8080
```

Copie para `.env` caso queira sobrescrever o padrão.

---

## Scripts úteis

**Frontend** (`frontend/`):

| Comando | Descrição |
|---|---|
| `npm run dev` | inicia o servidor de desenvolvimento (porta 5173) |
| `npm run build` | type-check + build de produção (`dist/`) |
| `npm run preview` | serve o build de produção localmente |
| `npm run typecheck` | apenas checagem de tipos TypeScript |

**Backend** (`backend/`):

| Comando | Descrição |
|---|---|
| `./mvnw spring-boot:run` | roda a aplicação |
| `./mvnw test` | roda os testes (requer PostgreSQL local ativo) |
| `./mvnw clean package` | gera o `.jar` em `target/` |

---

## Decisões e observações desta etapa

- **Spring Boot 3.5.16** foi escolhido em vez do já disponível Spring Boot 4.x. A série 4.x reorganizou os starters em módulos menores e mudou parte da configuração de segurança/serialização; a 3.5.x é a versão mais recente e estável da série 3, plenamente compatível com Java 21–25, e reduz o risco de quebra nesta fase inicial. Migrar para o Boot 4.x é um próximo passo natural, seguindo o [guia oficial de migração](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
- **Java 21** foi definido como `java.version` no `pom.xml` (LTS, requisito mínimo confortável do Spring Boot 3.5.x). O projeto compila e roda normalmente sobre uma JDK 25 instalada localmente.
- **Flyway**: a primeira migration (`V1__baseline.sql`) não cria nenhuma tabela de negócio — serve apenas para validar que o pipeline Banco → Flyway → Spring Boot está funcionando.
- **Sem conexão de negócio** entre frontend e backend: a única integração existente é a checagem opcional de `/api/health` na tela inicial.
- **Sem Dockerfile de aplicação, sem deploy, sem VPS, sem Coolify, sem HTTPS/domínio** — propositalmente fora do escopo desta etapa.

---

## Próximos passos recomendados

1. Rodar `npm install` no frontend e `./mvnw spring-boot:run` (ou abrir no IntelliJ) no backend para confirmar que tudo builda no seu ambiente.
2. Modelar as primeiras entidades de domínio (projetos, ideias, clientes, pendências, links, decisões) e criar a `V2__...sql` correspondente no Flyway.
3. Definir a primeira tela funcional (provavelmente um board/kanban usando dnd-kit).
4. Adicionar mais componentes shadcn/ui conforme a necessidade das telas (`npx shadcn@latest add <componente>`).
5. Só then avaliar autenticação real (login local, depois JWT/sessão).
6. Deixar VPS, Coolify, Dockerfile de aplicação, domínio e HTTPS para uma fase de deploy, quando o produto local estiver mais maduro.
