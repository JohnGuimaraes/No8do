# No8do Replays MCP

Servidor MCP por STDIO para consultar e registrar Replays usando somente a API HTTP autenticada do No8do.

## Requisitos e uso

Requer Node.js 20+ e uma Personal API Token criada no No8do. Instale com `npm install`, valide com `npm test`, e execute com `npm run build && node dist/index.js`.

Defina `NO8DO_API_URL` como a origem da API, por exemplo `http://localhost:8080`; o cliente acrescenta `/api`. Defina `NO8DO_API_TOKEN` com uma token `no8do_pat_...`. Nunca inclua a token em arquivos de configuração versionados.

Exemplo de configuração MCP:

```json
{ "mcpServers": { "no8do-replays": { "command": "node", "args": ["/caminho/no8do/mcp/dist/index.js"], "env": { "NO8DO_API_URL": "http://localhost:8080", "NO8DO_API_TOKEN": "no8do_pat_EXEMPLO" } } } }
```

Tools: `search_replays`, `get_replay`, `create_replay`, `update_replay`. Pesquise antes de criar; quando houver um equivalente, obtenha-o e atualize-o somente quando houver melhoria relevante.
