# Integration Core — B.1 + B.2 + B.3

Pacote TypeScript/ESM provider-neutral. Requer Web Crypto, fetch, AbortController e AbortSignal.any/timeout (validado em Node 24).

B.1 oferece PKCE S256, bootstrap/exchange REST e persistência abstrata. B.2 conecta ao Remote MCP real com Client e StreamableHTTPClientTransport do SDK MCP. Authorization CONNECTED é histórico do fluxo; Runtime DISCONNECTED continua válido.

Forneça origin da API, verificationOrigin e, para runtime, mcpOrigin explicitamente confiáveis. mcpOrigin é opcional para preservar B.1; connectRuntime sem configuração falha com MCP_ORIGIN_REQUIRED. HTTPS obrigatório fora de localhost/127.0.0.1/::1; sem userinfo, path, query ou fragment. A barra final é normalizada e /mcp construído internamente. Bootstrap nunca escolhe o destino MCP.

CredentialStore usa origin da API normalizada + installationId persistido. Runtime carrega a IntegrationCredential internamente, valida seu formato e a envia apenas como Authorization Bearer ao MCP confiável. Ausência gera AUTHORIZATION_REQUIRED sem request. Não há PAT, cookie, AgentCredential, OAuth fallback ou reautorização automática. Fetch privado impõe redirect:error e credentials:omit em POST, GET/SSE e DELETE. Não altera global fetch.

API B.1: startAuthorization, cancelAuthorization, getAuthorizationState, waitForAuthorization, getInstallationId, hasStoredAuthorization, forgetLocalAuthorization. Stores abstratos devem prover persistência segura/atômica; saveIfAbsent retorna o UUIDv4 vencedor. Cancelar autorização não equivale a rollback de um save não cooperativo; exclusividade é preservada até seu término.

API B.2: connectRuntime, getRuntimeState, getNegotiatedProtocol, closeRuntime. Não retorna Client, transport, headers, session ID, segredo ou raw response; não oferece callTool genérico.

Runtime: DISCONNECTED → CONNECTING → NEGOTIATING → CONNECTED; falhas geram FAILED; fechamento passa por CLOSING → DISCONNECTED. Initialize usa no8do-integration-core v0.1.0, capabilities vazias e nenhuma autoridade escolhida pelo cliente. Após initialize, somente get_agent_protocol é chamado: aceita Agent Protocol v2, no8do-integration v1 e subcontrato Operational Context v1. Metadata é allowlisted e congelada; incompatibilidade ou resposta malformada fecha o transporte sem CONNECTED. O custom capabilities não é necessário: a extensão vem no protocolo canônico.

Connect concorrente compartilha uma tentativa; close cancela a tentativa e é idempotente. Tentativas antigas não podem publicar resultados tardios. Sem retry/reconnect automático (inclusive SSE maxRetries:0). Startup tem limite de 30s e HTTP aguarda headers no máximo 15s; um SSE estabelecido permanece ativo. 401/403/409 geram RUNTIME_AUTHENTICATION_FAILED / RUNTIME_AUTHORIZATION_FAILED / RUNTIME_CONFLICT sem bodies ou causes externos.

close runtime ≠ forget ≠ revoke. Close fecha Client/SSE, tenta MCP DELETE com timeout e limpa referências locais; não apaga o store nem revoga autorização. Falha remota no DELETE é best-effort: não impede DISCONNECTED local. AgentSession e seu heartbeat/lifecycle pertencem ao Remote MCP; Core não chama REST de sessão.

B.3 observa a sessão e transporta sinais operacionais explicitamente fornecidos pelo caller. ContextCollector/adapter e lifecycle de chat do host permanecem fora do Core; B.4 (Replays) permanece pendente.

Logger recebe somente estado/código allowlisted; exceptions externas não são propagadas. Segredos ficam em memória e no store do adapter; liberar referências não garante zeroização em JavaScript. Sem secure store específico de SO.

Validação: npm run typecheck; npm test; npm run build. Testes de B.1 e fixture MCP local via SDK, com segredo artificial, sem internet/backend de produção/banco real.

## B.3 — Session awareness e Operational Context

Agent é persistente; AgentSession representa a sessão de runtime. Uma conexão MCP efetiva corresponde a uma AgentSession criada pelo Remote MCP durante initialize. O Core não escolhe Agent, Workspace ou sessão e não cria sessão REST.

Novas APIs: getAgentSessionContext(), getOperationalContext(), replaceOperationalContext({ expectedVersion, repository, branch, workingDirectory, references }). Exigem runtime CONNECTED, sem autorização/conexão automática. Nenhuma API MCP genérica, Client/Transport bruto ou setter de authority é exposto.

getAgentSessionContext chama somente get_agent_context. Retorna DTO profundamente congelado com identidade observada de AgentSession/Workspace, runtimeMode, capabilities/policies e presence/timestamps. Este sessionId não é o MCP transport session ID e não pode selecionar autoridade. Sessão DISCONNECTED ou disconnectedAt preenchido gera AGENT_SESSION_DISCONNECTED e invalida runtime; revoked também falha fechado. Presence é server-managed: sem polling ou heartbeat do Core.

Operational Context usa custom MCP requests no8do/operational-context/get e no8do/operational-context/update, com schemas explícitos Zod (dependência direta). GET retorna exists:false ou exists:true com snapshot. Replace envia exatamente os cinco campos definidos; unknown fields, authority, resolution, chat/código/logs e raw Git URL são rejeitados. Campos nullable devem ser enviados explicitamente como null quando desconhecidos.

expectedVersion é obrigatório: null espera ausência; inteiro seguro >=0 espera aquela versão, incluindo 0. OPERATIONAL_CONTEXT_CONFLICT não altera o snapshot nem dispara overwrite/retry/reconexão. Camada superior deve GET, reconciliar e enviar novo UPDATE explícito. HTTP409 sem discriminante conhecido permanece RUNTIME_CONFLICT; JSON-RPC com código canônico identifica conflito operacional. HTTP e JSON-RPC session revoked/disconnected são tratados por allowlist, sem bodies/data/exception text públicos.

Repository exige identidade estruturada GIT/github/github.com, namespace<=512 e name<=255; segmentos seguem os limites canônicos do backend. Branch<=255 sem controles. WorkingDirectory<=1024 é relativo ao repositório: barras convertidas para /; absolute, drive, . e .. rejeitados. Null é válido. References<=20, kinds ISSUE/TICKET/TASK/WORK_ITEM, provider<=64 e key<=128; provider normalizado, duplicates/URLs e padrões conhecidos de credenciais rejeitados. Host não é descoberto e nenhum Git/cwd/branch/chat é lido automaticamente. Backend permanece autoridade final.

Operational Context é sinal não confiável, separado de Resolution, Assignment e Permission. Resolution project/workItem é backend-derived e retornada somente read-only; associação de Project não concede acesso.

Requests pertencem à tentativa atual: close/falha invalida ownership e aborta pendências; respostas antigas não são entregues à nova conexão. A identidade observada é guardada apenas na tentativa para rejeitar troca de sessão/workspace em respostas. Nenhum snapshot/presence é cacheado entre conexões; reconnect consulta nova sessão.

close runtime != forget != revoke. Nenhuma chamada Core a REST de AgentSession, heartbeat, disconnect, provider GitHub ou Replays.
