# Replay Context Package

`ReplayContextPackageAssembler` consolida, sem efeitos colaterais, o retrieval já executado, o contexto compacto aprovado pela 5F.4 e as expansões selecionadas pela 5F.5. Não executa consulta, compactação, estimativa, expansão ou persistência.

O pacote é estruturado e imutável: mantém `compactSources` e `fullSources` separados, sem montar uma string ou formato de prompt. Uma fonte `FULL` substitui o compacto do mesmo Replay; somente compactos que permaneceram no pacote entram em `compactSources`. Skips das etapas anteriores não fornecem conteúdo ao pacote.

`sourceManifest` contém uma única entrada por Replay, ordenada por `retrievalRank`, com provenance lexical/vetorial e tipo `COMPACT` ou `FULL`. O assembler valida ID, rank e provenance entre retrieval, compactos selecionados e fontes expandidas; inconsistências e duplicatas são rejeitadas.

`tokenAccounting` informa tokens compactos finais, tokens full, total, capacidades dos budgets, compactos selecionados originais e os tokens compactos substituídos. O total é a soma dos conteúdos finais; o custo compacto removido não é contado junto com a expansão correspondente.

Esta fase não cria prompt, não chama LLM/provider, não implementa tokenizer, não gera resposta e não persiste o pacote.
