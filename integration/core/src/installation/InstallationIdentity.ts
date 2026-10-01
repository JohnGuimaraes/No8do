import { CoreError } from "../errors/CoreError.js";
import type { CryptoPort, InstallationIdentityStore } from "../ports.js";
export function isUuidV4(value: unknown): value is string {
  return typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}
export class InstallationIdentity {
  #pending?: Promise<string>;
  constructor(private store: InstallationIdentityStore, private crypto: CryptoPort) {}
  get(): Promise<string> {
    return this.#pending ??= this.obtain().catch(() => { this.#pending = undefined; throw new CoreError("INSTALLATION_STORE_FAILED"); });
  }
  private async obtain(): Promise<string> {
    const loaded = await this.store.load();
    if (loaded !== null) { if (!isUuidV4(loaded)) throw new Error(); return loaded; }
    const candidate = this.crypto.randomUUID();
    if (!isUuidV4(candidate)) throw new Error();
    const winner = await this.store.saveIfAbsent(candidate);
    if (!isUuidV4(winner)) throw new Error();
    return winner;
  }
}
