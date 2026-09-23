# Agent Runtime Modes

Agent Runtime Mode limita as operações de domínio Replay para uma `AgentSession` específica. A autenticação PAT/Bearer continua identificando o usuário; o backend continua sendo a autoridade para workspace, RBAC e regras de domínio.

## Protocol e effective capabilities

O Agent Protocol continua descrevendo as capabilities oferecidas pela versão atual do backend. As effective capabilities são calculadas a cada leitura de contexto como a interseção entre as capabilities do protocolo atual e a matriz explícita do runtime mode da sessão:

`effectiveCapabilities = protocol.capabilities ∩ runtimeModeMatrix[runtimeMode]`

A resolução é determinística, imutável e não persiste uma cópia do manifesto. `FULL` resulta nas capabilities anunciadas pelo protocolo atual. Uma capability futura não entra automaticamente nos modos inferiores: sua inclusão exige uma decisão explícita na matriz.

## Modos

| Modo | Capabilities efetivas da matriz |
| --- | --- |
| `OFF` | Nenhuma capability Replay |
| `READ_ONLY` | `REPLAY_CATALOG_LIST`, `REPLAY_READ`, `REPLAY_VERSION_READ`, `REPLAY_QUALITY_READ`, `REPLAY_RELATIONS`, `REPLAY_USAGE_HISTORY_READ` |
| `RETRIEVAL` | `READ_ONLY` + `REPLAY_SEARCH`, `REUSABLE_KNOWLEDGE_DISCOVERY`, `SEMANTIC_DUPLICATE_SEARCH`, `HYBRID_RETRIEVAL`, `CONTEXT_PACKAGE_ASSEMBLY`, `CONTEXT_RENDERING` |
| `ASSISTED` | `RETRIEVAL` + `REPLAY_USAGE_RECORD` |
| `FULL` | Todas as capabilities presentes no Agent Protocol atual |

A matriz é cumulativa: `OFF ⊆ READ_ONLY ⊆ RETRIEVAL ⊆ ASSISTED ⊆ FULL`. Relações usam a capability existente `REPLAY_RELATIONS`; operações que criam ou removem relação também exigem respectivamente `REPLAY_CREATE` ou `REPLAY_UPDATE`, mantendo leitura disponível sem permitir mutação nos modos inferiores.

## Persistência e escopo

O runtime mode pertence à sessão, não ao usuário ou workspace. A migration adiciona `runtime_mode` com `FULL` como default, incluindo sessões existentes. O registro de sessão não aceita runtime mode; o backend define `FULL` para novas sessões para preservar o comportamento anterior.

`GET /api/agent-sessions/{sessionId}/context` retorna identidade e contexto da sessão, o runtime mode atual e effective capabilities calculadas dinamicamente. `PATCH /api/agent-sessions/{sessionId}/runtime-mode` altera somente a sessão do path. Ambos exigem autenticação e ownership da sessão.

O MCP consulta `get_agent_context` sem parâmetros. A tool consulta o backend a cada chamada, não um snapshot de initialize; uma mudança de modo vale imediatamente sem reconectar.

## Enforcement e autenticação

O MCP anexa `X-No8do-Agent-Session-Id` às chamadas de backend feitas pelas tools depois que initialize registra a sessão. Esse identificador é apenas contexto, não é segredo, não autentica e não substitui PAT/Bearer. O backend exige autenticação normal, valida a propriedade da sessão para o usuário autenticado e, em operações Replay, aplica a capability antes da autorização de workspace/RBAC já existente. Sem o header, o fluxo normal da API permanece inalterado.

Negativas de capability retornam HTTP 403 com `AGENT_CAPABILITY_DENIED` e metadata de session ID, runtime mode e capability exigida. Credenciais, fingerprints e identificadores da sessão bruta do transporte não são incluídos.

## Control plane e catálogo MCP

Handshake, bootstrap, `get_agent_protocol` e `get_agent_context` permanecem disponíveis em qualquer modo. `OFF` bloqueia operações de domínio Replay, mas não desconecta nem revoga a sessão. As tools MCP continuam visíveis em todos os modos; o backend decide durante cada execução.

O capability gate complementa, sem substituir, autenticação, isolamento de workspace, RBAC e validações existentes. Um Policy Engine é uma fase futura; heartbeat/presence, realtime e estado de conexão não fazem parte desta implementação.
