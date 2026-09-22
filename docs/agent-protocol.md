# Protocolo de agentes No8do

`No8doAgentProtocol` descreve de forma estruturada e legível por máquinas como agentes interoperam com o No8do: sistema, propósito, versão explícita (`no8do-agent-protocol`, versão `1`), Replay Guidance, Capability Manifest e Policy Manifest. O contrato é canônico, imutável por resposta, determinístico e independente de agente, provider, frontend ou transporte.

Replay Guidance expõe regras comportamentais como campos booleanos — pesquisar antes de trabalho não trivial/criação, preferir conhecimento existente, exigir evidência para `VALIDATED`, registrar uso somente quando materialmente aplicado, evitar conteúdo trivial/duplicado/tentativas descartadas e nunca guardar segredos ou credenciais. O resumo textual é auxiliar; os campos estruturados são a fonte canônica.

O Capability Manifest lista somente capacidades presentes no MCP Replays ou suportadas por serviços/componentes atuais do backend. Não declara uma capacidade end-to-end de `CONTEXT_RETRIEVAL`: essa integração ainda não existe. A lista funcional não é acoplada a nomes de tools MCP, e a ordenação por ID é estável.

O Policy Manifest separa orientação de policy potencialmente aplicável pelo servidor. Cada policy possui ID, descrição e enforcement explícito. Isolamento por workspace é `ENFORCED`, pois as operações atuais de Replay verificam membership/escopo no backend; regras sobre segredos, credenciais, duplicatas semânticas, evidência para `VALIDATED` e uso material são `ADVISORY`, pois ainda não são garantidas em todas as gravações.

`No8doAgentProtocolProvider.current()` fornece a versão canônica construída no código, sem banco, configuração dinâmica ou dependência MCP. A serialização futura poderá usar os records normalmente; não há parser nem JSON/YAML manual. MCP poderá ser um transporte futuro para entregar o contrato, sem ser parte do domínio. `AGENTS.md` continua ativo no projeto atual; este protocolo prepara um mecanismo canônico autodescritivo para consumidores futuros, mas não o remove nem declara sua remoção.

Esta fase não implementa handshake/tool MCP, sessões, enforcement das policies, frontend, WebSocket/SSE, LLM, provider, RAG ou 5H.2.
