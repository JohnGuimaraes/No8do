import { z } from "zod";
import { CoreError } from "../errors/CoreError.js";

// Reject known credential/token forms wherever untrusted metadata might carry them.
const secret = /(?:no8do_int_|no8do_agent_|github_pat_|gh[pousr]_|Bearer\s)/i;
const text = (max: number) => z.string().min(1).max(max).refine(v => v.trim().length > 0 &&
  !/[\u0000-\u001f\u007f-\u009f]/.test(v) && !secret.test(v));
const segment = text(128).regex(/^[a-zA-Z0-9._~-]+$/).refine(v => v !== "." && v !== "..");
const repository = z.object({
  vcs: z.literal("GIT"), provider: z.literal("github"), host: z.literal("github.com"),
  namespace: text(512).refine(v => v.split("/").every(s => segment.safeParse(s).success)),
  name: text(255).refine(v => segment.safeParse(v).success)
}).strict();
const reference = z.object({ kind: z.enum(["ISSUE", "TICKET", "TASK", "WORK_ITEM"]),
  provider: text(64).regex(/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/).transform(v => v.toLowerCase()),
  key: text(128).regex(/^[a-zA-Z0-9][a-zA-Z0-9._~-]*$/)
}).strict();
const references = z.array(reference).max(20).refine(values =>
  new Set(values.map(v => JSON.stringify([v.kind, v.provider, v.key]))).size === values.length);
const directory = text(1024).transform(v => v.replace(/\\/g, "/")).refine(v =>
  !v.startsWith("/") && !/^[a-zA-Z]:/.test(v) &&
  v.split("/").filter(Boolean).every(s => segment.safeParse(s).success))
  .transform(v => v.split("/").filter(Boolean).join("/"));
const fields = { repository: repository.nullable(), branch: text(255).nullable(),
  workingDirectory: directory.nullable(), references };
const signal = z.object(fields).strict();
const version = z.number().int().nonnegative().safe();
const update = z.object({ expectedVersion: version.nullable(), ...fields }).strict();
const instant = z.string().datetime({ offset: true });
const uuid = z.string().uuid();
const capability = z.object({ id: text(128), description: text(2048), readOnly: z.boolean() }).strict();
const policy = z.object({ id: text(128), description: text(2048), enforcement: z.enum(["ADVISORY", "ENFORCED"]) }).strict();
const session = z.object({
  sessionId: uuid, workspaceId: uuid, clientName: text(128), clientVersion: text(64), transport: z.literal("MCP"),
  protocolName: z.literal("no8do-agent-protocol"), protocolVersion: z.literal(2),
  runtimeMode: z.enum(["OFF", "READ_ONLY", "RETRIEVAL", "ASSISTED", "FULL"]),
  effectiveCapabilities: z.array(capability), policies: z.array(policy), registeredAt: instant,
  presenceStatus: z.enum(["CONNECTED", "ACTIVE", "IDLE", "DISCONNECTED"]),
  lastSeenAt: instant, lastActivityAt: instant.nullable(), disconnectedAt: instant.nullable()
}).strict();
const resolution = z.object({ id: uuid.nullable(), status: z.enum(["RESOLVED", "UNRESOLVED", "AMBIGUOUS"]),
  confidence: z.enum(["LOW", "MEDIUM", "HIGH"]).nullable() }).strict()
  .refine(v => v.status === "RESOLVED" ? v.id !== null : v.id === null && v.confidence === null);
export const operationalResponseSchema = z.object({ sessionId: uuid, version, signal,
  resolution: z.object({ project: resolution, workItem: resolution }).strict(), updatedAt: instant }).strict();
export const operationalReadSchema = z.discriminatedUnion("exists", [
  z.object({ exists: z.literal(false) }).strict(),
  z.object({ exists: z.literal(true), context: operationalResponseSchema }).strict()
]);
type DeepReadonly<T> = T extends object ? { readonly [K in keyof T]: DeepReadonly<T[K]> } : T;
export type AgentSessionContext = DeepReadonly<z.infer<typeof session>>;
export type OperationalContextSignal = DeepReadonly<z.infer<typeof signal>>;
export type OperationalContextUpdate = DeepReadonly<z.infer<typeof update>>;
export type OperationalContext = DeepReadonly<z.infer<typeof operationalResponseSchema>>;
export type OperationalContextRead = DeepReadonly<z.infer<typeof operationalReadSchema>>;
function freeze<T>(value: T): DeepReadonly<T> {
  if (value && typeof value === "object") {
    for (const child of Object.values(value)) freeze(child);
    Object.freeze(value);
  }
  return value as DeepReadonly<T>;
}
function parse<T>(schema: z.ZodType<T>, value: unknown, input = false): DeepReadonly<T> {
  const result = schema.safeParse(value);
  if (!result.success) throw new CoreError(input ? "INVALID_OPERATIONAL_CONTEXT" : "INVALID_RESPONSE");
  return freeze(result.data);
}
export const validateUpdate = (value: unknown): OperationalContextUpdate => parse(update, value, true);
export const parseSession = (value: unknown): AgentSessionContext => parse(session, value);
export const parseOperational = (value: unknown): OperationalContext => parse(operationalResponseSchema, value);
export const parseOperationalRead = (value: unknown): OperationalContextRead => parse(operationalReadSchema, value);
