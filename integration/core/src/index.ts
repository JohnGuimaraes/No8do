import { RemoteMcpClient } from "./runtime/RemoteMcpClient.js";
export type { RuntimeConnectionState, RuntimeConnectionStatus } from "./runtime/RemoteMcpClient.js";
export type { NegotiatedProtocol } from "./runtime/protocol.js";
import { BootstrapClient } from "./bootstrap/BootstrapClient.js";
import { AuthorizationFlow } from "./authorization/AuthorizationFlow.js";
import { InstallationIdentity } from "./installation/InstallationIdentity.js";
import { FetchTransport } from "./transport/FetchTransport.js";
import { systemClock, systemScheduler } from "./scheduling/scheduling.js";
import { systemCrypto, trustedOrigin } from "./security/security.js";
import { CoreError } from "./errors/CoreError.js";
import type { HttpTransport, Clock, Scheduler, SafeLogger, CredentialStore, InstallationIdentityStore, CryptoPort } from "./ports.js";
export type * from "./ports.js";
export type { AuthorizationInput, HostType } from "./bootstrap/BootstrapClient.js";
export type { AuthorizationState, AuthorizationStatus, AuthorizationPrompt } from "./authorization/AuthorizationFlow.js";
export { CoreError };
export interface CoreOptions {
  origin: string; verificationOrigin: string; mcpOrigin?: string; credentialStore: CredentialStore; installationStore: InstallationIdentityStore;
  http?: HttpTransport; clock?: Clock; scheduler?: Scheduler; logger?: SafeLogger; crypto?: CryptoPort;
}
export function createIntegrationCore(options: CoreOptions) {
  const origin = trustedOrigin(options.origin);
  const verificationOrigin = trustedOrigin(options.verificationOrigin);
  const clock = options.clock ?? systemClock;
  const scheduler = options.scheduler ?? systemScheduler;
  const crypto = options.crypto ?? systemCrypto;
  const logger = options.logger ?? { log() {} };
  const identity = new InstallationIdentity(options.installationStore, crypto);
  const client = new BootstrapClient(origin, verificationOrigin, options.http ?? new FetchTransport(), scheduler, logger);
  const flow = new AuthorizationFlow(client, identity, origin, crypto, clock, scheduler, options.credentialStore, logger);
  const key = async () => Object.freeze({ trustedOrigin: origin, installationId: await identity.get() });
  const runtime = new RemoteMcpClient(options.mcpOrigin === undefined ? undefined : trustedOrigin(options.mcpOrigin), options.credentialStore, key, logger);
  let forgetting = false;
  return {
    connectRuntime: runtime.connect,
    getRuntimeState: runtime.getState,
    getNegotiatedProtocol: runtime.getProtocol,
    closeRuntime: runtime.close,
    startAuthorization(input: import("./bootstrap/BootstrapClient.js").AuthorizationInput) {
      if (forgetting) return Promise.reject(new CoreError("FLOW_BUSY"));
      return flow.start(input);
    },
    cancelAuthorization: flow.cancel.bind(flow),
    getAuthorizationState: flow.getState.bind(flow),
    waitForAuthorization: flow.waitForCompletion.bind(flow),
    getInstallationId: () => identity.get(),
    async hasStoredAuthorization(): Promise<boolean> {
      try { return await options.credentialStore.load(await key()) !== null; }
      catch { throw new CoreError("CREDENTIAL_STORE_FAILED"); }
    },
    async forgetLocalAuthorization(): Promise<void> {
      if (forgetting || flow.isBusy()) throw new CoreError("FLOW_BUSY");
      forgetting = true;
      try { await options.credentialStore.delete(await key()); }
      catch { throw new CoreError("CREDENTIAL_STORE_FAILED"); }
      finally { forgetting = false; }
    }
  };
}
