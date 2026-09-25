# Agent Session Governance

## Purpose

Este documento define o contrato de governança administrativa de `AgentSession` antes de qualquer implementação de revogação. É uma proposta arquitetural baseada no modelo existente em `add71264d4ff62f698933aade5bf09308cf72587`; não adiciona endpoint, estado, migration ou comportamento executável.

## Existing Session Model

`AgentSession` persiste `id`, `userId`, `workspaceId` opcional, identidade do cliente, transporte, runtime mode, protocolo, `registeredAt`, `lastSeenAt`, `lastActivityAt`, `disconnectedAt` e fingerprint do transporte. O fingerprint não é retornado nos DTOs. A sessão pertence ao usuário autenticado que a registrou; o header `X-No8do-Agent-Session-Id` fornece contexto e nunca substitui autenticação.

`AgentSessionContextResolver`, os endpoints de sessão e a descoberta verificam ownership. `GET /api/agent-sessions` e `GET /api/agent-sessions/{sessionId}` são owner-scoped. Um filtro por workspace exige membership, mas não amplia resultados para sessões de outros membros. A superfície administrativa distinta `GET /api/agent-sessions/admin` exige `workspaceId` e `OWNER`/`ADMIN` desse workspace; ela não altera a descoberta do owner.

O registro de uma sessão com workspace valida membership através de `WorkspaceAuthorizationService`. A autorização normal dos recursos continua independente. As roles existentes são `OWNER`, `ADMIN`, `MEMBER` e `VIEWER`; `requireWorkspaceManager` permite exatamente `OWNER` e `ADMIN`. As rotas `/api/**` exigem autenticação, e a autorização de recurso/workspace é aplicada nos serviços.

`disconnectedAt` não nulo representa disconnect explícito persistido e terminal: heartbeat, activity touch e operações com o header são recusados. A expiração por falta de heartbeat é somente `DISCONNECTED` derivado na leitura; não grava uma transição na sessão. `CONNECTED`, `ACTIVE` e `IDLE` também são derivados de timestamps e limites configurados. O disconnect existente é idempotente e pertence ao lifecycle do owner/cliente.

O Agent Protocol é provido pelo backend. Runtime mode e capabilities efetivas são resolvidos pelo contexto da sessão; o MCP HTTP/STDIO cria e mantém seu lifecycle, envia heartbeats e solicita disconnect no fechamento normal. O MCP obtém o protocolo em momentos próprios do lifecycle; o snapshot capturado no bootstrap pode ficar obsoleto.

## Governance Principles

- Capability limita categorias de operação; policy aplica regras do produto; nenhuma delas concede identidade, membership ou autoridade administrativa.
- Autenticação e ownership vêm antes de governança. Workspace authority é sempre verificada no backend.
- `SELF DISCONNECT` permanece a ação de lifecycle existente do owner/cliente. `ADMIN REVOKE` é decisão separada, motivada por segurança/governança, não altera a semântica de disconnect.
- Revoke é monotônico, persistido, auditável e idempotente. Não existe operação de reativação; continuar exige registrar uma nova `AgentSession`.
- O endpoint público de revoke não pode ser liberado antes de haver registro de auditoria obrigatório e transacional para a primeira revogação.

## Disconnect vs Revoke

`DISCONNECTED` significa que o lifecycle/transporte foi encerrado pelo cliente/owner ou que a ausência de heartbeat ultrapassou o timeout. Disconnect explícito persiste `disconnectedAt`; timeout é apenas uma observação derivada. Nenhum dos dois expressa uma decisão administrativa.

`REVOKED` significa que uma autoridade administrativa invalidou a sessão por governança. É independente do transporte e terminal. Uma sessão pode ser revogada enquanto conectada, ativa, ociosa ou desconectada. Depois do revoke, nenhuma atividade, heartbeat, disconnect tardio ou cleanup de transporte pode torná-la válida novamente.

## Authorization Model

Para sessão vinculada a workspace, a autoridade mínima já modelada e verificável é `WorkspaceRole.ADMIN` via `requireWorkspaceManager`; `OWNER` também é aceito pelo mesmo guard existente. `MEMBER` e `VIEWER` não podem administrar sessões alheias. A decisão usa o workspace persistido na sessão-alvo, nunca um workspace fornecido pelo chamador.

A sequência futura é: autenticar ator → localizar a sessão dentro de uma resposta não enumerável → verificar ownership para o caso sem workspace, ou verificar `OWNER`/`ADMIN` no workspace persistido → aplicar transição idempotente → gravar auditoria obrigatória na mesma transação → confirmar commit → emitir evento/atualizar métrica conforme contrato. Revoke não substitui capability, Policy Engine, membership/RBAC nem autorização dos recursos Replay.

