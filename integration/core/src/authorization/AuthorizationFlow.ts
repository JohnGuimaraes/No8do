import { BootstrapClient, record, seconds, type AuthorizationInput } from "../bootstrap/BootstrapClient.js";
import { CoreError, type ErrorCode } from "../errors/CoreError.js";
import { InstallationIdentity } from "../installation/InstallationIdentity.js";
import type { Clock, Scheduler, CryptoPort, CredentialStore, CredentialKey, SafeLogger } from "../ports.js";
import { abortable, delay } from "../scheduling/scheduling.js";
import { generatePkce, safeLog } from "../security/security.js";
export type AuthorizationState = "IDLE" | "STARTING" | "AWAITING_USER" | "EXCHANGING" | "CONNECTED" | "DENIED" | "EXPIRED" | "FAILED" | "CANCELLED";
export interface AuthorizationStatus { state: AuthorizationState; errorCode?: ErrorCode; }
export interface AuthorizationPrompt { requestId: string; userCode: string; verificationUri: string; expiresIn: number; expiresAt: number; interval: number; }
interface Run { abort: AbortController; stopDeadline: () => void; done?: Promise<AuthorizationStatus>; key?: CredentialKey; }
export class AuthorizationFlow {
  #status: AuthorizationStatus = { state: "IDLE" };
  #run?: Run;
  #pendingSave = false;
  constructor(private client: BootstrapClient, private identity: InstallationIdentity,
    private origin: string, private crypto: CryptoPort, private clock: Clock, private scheduler: Scheduler,
    private store: CredentialStore, private logger: SafeLogger) {}
  getState(): AuthorizationStatus { return { ...this.#status }; }
  isBusy(): boolean { return this.#run !== undefined || this.#pendingSave; }
  private state(state: AuthorizationState, errorCode?: ErrorCode): void {
    this.#status = errorCode ? { state, errorCode } : { state };
    safeLog(this.logger, { operation: "authorization", state, ...(errorCode ? { code: errorCode } : {}) });
  }
  cancel(): void {
    if (!this.#run || !["STARTING", "AWAITING_USER", "EXCHANGING"].includes(this.#status.state)) return;
    this.state("CANCELLED", "AUTHORIZATION_CANCELLED");
    this.#run.abort.abort();
    this.#run.stopDeadline();
  }
  async start(input: AuthorizationInput): Promise<AuthorizationPrompt> {
    if (this.isBusy()) throw new CoreError("FLOW_BUSY");
    input = { hostType: input.hostType, integrationVersion: input.integrationVersion,
      ...(input.displayLabel === undefined ? {} : { displayLabel: input.displayLabel }) };
    if (!["CODEX", "CLAUDE", "VSCODE", "IDE"].includes(input.hostType) ||
      !/^[A-Za-z0-9][A-Za-z0-9.+_-]{0,63}$/.test(input.integrationVersion) ||
      (input.displayLabel !== undefined && (input.displayLabel.length > 120 || /[\x00-\x1f\x7f-\x9f]/.test(input.displayLabel)))) throw new CoreError("INVALID_INPUT");
    const run: Run = { abort: new AbortController(), stopDeadline: () => undefined };
    this.#run = run;
    this.state("STARTING");
    run.stopDeadline = this.scheduler.schedule(() => {
      this.state("FAILED", "BOOTSTRAP_FAILED"); run.abort.abort();
    }, 30_000);
    try {
      const installationId = await abortable(this.identity.get(), run.abort.signal);
      run.key = Object.freeze({ trustedOrigin: this.origin, installationId });
      const pkce = await abortable(generatePkce(this.crypto), run.abort.signal);
      const startedAt = this.clock.now();
      const data = await this.client.start(input, installationId, pkce.challenge, run.abort.signal);
      if (run.abort.signal.aborted) throw new Error();
      const expiresAt = startedAt + data.expiresIn * 1000;
      run.stopDeadline();
      run.stopDeadline = this.scheduler.schedule(() => {
        this.state("EXPIRED", "AUTHORIZATION_EXPIRED"); run.abort.abort();
      }, Math.max(0, expiresAt - this.clock.now()));
      this.state("AWAITING_USER");
      run.done = this.poll(run, data.deviceCode, pkce.verifier, data.interval, expiresAt)
        .finally(() => { run.stopDeadline(); if (this.#run === run) this.#run = undefined; });
      return { requestId: data.requestId, userCode: data.userCode, verificationUri: data.verificationUri,
        expiresIn: data.expiresIn, expiresAt, interval: data.interval };
    } catch (error) {
      run.stopDeadline();
      if (!run.abort.signal.aborted) this.state("FAILED", error instanceof CoreError ? error.code : "BOOTSTRAP_FAILED");
      this.#run = undefined;
      throw new CoreError(this.#status.errorCode ?? "BOOTSTRAP_FAILED");
    }
  }
  async waitForCompletion(): Promise<AuthorizationStatus> {
    return this.#run?.done ? await this.#run.done : this.getState();
  }
  private async poll(run: Run, deviceCode: string, verifier: string, interval: number, expiresAt: number): Promise<AuthorizationStatus> {
    let failures = 0;
    let waitMs = interval * 1000;
    try {
      while (!run.abort.signal.aborted) {
        if (this.clock.now() + waitMs >= expiresAt) {
          await delay(this.scheduler, Math.max(0, expiresAt - this.clock.now()), run.abort.signal);
          throw new CoreError("AUTHORIZATION_EXPIRED");
        }
        await delay(this.scheduler, waitMs, run.abort.signal);
        if (this.clock.now() >= expiresAt) throw new CoreError("AUTHORIZATION_EXPIRED");
        this.state("EXCHANGING");
        let response;
        try { response = await this.client.post("exchange", { deviceCode, codeVerifier: verifier }, run.abort.signal); }
        catch (error) {
          if (run.abort.signal.aborted) throw error;
          if (!(error instanceof CoreError) || error.code !== "TRANSPORT_ERROR" || ++failures > 3) throw error;
          waitMs = Math.max(interval * 1000, Math.min(60_000, 1000 * 2 ** failures));
          this.state("AWAITING_USER"); continue;
        }
        if (run.abort.signal.aborted) throw new Error();
        if (this.clock.now() >= expiresAt) throw new CoreError("AUTHORIZATION_EXPIRED");
        if (response.status === 429) {
          interval = Math.min(60, interval + 5);
          const retry = response.retryAfter;
          let retryMs = 0;
          if (retry !== undefined) {
            retryMs = /^\d+$/.test(retry) ? Number(retry) * 1000 : Date.parse(retry) - this.clock.now();
            if (!Number.isFinite(retryMs)) throw new CoreError("INVALID_RESPONSE");
          }
          waitMs = Math.max(interval * 1000, retryMs);
          this.state("AWAITING_USER"); continue;
        }
        if (response.status >= 500 && response.status <= 599 && ++failures <= 3) {
          waitMs = Math.max(interval * 1000, Math.min(60_000, 1000 * 2 ** failures));
          this.state("AWAITING_USER"); continue;
        }
        if (response.status !== 200) throw new CoreError("EXCHANGE_FAILED");
        failures = 0;
        const data = record(response.body);
        if (data.state === "DENIED") throw new CoreError("AUTHORIZATION_DENIED");
        if (data.state === "EXPIRED") throw new CoreError("AUTHORIZATION_EXPIRED");
        if (data.state === "PENDING" || data.state === "APPROVED") {
          interval = Math.max(interval, seconds(data.interval, 60));
          waitMs = interval * 1000; this.state("AWAITING_USER"); continue;
        }
        if (data.state !== "CONSUMED") throw new CoreError("INVALID_RESPONSE");
        if (data.integrationCredential === null || data.integrationCredential === undefined) throw new CoreError("EXCHANGE_ALREADY_CONSUMED");
        if (typeof data.integrationCredential !== "string" ||
          !/^no8do_int_[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(data.integrationCredential) ||
          data.integrationCredential.length > 512) throw new CoreError("INVALID_RESPONSE");
        // Stop retaining exchange material before awaiting potentially slow persistence.
        deviceCode = ""; verifier = "";
        try {
          this.#pendingSave = true;
          let save: Promise<void>;
          try { save = this.store.save(run.key!, data.integrationCredential, run.abort.signal); }
          catch { this.#pendingSave = false; throw new CoreError("CREDENTIAL_PERSISTENCE_FAILED"); }
          const settled = save.finally(() => { this.#pendingSave = false; });
          await abortable(settled, run.abort.signal);
        } catch {
          throw new CoreError("CREDENTIAL_PERSISTENCE_FAILED");
        } finally { data.integrationCredential = undefined; }
        if (this.clock.now() >= expiresAt) throw new CoreError("AUTHORIZATION_EXPIRED");
        if (!run.abort.signal.aborted) this.state("CONNECTED");
        return this.getState();
      }
    } catch (error) {
      if (!run.abort.signal.aborted) {
        const code = error instanceof CoreError ? error.code : "EXCHANGE_FAILED";
        this.state(code === "AUTHORIZATION_DENIED" ? "DENIED" : code === "AUTHORIZATION_EXPIRED" ? "EXPIRED" : "FAILED", code);
      }
    } finally { deviceCode = ""; verifier = ""; }
    return this.getState();
  }
}
