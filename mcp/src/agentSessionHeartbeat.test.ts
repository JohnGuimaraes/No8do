import assert from "node:assert/strict";
import test from "node:test";
import { AgentSessionHeartbeat, AGENT_SESSION_HEARTBEAT_INTERVAL_MS } from "./agentSessionHeartbeat.js";
import { AgentSessionHeader, No8doClient } from "./no8doClient.js";

const sessionId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const flush = () => new Promise<void>(resolve => setImmediate(resolve));

test("heartbeat começa em intervalo único, usa API e para sem manter processo vivo", async () => {
  const sessionHeader = new AgentSessionHeader();
  sessionHeader.set(sessionId);
  const requests: Array<{ url: string; headers: Headers }> = [];
  const client = new No8doClient("http://localhost:8080", "PAT_PRIVATE", (async (url, init) => {
    requests.push({ url: String(url), headers: new Headers(init?.headers) });
    return new Response(JSON.stringify({ sessionId, lastSeenAt: "2026-09-23T12:00:00Z" }), { status: 200 });
  }) as typeof fetch, sessionHeader);
  let tick: (() => void) | undefined;
  let schedules = 0;
  let cleanups = 0;
  const heartbeat = new AgentSessionHeartbeat(client, sessionId, (callback, intervalMs) => {
    schedules++;
    assert.equal(intervalMs, AGENT_SESSION_HEARTBEAT_INTERVAL_MS);
    tick = callback;
    return () => { cleanups++; };
  });

  heartbeat.start();
  heartbeat.start();
  assert.equal(schedules, 1);
  assert.equal(requests.length, 0);
  tick?.();
  await flush();
  assert.equal(requests.length, 1);
  assert.equal(requests[0]?.url, `http://localhost:8080/api/agent-sessions/${sessionId}/heartbeat`);
  assert.equal(requests[0]?.headers.get("Authorization"), "Bearer PAT_PRIVATE");
  assert.equal(requests[0]?.headers.get("X-No8do-Agent-Session-Id"), sessionId);
  heartbeat.stop();
  heartbeat.stop();
  assert.equal(cleanups, 1);
});

test("close para o heartbeat antes de enviar disconnect uma única vez e com timeout", async () => {
  const events: string[] = [];
  const requests: Array<{ url: string; init?: RequestInit }> = [];
  const sessionHeader = new AgentSessionHeader();
  sessionHeader.set(sessionId);
  const client = new No8doClient("http://localhost:8080", "PAT_PRIVATE", (async (url, init) => {
    requests.push({ url: String(url), init });
    events.push("disconnect");
    return new Response(JSON.stringify({ disconnectedAt: "2026-09-23T12:00:00Z" }), { status: 200 });
  }) as typeof fetch, sessionHeader);
  let tick: (() => void) | undefined;
  let schedules = 0;
  const heartbeat = new AgentSessionHeartbeat(client, sessionId, callback => {
    schedules++;
    tick = callback;
    return () => { events.push("stop"); };
  });

  heartbeat.start();
  await Promise.all([heartbeat.close(), heartbeat.close()]);
  tick?.();
  await flush();
  heartbeat.start();

  assert.deepEqual(events, ["stop", "disconnect"]);
  assert.equal(requests.length, 1);
  assert.equal(requests[0]?.url, `http://localhost:8080/api/agent-sessions/${sessionId}/disconnect`);
  assert.equal(requests[0]?.init?.method, "POST");
  assert.equal(requests[0]?.init?.body, undefined);
  assert.equal(requests[0]?.init?.signal instanceof AbortSignal, true);
  assert.equal((requests[0]?.init?.signal as AbortSignal).aborted, false);
  assert.equal(schedules, 1);
});

test("falha no disconnect é absorvida após parar o heartbeat", async () => {
  const events: string[] = [];
  const failures: Array<{ error: unknown; operation: string }> = [];
  const client = new No8doClient("http://localhost:8080", "PAT_PRIVATE", (async () => {
    events.push("disconnect");
    return new Response(JSON.stringify({ error: "unavailable" }), { status: 503 });
  }) as typeof fetch);
  const heartbeat = new AgentSessionHeartbeat(client, sessionId, () => () => { events.push("stop"); },
    (error, operation) => failures.push({ error, operation }));
  heartbeat.start();

  await heartbeat.close();
  await heartbeat.close();

  assert.deepEqual(events, ["stop", "disconnect"]);
  assert.equal(failures.length, 1);
  assert.equal(failures[0]?.operation, "disconnect");
});

test("falha isolada é registrada e tick posterior tenta novamente", async () => {
  let calls = 0;
  const errors: unknown[] = [];
  const client = new No8doClient("http://localhost:8080", "PAT_PRIVATE", (async () => {
    calls++;
    return new Response(JSON.stringify({ error: "temporary failure" }), { status: calls === 1 ? 503 : 200 });
  }) as typeof fetch);
  let tick: (() => void) | undefined;
  const heartbeat = new AgentSessionHeartbeat(client, sessionId, callback => {
    tick = callback;
    return () => {};
  }, error => errors.push(error));
  heartbeat.start();

  tick?.();
  await flush();
  tick?.();
  await flush();

  assert.equal(calls, 2);
  assert.equal(errors.length, 1);
  heartbeat.stop();
});
