# Agent Gateway Final Backend Audit

## Scope

Auditoria documental e estática original do backend e do contrato MCP/HTTP no commit-base `53937ca81cc10237545a6044df8c7157d3386eea`. O escopo cobre as fases 5H.1–5H.8 e os caminhos de sessão, autenticação, runtime mode, capability, policy, Replay, eventos, presença, SSE, auditoria, métricas e health. A execução original não alterou código, migrations, configuração ou testes. Atualização 5H.8C.2C: o finding de capability foi revalidado no código e resolvido no contrato, controller, testes e documentação. O navegador não foi usado. O MCP de conhecimento No8do não estava disponível nesta execução; as conclusões desta atualização se baseiam no checkout e testes locais.

As referências de evidência apontam para arquivos e linhas do checkout auditado, não para resultados de execução desta auditoria.

## Architecture

```text
Cliente MCP (STDIO ou HTTP)
  -> tools do MCP / AgentSession header
  -> API HTTP autenticada
  -> AgentSessionContextInterceptor / Resolver (ownership e sessão)
  -> capability efetiva conforme Runtime Mode
  -> policy ENFORCED do Agent Protocol
  -> membership/RBAC e autorização de workspace do domínio
  -> serviço Replay e persistência
  -> AgentEvent (denial imediato; evento transacional após commit)
       -> SSE in-process por usuário
       -> Audit Trail append-only após commit
       -> métricas de baixa cardinalidade
```

O backend é a autoridade para autenticação, ownership, membership/RBAC, capabilities e policies. O MCP encaminha chamadas autenticadas e limita o workspace HTTP configurado; não substitui a autorização do backend (`backend/src/main/java/com/no8do/api/agent/AgentSessionContextInterceptor.java:32-61`, `AgentSessionContextResolver.java:32-44`, `backend/src/main/java/com/no8do/api/replay/ReplayService.java:152-177`, `mcp/src/workspace.ts:10-28`).

### Responsabilidades e contratos

- **Protocolo e manifesto:** `No8doAgentProtocolProvider` é a fonte versionada das capabilities e policies; `AgentSessionContextResolver` resolve os valores atuais para o contexto da sessão. Fingerprint de transporte é hash para correlação/idempotência, não autenticação (`No8doAgentProtocolProvider.java:8-41`, `mcp/src/transportSessionFingerprint.ts`).
- **Sessão/ownership:** sessão é vinculada ao usuário autenticado; leitura/listagem é escopada por usuário; a sessão desconectada explicitamente não pode continuar operações normais (`AgentSessionContextResolver.java:32-44`, `AgentSessionRepository.java:14-23`, `AgentSessionControllerTests.java:205-265`).
- **Runtime mode/capability:** modo determina o conjunto efetivo pela interseção do manifesto publicado, do conjunto suportado pelo Gateway e da matriz de modo (OFF vazio; READ_ONLY básico; RETRIEVAL acrescenta busca e descoberta lexical; ASSISTED acrescenta registro de uso; FULL concede o manifesto suportado, não o enum completo). Capability responde se a categoria está habilitada para aquela sessão; não concede membership nem acesso a um objeto.
- **Policy:** `AgentPolicyAuthorizationService` avalia o manifesto canônico e somente bloqueia decisões DENY de policy ENFORCED; policy ADVISORY não bloqueia. Policy não substitui membership/RBAC (`AgentPolicyAuthorizationService.java:44-65`, `AgentPolicyEngine.java:9-52`).
- **Replay/workspace:** controller aplica capability e policy; serviços validam workspace e recursos no domínio. Sessão com workspace associado não pode operar em outro workspace; sessão sem workspace não inventa um, e a autorização regular permanece (`ReplayController.java:49-58`, `ReplayService.java:76-99`, `ReplayService.java:152-200`).
- **Eventos e presença:** eventos de estado persistidos são after-commit; denials são decisões de autorização e são emitidos sem exigir commit de domínio. Presença ACTIVE/IDLE é derivada de timestamps; `disconnectedAt` representa desconexão explícita (`SpringAgentEventPublisher.java:23-54`, `AgentPresenceResolver.java`, `docs/agent-presence.md`).
- **SSE:** stream autenticado, com subscribers em memória agrupados por usuário, framing genérico por tipo e id canônico; não é histórico/replay e sua entrega é single-instance (`AgentEventStreamController.java:20-23`, `AgentEventStreamHub.java:61-129`, `docs/agent-events.md`).
- **Audit Trail:** `eventId` é chave idempotente; registros são append-only e armazenam metadados tipados/sanitizados. O listener é after-commit; falha não desfaz o domínio já commitado (`AgentAuditTrailService.java`, `AgentAuditEventListener.java`, `V45__create_agent_audit_entries.sql`).
- **Métricas/health:** tags de métricas são categorias finitas, não identificadores de usuário/sessão; somente health é exposto pelo Actuator, sem detalhes/componentes. O indicador Agent Gateway representa disponibilidade técnica de wiring, não uma sondagem de uso (`AgentGatewayMetrics.java`, `AgentGatewayHealthIndicator.java`, `backend/src/main/java/com/no8do/api/config/SecurityConfig.java:74-90`, `backend/src/main/resources/application.yml`).

