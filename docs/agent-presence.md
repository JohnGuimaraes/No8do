# AgentSession heartbeat e presença

`registeredAt` registra quando o servidor criou a sessão. `lastSeenAt` representa o último sinal de vida; começa igual a `registeredAt` e é avançado pelo heartbeat. `lastActivityAt` representa a última operação real recebida com o header interno da AgentSession; começa nulo e não é atualizado pelo heartbeat. O banco preenche `lastSeenAt` histórico com `registeredAt` e mantém `lastActivityAt` nulo, sem inventar atividade passada.

O MCP inicia um heartbeat HTTP autenticado após o registro da AgentSession no `initialize`, em intervalos de 60 segundos. O heartbeat não é uma tool visível ao modelo, não recebe timestamps do cliente e não significa atividade real. Falhas individuais são registradas sem derrubar o processo; a tentativa seguinte ocorre no próximo intervalo. O timer é encerrado pelo fechamento da sessão HTTP ou do servidor correspondente e pelo fechamento do transporte stdio; timers não mantêm o processo vivo.

A presença é calculada na leitura do contexto e não é persistida:

- `CONNECTED`: heartbeat recente e nenhuma atividade real registrada;
- `ACTIVE`: heartbeat recente e atividade dentro da janela ativa;
- `IDLE`: heartbeat recente, mas a última atividade real ultrapassou a janela ativa;
- `DISCONNECTED`: `lastSeenAt` ultrapassou o timeout de presença.

Os limites são configuráveis em `no8do.agent.presence`. Os defaults conservadores são `active-window: 2m` e `disconnect-timeout: 5m`, com a validação obrigatória `active-window < disconnect-timeout`. Igualdade ao limite ainda é considerada recente; a expiração ocorre quando o limite é ultrapassado. Não há job periódico: o status é derivado quando consultado, inclusive em `get_agent_context`, sem congelar o valor do `initialize`.

Ainda não há realtime (SSE/WebSocket), disconnect explícito, revoke, status `REVOKED`, histórico de eventos ou lista global de sessões.