Para requests de operação com AgentSession, a sequência é: autenticação → resolver sessão e confirmar ownership → rejeitar estado `REVOKED` → capability → policy → autorização workspace/RBAC normal → domínio. O gate de revoke deve anteceder capability, policy, workspace/RBAC e domínio, sem permitir que um header de sessão contorne autenticação ou ownership.

## Workspace Boundaries

Uma sessão com `workspaceId` só pode ser revogada por `OWNER` ou `ADMIN` daquele mesmo workspace. Ser administrador de outro workspace, ser membro do workspace-alvo ou ter autorização para outro Replay não confere autoridade sobre essa sessão. O workspace efetivo vem da linha de `AgentSession`; não é parâmetro de elevação de privilégio.

## Null-Workspace Sessions

Quando `workspaceId = null`, não há autoridade global implícita. Até existir uma autoridade global explicitamente modelada, somente o owner autenticado da própria sessão pode administrá-la; nenhum `OWNER`/`ADMIN` de workspace pode revogar sessões sem workspace de outra pessoa. O comportamento não cria um workspace artificial nem amplia a descoberta atual. O self-disconnect continua sendo a forma normal de encerrar o lifecycle do owner; revoke, se necessário para o próprio owner, continua semanticamente administrativo e auditado.

## Revocation State Model

O mínimo recomendado para persistência é `revokedAt` (instante do servidor) e `revokedByUserId` (ator autenticado). Ambos são imutáveis após a primeira transição. Não persistir texto livre de motivo nesta primeira versão; se surgir requisito operacional, avaliar depois um `reasonCode` de enum finito, validado e sem conteúdo fornecido pelo cliente. Não armazenar token, PAT, fingerprint, raw MCP session ID ou conteúdo Replay.

`REVOKED` deve ser um status visível de sessão/presença, calculado a partir da revogação persistida, e não uma inferência de heartbeat. A resposta de sessão pode expor o status e o timestamp de revogação somente a quem já tem acesso à sessão; não expor `revokedByUserId` em DTO de presença comum. A trilha de auditoria mantém a identidade do ator.

## State Precedence

Para resolver a apresentação de estado, a precedência proposta é:

`REVOKED` > `DISCONNECTED` explícito (`disconnectedAt`) > `DISCONNECTED` por timeout > `ACTIVE` > `IDLE` > `CONNECTED`.

Revogação persistida prevalece mesmo se o transporte ainda envia heartbeat. Disconnect explícito prevalece sobre qualquer timestamp de atividade; timeout é avaliado depois de verificar os estados persistidos. `ACTIVE`, `IDLE` e `CONNECTED` são alternativas derivadas quando nenhum estado terminal se aplica. Essa precedência preserva o resolver atual e insere `REVOKED` como estado administrativo de maior prioridade.

## Proposed API Contract

Contrato candidato, ainda não implementado: `POST /api/agent-sessions/{sessionId}/revoke`, autenticado pelo mecanismo normal da API e protegido por CSRF conforme a configuração normal da aplicação. Não requer body nem aceita workspace alvo ou motivo livre. Sucesso retorna `204 No Content`.

- Sessão com workspace: exige `OWNER` ou `ADMIN` no workspace persistido da sessão.
- Sessão sem workspace: exige que o ator seja o owner da sessão.
- Sessão desconectada explícita ou apenas timeout-disconnected: ainda pode ser revogada; disconnect não equivale a revoke.
- Primeira revogação: persiste estado e auditoria; evento realtime é publicado após commit.
- Repetição: `204`, sem alterar timestamps/ator originais, sem novo evento, nova linha de auditoria ou incremento duplicado da métrica.
- Ausente ou inacessível (outro owner, outro workspace, role insuficiente): resposta indistinguível `404`, sem confirmar existência ou estado. O guard de membership pode internamente produzir `403`; a fronteira de revoke deve normalizar falha de visibilidade/autorização para não permitir enumeração.

## Error Contract

Erros candidatos estáveis para a operação e o gate de sessão:

| HTTP | Código | Semântica |
| --- | --- | --- |
| `401` | resposta normal de autenticação | Não há identidade autenticada. |
| `400` | `Invalid request` | UUID/path malformado ou entrada estrutural inválida. |
| `404` | `AGENT_SESSION_NOT_FOUND` | Sessão inexistente ou não administrável pelo chamador; não distingue owner/workspace/role. |
| `409` | `AGENT_SESSION_REVOKED` | Operação autenticada e pertencente ao owner tentou usar sessão revogada. |
| `409` | `AGENT_SESSION_DISCONNECTED` | Lifecycle encerrou, mas não há decisão administrativa de revoke. |

