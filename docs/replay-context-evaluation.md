# Replay Context Evaluation

A avaliação de retrieval da 5E.8 mede se os Replays relevantes foram encontrados e ordenados. A 5F.7 mede, separadamente, o que chegou ao `ReplayContextPackage` depois dos budgets e da expansão; não modifica retrieval, ranking, budgets ou seleção.

Cada `ReplayContextEvaluationCase` fornece um `caseId`, ground truth explícito em `relevantReplayIds` e o pacote a avaliar. Ground truth vazio, nulo, com IDs nulos ou duplicados é inválido.

`sourceManifest` é a fonte canônica de presença. `sourcePrecision` é relevantes incluídos dividido pelo total de fontes do pacote (zero quando o pacote está vazio). `sourceRecall` é relevantes incluídos dividido pelo total relevante. As contagens relevantes COMPACT e FULL são expostas separadamente, sem julgamento de qualidade.

Tokens e capacidades vêm do `ReplayContextTokenAccounting` existente; não há nova estimativa. O resultado expõe compact/full/total, tokens compactos substituídos, compactos selecionados originalmente e utilização de cada budget. `evaluateAll` calcula médias aritméticas macro, sem ponderação pelo número de fontes.

O relatório contém dados objetivos por caso e médias. Não cria score global, vencedor ou melhor policy. Não chama LLM, não cria prompt, não acessa banco, não executa retrieval, não estima tokens e não persiste resultados.
