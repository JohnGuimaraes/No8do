import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { CoreError } from "../errors/CoreError.js";
import type { CredentialKey, CredentialStore, SafeLogger } from "../ports.js";
import { safeLog } from "../security/security.js";
import { negotiate, type NegotiatedProtocol } from "./protocol.js";
export type RuntimeConnectionStatus = "DISCONNECTED" | "CONNECTING" | "NEGOTIATING" | "CONNECTED" | "FAILED" | "CLOSING";
export interface RuntimeConnectionState {
  readonly state: RuntimeConnectionStatus; readonly errorCode?: import("../errors/CoreError.js").ErrorCode;
}
type Attempt = { abort: AbortController; client?: Client; transport?: StreamableHTTPClientTransport;
  dispose?: Promise<void>; failure?: CoreError };
function statusError(status: number): CoreError {
  return new CoreError(status === 401 ? "RUNTIME_AUTHENTICATION_FAILED" :
    status === 403 ? "RUNTIME_AUTHORIZATION_FAILED" : status === 409 ? "RUNTIME_CONFLICT" : "TRANSPORT_ERROR");
}
/** Internal owner; no generic MCP operation or credential reaches the Core facade. */
export class RemoteMcpClient {
  #state: RuntimeConnectionState = Object.freeze({ state: "DISCONNECTED" });
  #protocol: NegotiatedProtocol | null = null;
  #attempt?: Attempt; #connecting?: Promise<NegotiatedProtocol>; #closing?: Promise<void>; #cleanup?: Promise<void>;
  constructor(private readonly origin: string | undefined, private readonly store: CredentialStore,
    private readonly key: () => Promise<CredentialKey>, private readonly logger: SafeLogger) {}
  getState = (): RuntimeConnectionState => this.#state;
  getProtocol = (): NegotiatedProtocol | null => this.#protocol;
  #set(state: RuntimeConnectionStatus, error?: CoreError) {
    this.#state = Object.freeze({ state, ...(error ? { errorCode: error.code } : {}) });
    safeLog(this.logger, { operation: "runtime", state, ...(error ? { code: error.code } : {}) });
  }
  connect = (): Promise<NegotiatedProtocol> => {
    if (this.#closing || this.#cleanup) return Promise.reject(new CoreError("RUNTIME_BUSY"));
    if (this.#connecting) return this.#connecting;
    if (this.#protocol && this.#state.state === "CONNECTED") return Promise.resolve(this.#protocol);
    if (!this.origin) return Promise.reject(new CoreError("MCP_ORIGIN_REQUIRED"));
    const a: Attempt = { abort: new AbortController() };
    this.#attempt = a;
    const operation = Promise.resolve().then(() => this.#connect(a));
    this.#connecting = operation; this.#set("CONNECTING");
    void operation.finally(() => { if (this.#connecting === operation) this.#connecting = undefined; }).catch(() => {});
    return operation;
  };
  async #connect(a: Attempt): Promise<NegotiatedProtocol> {
    const deadline = setTimeout(() => {
      a.failure ??= new CoreError("RUNTIME_CONNECTION_TIMEOUT"); a.abort.abort();
    }, 30_000);
    const current = () => {
      if (a.failure) throw a.failure;
      if (this.#attempt !== a || a.abort.signal.aborted) throw new CoreError("RUNTIME_CANCELLED");
    };
    try {
      const credential = await this.#untilAbort(a, (async () => {
        try { return await this.store.load(await this.key()); }
        catch { throw new CoreError("CREDENTIAL_STORE_FAILED"); }
      })());
      current();
      if (credential === null) throw new CoreError("AUTHORIZATION_REQUIRED");
      if (!/^no8do_int_[A-Za-z0-9._-]+$/.test(credential)) throw new CoreError("RUNTIME_AUTHENTICATION_FAILED");
      const endpoint = new URL("/mcp", this.origin);
      const guardedFetch: typeof fetch = async (input, init) => {
        if (String(input) !== endpoint.href) throw new CoreError("TRANSPORT_ERROR");
        const closing = init?.method === "DELETE";
        const headers = new Headers(init?.headers);
        headers.set("Authorization", "Bearer " + credential);
        headers.delete("Cookie"); headers.delete("X-No8do-Agent-Credential");
        const headerTimeout = new AbortController();
        const headerTimer = setTimeout(() => headerTimeout.abort(), 15_000);
        const signals = [headerTimeout.signal];
        if (!closing && init?.signal) signals.push(init.signal);
        if (!closing) signals.push(a.abort.signal);
        try {
          const response = await fetch(endpoint, { ...init, headers, redirect: "error", credentials: "omit",
            signal: AbortSignal.any(signals) });
          clearTimeout(headerTimer);
          if (response.redirected) { await response.body?.cancel(); throw new CoreError("TRANSPORT_ERROR"); }
          if ([401, 403, 409].includes(response.status)) {
            await response.body?.cancel(); throw statusError(response.status);
          }
          return response;
        } catch (error) {
          const safe = error instanceof CoreError ? error : new CoreError("TRANSPORT_ERROR");
          if (!closing && !a.abort.signal.aborted) a.failure ??= safe;
          throw safe;
        } finally { clearTimeout(headerTimer); }
      };
      const transport = new StreamableHTTPClientTransport(endpoint, {
        fetch: guardedFetch, requestInit: { redirect: "error", credentials: "omit" },
        reconnectionOptions: { maxRetries: 0, initialReconnectionDelay: 1000,
          maxReconnectionDelay: 1000, reconnectionDelayGrowFactor: 1 }
      });
      const client = new Client({ name: "no8do-integration-core", version: "0.1.0" }, { capabilities: {} });
      a.transport = transport; a.client = client;
      const failed = () => {
        if (this.#attempt !== a || a.abort.signal.aborted) return;
        a.failure ??= new CoreError("TRANSPORT_ERROR"); a.abort.abort();
        if (this.#state.state === "CONNECTED") {
          this.#protocol = null;
          this.#cleanup = this.#dispose(a).finally(() => {
            if (this.#attempt === a) this.#attempt = undefined;
            this.#cleanup = undefined;
          });
          this.#set("FAILED", a.failure);
        }
      };
      client.onerror = failed; client.onclose = failed;
      await this.#untilAbort(a, client.connect(transport));
      current(); this.#set("NEGOTIATING");
      const result = await this.#untilAbort(a, client.callTool({ name: "get_agent_protocol", arguments: {} }));
      current();
      if (result.isError || !result.structuredContent) throw new CoreError("INVALID_RESPONSE");
      const protocol = negotiate(result.structuredContent);
      current(); this.#protocol = protocol; this.#set("CONNECTED");
      return protocol;
    } catch (error) {
      const safe = a.failure ?? (error instanceof CoreError ? error : new CoreError("TRANSPORT_ERROR"));
      a.abort.abort(); await this.#dispose(a);
      if (this.#attempt === a) {
        this.#attempt = undefined; this.#protocol = null; this.#set("FAILED", safe);
      }
      throw safe;
    } finally { clearTimeout(deadline); }
  }
  #untilAbort<T>(a: Attempt, work: Promise<T>): Promise<T> {
    return new Promise((resolve, reject) => {
      const cancelled = () => reject(a.failure ?? new CoreError("RUNTIME_CANCELLED"));
      if (a.abort.signal.aborted) { void work.catch(() => {}); cancelled(); return; }
      a.abort.signal.addEventListener("abort", cancelled, { once: true });
      work.then(resolve, reject).finally(() => a.abort.signal.removeEventListener("abort", cancelled)).catch(() => {});
    });
  }
  #dispose(a: Attempt): Promise<void> {
    return a.dispose ??= (async () => {
      try { await a.client?.close(); } catch { /* safe best-effort local close */ }
      // MCP DELETE performs server lifecycle; never call REST AgentSession/disconnect.
      try { if (a.transport?.sessionId) await a.transport.terminateSession(); } catch { /* no external exceptions */ }
      a.client = undefined; a.transport = undefined;
    })();
  }
  close = (): Promise<void> => {
    if (this.#closing) return this.#closing;
    const a = this.#attempt;
    this.#attempt = undefined; this.#protocol = null;
    if (!a) { this.#set("DISCONNECTED"); return Promise.resolve(); }
    a.abort.abort();
    const operation = Promise.resolve().then(async () => {
      await this.#dispose(a); await this.#connecting?.catch(() => {}); this.#set("DISCONNECTED");
    });
    this.#closing = operation; this.#set("CLOSING");
    void operation.finally(() => { if (this.#closing === operation) this.#closing = undefined; }).catch(() => {});
    return operation;
  };
}
