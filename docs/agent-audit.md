# Agent Audit Trail

## Responsabilidades

`AgentEvent` é o fato canônico e efêmero usado para notificar consumidores em processo, incluindo o transporte SSE. `AgentAuditEntry` é o registro persistente, append-only e consultável destinado à investigação operacional/forense. Persistir o audit não transforma SSE em event store nem habilita replay.

O listener do `AgentEventPublisher` registra os seis tipos atuais: `AGENT_CONNECTED`, `AGENT_DISCONNECTED`, `RUNTIME_MODE_CHANGED`, `CAPABILITY_DENIED`, `POLICY_DENIED` e `REPLAY_USAGE_RECORDED`. Operações de estado seguem a publicação after-commit; eventos descartados por rollback não chegam ao trail. Denials são decisões de autorização e podem ser registrados imediatamente, mesmo sem commit de domínio.

## Dados e segurança

Cada linha guarda `id`, `eventId` único, tipo, sessão, usuário, workspace opcional, instante de ocorrência, metadata tipada segura e `recordedAt` gerado pelo PostgreSQL. Não são gravados PATs, Bearer tokens, fingerprints, IDs brutos de sessão MCP, credenciais, secrets ou conteúdo de Replay. A metadata JSONB é reconstruída pelo tipo canônico do evento; motivos de denial de policy são normalizados para uma razão genérica segura.

O modelo não possui operações de atualização ou remoção e a migration instala uma proteção contra `UPDATE`/`DELETE`. Não há retenção automática nesta fase; uma política de retenção e eventual purga controlada são preocupações operacionais futuras.

## Consulta e isolamento

`GET /api/agent-audit` retorna uma página com os dados suficientes de cada entrada; não há endpoint de detalhe redundante. A consulta sempre filtra pelo usuário autenticado. São aceitos filtros objetivos por `sessionId`, `workspaceId`, `eventType`, `from` e `to`, com paginação de banco limitada a 100 itens e ordenação por `occurredAt DESC, id DESC`. O filtro de workspace exige membership, mas não amplia o resultado para eventos de colegas.

O trail tem finalidade de auditoria/investigação e não alimenta o SSE. `GET /api/agent-events/stream` continua in-process, efêmero e sem `Last-Event-ID`; não existe replay nem histórico SSE. Esta fase não define retention policy nem distribuição entre instâncias.
