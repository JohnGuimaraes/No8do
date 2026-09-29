# No8do

No8do é um workspace colaborativo para organizar projetos, conhecimento reutilizável e contexto operacional. O repositório reúne a aplicação web, a API e o servidor MCP.

## Funcionalidades

- Workspaces, projetos, atividades e conhecimento reutilizável em Replays.
- Agent Hub: Registry de Agents, sessões e ciclo de vida.
- Connections e atribuições de Agents a projetos e Connections.
- Capability grants, modos de runtime, políticas e trilhas de auditoria para Agents.
- Gateway MCP para acesso às ferramentas autorizadas pela API.

## Tecnologias

- Frontend: React, Vite, TypeScript, Tailwind CSS e shadcn/ui.
- Backend: Java 21, Spring Boot, Spring Security, JPA e Flyway.
- Persistência: PostgreSQL e pgvector.
- MCP: Node.js e TypeScript.

## Execução local

Pré-requisitos: Docker com Compose, JDK 21 ou superior e Node.js/npm. Configure os valores locais a partir dos arquivos `.env.example`; não coloque credenciais reais nesses arquivos.

1. Inicie o PostgreSQL na raiz:

   ```bash
   docker compose up -d
   ```

2. Inicie a API:

   ```bash
   cd backend
   # Linux/macOS
   ./mvnw spring-boot:run
   # Windows PowerShell
   .\mvnw.cmd spring-boot:run
   ```

3. Em outro terminal, inicie o frontend:

   ```bash
   cd frontend
   npm ci
   npm run dev
   ```

4. Para desenvolver ou validar o servidor MCP:

   ```bash
   cd mcp
   npm ci
   npm test
   npm run build
   ```

Consulte `backend/.env.example`, `frontend/.env.example` e `mcp/README.md` para as variáveis de cada componente. A API local usa `http://localhost:8080`; o frontend de desenvolvimento usa `http://localhost:5173`.

## Segurança

Autenticação e autorização são aplicadas pela API. O escopo de workspace e as permissões são validados no backend. Tokens e outras credenciais devem ser fornecidos fora do controle de versão; nunca publique arquivos `.env` reais, chaves privadas ou dados de produção.

## Estrutura

```text
backend/   API Java e migrations Flyway
frontend/  aplicação web React
mcp/       servidor MCP em TypeScript
docs/      contratos e documentação técnica mantidos pelo projeto
```

## Licenças de assets

As fontes Kalam distribuídas com o frontend estão acompanhadas da licença SIL Open Font License 1.1 em `frontend/src/assets/fonts/kalam/OFL.txt`.
