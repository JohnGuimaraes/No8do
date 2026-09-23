import { createHash } from "node:crypto";

/** Used only for session correlation/idempotency; never an authentication or authorization value. */
export function fingerprintTransportSession(sessionId: string): string {
  return createHash("sha256").update(sessionId, "utf8").digest("hex");
}
