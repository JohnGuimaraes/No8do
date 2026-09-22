# Evaluation de Retrieval de Replays

A evaluation mede, sem alterar ranking, Discovery lexical, Vector Search e Hybrid Search contra ground truth explícito por query: um conjunto binário de Replay IDs relevantes.

Para cada estratégia e caso, Precision@K usa `K` como denominador; Recall@K usa o total de relevantes; MRR@K considera somente o primeiro relevante no top K; nDCG@K usa relevância binária. Os resultados agregados são macro-average: média simples dos casos, sem pesos. Rankings com IDs duplicados falham para não mascarar defeitos do retrieval.