## Canonical Flows

| Fluxo | Fonte da verdade e ordem | Transação, idempotência e falha | Isolamento/exposição |
|---|---|---|---|
| **CONNECT** | MCP obtém o protocolo; backend registra identidade/transport/fingerprint; modo inicial e capabilities são resolvidos pelo backend. | Registro usa insert-if-absent e unicidade por transport/fingerprint; colisão incompatível retorna conflito. Evento de conexão só é publicado na inserção nova. | Token autentica chamadas; sessão pertence ao usuário autenticado. Fingerprint é hash interno e não consta nos DTOs. |
| **REQUEST** | autenticação → contexto/ownership da AgentSession → capability efetiva → policy → workspace membership/RBAC e domínio. | Negação capability/policy interrompe antes da operação; erros HTTP são respostas distintas. Operação de domínio usa os limites transacionais próprios do serviço. | Usuário e workspace são novamente verificados pelo backend; header de sessão não substitui identidade nem membership. |
| **EVENT** | evento canônico representa fato/denial; publisher separa denials imediatos dos fatos transacionais after-commit; listeners entregam SSE, auditam e atualizam métricas. | eventId dá deduplicação ao Audit Trail; rollback suprime evento transacional; listener/audit falhando após commit não reverte domínio e não tem outbox identificado. | SSE filtra por usuário; payload omite userId e conteúdo Replay; audit query é escopada por usuário/workspace. |
| **SESSION LIFECYCLE** | Registro e timestamps persistidos são canônicos; CONNECTED/ACTIVE/IDLE/DISCONNECTED são derivados, exceto `disconnectedAt` explícito. | Heartbeat atualiza lastSeen; atividade atualiza lastActivity; disconnect explícito é update-if-absent/idempotente. Timeout não é transição persistida; falha no disconnect pode deixar apenas timeout derivado. | Listagem e descoberta escopadas por usuário; shutdown normal de HTTP e STDIO pede disconnect explícito via `heartbeat.close()`; encerramento abrupto permanece sujeito ao timeout derivado. |
| **REPLAY USAGE** | API valida capability/policy antes de `ReplayService.registerUsage`; este valida workspace/Replay e persiste declaração; controller emite evento para contexto AgentSession. | ReplayUsage persiste em transação de serviço; evento publicado após retorno. A policy verifica declaração/texto, não aplicação semântica independente. EventId deduplica auditoria se evento chegar ao listener. | Resposta/metadata contém dados de uso autorizados do Replay, sem token/fingerprint; recurso é filtrado por workspace e usuário. |

