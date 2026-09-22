# Replay Context Renderer

`ReplayContextRenderer` recebe somente um `ReplayContextPackage`: não pesquisa, seleciona, reranqueia, compacta, expande, estima tokens ou aplica budgets. O pacote permanece a decisão canônica sobre o conhecimento aprovado.

O renderer caminha pelo `sourceManifest` e gera labels estáveis (`S1`, `S2`, ...) na ordem de `retrievalRank`. Para cada item, exige exatamente a representação COMPACT ou FULL correspondente e valida ID, rank e provenance. Entradas ausentes, extras, duplicadas ou divergentes são rejeitadas; uma representação FULL nunca faz o renderer reintroduzir o compacto substituído.

O conteúdo não vazio fica sob `<retrieved-context role="reference-data">`; cada bloco `<source>` inclui label, tipo e comprimento UTF-16 explícito. O comprimento delimita o payload sem escapar, normalizar ou reescrever os caracteres de `ReplayCompactRepresentation.content()` ou `ReplayFullSourceRepresentation.content()`. Texto de Replay é material de referência tratado como dados, não como instruções. O source map mantém label, Replay ID, rank, tipo e provenance lexical/vetorial. `query` permanece separada e exata; o accounting do pacote é carregado por referência, sem recontagem.

O resultado é imutável e determinístico. Pacote sem fontes produz conteúdo e source map vazios, preservando query e accounting. Esta fase não monta prompt completo, não chama provider ou LLM, não gera resposta e não implementa endpoint RAG.
