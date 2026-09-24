import type { AgentSessionHeartbeat } from "./agentSessionHeartbeat.js";

/** Closes and releases the heartbeat retained by one STDIO transport lifecycle. */
export function createStdioSessionCloseHandler(
  getHeartbeat: () => AgentSessionHeartbeat | undefined,
  clearHeartbeat: () => void
): () => Promise<void> {
  return async () => {
    const heartbeat = getHeartbeat();
    try {
      await heartbeat?.close();
    } finally {
      clearHeartbeat();
    }
  };
}