## Security Boundaries

- **Autenticação:** rotas `/api/**` exigem autenticação; `/actuator` é negado exceto health autorizado. O token MCP permanece no cliente/servidor MCP e é usado para chamadas API; não é parte do AgentSession DTO.
- **Ownership/workspace:** AgentSessionContextResolver compara usuário autenticado com proprietário da sessão. Serviços Replay verificam membership e escopo de recursos independentemente da capability/policy.
- **Headers/fingerprint:** `X-No8do-Agent-Session-Id` é um identificador interno necessário para contexto de agente, não credencial; raw MCP transport id fica no transporte MCP e backend recebe fingerprint derivado. Fingerprint não é autorização.
- **DTO/eventos/audit:** DTOs omitem fingerprint/token; evento de uso transporta id/version/result, não conteúdo; denial policy usa reason normalizada; audit metadata é codificada por tipos allowlisted. SSE exige principal autenticado e entrega somente ao userId destinatário.
- **Métricas/logs:** valores de tags são enums/categorias limitadas; métricas não incluem ids de usuário/sessão/workspace nem texto de Replay. Falhas de listeners/métricas não são propagadas como alteração de regra de domínio.
- **Health:** `/actuator/health` não mostra componentes/detalhes; `/actuator/metrics` não está exposto. Health do gateway não depende de presença/atividade de agente.

## Session Lifecycle

Registro persiste usuário, workspace opcional, cliente, versão, transporte, fingerprint hash e modo. Heartbeat e atividade atualizam timestamps somente para sessão não explicitamente desconectada. Presence resolver calcula status por idade/limites; status timeout DISCONNECTED é observação derivada, não encerramento durável. `disconnectedAt` explícito é terminal e o caminho heartbeat retorna conflito; no shutdown normal, HTTP e STDIO pedem disconnect explícito, sendo o timeout derivado apenas fallback para encerramento abrupto. Descoberta/listagem retorna somente sessões do proprietário autenticado e aplica membership quando há filtro de workspace.

## Authorization Model

Autenticação estabelece o principal. O contexto da sessão é opcional para manter a API regular compatível; se informado, seu ownership é obrigatório. Capability limita categoria por Runtime Mode, policy adiciona regras No8do específicas, e serviços de domínio ainda aplicam membership/RBAC e ownership dos objetos. Capability denial e policy denial usam erros/códigos diferentes. Policy não concede acesso e nunca substitui autorização normal.

## Policy Enforcement

As policies semantic duplicate, secrets e credentials permanecem ADVISORY e não bloqueiam. Workspace isolation é aplicado a cada operação Replay com AgentSession; evidence-required é avaliada em transições para VALIDATED; material-usage é aplicada somente ao registro de ReplayUsage por AgentSession e exige declaração/texto limitados. Desde 5H.8C.2A, policy ENFORCED sem evaluator reconhecido resulta em DENY sanitizado; `AgentPolicyAuthorizationService` preserva o fluxo normal de exceção, métrica e evento `POLICY_DENIED`. A declaração de material use não é prova semântica independente. O comportamento fail-open anterior está preservado somente como histórico do finding RESOLVED abaixo.

## Events and Realtime

AgentEvent possui eventId, tipo, session/user/workspace e metadados tipados. Eventos de estado transacionais são entregues após commit; rollback não os entrega. Capability/policy denials são fatos de autorização, publicados sem esperar commit de domínio. SSE oferece múltiplos subscribers por usuário, framing genérico, id/event/data, keepalive e cleanup; é transporte em memória single-instance sem histórico/replay. ACTIVE/IDLE são derivados e não geram eventos.

## Audit Trail

Listener persiste os tipos canônicos em tabela append-only depois do evento, com `eventId` único/idempotente e metadata codec limitado. UPDATE/DELETE são bloqueados no banco. Denials são auditáveis mesmo sem commit de domínio. A persistência ocorre em callback após commit e falha é contida pelo publisher; não foi encontrado outbox/retry durável, então completude contra crash/falha é risco e não garantia.

