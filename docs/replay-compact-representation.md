# Replay Compact Representation

A 5F.3 projeta candidatos de Retrieval em texto técnico compacto e determinístico; não produz resumo por IA. Usa somente `title`, `type`, `tags`, `stack`, `problem`, `context` e `solution`. A versão atual (`ReplayVersion`) é a fonte preferida; na ausência dela, usa `ReplayResponse`. Identidades incompatíveis ou conteúdo insuficiente causam erro.

O caller informa uma policy com limites em caracteres para texto e em quantidade para tags/stack. O truncamento opera por Unicode code point, inclui `…` no limite configurado e preserva whitespace interno. Tags e stack mantêm a ordem existente. A representação preserva `replayId`, `retrievalRank`, `hybridScore`, `lexicalRank` e `vectorRank` sem reranquear.

Não há tokenizer, estimativa de tokens, provider, chamada a rede ou LLM nesta fase.

Fluxo: Retrieval → Compact Representation → futura estimativa de tokens → Context Budget.
