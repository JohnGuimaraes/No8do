# Agent Policy Engine

Runtime Mode e Effective Capabilities respondem se uma categoria de operação está disponível para uma AgentSession. Policies respondem se a operação, mesmo autorizada por capability, respeita uma regra objetiva do No8do.

O `AgentPolicyEngine` avalia a lista canônica publicada pelo `No8doAgentProtocolProvider` e produz decisões imutáveis `ALLOW` ou `DENY`, com `policyId` e motivo. O estado `ADVISORY` nunca bloqueia. Uma policy `ENFORCED` pode negar quando sua condição objetiva falha; o estado publicado pelo Agent Protocol continua sendo a fonte canônica.

Nesta fase, `workspace-isolation-required` é ENFORCED: quando a sessão tem `workspaceId`, toda operação Replay deve usar esse workspace. Sessão sem workspace e chamadas sem AgentSession não inventam escopo e seguem para a autorização normal. O Policy Engine não substitui membership nem RBAC.

Ordem: autenticação → ownership da AgentSession → capability → policy → autorização de workspace/RBAC → domínio. Denial de policy retorna HTTP 403 com `AGENT_POLICY_DENIED`, separado de `AGENT_CAPABILITY_DENIED`.

Segredos, credenciais, duplicidade semântica, evidência para VALIDATED e uso material continuam ADVISORY. Fases futuras podem implementar suas condições objetivas, sem promovê-las nesta fundação. Profiles configuráveis, presence/realtime, frontend, RAG e plugin/skill estão fora de escopo.
