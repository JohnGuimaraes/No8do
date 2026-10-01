import { CoreError } from "../errors/CoreError.js";
import type { HttpTransport, Scheduler, SafeLogger } from "../ports.js";
import { abortable } from "../scheduling/scheduling.js";
import { safeLog } from "../security/security.js";
export type HostType = "CODEX" | "CLAUDE" | "VSCODE" | "IDE";
export interface AuthorizationInput { hostType: HostType; integrationVersion: string; displayLabel?: string; }
export interface BootstrapData { requestId: string; deviceCode: string; userCode: string; verificationUri: string; expiresIn: number; interval: number; }
export function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new CoreError("INVALID_RESPONSE");
  return value as Record<string, unknown>;
}
export function seconds(value: unknown, max: number): number {
  if (typeof value !== "number" || !Number.isInteger(value) || value < 1 || value > max) throw new CoreError("INVALID_RESPONSE");
  return value;
}
/** Internal HTTP contract, intentionally not exported from package entrypoint. */
export class BootstrapClient {
  constructor(private origin: string, private verificationOrigin: string, private http: HttpTransport,
    private scheduler: Scheduler, private logger: SafeLogger) {}
  async post(operation: "bootstrap" | "exchange", body: object, signal: AbortSignal) {
    const requestAbort = new AbortController();
    const abort = () => requestAbort.abort();
    signal.addEventListener("abort", abort, { once: true });
    if (signal.aborted) abort();
    const stopTimeout = this.scheduler.schedule(abort, 15_000);
    try {
      let response;
      try { response = await abortable(this.http.send({
        url: this.origin + "/api/integration-authorizations/bootstrap" + (operation === "exchange" ? "/exchange" : ""),
        method: "POST", body: JSON.stringify(body), signal: requestAbort.signal,
        redirect: "error", credentials: "omit"
      }), requestAbort.signal); }
      catch { throw new CoreError("TRANSPORT_ERROR"); }
      safeLog(this.logger, { operation, status: response.status });
      if (response.redirected || (response.status >= 300 && response.status < 400)) throw new CoreError("INVALID_RESPONSE");
      return response;
    } catch (error) {
      if (error instanceof CoreError && ["TRANSPORT_ERROR", "INVALID_RESPONSE"].includes(error.code)) throw error;
      throw new CoreError("TRANSPORT_ERROR");
    } finally { stopTimeout(); signal.removeEventListener("abort", abort); }
  }
  async start(input: AuthorizationInput, installationId: string, challenge: string, signal: AbortSignal): Promise<BootstrapData> {
    const response = await this.post("bootstrap", {
      installationId, hostType: input.hostType, integrationVersion: input.integrationVersion,
      ...(input.displayLabel === undefined ? {} : { displayLabel: input.displayLabel }),
      codeChallenge: challenge, codeChallengeMethod: "S256"
    }, signal);
    if (response.status !== 201) throw new CoreError(response.status === 429 ? "RATE_LIMITED" : "BOOTSTRAP_FAILED");
    const data = record(response.body);
    if (typeof data.requestId !== "string" || !/^[0-9a-f-]{36}$/i.test(data.requestId) ||
      typeof data.deviceCode !== "string" || !/^[A-Za-z0-9_-]{43}$/.test(data.deviceCode) ||
      typeof data.userCode !== "string" || !/^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$/.test(data.userCode) ||
      typeof data.verificationUri !== "string") throw new CoreError("INVALID_RESPONSE");
    try {
      const uri = new URL(data.verificationUri);
      if (uri.origin !== this.verificationOrigin || uri.pathname !== "/connect/no8do" ||
        uri.username || uri.password || uri.search || uri.hash) throw new Error();
    } catch { throw new CoreError("INVALID_RESPONSE"); }
    return { requestId: data.requestId, deviceCode: data.deviceCode, userCode: data.userCode,
      verificationUri: data.verificationUri, expiresIn: seconds(data.expiresIn, 600), interval: seconds(data.interval, 60) };
  }
}