## Observability

Actuator/Micrometer expõe health técnico seguro, sem details/components, e métricas internas com tags de cardinalidade finita. `/actuator/metrics` não é exposto. Agent Gateway health indica wiring/availability simples e não presença ou atividade. Falhas de métricas são isoladas do comportamento funcional.

## Confirmed Invariants

1. **Identidade e isolamento:** requisições com `X-No8do-Agent-Session-Id` exigem usuário autenticado, sessão existente e ownership correspondente. O fingerprint não é retornado nos DTOs de sessão/contexto. Membership/RBAC continua sendo verificado pelo domínio, inclusive quando existe AgentSession.
2. **Runtime Mode:** capabilities efetivas são calculadas a partir do modo atual, não copiadas como autorização persistida. A sessão não transforma capability em permissão de workspace.
3. **Workspace:** policy `workspace-isolation-required` é ENFORCED e compara o workspace solicitado ao da sessão; sem sessão ou com workspace nulo não inventa escopo. A autorização normal decide acesso nesses casos.
4. **Policies:** `secrets-forbidden`, `credentials-forbidden` e `semantic-duplicate-check-before-create` permanecem ADVISORY. Nesta base, as demais policies ENFORCED são workspace, evidência para VALIDATED e declaração/evidência de uso material.
5. **Evidência VALIDATED:** criação/atualização que efetivamente deixa Replay VALIDATED exige objeto de evidência que satisfaz os requisitos; a regra é aplicada antes da persistência (`AgentPolicyEngine.java:27-35`, `ReplayService.java:76-99`, `ReplayService.java:102-149`).
6. **Uso material:** em AgentSession, `REPLAY_USAGE_RECORD` exige `materiallyUsed=true` e texto de contexto não vazio de até 1.000 caracteres. O contexto é persistido no ReplayUsage. Trata-se de declaração do chamador e evidência textual, não de verificação independente de que o conteúdo foi aplicado (`AgentPolicyEngine.java:37-48`, `ReplayUsage.java:53-57`, `mcp/src/server.ts:174`).
7. **Eventos:** os seis tipos canônicos são `AGENT_CONNECTED`, `AGENT_DISCONNECTED`, `RUNTIME_MODE_CHANGED`, `CAPABILITY_DENIED`, `POLICY_DENIED` e `REPLAY_USAGE_RECORDED`. Eventos de estado são publicados após commit; rollback não deve produzir entrega; capability/policy denial é emitido como decisão de autorização sem commit de domínio. Metadados de Replay não incluem seu conteúdo (`SpringAgentEventPublisher.java:23-54`, `AgentEventMetadata.java`, `AgentAuditMetadataCodec.java`).
8. **Presença:** ACTIVE/IDLE são estados derivados, não eventos persistidos. Desconexão explícita é terminal; timeout produz estado derivado a partir de `lastSeenAt`, sem job de mutação (`AgentPresenceResolver.java`, `docs/agent-presence.md`).
9. **SSE/Audit:** SSE não mantém replay/history; Audit Trail tem unicidade por `eventId` e trigger append-only. As duas responsabilidades são separadas (`docs/agent-events.md`, `V45__create_agent_audit_entries.sql`).
10. **Métricas/health:** contadores/gauges não carregam identificadores de alta cardinalidade; `/actuator/metrics` não é exposto e `/actuator/health` não revela details/components (`AgentGatewayMetrics.java`, `SecurityConfig.java:74-90`, `application.yml`).

