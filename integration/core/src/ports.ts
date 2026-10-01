export interface HttpRequest { url: string; method: "POST"; body: string; signal: AbortSignal; redirect: "error"; credentials: "omit"; }
export interface HttpResponse { status: number; body: unknown; retryAfter?: string; redirected?: boolean; }
/** Must reject redirects, omit cookies and never log requests or arbitrary errors. */
export interface HttpTransport { send(request: HttpRequest): Promise<HttpResponse>; }
export interface Clock { now(): number; }
export interface Scheduler { schedule(callback: () => void, delayMs: number): () => void; }
export interface SafeLogEntry { operation: "bootstrap" | "exchange" | "authorization" | "runtime"; state?: string; status?: number; code?: string; }
export interface SafeLogger { log(entry: SafeLogEntry): void; }
export interface CredentialKey { readonly trustedOrigin: string; readonly installationId: string; }
/** Adapter owns secure atomic persistence. Cancellation is not deletion. */
export interface CredentialStore {
  save(key: CredentialKey, credential: string, signal: AbortSignal): Promise<void>;
  load(key: CredentialKey): Promise<string | null>;
  delete(key: CredentialKey): Promise<void>;
}
export interface InstallationIdentityStore {
  load(): Promise<string | null>;
  /** Atomic first-writer-wins, returning persisted winner across processes. */
  saveIfAbsent(candidate: string): Promise<string>;
}
export interface CryptoPort { randomBytes(length: number): Uint8Array; sha256(bytes: Uint8Array): Promise<Uint8Array>; randomUUID(): string; }
