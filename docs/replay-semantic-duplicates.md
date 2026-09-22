# Duplicatas semânticas de Replays

A detecção é uma consulta consultiva sobre embeddings existentes: usa a mesma canonicalização do backfill e cosine distance do pgvector, onde menor distância indica maior semelhança. Cada chamada informa explicitamente o threshold (`maxDistance`) e pode excluir o próprio Replay em edições. Ela não bloqueia criação ou edição, nem altera, mescla ou persiste Replays automaticamente.
