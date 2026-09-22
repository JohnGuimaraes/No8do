# Replay Source Expansion

A expansão da Fase 5F.5 aceita o `ReplayRetrievalResult` já carregado em memória e o `ReplayBudgetedContextResult` da Fase 5F.4. Apenas `selectedEntries()` são elegíveis; entradas `skippedEntries()` nunca são expandidas. A correspondência usa `replayId` e `retrievalRank`, sem nova consulta ou execução de retrieval.

`ReplaySourceExpansionPolicy` define `maxFullSources` (de 1 a 10) e `maxExpansionTokens` (maior que zero), um budget independente do budget compacto. As fontes são tentadas greedy em ordem crescente de `retrievalRank`, sem reranking. Uma fonte que excede o restante recebe `TOKEN_BUDGET`; fontes posteriores ainda podem caber. Após atingir `maxFullSources`, as seguintes recebem `SOURCE_LIMIT` sem estimativa desnecessária (`estimatedTokens` fica nulo nesse caso).

Full source significa conteúdo técnico permitido e não truncado (`title`, `type`, `tags`, `stack`, `problem`, `context` e `solution`), não a serialização de uma entidade. Usa `ReplayVersion` atual quando disponível, com `ReplayResponse` como fallback, valida as identidades e preserva provenance de retrieval. As listas de resultado são imutáveis.

O fluxo não chama LLM, não acessa banco, não faz retrieval adicional, não persiste dados, não gera resposta e não cria `ReplayUsage`.
