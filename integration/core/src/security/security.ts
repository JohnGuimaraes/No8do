import { CoreError } from "../errors/CoreError.js";
import type { CryptoPort, SafeLogger, SafeLogEntry } from "../ports.js";
export const systemCrypto: CryptoPort = {
  randomBytes: length => crypto.getRandomValues(new Uint8Array(length)),
  sha256: async bytes => new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)),
  randomUUID: () => crypto.randomUUID()
};
export function base64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
export async function generatePkce(port: CryptoPort): Promise<{ verifier: string; challenge: string }> {
  const verifier = base64url(port.randomBytes(32));
  const challenge = base64url(await port.sha256(new TextEncoder().encode(verifier)));
  if (!/^[A-Za-z0-9_-]{43}$/.test(verifier) || !/^[A-Za-z0-9_-]{43}$/.test(challenge)) throw new CoreError("INVALID_INPUT");
  return { verifier, challenge };
}
export function trustedOrigin(input: string): string {
  try {
    const url = new URL(input);
    const local = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
    if (url.username || url.password || url.search || url.hash || url.pathname !== "/" ||
      !(url.protocol === "https:" || (url.protocol === "http:" && local))) throw new Error();
    return url.origin;
  } catch { throw new CoreError("INVALID_ORIGIN"); }
}
export function safeLog(logger: SafeLogger, entry: SafeLogEntry): void {
  try { logger.log(entry); } catch { /* Logging cannot change authorization. */ }
}
