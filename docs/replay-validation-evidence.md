# Evidência de validação do Replay

Um Replay pode ser salvo sem evidência enquanto não estiver VALIDATED. Para criar ou atualizar um Replay cujo estado efetivo seja VALIDATED, `evidence-required-for-validated` exige evidência com resumo e método não vazios; a referência é opcional. A falha retorna HTTP 403 com `AGENT_POLICY_DENIED` e o identificador da policy.

O objeto `validationEvidence` é persistido como JSONB no Replay e copiado para cada snapshot imutável de `ReplayVersion`. Atualizações parciais preservam evidência existente quando o campo é omitido; enviá-lo explicitamente como null remove-o somente se o estado final continuar não VALIDATED.

A migração adiciona colunas anuláveis sem backfill. Replays históricos VALIDATED sem evidência permanecem legíveis e não são reescritos. A regra aplica-se às novas gravações que resultem em VALIDATED.

`create_replay` e `update_replay` aceitam `validationEvidence`; `get_agent_protocol` e `get_agent_context` publicam o enforcement canônico. A autenticação, capability, autorização workspace/RBAC existentes e o restante do fluxo permanecem independentes.
