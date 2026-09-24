# Protocolo de agentes No8do

`No8doAgentProtocol` descreve de forma estruturada e legível por máquinas como agentes interoperam com o No8do: sistema, propósito, versão explícita (`no8do-agent-protocol`, versão `1`), Replay Guidance, Capability Manifest e Policy Manifest. O contrato é canônico, imutável por resposta, determinístico e independente de agente, provider, frontend ou transporte.

Replay Guidance expõe regras comportamentais como campos booleanos — pesquisar antes de trabalho não trivial/criação, preferir conhecimento existente, exigir evidência para `VALIDATED`, registrar uso somente quando materialmente aplicado, evitar conteúdo trivial/duplicado/tentativas descartadas e nunca guardar segredos ou credenciais. O resumo textual é auxiliar; os campos estruturados são a fonte canônica.

O Capability Manifest lista somente operações que o agente consegue invocar agora pelo Agent Gateway. Infraestrutura interna, roadmap ou serviço sem operação HTTP correspondente não constitui capability publicada. As capabilities Replay atuais têm semântica compatível com suas rotas e ferramentas MCP; a ordenação por ID é estável. Busca lexical/determinística não é anunciada como busca semântica.

Semantic Retrieval 5E e Retrieval→Context 5F continuam disponíveis como infraestrutura interna, mas `SEMANTIC_DUPLICATE_SEARCH`, `HYBRID_RETRIEVAL`, `CONTEXT_PACKAGE_ASSEMBLY` e `CONTEXT_RENDERING` não são publicadas até existir operação externa coerente. Runtime RAG permanece planejado; a distinção é `INTERNAL RETRIEVAL INFRASTRUCTURE ≠ EXPOSED AGENT CAPABILITY`.

O Policy Manifest separa orientação de policy potencialmente aplicável pelo servidor. Cada policy possui ID, descrição e enforcement explícito. Isolamento por workspace é `ENFORCED`, pois as operações atuais de Replay verificam membership/escopo no backend; regras sobre segredos, credenciais, duplicatas semânticas, evidência para `VALIDATED` e uso material são `ADVISORY`, pois ainda não são garantidas em todas as gravações.

`No8doAgentProtocolProvider.current()` fornece a versão canônica construída no código, sem banco, configuração dinâmica ou dependência MCP. O endpoint autenticado serializa os records para o transporte MCP, sem parser nem JSON/YAML manual no adapter. MCP é transporte, não parte do domínio. `AGENTS.md` continua ativo no projeto atual; este protocolo prepara um mecanismo canônico autodescritivo para consumidores futuros, mas não o remove nem declara sua remoção.

A exposição do protocolo pelo bootstrap e pela operação estruturada do MCP está descrita em [`agent-protocol-mcp-bootstrap.md`](agent-protocol-mcp-bootstrap.md).

A 5H.1 definiu o contrato canônico; a 5H.2 o expõe por bootstrap e discovery MCP. A 5H.8C.2C alinha capabilities às operações realmente invocáveis e restringe `FULL` ao manifesto suportado pelo Gateway. Sessões, enforcement das policies advisory, frontend, WebSocket/SSE, LLM, modos específicos de provider e Runtime RAG permanecem fora deste escopo.