Os testes existentes relacionados incluem `AgentSessionControllerTests`, `AgentSessionDiscoveryServiceTests`, `AgentEffectiveCapabilityResolverTests`, `AgentPolicyEngineTests`, `AgentPolicyAuthorizationEventTests`, `ReplayControllerTests`, `ReplayServiceTests`, `AgentEventLifecycleIntegrationTests`, `AgentEventStreamHubTests`, `AgentAuditEventListenerIntegrationTests`, `AgentAuditEntryRepositoryTests` e `AgentObservabilityTests`. Esta lista é inventário de cobertura presente no checkout, não afirma que os testes foram executados nesta auditoria.

## Diferenças MCP / HTTP

| Aspecto | MCP STDIO | MCP HTTP | Backend |
|---|---|---|---|
| Workspace | Pode receber workspace explícito ou default; backend valida membership e a sessão pode fixar o seu workspace. | Exige `NO8DO_WORKSPACE_ID` e rejeita workspace explícito diferente antes da API. | Ownership, membership/RBAC e isolamento da sessão são autoritativos. |
| Identidade da sessão de transporte | Gera identificador local efêmero e envia fingerprint SHA-256; autenticação é o token da API. | Usa session id do transporte MCP e fingerprint derivado; token é mantido em memória por sessão. | Recebe apenas AgentSession id no header e fingerprint na criação; valida usuário autenticado e ownership. |
| Fechamento | RESOLVED em 5H.8C.2B: o callback aguardável reutiliza `heartbeat.close()`, para o timer, tenta disconnect e limpa a referência local. | `onsessionclosed` e fechamento do serviço chamam `closeSession`, que executa `heartbeat.close()` e pede disconnect explícito. | Disconnect explícito persiste `disconnectedAt` e publica o evento correspondente. |
| Protocolo | Obtido uma vez no startup do processo e capturado pelo servidor MCP. | Obtido ao criar sessão MCP; a consulta posterior ao backend não substitui o snapshot já capturado. | Provider é canônico para a versão implantada; context da sessão é resolvido dinamicamente. |
| Ferramentas | Lista operações implementadas no servidor MCP; `get_agent_protocol` transporta o manifesto recebido do backend. | Mesmas ferramentas do servidor MCP. | Cada capability Replay publicada corresponde a uma operação HTTP real e a um caminho MCP coerente; control-plane é descrito separadamente. |

Evidência: `mcp/src/index.ts:10-25`, `mcp/src/http.ts:37-83`, `mcp/src/agentSessionHeartbeat.ts:41-53`, `mcp/src/server.ts:102-175`, `mcp/src/workspace.ts:10-28`.

## Findings

### REQUIRED — Fechamento STDIO não registra desconexão explícita

- **Status:** RESOLVED — 5H.8C.2B.
- **Componente:** lifecycle da AgentSession no transporte MCP STDIO.
- **Comportamento anterior:** o callback chamava somente `heartbeat.stop()`, cancelando o timer sem pedir disconnect explícito; HTTP já chamava `heartbeat.close()`.
- **Mudança aplicada:** STDIO agora usa `createStdioSessionCloseHandler`, aguarda `AgentSessionHeartbeat.close()` e limpa a referência local após a tentativa. O contrato do callback `onClosed` aceita `void | Promise<void>` e o wrapper aguarda sua conclusão; `close()` existente para heartbeats, envia um único POST de disconnect, usa timeout de 2 s e absorve falhas. Fechamentos repetidos continuam idempotentes.
- **Paridade STDIO/HTTP:** ambos os transports reutilizam `AgentSessionHeartbeat.close()` para solicitar disconnect explícito. A diferença de fechamento que dependia do timeout derivado fica resolvida para shutdown normal; timeout continua como fallback para encerramentos abruptos/processo interrompido.
- **Falha e segurança:** a falha remota não propaga pelo `AgentSessionHeartbeat.close()` e não impede cleanup local; o cleanup do wrapper também contém falhas. A correção não adiciona logs ou mensagens com token/fingerprint; o logger existente redige Bearer/PAT.
- **Evidência:** `mcp/src/index.ts`; `mcp/src/stdioSessionLifecycle.ts`; `mcp/src/server.ts` (`handleClose` aguarda callback); `mcp/src/agentSessionHeartbeat.ts:13-20,41-53`; implementação SDK MCP 1.30.0 (`StdioServerTransport.close()` emite `onclose` sincronamente; `Protocol.close()` aguarda `transport.close()`).
- **Testes executados:** `npm run typecheck` exit 0; `npm test` — 50 testes, 50 passaram, 0 falharam/cancelados/skipped, exit 0 (inclui integração STDIO e testes HTTP); `npm run build` exit 0. Dependências instaladas pelo lockfile no `mcp/node_modules` deste worktree.

