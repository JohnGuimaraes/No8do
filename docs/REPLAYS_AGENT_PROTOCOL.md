# No8do Replays — Agent Protocol

Este protocolo é independente de agente e serve para Codex, Claude, agentes n8n, automações e demais clientes MCP/API.

## Papel da Knowledge Layer

Git/GitHub guarda código e histórico. Replays guarda aprendizado técnico reutilizável extraído do trabalho; não é cópia do repositório. A memória pertence ao No8do, não a um agente específico, e pode ser consultada e enriquecida por diferentes agentes.

O MCP `No8do` de produção é a fonte canônica. `No8do Local` serve ao desenvolvimento e teste do próprio MCP/Replays e a cenários que não devem contaminar produção.

> Não salvar porque código foi escrito. Salvar quando conhecimento reutilizável foi aprendido e validado.

## Antes da implementação

Para tarefa técnica não trivial — bugs, troubleshooting, arquitetura, segurança, autenticação/autorização, integrações, infraestrutura, banco, configurações relevantes, padrões, decisões ou procedimentos — o agente deve:

1. Consultar o MCP `No8do`.
2. Preferir `find_reusable_knowledge` para descoberta.
3. Usar `search_replays` quando busca textual direta for adequada.
4. Usar `list_replays` somente para navegar pelo catálogo.
5. Abrir candidatos relevantes com `get_replay`.
6. Avaliar a aplicabilidade ao contexto atual; nunca aplicar um Replay cegamente.

Sem conhecimento relevante, a investigação continua normalmente. Não crie Replay antes de existir aprendizado real. Se o MCP estiver indisponível, informe a limitação, continue o desenvolvimento local e não invente conteúdo supostamente vindo do No8do.

## Durante a implementação

Código, testes, experimentação e debugging continuam no ambiente local. Produção é memória canônica, não sandbox. Não registre tentativas descartadas, não atualize Replay durante investigação inconclusiva e valide conhecimento encontrado contra o contexto atual: um Replay antigo pode estar incompleto ou não se aplicar integralmente.

## Após a validação

Após evidência suficiente, pergunte: “Esta tarefa produziu conhecimento técnico reutilizável?” Bons candidatos incluem causa não óbvia de bug, troubleshooting, padrão arquitetural ou de segurança, integração, procedimento, configuração importante, snippet mínimo reutilizável, decisão justificada, checklist ou técnica de debugging que poupe trabalho futuro.

Antes de criar: execute `find_reusable_knowledge`, pesquise equivalentes, abra candidatos relevantes e decida entre atualizar ou criar. Se houver equivalente, atualize apenas com melhoria real; se não houver equivalente apropriado, crie novo Replay. Este protocolo é obrigatório para conhecimento novo, mas não bloqueia tecnicamente `create_replay`.

### Status

- `DRAFT`: conhecimento plausível sem evidência suficiente.
- `VALIDATED`: implementação confirmada por testes ou evidências suficientes no contexto registrado.
- `DEPRECATED`: conhecimento preservado, mas não recomendado como solução atual e não privilegiado na descoberta.

Nunca marque automaticamente como `VALIDATED` apenas porque a solução foi escrita.

## ReplayUsage e evidência

Registre `ReplayUsage` somente quando o Replay influenciar materialmente a execução.

- `SUCCESS`: conhecimento reutilizado, aplicado e com resultado esperado confirmado.
- `FAILURE`: Replay realmente aplicado ou tentado, mas a abordagem não funcionou no contexto.
- `UNKNOWN`: houve uso real, porém o resultado não pôde ser determinado com confiança.

Não registre uso por busca, leitura, citação, aumento de métricas, migração/população ou teste artificial sem finalidade específica.

## O que não vira Replay

Não registre rename, texto trivial, CSS puramente cosmético, CRUD comum, código específico sem valor fora da tarefa, tentativa descartada, logs temporários, dados artificiais, conhecimento já coberto, cópia de arquivo inteiro ou do repositório, nem segredo, token, senha, PAT, credencial ou informação sensível desnecessária.

Quando código fizer parte do aprendizado, inclua apenas o trecho mínimo reutilizável, com explicação e contexto; remova segredos e dados específicos.

## Produção e desenvolvimento local

`No8do` contém conhecimento real, persistente e evidências reais de uso. `No8do Local` atende desenvolvimento do Replays/MCP, testes de schema, autenticação e fluxos experimentais. Nunca use produção para dados falsos, testes destrutivos, criação artificial em massa ou manipulação apenas para exercitar uma tool.

## Ferramentas MCP

- `list_replays`: catálogo completo; não é busca textual.
- `search_replays`: busca textual.
- `find_reusable_knowledge`: descoberta por relevância e similaridade; preferencial antes de criar conhecimento técnico.
- `get_replay`: leitura completa.
- `create_replay`: criação após descoberta e revisão.
- `update_replay`: evolução de conhecimento existente.
- `register_replay_usage`: evidência de reutilização real.

As tools podem receber `workspaceId` explícito; quando omitido, o MCP pode usar `NO8DO_WORKSPACE_ID`. O valor explícito tem prioridade. Nunca hardcode IDs, tokens, PATs, URLs secretas ou configurações sensíveis.

## Relatório do agente

Quando Replays participar materialmente de uma tarefa, mencione de forma breve se consultou Replays, quais conhecimentos foram usados, se houve ReplayUsage, criação ou atualização e o status correspondente, ou eventual indisponibilidade. Não transforme todo relatório em relatório de Replays.

O `AGENTS.md` mantém somente o direcionamento curto para economizar contexto. Carregue este protocolo completo apenas quando a tarefa se enquadrar nos gatilhos técnicos definidos.
