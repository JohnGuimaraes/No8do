# Agent Session Registry

## Agent Hub read API

The administrative Agent Hub reads only sessions bound to the requested Agent and derives operational values from existing session timestamps and audit rows:

- `GET /api/workspaces/{workspaceId}/agents/{agentId}/sessions` returns a deterministic, paginated history with the existing `AgentPresenceStatus` projection.
- `GET /api/workspaces/{workspaceId}/agents/{agentId}/overview` returns lifecycle plus computed session counts, presence, and latest timestamps; no aggregate is persisted.
- `GET /api/workspaces/{workspaceId}/agents/{agentId}/activity?limit=50` merges recent safe session audit and non-credential Agent registry audit, capped at 100 items.

All three reads require workspace `OWNER` or `ADMIN`. The `(workspaceId, agentId)` lookup is scoped before related data is read. Legacy unbound sessions are not inferred or included. Activity metadata is whitelisted; raw audit JSON and credential audit details are not returned.

No8do distingue **User** (conta autenticada), **Client** (software MCP declarado em `initialize.params.clientInfo`) e **Session** (uma conexão MCP). Cada conexão HTTP stateful recebe um registro automático antes da resposta de initialize; chamadas subsequentes reutilizam essa sessão, sem criar registros adicionais.

O usuário vem exclusivamente do principal autenticado pelo Spring Security. `workspaceId` é opcional e, quando presente, o backend valida membership. O transporte é `MCP`; o servidor obtém `protocolName` e `protocolVersion` de `No8doAgentProtocolProvider`, e define `registeredAt` no momento da gravação.

Opcionalmente, o registration pode receber `X-No8do-Agent-Credential`. A credential é verificada pelo backend e o workspace é derivado do Agent; se o request também informar um workspace diferente, ou se o usuário autenticado não tiver membership, o registro falha. Uma sessão validamente registrada mantém referências imutáveis e internas ao Agent e ao registro da credential usada; sessões legadas permanecem sem essas referências. O audit `AGENT_SESSION_BOUND` registra IDs seguros e ator, nunca credential, secret ou hash. Revogar a sessão ou a credential não apaga o vínculo histórico.

O MCP calcula SHA-256 em UTF-8 sobre a identidade estável da conexão: no Streamable HTTP, o ID emitido pelo SDK; no stdio, como o SDK não emite ID, um UUID efêmero mantido pelo adaptador daquela instância de transporte. O fingerprint hexadecimal serve apenas para correlação e idempotência; não autentica nem autoriza. A identidade bruta permanece apenas no mecanismo de transporte em memória; não é enviada ao backend, persistida, devolvida pela API ou registrada em logs. Nenhuma credential, secret ou hash é gravada na entidade ou na resposta. A unicidade por transporte/fingerprint torna repetição idempotente e conflitos de identidade são rejeitados.

Uma linha registra que a sessão foi criada, não que permanece conectada. Ainda não há presence, estado online/offline, heartbeat ou status. O registro é a base persistente necessária para as próximas fases de Runtime Modes. Consulte [Agent Runtime Modes](agent-runtime-modes.md) para o controle atual de capabilities por sessão.