### REQUIRED — Capabilities de retrieval/contexto não correspondem a operações anunciadas

- **Status:** RESOLVED — 5H.8C.2C.
- **Componente:** manifesto de AgentCapability, autorização de `/similar` e ferramentas MCP.
- **Comportamento anterior:** manifesto anunciava `SEMANTIC_DUPLICATE_SEARCH`, `HYBRID_RETRIEVAL`, `CONTEXT_PACKAGE_ASSEMBLY` e `CONTEXT_RENDERING`. `/similar` exigia `SEMANTIC_DUPLICATE_SEARCH`, mas delegava a `ReplayService.findSimilar`, que usa ranking determinístico lexical; MCP não tinha ferramenta específica de busca vetorial/híbrida, assembly de pacote ou rendering.
- **Solução aplicada:** as quatro capabilities internas foram retiradas do manifesto e da matriz efetiva de Retrieval sem remover seus enums, serviços ou infraestrutura 5E/5F/5G.1. `/similar` agora exige `REPLAY_SEARCH` e `REUSABLE_KNOWLEDGE_DISCOVERY`, compatíveis com a busca lexical e a descoberta determinística executadas. `FULL` é filtrado pelo manifesto canônico suportado do Gateway; enum futuro/interno presente em snapshot antigo não é efetivado.
- **Distinção de escopo:** Semantic Retrieval 5E e Retrieval→Context 5F permanecem infraestrutura interna; Runtime RAG segue planejado. Elas só devem retornar ao Agent Protocol quando uma operação externa coerente for disponibilizada.
- **Evidência:** `AgentCapability.java:20-23`; `No8doAgentProtocolProvider.java:17-32`; `ReplayController.java:80-86`; `ReplayService.java:165-177`; `mcp/src/server.ts:162-175`.
- **Validação 5H.8C.2C informada e aceita:** backend focado — 62 testes, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS, exit 0; suíte backend — 519 testes, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS, exit 0; MCP `npm ci`, typecheck e build com exit 0; `npm test` — 50 passed, 0 failed, 0 cancelled, 0 skipped, exit 0. `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2` foi aplicado somente aos processos Maven. Os testes de contrato verificam manifesto/backend, tools MCP determinísticas e capabilities efetivas.

### REQUIRED — Policy ENFORCED desconhecida falha aberta

