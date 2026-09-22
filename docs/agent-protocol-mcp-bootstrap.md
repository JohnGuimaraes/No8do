# Bootstrap e discovery do Agent Protocol via MCP

`No8doAgentProtocolProvider.current()` continua sendo a fonte canônica do contrato. A API autenticada do backend publica esse objeto globalmente em `GET /api/agent-protocol`; o MCP apenas o obtém com o Bearer da conexão e o transporta, sem manter uma cópia independente de capabilities ou policies.

Na inicialização MCP, `AgentProtocolBootstrapRenderer` gera instruções curtas a partir do protocolo recebido. Elas identificam o No8do, orientam a busca e o reúso de Replays e indicam `get_agent_protocol` para detalhes. O SDK MCP atual oferece `instructions` no handshake e `outputSchema`/`structuredContent` para resultados de tools.

A tool sem parâmetros `get_agent_protocol` devolve o contrato completo de forma machine-readable: identidade e versão, propósito, Replay Guidance, capabilities e policies com enforcement status. O resultado estruturado é acompanhado apenas de um texto curto de compatibilidade; não substitui nem achata o contrato em Markdown.

As duas camadas têm papéis distintos: instruções reduzem o atrito no primeiro contato; discovery estruturado fornece a fonte consultável e compatível com clientes que não aplicam instruções de servidor automaticamente. Ambas derivam da mesma resposta do provider. A autenticação Bearer/PAT existente é mantida também para a leitura do protocolo; a descoberta não exige workspace e não altera autorização das tools Replay.

Esta fase não cria Plugin ou `SKILL.md`, sessões, identidade de cliente, heartbeat ou presence, runtime modes, novo enforcement de policies, eventos/realtime, LLM, nem frontend. `AGENTS.md` permanece vigente durante a transição.
