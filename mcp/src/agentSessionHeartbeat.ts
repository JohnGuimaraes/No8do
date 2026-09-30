import { No8doApiError, No8doClient } from "./no8doClient.js";

export const AGENT_SESSION_HEARTBEAT_INTERVAL_MS = 60_000;

export type IntervalScheduler = (callback: () => void, intervalMs: number) => () => void;

const scheduleInterval: IntervalScheduler = (callback, intervalMs) => {
  const timer = setInterval(callback, intervalMs);
  timer.unref();
  return () => clearInterval(timer);
};

function defaultErrorLogger(error: unknown, operation: "heartbeat" | "disconnect"): void {
  // Never log arbitrary exception text from transports/providers carrying credentials.
  console.error(`No8do AgentSession ${operation} failed`, {
    status: error instanceof No8doApiError ? error.status : undefined,
    code: error instanceof No8doApiError ? error.code : undefined
  });
}

/** Owns one non-blocking periodic heartbeat and its transport-scoped cleanup. */
export class AgentSessionHeartbeat {
  private stopTimer?: () => void;
  private inFlight = false;
  private closePromise?: Promise<void>;
  private revoked = false;

  constructor(
    private readonly client: No8doClient,
    private readonly sessionId: string,
    private readonly schedule: IntervalScheduler = scheduleInterval,
    private readonly logError: (error: unknown, operation: "heartbeat" | "disconnect") => void = defaultErrorLogger
  ) {}

  start(): void {
    if (this.stopTimer || this.closePromise || this.revoked) return;
    this.stopTimer = this.schedule(() => { void this.sendHeartbeat(); }, AGENT_SESSION_HEARTBEAT_INTERVAL_MS);
  }

  isRevoked(): boolean { return this.revoked; }

  markRevoked(): void {
    if (this.revoked) return;
    this.revoked = true;
    this.stop();
  }

  stop(): void {
    const stopTimer = this.stopTimer;
    this.stopTimer = undefined;
    stopTimer?.();
  }

  close(): Promise<void> {
    if (this.closePromise) return this.closePromise;
    this.stop();
    if (this.revoked) return this.closePromise = Promise.resolve();
    this.closePromise = this.client.disconnectAgentSession(this.sessionId)
      .then(() => undefined)
      .catch(error => { this.logError(error, "disconnect"); });
    return this.closePromise;
  }

  private async sendHeartbeat(): Promise<void> {
    if (this.closePromise || this.inFlight || this.revoked) return;
    this.inFlight = true;
    try {
      await this.client.heartbeatAgentSession(this.sessionId);
    } catch (error) {
      if (error instanceof No8doApiError && error.code === "AGENT_SESSION_REVOKED") {
        this.markRevoked();
        return;
      }
      this.logError(error, "heartbeat");
    } finally {
      this.inFlight = false;
    }
  }
}
