# No8do Replays MCP

Servidor MCP por STDIO para consultar e registrar Replays usando somente a API HTTP autenticada do No8do.

## Requisitos e uso

Requer Node.js 20+ e uma Personal API Token criada no No8do. Instale com `npm install`, valide com `npm test`, e execute com `npm run build && node dist/index.js`.

Defina `NO8DO_API_URL` como a origem da API, por exemplo `http://localhost:8080`; o cliente acrescenta `/api`. Defina `NO8DO_API_TOKEN` com uma token `no8do_pat_...`. Opcionalmente, defina `NO8DO_WORKSPACE_ID` para usar esse workspace quando uma chamada não enviar `workspaceId`; o valor explícito na chamada sempre tem prioridade. Sem um dos dois, a tool retorna erro claro. Nunca inclua a token em arquivos de configuração versionados.

Exemplo de configuração MCP:

```json
{ "mcpServers": { "no8do-replays": { "command": "node", "args": ["/caminho/no8do/mcp/dist/index.js"], "env": { "NO8DO_API_URL": "http://localhost:8080", "NO8DO_API_TOKEN": "no8do_pat_EXEMPLO", "NO8DO_WORKSPACE_ID": "UUID_DO_WORKSPACE" } } } }
```

Tools: `list_replays`, `search_replays`, `find_reusable_knowledge`, `get_replay`, `create_replay`, `update_replay`, `register_replay_usage`, `list_replay_relations`, `create_replay_relation`, `delete_replay_relation`, `list_replay_versions` e `get_replay_version`.

Use `list_replays` para obter o catálogo completo do workspace, na ordem retornada pela API. O endpoint atual não expõe paginação ou filtros. Use `search_replays` somente para busca textual de conhecimento técnico existente.

Em `create_replay`, `projectId` é opcional. Você pode omiti-lo ou enviar `null`; ambos criam o Replay sem projeto associado. Em `update_replay`, `projectId: null` preserva o comportamento existente da API para remover a associação.

As relações aceitam `RELATED_TO`, `SUPERSEDES`, `RESOLVES` e `DEPENDS_ON`, sempre entre Replays do mesmo workspace. `RELATED_TO` é tratado como associação não direcional, portanto a inversão não cria outra relação equivalente. As demais relações preservam a direção do `replayId` para `targetReplayId`.

As tools de versão são somente leitura. Elas retornam snapshots históricos do conteúdo; não existe restauração, edição ou exclusão de versão pelo MCP.

## Remote MCP — workspace único

O transporte remoto usa Streamable HTTP oficial em `POST`/`GET /mcp` e `GET /health`. Cada instância representa exclusivamente um workspace e exige `NO8DO_API_URL`, `NO8DO_WORKSPACE_ID` (UUID válido) e `PORT`. Exemplo:

```bash
NO8DO_API_URL=https://api.example
NO8DO_WORKSPACE_ID=c8700de6-8549-4cc2-aa5f-1e652eba4c96
PORT=3000
```

O servidor não usa `NO8DO_API_TOKEN`: cada cliente envia `Authorization: Bearer <PAT pessoal>`, encaminhado somente à API No8do para aquela requisição. Sem `workspaceId`, uma tool usa o workspace configurado; com o mesmo ID, é permitida; com ID divergente, é rejeitada antes de qualquer chamada à API. O MCP nunca acessa outro workspace. O PAT identifica o usuário, enquanto o workspace configurado limita a instância; a API continua validando membership e RBAC. O STDIO mantém o comportamento anterior: `workspaceId` explícito tem prioridade e `NO8DO_WORKSPACE_ID` é apenas fallback.

Para Docker: `docker build -t no8do-mcp .`; configure `NO8DO_API_URL`, `NO8DO_WORKSPACE_ID` e `PORT`. Para Coolify, publique `/mcp`, use `/health` como health check e crie uma instância separada para cada workspace futuro. OAuth, rate limiting e auditoria de abuso permanecem pendentes antes de exposição pública ampla.
