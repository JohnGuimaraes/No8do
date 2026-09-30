# No8do Replays MCP

Servidor MCP em TypeScript para acessar Replays pela API autenticada do No8do. O projeto oferece transporte STDIO e Streamable HTTP; as permissões e o isolamento de workspace continuam sob responsabilidade da API.

## STDIO

Requer Node.js 20 ou superior. Instale dependências, rode os testes e compile:

```bash
npm ci
npm test
npm run build
```

O STDIO permanece como modo de compatibilidade técnica e desenvolvimento, sem alteração de comportamento nesta fase. Configure `NO8DO_API_URL` com a origem da API, `NO8DO_API_TOKEN` com um PAT fornecido em tempo de execução e, opcionalmente, `NO8DO_WORKSPACE_ID` como workspace padrão. No fluxo STDIO legado PAT-only, um `workspaceId` explícito numa chamada tem prioridade sobre esse padrão. Nunca coloque tokens em arquivos versionados.

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

O transporte Streamable HTTP expõe `POST`/`GET /mcp` e `GET /health`. Ele oferece os modos Agent-bound e legado PAT-only; o workspace efetivo é isolado por sessão MCP.

```text
NO8DO_API_URL=https://api.example.com
NO8DO_WORKSPACE_ID=00000000-0000-0000-0000-000000000000
PORT=3000
```

`NO8DO_WORKSPACE_ID` é obrigatório para sessões remotas legadas PAT-only. Não é uma configuração de autoridade para sessões Agent-bound.

Clientes remotos legacy enviam o PAT do usuário como `Authorization: Bearer …`; o servidor encaminha a autenticação somente à API No8do. A API continua responsável por autenticação, membership e RBAC.

**IntegrationCredential (A2-D V1):** use somente `Authorization: Bearer no8do_int_…`, sem PAT, AgentCredential ou Workspace configurado. O backend deriva IntegrationAuthorization → Agent → Workspace e registra uma AgentSession própria da conexão com userId null. O MCP ignora `NO8DO_WORKSPACE_ID` neste modo e rejeita credenciais mistas/autoridade escolhida pelo cliente. Antes de cada request valida também a sessão, bloqueando revogação/desconexão e perda de elegibilidade. Replay permite apenas list/search/discovery/get/quality/versions/relation reads, sob runtime/capabilities/policies; create/update/usage/relation mutations não são tools deste modo. Nenhum controller humano é liberado.

IntegrationCredential permanece somente em memória e no Bearer enviado à API configurada. Todas essas chamadas exigem HTTPS fora de loopback e não seguem redirects. A entrada pública deve usar TLS termination confiável, com o listener HTTP interno não exposto; o serviço não confia em X-Forwarded-Proto arbitrário. Nunca incluir credentials em logs, payloads, URLs ou respostas.

No `initialize` inicial, o cliente pode enviar `X-No8do-Agent-Credential`. Esse header é encaminhado exclusivamente a `POST {NO8DO_API_URL}/api/agent-sessions`; não é guardado na sessão MCP nem incluído em body, URL, fingerprint ou resposta. Credential inválida faz o registro falhar, sem fallback para sessão legada. O envio exige HTTPS, exceto loopback local, e não segue redirects.

**Modo Agent-bound (produto/integração):** quando `X-No8do-Agent-Credential` é enviado no `initialize`, o Remote MCP não fica preso a `NO8DO_WORKSPACE_ID` e o registro envia `workspaceId: null`. O backend verifica a Agent Credential e deriva `AgentCredential → Agent → Workspace`; a AgentSession retorna o Workspace efetivo, que fica associado e restrito àquela sessão. Tools sem `workspaceId` usam esse Workspace. Um `workspaceId` explícito igual ao efetivo é permitido como verificação de consistência; um valor diferente é rejeitado antes da chamada à API. O caller não escolhe a autoridade. A mesma instância/processo Remote MCP pode atender Agents de Workspaces distintos, cada um em sua própria sessão.

**Modo legado PAT-only:** sem Agent Credential, o Remote MCP exige `NO8DO_WORKSPACE_ID` válido na configuração do processo e vincula cada sessão legada inicializada a esse Workspace. Se a configuração estiver ausente ou inválida, o registro falha fechado. Tools usam esse Workspace; um `workspaceId` explícito divergente é rejeitado antes da chamada à API.

**STDIO:** continua disponível para compatibilidade técnica e desenvolvimento, sem mudança de comportamento nesta fase.

O Agent Protocol versão 2 anuncia `integrationExtensions`; `no8do-integration` extensão v1 e Operational Context v1 são capabilities de protocolo, não grants de autorização. O MCP também oferece `no8do/integration/capabilities`. Os requests internos `no8do/operational-context/get` e `no8do/operational-context/update` não aparecem em `tools/list` e não são destinados ao modelo. GET retorna explicitamente `{ exists: false }` antes do primeiro snapshot. UPDATE envia `expectedVersion` explicitamente: `null` significa esperar ausência; inteiro não negativo exige aquela versão exata; conflito retorna o código seguro `OPERATIONAL_CONTEXT_CONFLICT` para reconciliação explícita. O contrato HTTP detalhado está em `docs/agent-integration-mcp-contract.md`.

## Ferramentas

`list_replays`, `search_replays`, `find_reusable_knowledge`, `get_replay`, `create_replay`, `update_replay`, `register_replay_usage`, `list_replay_relations`, `create_replay_relation`, `delete_replay_relation`, `list_replay_versions` e `get_replay_version`.

`list_replays` retorna o catálogo do workspace conforme disponibilizado pela API. `search_replays` busca conhecimento técnico existente. `projectId` é opcional em `create_replay`; em `update_replay`, `projectId: null` preserva a semântica da API para remover a associação. Relações aceitas: `RELATED_TO`, `SUPERSEDES`, `RESOLVES` e `DEPENDS_ON`, entre Replays do mesmo workspace. As ferramentas de versão são somente leitura.
