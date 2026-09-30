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

test("erro de disconnect não expõe bearer nem PAT no log de cleanup", async () => {
  const logEntries: unknown[][] = [];
  const originalError = console.error;
  console.error = (...args: unknown[]) => { logEntries.push(args); };
  try {
    const client = new No8doClient("http://localhost:8080", "PAT_STDIO_SECRET", (async () => {
      throw new Error("Request failed: Bearer PAT_STDIO_SECRET");
    }) as typeof fetch);
    const heartbeat = new AgentSessionHeartbeat(client, sessionId, () => () => {});

    heartbeat.start();
    await heartbeat.close();

    const logged = JSON.stringify(logEntries);
    assert.doesNotMatch(logged, /PAT_STDIO_SECRET/);
    assert.match(logged, /No8do AgentSession disconnect failed/);
    assert.doesNotMatch(logged, /Request failed|Bearer/);
    assert.doesNotMatch(logged, /transportSessionFingerprint|fingerprint/);
  } finally {
    console.error = originalError;
  }
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

test("revogação encerra definitivamente o heartbeat sem retry nem novo agendamento", async () => {
  let calls = 0;
  let tick: (() => void) | undefined;
  let schedules = 0;
  let stops = 0;
  const errors: unknown[] = [];
  const client = new No8doClient("http://localhost:8080", "PAT_PRIVATE", (async () => {
    calls++;
    return new Response(JSON.stringify({ error: "AGENT_SESSION_REVOKED" }), { status: 409 });
  }) as typeof fetch);
  const heartbeat = new AgentSessionHeartbeat(client, sessionId, callback => {
    schedules++;
    tick = callback;
    return () => { stops++; };
  }, error => errors.push(error));

  heartbeat.start();
  tick?.();
  await flush();
  tick?.();
  await flush();
  heartbeat.start();
  heartbeat.stop();
  heartbeat.stop();

  assert.equal(heartbeat.isRevoked(), true);
  assert.equal(calls, 1);
  assert.equal(schedules, 1);
  assert.equal(stops, 1);
  assert.deepEqual(errors, []);
});

test("erros transitórios, 5xx e sessão desconectada não marcam o heartbeat como revogado", async () => {
  for (const response of [
    new Response(JSON.stringify({ error: "temporary" }), { status: 503 }),
    new Response(JSON.stringify({ error: "AGENT_SESSION_DISCONNECTED" }), { status: 409 })
  ]) {
    let tick: (() => void) | undefined;
    const client = new No8doClient("http://localhost:8080", "PAT_PRIVATE", (async () => response.clone()) as typeof fetch);
    const heartbeat = new AgentSessionHeartbeat(client, sessionId, callback => { tick = callback; return () => {}; }, () => {});
    heartbeat.start();
    tick?.();
    await flush();
    assert.equal(heartbeat.isRevoked(), false);
    heartbeat.stop();
  }

  let networkTick: (() => void) | undefined;
  const networkClient = new No8doClient("http://localhost:8080", "PAT_PRIVATE", (async () => {
    throw new Error("network failed");
  }) as typeof fetch);
  const networkHeartbeat = new AgentSessionHeartbeat(networkClient, sessionId,
    callback => { networkTick = callback; return () => {}; }, () => {});
  networkHeartbeat.start();
  networkTick?.();
  await flush();
  assert.equal(networkHeartbeat.isRevoked(), false);
  networkHeartbeat.stop();
});

test("close após revogação preserva o estado terminal sem disconnect e permanece idempotente", async () => {
  let disconnects = 0;
  let tick: (() => void) | undefined;
  const client = new No8doClient("http://localhost:8080", "PAT_PRIVATE", (async (url) => {
    if (String(url).endsWith("/heartbeat")) {
      return new Response(JSON.stringify({ error: "AGENT_SESSION_REVOKED" }), { status: 409 });
    }
    disconnects++;
    return new Response(JSON.stringify({ disconnectedAt: "2026-09-23T12:00:00Z" }), { status: 200 });
  }) as typeof fetch);
  const heartbeat = new AgentSessionHeartbeat(client, sessionId, callback => { tick = callback; return () => {}; }, () => {});

  heartbeat.start();
  tick?.();
  await flush();
  await Promise.all([heartbeat.close(), heartbeat.close()]);

  assert.equal(heartbeat.isRevoked(), true);
  assert.equal(disconnects, 0);
});

test("markRevoked é idempotente e impede novo timer", () => {
  let schedules = 0;
  let stops = 0;
  const heartbeat = new AgentSessionHeartbeat(new No8doClient("http://localhost:8080", "PAT_PRIVATE"), sessionId,
    () => { schedules++; return () => { stops++; }; });
  heartbeat.start();
  heartbeat.markRevoked();
  heartbeat.markRevoked();
  heartbeat.start();
  assert.equal(heartbeat.isRevoked(), true);
  assert.equal(schedules, 1);
  assert.equal(stops, 1);
});
