export type ErrorCode = "INVALID_ORIGIN" | "INVALID_INPUT" | "INVALID_RESPONSE" | "BOOTSTRAP_FAILED" | "EXCHANGE_FAILED" | "EXCHANGE_ALREADY_CONSUMED" | "AUTHORIZATION_DENIED" | "AUTHORIZATION_EXPIRED" | "AUTHORIZATION_CANCELLED" | "CREDENTIAL_PERSISTENCE_FAILED" | "CREDENTIAL_STORE_FAILED" | "INSTALLATION_STORE_FAILED" | "RATE_LIMITED" | "TRANSPORT_ERROR" | "FLOW_BUSY";
/** Never contains raw provider message, body, cause or metadata. */
export class CoreError extends Error {
  constructor(public readonly code: ErrorCode) { super(code); this.name = "CoreError"; }
}
