import { CoreError } from "../errors/CoreError.js";
export interface NegotiatedProtocol {
  readonly protocolName: "no8do-agent-protocol"; readonly protocolVersion: 2;
  readonly systemName: "No8do"; readonly integrationExtensionVersion: 1;
  readonly operationalContext: Readonly<{ version: 1; getMethod: "no8do/operational-context/get";
    updateMethod: "no8do/operational-context/update"; optimisticConcurrency: "EXPECTED_VERSION" }>;
}
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new CoreError("INVALID_RESPONSE");
  return value as Record<string, unknown>;
}
export function negotiate(value: unknown): NegotiatedProtocol {
  const p = object(value);
  if (!Number.isInteger(p.protocolVersion)) throw new CoreError("INVALID_RESPONSE");
  if (p.protocolVersion !== 2) throw new CoreError("UNSUPPORTED_PROTOCOL");
  if (p.protocolName !== "no8do-agent-protocol" || p.systemName !== "No8do") throw new CoreError("INVALID_RESPONSE");
  if (typeof p.purpose !== "string") throw new CoreError("INVALID_RESPONSE");
  const guidance = object(p.replayGuidance);
  if (typeof guidance.summary !== "string" || [
    "searchBeforeNonTrivialWork", "preferExistingKnowledge", "searchBeforeCreate", "recordUsageOnlyWhenMateriallyUsed",
    "validatedRequiresEvidence", "avoidTrivialKnowledge", "avoidDuplicateKnowledge", "neverStoreSecrets",
    "neverStoreCredentials", "avoidDiscardedAttempts"
  ].some(key => typeof guidance[key] !== "boolean")) throw new CoreError("INVALID_RESPONSE");
  const capabilities = object(p.capabilities).capabilities;
  const policies = object(p.policies).policies;
  if (!Array.isArray(capabilities) || !Array.isArray(policies)) throw new CoreError("INVALID_RESPONSE");
  for (const value of capabilities) {
    const c = object(value);
    if (typeof c.id !== "string" || typeof c.description !== "string" || typeof c.readOnly !== "boolean")
      throw new CoreError("INVALID_RESPONSE");
  }
  for (const value of policies) {
    const policy = object(value);
    if (typeof policy.id !== "string" || typeof policy.description !== "string" ||
        (policy.enforcement !== "ADVISORY" && policy.enforcement !== "ENFORCED")) throw new CoreError("INVALID_RESPONSE");
  }
  const manifest = object(p.integrationExtensions);
  if (!Array.isArray(manifest.extensions)) throw new CoreError("INVALID_RESPONSE");
  const extensions = manifest.extensions.map(object);
  if (extensions.some(e => typeof e.id !== "string" || !Number.isInteger(e.version) || Number(e.version) < 1))
    throw new CoreError("INVALID_RESPONSE");
  for (const extension of extensions) {
    const context = object(extension.operationalContext);
    if (!Number.isInteger(context.version) || Number(context.version) < 1 ||
        typeof context.getMethod !== "string" || typeof context.updateMethod !== "string" ||
        context.optimisticConcurrency !== "EXPECTED_VERSION") throw new CoreError("INVALID_RESPONSE");
  }
  const matches = extensions.filter(e => e.id === "no8do-integration");
  if (!matches.length) throw new CoreError("MISSING_INTEGRATION_EXTENSION");
  if (matches.length !== 1) throw new CoreError("INVALID_RESPONSE");
  const e = matches[0]!;
  if (e.version !== 1) throw new CoreError("UNSUPPORTED_INTEGRATION_EXTENSION");
  const c = object(e.operationalContext);
  if (c.version !== 1 || c.getMethod !== "no8do/operational-context/get" ||
      c.updateMethod !== "no8do/operational-context/update" || c.optimisticConcurrency !== "EXPECTED_VERSION")
    throw new CoreError("UNSUPPORTED_INTEGRATION_EXTENSION");
  // Literal allowlist: no arbitrary provider fields are published.
  return Object.freeze({ protocolName: "no8do-agent-protocol", protocolVersion: 2, systemName: "No8do",
    integrationExtensionVersion: 1, operationalContext: Object.freeze({ version: 1,
      getMethod: "no8do/operational-context/get", updateMethod: "no8do/operational-context/update",
      optimisticConcurrency: "EXPECTED_VERSION" }) });
}