- **Status:** RESOLVED — 5H.8C.2A.
- **Componente:** `AgentPolicyEngine`.
- **Comportamento anterior:** depois dos branches conhecidos, qualquer policy não reconhecida retornava ALLOW, inclusive uma policy ENFORCED futura sem evaluator.
- **Solução aplicada:** o fallback do `AgentPolicyEngine` agora retorna DENY para policy ENFORCED sem evaluator, com motivo fixo/sanitizado `Policy ENFORCED sem evaluator reconhecido.`. O branch ADVISORY continua retornando ALLOW antes da avaliação específica. Os três evaluators ENFORCED atuais foram preservados; manifesto real, workspace/RBAC e políticas não foram alterados ou promovidos.
- **Fluxo de autorização:** o DENY segue `AgentPolicyAuthorizationService` existente: lança `AgentPolicyDeniedException`, publica um `POLICY_DENIED` quando há AgentSession e incrementa `policy.denied` uma vez. O evento continua usando reason pública genérica sanitizada.
- **Evidência:** `backend/src/main/java/com/no8do/api/agent/AgentPolicyEngine.java:13-17,18-49,50-51`; manifesto em `No8doAgentProtocolProvider.java:33-39`; testes `AgentPolicyEngineTests.unknownAdvisoryPolicyAllowsWithoutBlocking`, `AgentPolicyEngineTests.unknownEnforcedPolicyDeniesWithStableSanitizedReason` e `AgentPolicyAuthorizationUnknownPolicyTests.unknownEnforcedPolicyDenialUsesNormalSingleEventAndMetricFlow`.
- **Validação informada:** testes focados 12 / 0 failures / 0 errors / 0 skipped; suíte backend 518 / 0 / 0 / 0; BUILD SUCCESS e exit code 0 em ambos. Executada externamente no PowerShell normal do host com `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2` somente nos processos Maven. O código Java permaneceu byte-for-byte inalterado após essa validação; não se repetiu Maven.

### TECH-DEBT — entrega durável de eventos depende de callback in-process após commit

- **Componente:** publicação de eventos e persistência do Audit Trail.
- **Comportamento atual:** eventos de estado são encaminhados por sincronização after-commit; listener de auditoria grava com transação separada e deduplicação por eventId. Exceções do listener são contidas pelo publisher depois do commit.
- **Evidência:** `SpringAgentEventPublisher.java:23-54`; `AgentAuditEventListener.java`; `AgentAuditTrailService.java`; `V45__create_agent_audit_entries.sql`.
- **Impacto:** crash entre commit de domínio e conclusão do callback, ou falha persistente do banco de auditoria, pode deixar operação de domínio commitada sem AuditEntry. O publisher evita quebrar resposta já commitada, mas não há outbox/retry durável identificado no fluxo auditado. Portanto, append-only/idempotência do registro não equivalem a garantia de entrega.
- **Recomendação:** se o requisito for completude forense garantida, persistir evento/outbox na mesma transação de domínio e processar com retry/idempotência; caso contrário, manter explícito que a trilha é best-effort.

### TECH-DEBT — snapshot do Agent Protocol pode ficar obsoleto em processo MCP longo

- **Componente:** bootstrap e `get_agent_protocol` no MCP.
- **Comportamento atual:** STDIO busca protocolo uma vez no startup; MCP HTTP captura protocolo ao construir servidor/sessão. A consulta por request no HTTP não substitui o objeto capturado usado pelo `get_agent_protocol`; `get_agent_context` consulta contexto atual da sessão.
- **Evidência:** `mcp/src/index.ts:10-15`; `mcp/src/http.ts:37-51`; `mcp/src/server.ts:102-132,151-160`.
- **Impacto:** em processo/conexão sobrevivendo a atualização do backend, tools/bootstrap podem apresentar manifesto antigo enquanto autorização/contexto vêm da versão atual do backend. A mitigação operacional provável é reinício coordenado, mas não há refresh/version guard no contrato observado.
- **Recomendação:** vincular versão do snapshot ao ciclo de vida do processo/sessão com política de reinício documentada, ou implementar refresh/version check antes de tratar o manifesto como dinâmico. Não é necessário para processo estritamente reiniciado junto ao backend.

## Risks

- `materiallyUsed=true` e `context` são declaração e evidência textual do agente; não comprovam materialmente uma aplicação. A mensagem do protocolo orienta o agente a não inventar a declaração, mas a verificação é objetiva apenas quanto à presença/formato.
- Timeout de presença é estado calculado na leitura, não transição persistida. `disconnectedAt` explícito é a condição terminal; clientes não devem interpretar todo status derivado DISCONNECTED como registro durável de encerramento.
- SSE é in-process e não retém eventos; não há replay/history nem garantia de entrega para subscriber desconectado. Reconstrução de estado depende de Session Discovery/API.
- Audit Trail é append-only depois de persistido e deduplica pelo eventId, mas a geração/entrega after-commit não é outbox durável.
- O indicador Agent Gateway health não sinaliza atividade recente nem valida todos os serviços operacionais; `/api/health` legado permanece separado. Health verde não deve ser interpretado como prova de sessão conectada.
- O modo FULL concede somente capabilities publicadas/suportadas pelo Agent Gateway atual; segurança ainda depende do usuário autenticado, ownership, membership/RBAC e policies no backend.

