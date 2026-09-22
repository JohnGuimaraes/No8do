# Replay Budgeted Context

A 5F.4 conecta retrieval, compactação, estimativa informada pelo caller e Context Budget. `TextTokenEstimator` define a porta provider-agnostic `estimate(String text)`; esta fase não fornece tokenizer nem estimador real, não usa `chars / 4` e não depende de LLM.

Fluxo: `ReplayRetrievalResult` → Compact Representation → token estimate → `ReplayContextBudgetItem` → `ReplayContextBudgetAllocator` → entradas selecionadas e ignoradas com custos individuais.

O planner estima uma vez o conteúdo de cada representação e delega toda a seleção ao allocator existente. A ordem e os totais da allocation são preservados, assim como a provenance da representação. O resultado é imutável; retrieval vazio produz allocation vazia sem chamar o estimator.
