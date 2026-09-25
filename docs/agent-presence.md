# AgentSession heartbeat e presença

`registeredAt` registra quando o servidor criou a sessão. `lastSeenAt` representa o último sinal de vida; começa igual a `registeredAt` e é avançado pelo heartbeat. `lastActivityAt` representa a última operação real recebida com o header interno da AgentSession; começa nulo e não é atualizado pelo heartbeat. O banco preenche `lastSeenAt` histórico com `registeredAt` e mantém `lastActivityAt` nulo, sem inventar atividade passada.

O MCP inicia um heartbeat HTTP autenticado após o registro da AgentSession no `initialize`, em intervalos de 60 segundos. O heartbeat não é uma tool visível ao modelo, não recebe timestamps do cliente e não significa atividade real. Falhas não terminais são registradas sem derrubar o processo; a tentativa seguinte ocorre no próximo intervalo. `409 AGENT_SESSION_REVOKED` é terminal: encerra o timer e não cria retry, auto-renew ou re-registration para a mesma sessão. O timer também é encerrado pelo fechamento da sessão HTTP ou do servidor correspondente e pelo fechamento do transporte stdio; timers não mantêm o processo vivo.

A presença é calculada na leitura do contexto e não é persistida. A precedência é `REVOKED` > disconnect explícito > disconnect derivado por timeout > `ACTIVE` > `IDLE` > `CONNECTED`:

- `REVOKED`: `revokedAt` está definido; é terminal e prevalece sobre todos os demais timestamps;
- `CONNECTED`: heartbeat recente e nenhuma atividade real registrada;
- `ACTIVE`: heartbeat recente e atividade dentro da janela ativa;
- `IDLE`: heartbeat recente, mas a última atividade real ultrapassou a janela ativa;
- `DISCONNECTED`: `disconnectedAt` está definido ou, na ausência de revogação e disconnect explícito, `lastSeenAt` ultrapassou o timeout de presença.

O endpoint autenticado `POST /api/agent-sessions/{sessionId}/disconnect` encerra a sessão de forma idempotente: o primeiro timestamp é preservado e chamadas posteriores não reabrem nem alteram o estado. Heartbeat, activity touch e requests de API com uma sessão desconectada são recusados com HTTP 409 (`AGENT_SESSION_DISCONNECTED`), exceto a leitura de contexto e chamadas repetidas de disconnect. Para uma sessão revogada, heartbeat, activity e operações são recusados antes com `409 AGENT_SESSION_REVOKED`; disconnect tardio não remove `revokedAt` nem transforma o estado público em `DISCONNECTED`.

No MCP, o DELETE da sessão Streamable HTTP (`onsessionclosed`) e o evento público `Transport.onclose`/`close()` do stdio param o heartbeat, enviam um único disconnect e concluem o cleanup local para sessões operáveis. Após `REVOKED`, `close()` faz somente cleanup local e não envia disconnect, preservando o estado terminal. Conexões que terminam sem shutdown limpo ainda são marcadas `DISCONNECTED` pelo timeout de presença quando não há revogação. Um disconnect não é revoke; revogação é uma decisão administrativa terminal distinta.

Os limites são configuráveis em `no8do.agent.presence`. Os defaults conservadores são `active-window: 2m` e `disconnect-timeout: 5m`, com a validação obrigatória `active-window < disconnect-timeout`. Igualdade ao limite ainda é considerada recente; a expiração ocorre quando o limite é ultrapassado. Não há job periódico: o status é derivado quando consultado, inclusive em `get_agent_context`, sem congelar o valor do `initialize`.

O transporte SSE de AgentEvents está documentado em [`agent-events.md`](agent-events.md); ele não altera o modelo de presença nem fornece histórico/replay de eventos. WebSocket, histórico de presença e lista global de sessões continuam fora do escopo. Revoke e o status `REVOKED` fazem parte do lifecycle atual; o MCP não consome SSE para detectar revogação.

## Session Discovery

`GET /api/agent-sessions` lista de forma paginada somente sessões do usuário autenticado, em ordem decrescente por `registeredAt` e, em caso de empate, por ID. O tamanho padrão é 20 e o máximo é 100. Os filtros disponíveis são `workspaceId`, `runtimeMode` e `clientName` (correspondência parcial sem distinção entre maiúsculas e minúsculas). Ao informar `workspaceId`, a membership é validada antes da consulta; isso não amplia a listagem para sessões de colegas. A consulta e os filtros são executados no banco.

`GET /api/agent-sessions/{sessionId}` retorna os detalhes operacionais somente ao dono da sessão. A resposta inclui identidade do cliente, workspace, transporte, protocolo, runtime mode, timestamps e `presenceStatus`; não inclui fingerprint de transporte nem credenciais ou identificadores MCP brutos.

O status de presença é recalculado em cada resposta por `AgentPresenceResolver`, usando o `Clock` e os limites configurados. Ele não é persistido. Não há filtro `presenceStatus` nesta fase: como o status depende do horário da consulta e dos limites configuráveis, evitar uma tradução SQL parcialmente correta é preferível a carregar sessões em memória ou divergir do resolver. Também não há visibilidade de sessões de terceiros por membership, endpoint global ou realtime.
