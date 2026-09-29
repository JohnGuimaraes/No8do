# No8do Replays MCP

Servidor MCP em TypeScript para acessar Replays pela API autenticada do No8do. O projeto oferece transporte STDIO e Streamable HTTP; as permissões e o isolamento de workspace continuam sob responsabilidade da API.

## STDIO

Requer Node.js 20 ou superior. Instale dependências, rode os testes e compile:

```bash
npm ci
npm test
npm run build
```

Configure `NO8DO_API_URL` com a origem da API, `NO8DO_API_TOKEN` com um PAT fornecido em tempo de execução e, opcionalmente, `NO8DO_WORKSPACE_ID` como workspace padrão. Um `workspaceId` explícito numa chamada tem prioridade sobre o padrão. Nunca coloque tokens em arquivos versionados.

Opcionalmente, `NO8DO_AGENT_CREDENTIAL` fornece uma credential para vincular a sessão inicial. Ela é consumida somente no registro da sessão e não é persistida pelo servidor MCP. Seu envio exige HTTPS, exceto em `localhost`, `127.0.0.1` ou `::1` para desenvolvimento local; o registro sensível não segue redirects. Sem a credential, permanece disponível o registro legado.

Exemplo de configuração — substitua os caminhos locais e forneça PAT real somente por um mecanismo seguro de configuração local, nunca por commit:

```json
{
  "mcpServers": {
    "no8do-replays": {
      "command": "node",
      "args": ["/path/to/no8do/mcp/dist/index.js"],
      "env": {
        "NO8DO_API_URL": "http://localhost:8080",
        "NO8DO_API_TOKEN": "no8do_pat_EXAMPLE_DO_NOT_USE",
        "NO8DO_WORKSPACE_ID": "00000000-0000-0000-0000-000000000000"
      }
    }
  }
}
```

## Remote MCP

O transporte Streamable HTTP expõe `POST`/`GET /mcp` e `GET /health`. Cada instância é configurada para um único workspace com `NO8DO_API_URL`, `NO8DO_WORKSPACE_ID` (UUID válido) e `PORT`.

```text
NO8DO_API_URL=https://api.example.com
NO8DO_WORKSPACE_ID=00000000-0000-0000-0000-000000000000
PORT=3000
```

Clientes remotos enviam o PAT do usuário como `Authorization: Bearer …`; o servidor encaminha a autenticação somente à API No8do. Um workspace divergente é rejeitado antes da chamada à API. A API continua responsável por autenticação, membership e RBAC.

No `initialize` inicial, o cliente pode enviar `X-No8do-Agent-Credential`. Esse header é encaminhado exclusivamente a `POST {NO8DO_API_URL}/api/agent-sessions`; não é guardado na sessão MCP nem incluído em body, URL, fingerprint ou resposta. Credential inválida faz o registro falhar, sem fallback para sessão legada. O envio exige HTTPS, exceto loopback local, e não segue redirects.

## Ferramentas

`list_replays`, `search_replays`, `find_reusable_knowledge`, `get_replay`, `create_replay`, `update_replay`, `register_replay_usage`, `list_replay_relations`, `create_replay_relation`, `delete_replay_relation`, `list_replay_versions` e `get_replay_version`.

`list_replays` retorna o catálogo do workspace conforme disponibilizado pela API. `search_replays` busca conhecimento técnico existente. `projectId` é opcional em `create_replay`; em `update_replay`, `projectId: null` preserva a semântica da API para remover a associação. Relações aceitas: `RELATED_TO`, `SUPERSEDES`, `RESOLVES` e `DEPENDS_ON`, entre Replays do mesmo workspace. As ferramentas de versão são somente leitura.