`AGENT_SESSION_REVOKED` é diferente de `AGENT_SESSION_DISCONNECTED`: o primeiro indica bloqueio administrativo persistente; o segundo indica encerramento de lifecycle. Para requests com header, confirmar autenticação e ownership antes de revelar estado; em seguida, `REVOKED` deve ser recusado antes de capability, policy, workspace/RBAC ou domínio. A resposta de erro não precisa incluir session ID, ator, workspace, motivo, fingerprint ou dados de transporte. `AGENT_SESSION_DISCONNECTED` permanece o código já existente e não deve ser renomeado.

## Heartbeat and Activity Semantics

- Heartbeat em `REVOKED`: rejeitado com `409 AGENT_SESSION_REVOKED`; não atualiza `lastSeenAt`.
- Activity em `REVOKED`: rejeitada com `409 AGENT_SESSION_REVOKED`; não atualiza timestamps nem alcança domínio.
- Disconnect depois de revoke: não reativa, não remove `revokedAt`/`revokedByUserId` nem emite um segundo fato de revogação; cleanup local do transporte é permitido.
- Revoke repetido: idempotente, preserva o primeiro ator e instante e não duplica auditoria/evento/métrica.
- Shutdown/cleanup do transporte depois de revoke: para timers e libera recursos locais; o estado administrativo permanece `REVOKED`.
- Nova conexão exige novo registro e novo ID de `AgentSession`; não reutiliza a sessão revogada nem seu fingerprint como autorização.

## Event Contract

Evento canônico futuro: `AGENT_SESSION_REVOKED`, criado pelo servidor somente na primeira transição. Reutiliza `AgentEvent` imutável: `eventId`, `sessionId` (sessão-alvo), `userId` (usuário afetado), `workspaceId` quando aplicável e `occurredAt` (timestamp do servidor). Metadata tipada contém `actorUserId`; não adiciona ator ao envelope compartilhado e não muda a semântica dos eventos atuais. Não incluir motivo livre.

O evento diferencia sujeito, usuário afetado, ator e workspace através de campos estruturados, não de texto livre. Como o SSE existente é autenticado e filtrado para o `userId` afetado, `AgentEventResponse` serializa `sessionId`, `workspaceId` e metadata; portanto `actorUserId` fica visível ao usuário afetado como atribuição administrativa. Não serializar `userId` do envelope no SSE. Estes UUIDs são identificadores internos necessários à atribuição e não são credenciais; nunca incluir PAT/token, fingerprint, ID bruto MCP, conteúdo Replay ou identificadores em tags de métricas/logs. Se o requisito de não expor qualquer UUID em SSE prevalecer, isso exigirá um DTO de transporte sanitizado antes de habilitar o evento; não deve ser resolvido removendo a atribuição do evento/auditoria.

Fatos de estado são publicados depois do commit e rollback não produz entrega realtime, seguindo `SpringAgentEventPublisher`. SSE continua efêmero, em-processo e sem replay/history; discovery continua sendo a fonte de estado atual. A publicação realtime é best-effort sob o TECH-DEBT existente de outbox/retry; isso não substitui a linha de Audit Trail obrigatória.

## Audit Requirements

A primeira transição exige entrada append-only de `AGENT_SESSION_REVOKED`, com `eventId` idempotente, sessão-alvo, usuário afetado, workspace, timestamp e metadata tipada contendo `actorUserId`. Sem motivo livre ou dados de transporte. Uma repetição idempotente conserva a entrada inicial e não cria outra.

Há uma diferença crítica em relação aos eventos atuais: `AgentAuditEventListener` recebe eventos após commit, chama persistência `REQUIRES_NEW` e o publisher contém falhas de listener. Isso não garante que um revoke commitado terá Audit Entry em caso de falha/crash. Portanto, o registro obrigatório do revoke deve ser gravado dentro da mesma transação de banco da mudança para `REVOKED`; falha ao gravar auditoria aborta o revoke. A emissão SSE/métrica pode ocorrer após commit e não é prova de auditoria. A implementação futura deve evitar uma segunda linha duplicada pelo listener, mantendo `eventId` único e integrando o novo tipo com o codec/repositório de forma idempotente.