## Required Fixes

1. Resolver o desalinhamento entre capabilities de semantic/hybrid/context, operações REST e tools MCP; manter descrições do manifesto fiéis às operações chamáveis. **RESOLVED — 5H.8C.2C.**
2. Cobertura fail-closed de cada policy ENFORCED desconhecida. **RESOLVED — 5H.8C.2A.**
3. Disconnect explícito no shutdown normal STDIO, com paridade de `heartbeat.close()` do HTTP. **RESOLVED — 5H.8C.2B.**

**BLOCKER abertos: 0. REQUIRED abertos: 0.** Os itens remanescentes estão classificados somente como TECH-DEBT e não fazem parte dos Required Fixes.

## Validation History — prior remediations

Os resultados abaixo pertencem às respectivas fases de remediação e não são uma nova execução da 5H.8C.3:

- **5H.8C.2A:** backend focado — 12 testes; suíte completa — 518 testes; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS e exit 0 em ambos. `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2` foi usado somente nos processos Maven.
- **5H.8C.2B:** MCP typecheck e build com exit 0; MCP tests — 50 passed, 0 failed, 0 cancelled, 0 skipped, exit 0.
- **5H.8C.2C:** backend focado — 62 testes; suíte completa — 519 testes; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS e exit 0 em ambos. MCP typecheck/build exit 0; MCP tests — 50 passed, 0 failed, 0 cancelled, 0 skipped, exit 0. `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2` foi usado somente nos processos Maven.

## Final Readiness

BLOCKER abertos: **0**. REQUIRED abertos: **0**. Os três findings REQUIRED foram corrigidos e validados nas fases 5H.8C.2A, 5H.8C.2B e 5H.8C.2C. Permanecem explicitamente abertos dois TECH-DEBT não bloqueantes: entrega do Audit Trail após commit sem outbox/retry durável e possibilidade de snapshot do Agent Protocol obsoleto em processo MCP longo. Eles não foram resolvidos nem implementados nesta fase.

Foram auditados autenticação, ownership, workspace/RBAC, Runtime Modes, capabilities, policies, lifecycle, presence, events, SSE, Audit Trail, health e metrics. O backend do Agent Gateway está pronto para seguir à fase de Governance & Administration: **"Agent Gateway backend ready for 5H.9 with two documented non-blocking technical-debt items."** Isto não elimina os TECH-DEBT e não inicia 5H.9.

## Technical Debt

Os dois achados TECH-DEBT são: (1) entrega de Audit Trail em callback in-process after-commit sem outbox/retry durável; (2) snapshot do Agent Protocol potencialmente obsoleto em processo MCP longo. Ambos estão descritos com comportamento, evidência, impacto e recomendação em Findings.

## Optional Improvements

Nenhum finding OPTIONAL foi necessário para registrar as divergências observadas. Melhorias não bloqueantes não foram adicionadas para manter o relatório concentrado em riscos sustentados por evidência.

## Explicitly Out of Scope — original audit

A auditoria original não implementou correções ou funcionalidades, Session Governance/Revoke, frontend, Plugin/SKILL.md ou Runtime RAG, nem alterou migrations, domínio, controllers, security, MCP, metrics ou testes. A 5H.8C.3 é exclusivamente documental; as três remediações de código e testes ocorreram nas fases indicadas nos findings. A auditoria não foi teste de penetração nem validação dinâmica de produção.
