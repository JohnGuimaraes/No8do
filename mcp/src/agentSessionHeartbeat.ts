import { No8doApiError, No8doClient } from "./no8doClient.js";

export const AGENT_SESSION_HEARTBEAT_INTERVAL_MS = 60_000;

export type IntervalScheduler = (callback: () => void, intervalMs: number) => () => void;

const scheduleInterval: IntervalScheduler = (callback, intervalMs) => {
  const timer = setInterval(callback, intervalMs);
  timer.unref();
  return () => clearInterval(timer);
};

function defaultErrorLogger(error: unknown, operation: "heartbeat" | "disconnect"): void {
  const name = error instanceof Error ? error.name : "UnknownError";
  const message = error instanceof No8doApiError
    ? `${error.message} (HTTP ${error.status})`
    : error instanceof Error ? error.message : String(error);
  const safeMessage = message.replace(/Bearer\s+\S+/gi, "Bearer [REDACTED]")
    .replace(/\bPAT_[A-Za-z0-9._-]+\b/g, "[REDACTED]");
  console.error(`No8do AgentSession ${operation} failed`, { name, message: safeMessage });
}

/** Owns one non-blocking periodic heartbeat and its transport-scoped cleanup. */
export class AgentSessionHeartbeat {
  private stopTimer?: () => void;
  private inFlight = false;
  private closePromise?: Promise<void>;

  constructor(
    private readonly client: No8doClient,
    private readonly sessionId: string,
    private readonly schedule: IntervalScheduler = scheduleInterval,
    private readonly logError: (error: unknown, operation: "heartbeat" | "disconnect") => void = defaultErrorLogger
  ) {}

  start(): void {
    if (this.stopTimer || this.closePromise) return;
    this.stopTimer = this.schedule(() => { void this.sendHeartbeat(); }, AGENT_SESSION_HEARTBEAT_INTERVAL_MS);
  }

  stop(): void {
    const stopTimer = this.stopTimer;
    this.stopTimer = undefined;
    stopTimer?.();
  }

  close(): Promise<void> {
    if (this.closePromise) return this.closePromise;
    this.stop();
    this.closePromise = this.client.disconnectAgentSession(this.sessionId)
      .then(() => undefined)
      .catch(error => { this.logError(error, "disconnect"); });
    return this.closePromise;
  }

  private async sendHeartbeat(): Promise<void> {
    if (this.closePromise || this.inFlight) return;
    this.inFlight = true;
    try {
      await this.client.heartbeatAgentSession(this.sessionId);
    } catch (error) {
      this.logError(error, "heartbeat");
    } finally {
      this.inFlight = false;
    }
  }
}
