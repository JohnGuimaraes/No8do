# Observabilidade operacional dos agentes

A Fase 5H.8B.1 adicionou Spring Boot Actuator e Micrometer; a Fase 5H.8B.2 instrumenta operações canônicas do Agent Gateway. As métricas são operacionais, voláteis e locais à instância.

## Health

O único endpoint Actuator exposto por HTTP é `GET /actuator/health`. A resposta oculta detalhes e componentes. O `AgentGatewayHealthIndicator` representa somente o wiring dos componentes essenciais do Gateway (registro/presença de sessões, publicação de eventos e hub SSE); sua disponibilidade não depende de agentes conectados, subscribers, heartbeats ou eventos recentes. Ele não consulta o banco; o health padrão do datasource continua sob responsabilidade do Actuator.

O endpoint legado `GET /api/health` permanece inalterado para compatibilidade.

## Segurança e exposição

A exposição web do Actuator está limitada a `health`. A `SecurityFilterChain` permite somente `GET /actuator/health`; os demais caminhos `/actuator/**` são negados pela regra padrão. Não há papel de platform admin nesta fase. `/actuator/metrics`, `env`, `configprops`, `beans`, `mappings` e `loggers` não são disponibilizados.

As métricas não são expostas por endpoint HTTP. As tags limitam-se a enums controlados; nenhuma métrica inclui usuário, sessão, workspace, evento, policy ID, e-mail, fingerprint ou token. `eventType`, `capability` e `runtimeMode` têm domínios finitos do Agent Protocol.

## Métricas do Agent Gateway

| Métrica | Tipo | Incremento/valor | Tags |
| --- | --- | --- | --- |
| `no8do.agent.events.published` | counter | Evento entregue ao dispatcher Spring sem exceção de listener | `eventType` |
| `no8do.agent.events.listener.failures` | counter | Dispatcher/listener lança RuntimeException durante publicação | `eventType` |
| `no8do.agent.capability.denied` | counter | Capability efetivamente negada antes de lançar a exceção de autorização | `capability`, `runtimeMode` |
| `no8do.agent.policy.denied` | counter | Policy `ENFORCED` produz decisão `DENY` | nenhuma |
| `no8do.agent.audit.persisted` | counter | Insert append-only retorna sucesso; duplicata não incrementa | `eventType` |
| `no8do.agent.audit.persistence.failures` | counter | Persistência do Audit Trail lança RuntimeException | `eventType` |
| `no8do.agent.sse.subscriptions.opened` | counter | Subscriber adicionado ao hub | nenhuma |
| `no8do.agent.sse.subscriptions.closed` | counter | Subscriber removido uma única vez por callback, erro ou shutdown | nenhuma |
| `no8do.agent.sse.subscriptions.active` | gauge | Subscribers atualmente ativos nesta instância; decremento idempotente e limitado a zero | nenhuma |
| `no8do.agent.sse.send.failures` | counter | Envio SSE de evento ou keepalive falha | nenhuma |
| `no8do.agent.sessions.registered` | counter | Nova sessão confirmada após commit; repetição idempotente não incrementa | nenhuma |
| `no8do.agent.sessions.disconnected` | counter | Primeira transição de desconexão confirmada após commit | nenhuma |
| `no8do.agent.heartbeats.accepted` | counter | Heartbeat aceito e confirmado após commit | nenhuma |

As métricas de sessão/heartbeat só são incrementadas após commit para não contar rollback. Falhas na própria instrumentação são contidas e não mudam o resultado da operação do Gateway. A contagem de publicação mede despacho síncrono concluído, não sucesso de persistência; métricas de Audit Trail medem o resultado idempotente de gravação.

## Metrics, AgentEvents e Audit Trail

O `MeterRegistry` agrega métricas operacionais voláteis; não é histórico durável. `AgentEvent` representa eventos canônicos publicados no fluxo realtime. O Agent Audit Trail persiste eventos para consulta histórica/forense. Essas responsabilidades permanecem separadas.

Não há exporter, Prometheus, Grafana, OpenTelemetry ou dashboard nesta fase. O Gateway e o registro de métricas são in-process; em implantação com múltiplas instâncias, as métricas locais não são agregadas entre instâncias.

Não há exporter, Prometheus, Grafana, OpenTelemetry ou dashboard. Em implantação multi-instância, counters e gauge não são agregados entre instâncias.
