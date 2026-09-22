# Orquestrador de Retrieval

A 5F.1 oferece uma entrada estável para candidatos de Retrieval. Ela delega integralmente ao Hybrid Search, preservando autorização por workspace, ordenação e provenance por `lexicalRank`/`vectorRank`. `candidateLimit` limita candidatos recuperados e não é Context Budget, tokens, contexto ou prompt. Esta fase não monta contexto nem usa LLM.
