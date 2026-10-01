# Integration Core — B.1

Pacote provider-neutral TypeScript/ESM. Requer runtime com Web Crypto, fetch e AbortController (validado em Node 24). Sem dependências de runtime.

Esta fase oferece somente PKCE S256, bootstrap, autorização e persistência abstrata. Não conecta MCP nem registra AgentSession. Runtime/protocol são B.2; presença/contexto B.3; retrieval B.4.

## Uso e ports

Forneça origin da API e verificationOrigin do frontend explicitamente confiáveis, CredentialStore e InstallationIdentityStore a createIntegrationCore. HTTP, relógio, scheduler, logger e crypto podem ser injetados.

API: startAuthorization, cancelAuthorization, getAuthorizationState, waitForAuthorization, getInstallationId, hasStoredAuthorization e forgetLocalAuthorization. Não retorna credencial, deviceCode ou verifier. O adapter recebe apenas prompt seguro.

InstallationIdentityStore.saveIfAbsent deve ser atômico entre processos e retornar o UUIDv4 vencedor. Escopo do store: uma instalação, não chat/projeto/Agent. CredentialStore usa origin normalizada + installationId; deve fornecer persistência segura/atômica e respeitar abort quando possível. Nenhum store concreto ou fallback plaintext é incluído.

## Fluxo

IDLE → STARTING → AWAITING_USER ⇄ EXCHANGING → CONNECTED.
Terminais alternativos: DENIED, EXPIRED, FAILED e CANCELLED.
CONNECTED somente após save concluído; não implica sessão/runtime conectado.

Primeiro poll aguarda interval. Polling serial, interval nunca reduzido no mesmo fluxo, slow_down aumenta até 60s, Retry-After pode estender a espera até o deadline. Até três retries de falhas transitórias consecutivas; deadline máximo do contrato 600s. Requests têm timeout 15s, startup 30s. Clock/Scheduler injetados devem ser consistentes.

Cancelamento aborta requests/timers e ignora resultados tardios. Não executa deny/delete/revoke. Se cancelar durante save, uma implementação não cooperativa pode já ter persistido a credencial: cancelamento não é rollback nem forget. No mesmo Core, novo start/forget fica bloqueado até o save pendente terminar; forget também reserva exclusividade antes de qualquer await. Não iniciar outro processo sobre o mesmo store sem coordenação do adapter.

Exchange emite credencial uma vez. Save falho resulta em CREDENTIAL_PERSISTENCE_FAILED, sem CONNECTED/fallback. CONSUMED sem credencial resulta em EXCHANGE_ALREADY_CONSUMED. Recuperação exige revogação humana adequada e nova autorização; repetir exchange não recupera segredo.

disconnect ≠ forget ≠ revoke. Forget só chama delete local e não muda autorização no servidor; estado CONNECTED é resultado histórico do fluxo, consulte hasStoredAuthorization para presença local. Disconnect/revoke estão fora de B.1.

## Segurança e testes

HTTPS exceto loopback, sem userinfo/path/query no origin, redirects rejeitados, cookies omitidos, sem PAT. Transporte injetado é uma porta confiável e deve honrar essas restrições e aborts. Logger recebe somente eventos construídos por allowlist, nunca payloads/causes externos. Segredos vivem em memória e no store do adapter; liberação de referências não garante zeroização da memória JS.

npm install; npm run typecheck; npm test; npm run build.
Testes usam relógio/scheduler e stores fake, sem backend, banco ou armazenamento de SO.
