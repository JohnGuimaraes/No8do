# Observabilidade operacional dos agentes

A Fase 5H.8B.1 adiciona Spring Boot Actuator e Micrometer como fundação para observabilidade do Agent Gateway. O `MeterRegistry` fica disponível internamente para instrumentação futura; esta fase ainda não registra counters ou gauges específicos do Gateway.

## Health

O único endpoint Actuator exposto por HTTP é `GET /actuator/health`. A resposta oculta detalhes e componentes. O `AgentGatewayHealthIndicator` representa somente o wiring dos componentes essenciais do Gateway (registro/presença de sessões, publicação de eventos e hub SSE); sua disponibilidade não depende de agentes conectados, subscribers, heartbeats ou eventos recentes. Ele não consulta o banco; o health padrão do datasource continua sob responsabilidade do Actuator.

O endpoint legado `GET /api/health` permanece inalterado para compatibilidade.

## Segurança e exposição

A exposição web do Actuator está limitada a `health`. A `SecurityFilterChain` permite somente `GET /actuator/health`; os demais caminhos `/actuator/**` são negados pela regra padrão. Não há papel de platform admin nesta fase. `/actuator/metrics`, `env`, `configprops`, `beans`, `mappings` e `loggers` não são disponibilizados.

Como nenhuma métrica do Gateway foi criada, ainda não há tags. Quando instrumentadas na Fase 5H.8B.2, tags deverão se limitar a dimensões controladas e nunca conter IDs, e-mails, fingerprints ou tokens.

## Metrics, AgentEvents e Audit Trail

O `MeterRegistry` agrega métricas operacionais voláteis; não é histórico durável. `AgentEvent` representa eventos canônicos publicados no fluxo realtime. O Agent Audit Trail persiste eventos para consulta histórica/forense. Essas responsabilidades permanecem separadas.

Não há exporter, Prometheus, Grafana, OpenTelemetry ou dashboard nesta fase. O Gateway e o registro de métricas são in-process; em implantação com múltiplas instâncias, as métricas locais não são agregadas entre instâncias.

A Fase 5H.8B.2 poderá adicionar métricas específicas do Agent Gateway.
