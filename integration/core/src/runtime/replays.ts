import { z } from "zod";
import { CoreError } from "../errors/CoreError.js";
const uuid = z.string().uuid().transform(value => value.toLowerCase());
const integer = z.number().int().nonnegative().safe();
const version = integer.min(1);
const instant = z.string().datetime({ offset: true });
const text = z.string();
const strings = z.array(text);
export const replayType = z.enum(["FIX", "PATTERN", "RECIPE", "SNIPPET", "DECISION", "PROCEDURE", "CHECKLIST", "TROUBLESHOOTING", "PROMPT", "REFERENCE"]);
const status = z.enum(["DRAFT", "VALIDATED", "DEPRECATED"]);
const evidence = z.object({ summary: text, method: text, reference: text.nullable() }).strict().nullable();
const counts = { usageCount: integer, successCount: integer, failureCount: integer };
const common = { title: text, type: replayType, status, version, tags: strings, stack: strings };
export const summarySchema = z.object({ id: uuid, ...common, projectId: uuid.nullable(), ...counts,
  lastUsedAt: instant.nullable(), updatedAt: instant }).strict();
export const detailSchema = z.object({ id: uuid, workspaceId: uuid, ...common, projectId: uuid.nullable(),
  projectName: text.nullable(), problem: text.nullable(), solution: text.nullable(), context: text.nullable(),
  ...counts, lastUsedAt: instant.nullable(), createdBy: uuid.nullable(), createdByName: text,
  createdAt: instant, updatedAt: instant, validationEvidence: evidence }).strict();
export const similarSchema = z.object({ id: uuid, title: text, type: replayType, status, version, stack: strings,
  usageCount: integer, score: integer }).strict();
export const qualitySchema = z.object({ score: integer.max(100), level: z.enum(["LOW", "MEDIUM", "HIGH"]),
  ...counts, successRate: integer.max(100).nullable(), signals: strings }).strict();
export const versionSchema = z.object({ ...common, problem: text.nullable(), solution: text.nullable(), context: text.nullable(),
  projectId: uuid.nullable(), changedBy: uuid.nullable(), changedByName: text, createdAt: instant, validationEvidence: evidence }).strict();
export const relationSchema = z.object({ id: uuid, type: z.enum(["RELATED_TO", "SUPERSEDES", "RESOLVES", "DEPENDS_ON"]),
  direction: z.enum(["RELATED", "OUTGOING", "INCOMING"]), relatedReplayId: uuid, relatedReplayTitle: text,
  relatedReplayType: replayType, relatedReplayStatus: status, relatedReplayVersion: version, createdAt: instant }).strict();
export const summariesSchema = z.array(summarySchema);
export const suggestionsSchema = z.object({ suggestions: z.array(similarSchema) }).strict();
export const versionsSchema = z.array(versionSchema);
export const relationsSchema = z.array(relationSchema);
const inputText = z.string().trim().min(1).max(65536);
const discoverySchema = z.object({ query: inputText.optional(), problem: inputText.optional(),
  stack: z.array(z.string().max(1024)).max(256).optional(), tags: z.array(z.string().max(1024)).max(256).optional(),
  type: replayType.optional() }).strict();
type DeepReadonly<T> = T extends object ? { readonly [K in keyof T]: DeepReadonly<T[K]> } : T;
export type ReplaySummary = DeepReadonly<z.infer<typeof summarySchema>>;
export type ReplayDetail = DeepReadonly<z.infer<typeof detailSchema>>;
export type SimilarReplay = DeepReadonly<z.infer<typeof similarSchema>>;
export type ReplayQuality = DeepReadonly<z.infer<typeof qualitySchema>>;
export type ReplayVersion = DeepReadonly<z.infer<typeof versionSchema>>;
export type ReplayRelation = DeepReadonly<z.infer<typeof relationSchema>>;
export type ReusableKnowledge = DeepReadonly<z.infer<typeof suggestionsSchema>>;
export type FindReusableKnowledgeInput = DeepReadonly<z.infer<typeof discoverySchema>>;
export type ReplayType = z.infer<typeof replayType>;
export function validateReplayInput<T>(schema: z.ZodType<T>, value: unknown): T {
  const result = schema.safeParse(value);
  if (!result.success) throw new CoreError("INVALID_INPUT");
  return result.data;
}
export const replayIdInput = z.tuple([uuid]);
export const replayVersionInput = z.tuple([uuid, version]);
export const searchInput = z.tuple([inputText]);
export const discoveryInput = z.tuple([discoverySchema]);
function freeze<T>(value: T): DeepReadonly<T> {
  if (value && typeof value === "object") { for (const child of Object.values(value)) freeze(child); Object.freeze(value); }
  return value as DeepReadonly<T>;
}
// Limits apply before JSON/schema parsing. Reject whole response, never truncate knowledge.
export const MAX_REPLAY_BYTES = 8 * 1024 * 1024;
export function parseReplay<T>(result: unknown, schema: z.ZodType<T>): DeepReadonly<T> {
  const envelope = z.object({ content: z.tuple([z.object({ type: z.literal("text"), text: z.string() }).strict()]),
    isError: z.boolean().optional() }).strict().safeParse(result);
  if (!envelope.success) throw new CoreError("INVALID_RESPONSE");
  if (envelope.data.isError) throw new CoreError("REPLAY_RETRIEVAL_FAILED");
  const raw = envelope.data.content[0].text;
  if (raw.length > MAX_REPLAY_BYTES || new TextEncoder().encode(raw).length > MAX_REPLAY_BYTES)
    throw new CoreError("REPLAY_RESPONSE_TOO_LARGE");
  try {
    const data: unknown = JSON.parse(raw);
    // Bound nesting/node count before schema recursion and deep-freeze.
    let nodes = 0; const pending: { value: unknown; depth: number }[] = [{ value: data, depth: 0 }];
    while (pending.length) {
      const item = pending.pop()!;
      if (++nodes > 100000 || item.depth > 32) throw new Error();
      if (item.value && typeof item.value === "object")
        for (const child of Object.values(item.value)) pending.push({ value: child, depth: item.depth + 1 });
    }
    const parsed = schema.safeParse(data);
    if (!parsed.success) throw new Error();
    return freeze(parsed.data);
  } catch { throw new CoreError("INVALID_RESPONSE"); }
}