O TECH-DEBT geral de ausência de outbox/retry durável permanece explicitamente aberto: ele afeta entrega do evento após commit, mas não pode tornar a persistência auditável da decisão administrativa best-effort. A consulta atual de Audit Trail filtra pelo usuário afetado e membership de workspace não amplia os resultados para colegas; ampliar leitura administrativa exige contrato/autorização próprio, não é pressuposto deste revoke.

## Metrics

Métrica candidata: counter `no8do.agent.sessions.revoked`, baixa cardinalidade e sem tags de identidade. Incrementar uma vez após commit da primeira transição; revoke repetido não incrementa. Não usar tags `sessionId`, `userId`, `workspaceId`, `actorUserId`, email, fingerprint, token ou texto livre de motivo. Um `reasonCode` enum finito poderia ser avaliado futuramente, mas não é necessário agora e não deve ser introduzido como tag nesta fase.

## Administrative Discovery

Discovery owner-scoped permanece inalterada. A rota administrativa `GET /api/agent-sessions/admin` exige `workspaceId`, valida `requireWorkspaceManager` e consulta diretamente no banco apenas linhas daquele workspace. Ela suporta `runtimeMode`, `clientName`, `page` e `size`, com ordenação determinística por registro e ID. `MEMBER`, `VIEWER`, usuário sem membership e manager de outro workspace recebem negação; sessões de outros workspaces e `workspaceId=null` nunca entram no resultado.

A resposta administrativa usa DTO próprio e contém somente `sessionId`, `clientName`, `clientVersion`, `transport`, `workspaceId`, `runtimeMode`, `presenceStatus`, `createdAt`, `lastSeenAt`, `lastActivityAt`, `disconnectedAt` e `revokedAt`. Não expõe owner/user ID, email, nome, protocol details, transport fingerprint, token/PAT, `revokedByUserId` nem conteúdo de Replay. Sessões revogadas permanecem visíveis como `presenceStatus=REVOKED`.

## MCP Behavior

Não é necessária tool MCP administrativa de revoke; a governança pertence inicialmente à API/backend. Quando a sessão associada a um MCP for revogada, a próxima chamada de tool que enviar `X-No8do-Agent-Session-Id`, ou o próximo heartbeat, recebe `AGENT_SESSION_REVOKED` sanitizado. O MCP futuro deve tratar o erro como terminal: parar heartbeat, limpar/reter como inválido o ID local, não fazer retry de operação nem re-registrar automaticamente uma nova sessão. O erro deve ser propagado sem token, fingerprint ou raw MCP session ID. O fechamento do transporte ainda pode fazer cleanup local; não desfaz a revogação.

## Security Invariants

1. Revoke nunca concede acesso nem substitui autenticação, capability, policy, membership, RBAC ou autorização de domínio.
2. Workspace admin só gerencia sessão cujo `workspaceId` persistido seja o workspace em que tem autoridade `OWNER`/`ADMIN`.
3. Sessão `workspaceId=null` não se torna globalmente administrável; apenas seu owner pode administrá-la até haver autoridade global explícita.
4. `REVOKED` é terminal e prevalece sobre qualquer presença ou estado de transporte.
5. Heartbeat, activity, disconnect e cleanup não reativam nem removem revogação.
6. Continuar operando exige uma nova `AgentSession`.
7. Revoke repetido é idempotente e preserva ator/instante/auditoria originais.
8. Erros e métricas não expõem IDs, tokens ou fingerprints; payloads não expõem credenciais, fingerprints, raw MCP session IDs ou conteúdo Replay. Evento/audit usam somente os UUIDs estruturados mínimos já requeridos para identificar sessão, usuário afetado, workspace e ator, no escopo autenticado descrito acima; métricas nunca carregam esses UUIDs.
9. Self-disconnect e administrative revoke são ações distintas; disconnect não pode servir como bypass de ownership/workspace nem significar revoke.
10. O endpoint público não é liberado sem auditoria obrigatória gravada na mesma transação da revogação.

## Explicitly Out of Scope

Ficam fora desta fase: rota pública de revoke, evento/métrica públicos de revoke, auditoria transacional do revoke, alteração MCP, autorização global, reason livre, outbox/retry, política de retenção, frontend e SSE replay. 5H.9D ainda não foi iniciada. Permanecem preservados os dois TECH-DEBT existentes: Audit Trail sem outbox/retry durável e snapshot do Agent Protocol potencialmente obsoleto.

## Implementation Plan

