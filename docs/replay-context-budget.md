# Replay Context Budget

A 5F.2 limita explicitamente os tokens disponíveis para conhecimento recuperado. `maxTokens` define o teto total informado pelo caller; `reservedTokens` reserva uma parcela opaca para instruções, pergunta, resposta e formatação; `availableTokens` é `maxTokens - reservedTokens`.

Cada `ReplayContextBudgetItem` contém somente o ID, `retrievalRank` e `estimatedTokens`. O custo estimado é informado externamente: esta fase não inspeciona texto, conta tokens nem depende de tokenizer ou provider.

O allocator puro processa os itens por `retrievalRank` crescente. Seleciona cada item que ainda cabe e pula os que excederiam o restante, continuando a avaliar os próximos. Assim, nunca ultrapassa `availableTokens`, e a ordem dos selecionados e ignorados é determinística. IDs de Replay e ranks duplicados são erros.

`candidateLimit` limita quantos candidatos o Retrieval retorna; Context Budget limita quantos tokens estimados podem ser alocados. São limites distintos. Esta fase não monta contexto nem chama LLM.