- **5H.9B — Session Revocation Core (IMPLEMENTED):** persistir `revokedAt`/`revokedByUserId`, estabelecer estado terminal e precedência, bloquear requests/heartbeat/activity antes dos gates posteriores e implementar a transição idempotente. A rota pública permanece bloqueada enquanto a auditoria obrigatória não for transacional.
- **5H.9C — Administrative Session Discovery (IMPLEMENTED):** `GET /api/agent-sessions/admin` fornece descoberta workspace-scoped para `OWNER`/`ADMIN`, sem resultados cross-workspace, sessões sem workspace de terceiros ou listagem global.
- **5H.9D — Governance Event/Audit/Observability & Contract Audit:** entregar atomicamente a API pública de revoke, auditoria transacional obrigatória, evento, métricas e propagação terminal pelo MCP. A gravação da auditoria precisa participar da transação de revoke e sua falha deve abortar a operação. O restante do Audit Trail continua sem outbox/retry durável e esse TECH-DEBT não é resolvido aqui.

## 5H.9B — IMPLEMENTED

O core de revogação foi implementado nesta fase. A migration Flyway V46 adiciona `revoked_at` e `revoked_by_user_id` como campos nullable, sem backfill, e mantém o ator por FK seguindo a convenção de `ON DELETE SET NULL`. O índice único parcial preserva a idempotência de sessões não revogadas e permite que um novo registro obtenha uma nova `AgentSession` depois que a anterior foi revogada.

- `REVOKED` é persistido, terminal e tem precedência sobre disconnect explícito, timeout, `ACTIVE`, `IDLE` e `CONNECTED`.
- Guards de request context, heartbeat e activity rejeitam a sessão revogada com `AGENT_SESSION_REVOKED`; heartbeat/activity não atualizam timestamps.
- A transição do core é idempotente e preserva o primeiro `revokedAt` e `revokedByUserId`.
- Para sessão com workspace, somente `OWNER`/`ADMIN` desse workspace podem revogar; `MEMBER`/`VIEWER` e o ownership da sessão, por si só, não concedem autoridade administrativa.
- Com `workspaceId = null`, somente o próprio owner pode revogar; não há autoridade global implícita.
- Disconnect posterior preserva a revogação; discovery owner-scoped reconhece `REVOKED` e não expõe `revokedByUserId` no DTO normal.
- A rota pública `POST /api/agent-sessions/{sessionId}/revoke` ainda **não existe**. Também não foram adicionados evento público `AGENT_SESSION_REVOKED`, Audit Trail transacional do revoke, métrica `sessions.revoked`, tool MCP de revoke ou frontend.
- A administrative discovery foi adicionada na 5H.9C. 5H.9D deverá entregar conjuntamente API pública + transactional audit + event + metrics + MCP terminal propagation.

Validação de 5H.9B, executada externamente no host conforme informado:

- Testes focados: 26; 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`; exit code 0.
- Suíte backend completa: 528; 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`; exit code 0.
- Flyway validou 46 migrations, aplicou V46 e concluiu com schema na versão 46.
- `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2` foi usado somente nos processos Maven.

## 5H.9C — IMPLEMENTED

`GET /api/agent-sessions/admin?workspaceId=...` oferece discovery administrativa paginada, com filtros opcionais `runtimeMode` e `clientName`. A autorização usa `requireWorkspaceManager`: somente `OWNER` e `ADMIN` do workspace consultado são aceitos; `MEMBER`, `VIEWER` e usuários sem membership são negados. A query exige igualdade exata do `workspaceId` no banco, então não inclui outros workspaces, sessões sem workspace ou sessões globais. Ordenação é determinística por `registeredAt DESC, id DESC`; os limites existentes de paginação (`page >= 0`, `1 <= size <= 100`) são preservados.

O DTO administrativo foi separado e expõe os campos operacionais documentados acima, sem identidade do owner, email/nome, protocol details, fingerprint, credentials, `revokedByUserId` ou conteúdo Replay. `revokedAt` é acompanhado de `presenceStatus=REVOKED` pelo mesmo resolver terminal. `GET /api/agent-sessions` e `GET /api/agent-sessions/{sessionId}` permanecem owner-scoped e semanticamente inalterados. Nenhuma rota pública de revoke foi criada; evento, auditoria transacional, métrica e propagação MCP seguem exclusivos da 5H.9D.

Validação final executada externamente no PowerShell normal do host, conforme resultados informados:

- Testes focados: 27; 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`; exit code 0.
- Suíte backend completa: 530; 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`; exit code 0.
- Flyway validou 46 migrations; o schema permaneceu em V46.
- `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2` foi usado somente nos processos Maven.
