import test from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { createIntegrationCore } from "../dist/index.js";
import { generatePkce, systemCrypto } from "../dist/security/security.js";
import { FetchTransport } from "../dist/transport/FetchTransport.js";

const id = "e5c24a61-2c65-40d1-8634-18f7897b2a18";
const secret = "no8do_int_fake-selector.fake-secret";
const device = "D".repeat(43);
const bootstrap = () => ({ status: 201, body: {
  requestId: id, deviceCode: device, userCode: "ABCD2345",
  verificationUri: "https://app.no8do.example/connect/no8do", expiresIn: 600, interval: 5
} });
const pending = interval => ({ status: 200, body: { state: "PENDING", interval } });
const consumed = () => ({ status: 200, body: { state: "CONSUMED", interval: 0, integrationCredential: secret } });
const input = { hostType: "CODEX", integrationVersion: "0.1.0", displayLabel: "Test host" };
const flush = async () => { for (let i = 0; i < 30; i++) await Promise.resolve(); };
const deferred = () => { let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b; }); return { promise, resolve, reject }; };

class Time {
  nowMs = 0; tasks = []; counter = 0;
  now = () => this.nowMs;
  schedule = (callback, delayMs) => {
    const task = { callback, at: this.nowMs + delayMs, id: ++this.counter };
    this.tasks.push(task);
    return () => { this.tasks = this.tasks.filter(t => t !== task); };
  };
  async advance(ms) {
    const end = this.nowMs + ms;
    await flush();
    while (true) {
      this.tasks.sort((a,b) => a.at-b.at || a.id-b.id);
      const task = this.tasks[0];
      if (!task || task.at > end) break;
      this.tasks.shift(); this.nowMs = task.at; task.callback(); await flush();
    }
    this.nowMs = end; await flush();
  }
}
function fixture(overrides = {}) {
  const time = new Time(), logs = [], requests = [], saves = [], deletes = [];
  let storedId = null, stored = null, generated = 0;
  const responses = [bootstrap(), consumed()];
  const http = { async send(request) {
    requests.push({ ...request, at: time.now() });
    const response = responses.shift();
    if (typeof response === "function") return response(request);
    if (!response) throw new Error("unexpected request");
    return response;
  } };
  const installationStore = {
    async load() { return storedId; },
    async saveIfAbsent(candidate) { storedId ??= candidate; return storedId; }
  };
  const credentialStore = {
    async save(key, credential) { saves.push({ key, credential }); stored = credential; },
    async load() { return stored; },
    async delete(key) { deletes.push(key); stored = null; }
  };
  const cryptoPort = { ...systemCrypto,
    sha256: async bytes => new Uint8Array(createHash("sha256").update(bytes).digest()),
    randomUUID() { generated++; return id; } };
  const core = createIntegrationCore({
    origin: "https://api.no8do.example", verificationOrigin: "https://app.no8do.example",
    clock: time, scheduler: time, http, crypto: cryptoPort,
    installationStore, credentialStore, logger: { log(entry) { logs.push(entry); } }, ...overrides
  });
  return { core, time, logs, requests, saves, deletes, responses, generated: () => generated };
}

test("PKCE RFC 7636 vector; verifier CSPRNG shape and padding", async () => {
  const verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
  const bytes = Buffer.from(verifier, "base64url");
  const pair = await generatePkce({ ...systemCrypto, randomBytes: () => bytes });
  assert.equal(pair.verifier, verifier);
  assert.equal(pair.challenge, "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
  const random = await generatePkce(systemCrypto);
  assert.match(random.verifier, /^[A-Za-z0-9_-]{43}$/);
  assert.equal(random.challenge, createHash("sha256").update(random.verifier).digest("base64url"));
  assert.ok(!random.challenge.includes("="));
});

test("bootstrap exact body, safe prompt, persistent UUID and initial states", async () => {
  const f = fixture();
  assert.equal(f.core.getAuthorizationState().state, "IDLE");
  const start = f.core.startAuthorization(input);
  assert.equal(f.core.getAuthorizationState().state, "STARTING");
  const prompt = await start;
  const body = JSON.parse(f.requests[0].body);
  assert.deepEqual(Object.keys(body).sort(), ["installationId","hostType","integrationVersion","displayLabel","codeChallenge","codeChallengeMethod"].sort());
  assert.equal(body.installationId, id); assert.equal(body.codeChallengeMethod, "S256");
  assert.match(body.codeChallenge, /^[A-Za-z0-9_-]{43}$/);
  assert.deepEqual(Object.keys(prompt).sort(), ["requestId","userCode","verificationUri","expiresIn","expiresAt","interval"].sort());
  assert.ok(!JSON.stringify(prompt).includes(device));
  assert.equal(f.requests[0].credentials, "omit"); assert.equal(f.requests[0].redirect, "error");
  assert.equal(f.core.getAuthorizationState().state, "AWAITING_USER");
  assert.equal(await f.core.getInstallationId(), id);
  f.core.cancelAuthorization(); await f.core.waitForAuthorization();
  f.responses.unshift(bootstrap());
  await f.core.startAuthorization(input);
  assert.equal(f.generated(), 1);
  f.core.cancelAuthorization(); await f.core.waitForAuthorization();
});

test("first poll waits interval; pending and approved schedule serially", async () => {
  const f = fixture(); f.responses.splice(1, 1, pending(7), { status: 200, body: { state: "APPROVED", interval: 8 } }, consumed());
  await f.core.startAuthorization(input);
  await f.time.advance(4999); assert.equal(f.requests.length, 1);
  await f.time.advance(1); assert.equal(f.requests.length, 2);
  await f.time.advance(6999); assert.equal(f.requests.length, 2);
  await f.time.advance(1); assert.equal(f.requests.length, 3);
  await f.time.advance(8000);
  assert.equal((await f.core.waitForAuthorization()).state, "CONNECTED");
  assert.equal(f.saves.length, 1);
  const exchange = JSON.parse(f.requests[1].body);
  assert.equal(exchange.deviceCode, device);
  assert.equal(createHash("sha256").update(exchange.codeVerifier).digest("base64url"), JSON.parse(f.requests[0].body).codeChallenge);
});

test("429 respects Retry-After and increased interval", async () => {
  const f = fixture(); f.responses.splice(1, 1, { status: 429, retryAfter: "20", body: { error: "slow_down" } }, pending(5), consumed());
  await f.core.startAuthorization(input); await f.time.advance(5000);
  await f.time.advance(19999); assert.equal(f.requests.length, 2);
  await f.time.advance(1); assert.equal(f.requests.length, 3);
  await f.time.advance(9999); assert.equal(f.requests.length, 3);
  await f.time.advance(1); assert.equal((await f.core.waitForAuthorization()).state, "CONNECTED");
});

test("HTTP date Retry-After honored", async () => {
  const f = fixture(); f.responses.splice(1, 1, { status: 429, retryAfter: new Date(40_000).toUTCString(), body: {} }, consumed());
  await f.core.startAuthorization(input); await f.time.advance(39_999); assert.equal(f.requests.length, 2);
  await f.time.advance(1); assert.equal(f.core.getAuthorizationState().state, "CONNECTED");
});

test("pending request never overlaps; cancellation aborts and ignores late response", async () => {
  const hold = deferred(), f = fixture(); f.responses[1] = () => hold.promise;
  await f.core.startAuthorization(input); await f.time.advance(5000);
  assert.equal(f.core.getAuthorizationState().state, "EXCHANGING");
  await f.time.advance(10_000); assert.equal(f.requests.length, 2);
  f.core.cancelAuthorization();
  assert.equal(f.requests[1].signal.aborted, true);
  assert.equal((await f.core.waitForAuthorization()).state, "CANCELLED");
  hold.resolve(consumed()); await flush(); await f.time.advance(100_000);
  assert.equal(f.saves.length, 0); assert.equal(f.requests.length, 2); assert.equal(f.time.tasks.length, 0);
});

test("cancel waiting clears timers and never calls deny", async () => {
  const f = fixture(); await f.core.startAuthorization(input); f.core.cancelAuthorization();
  assert.equal((await f.core.waitForAuthorization()).state, "CANCELLED");
  await f.time.advance(700_000); assert.equal(f.requests.length, 1); assert.equal(f.time.tasks.length, 0);
});

test("cancel startup aborts and late bootstrap cannot start polling", async () => {
  const f = fixture(), hold = deferred(); f.responses[0] = () => hold.promise;
  const start = f.core.startAuthorization(input); await flush(); f.core.cancelAuthorization();
  await assert.rejects(start, { code: "AUTHORIZATION_CANCELLED" });
  hold.resolve(bootstrap()); await flush(); assert.equal(f.requests.length, 1);
});

test("deadline expires during awaiting user without final poll", async () => {
  const f = fixture(); f.responses[0].body.expiresIn = 5;
  await f.core.startAuthorization(input); await f.time.advance(5000);
  assert.equal((await f.core.waitForAuthorization()).state, "EXPIRED"); assert.equal(f.requests.length, 1);
});

test("deadline aborts pending exchange", async () => {
  const f = fixture(); f.responses[0].body.expiresIn = 10; f.responses[1] = () => new Promise(() => {});
  await f.core.startAuthorization(input); await f.time.advance(10_000);
  assert.equal((await f.core.waitForAuthorization()).state, "EXPIRED");
  assert.equal(f.requests[1].signal.aborted, true);
});

for (const [server, state, code] of [
  ["DENIED","DENIED","AUTHORIZATION_DENIED"], ["EXPIRED","EXPIRED","AUTHORIZATION_EXPIRED"],
  ["CONSUMED","FAILED","EXCHANGE_ALREADY_CONSUMED"], ["UNKNOWN","FAILED","INVALID_RESPONSE"]
]) test("terminal " + server, async () => {
  const f = fixture(); f.responses[1] = { status: 200, body: { state: server, interval: 5 } };
  await f.core.startAuthorization(input); await f.time.advance(5000);
  assert.deepEqual(await f.core.waitForAuthorization(), { state, errorCode: code });
  assert.equal(f.saves.length, 0);
});

test("CONNECTED only after persistence completes; save once", async () => {
  const hold = deferred(), f = fixture({ credentialStore: {
    save: () => hold.promise, load: async () => null, delete: async () => {}
  } });
  await f.core.startAuthorization(input); await f.time.advance(5000);
  assert.equal(f.core.getAuthorizationState().state, "EXCHANGING");
  hold.resolve(); await flush(); assert.equal((await f.core.waitForAuthorization()).state, "CONNECTED");
  assert.equal(f.requests.length, 2);
});

test("persistence failure is typed and does not expose secret in errors/logs", async () => {
  const f = fixture({ credentialStore: {
    async save() { throw new Error(secret); }, load: async () => null, delete: async () => {}
  } });
  await f.core.startAuthorization(input); await f.time.advance(5000);
  const state = await f.core.waitForAuthorization();
  assert.deepEqual(state, { state: "FAILED", errorCode: "CREDENTIAL_PERSISTENCE_FAILED" });
  assert.ok(!JSON.stringify([state, f.logs]).includes(secret));
  assert.ok(!JSON.stringify(f.logs).includes(device));
  assert.ok(!JSON.stringify(f.logs).includes(JSON.parse(f.requests[1].body).codeVerifier));
});

test("cancel during persistence never connects; abort signal passed to store", async () => {
  const hold = deferred(); let signal;
  const f = fixture({ credentialStore: { save: (k,c,s) => { signal=s; return hold.promise; }, load: async () => null, delete: async () => {} } });
  await f.core.startAuthorization(input); await f.time.advance(5000); f.core.cancelAuthorization();
  assert.equal(signal.aborted, true); assert.equal((await f.core.waitForAuthorization()).state, "CANCELLED");
  hold.resolve(); await flush(); assert.equal(f.core.getAuthorizationState().state, "CANCELLED");
});

test("network and 503 retry bounded, sanitized and serialized", async () => {
  const f = fixture(); f.responses.splice(1, 1, () => { throw new Error(secret); }, { status: 503 }, { status: 503 }, { status: 503 });
  await f.core.startAuthorization(input); await f.time.advance(30_000);
  assert.equal((await f.core.waitForAuthorization()).state, "FAILED"); assert.equal(f.requests.length, 5);
  assert.ok(!JSON.stringify(f.logs).includes(secret));
});

for (const origin of ["http://example.com", "https://user:pass@example.com", "file:///tmp", "https://example.com/path", "https://example.com?token=x"]) {
  test("unsafe origin rejected: " + origin.split(":")[0] + origin.length, () => {
    assert.throws(() => fixture({ origin }), { code: "INVALID_ORIGIN" });
  });
}
for (const origin of ["http://localhost", "http://127.0.0.1", "http://[::1]"]) {
  test("loopback allowed " + origin, () => { assert.equal(fixture({ origin }).core.getAuthorizationState().state, "IDLE"); });
}
test("redirect and untrusted approval URL rejected without exchange", async () => {
  for (const change of [
    r => { r.status = 302; },
    r => { r.redirected = true; },
    r => { r.body.verificationUri = "https://evil.example/connect/no8do"; }
  ]) {
    const f = fixture(); change(f.responses[0]);
    await assert.rejects(f.core.startAuthorization(input), { code: "INVALID_RESPONSE" });
    assert.equal(f.requests.length, 1);
  }
});

test("invalid response fields fail safely", async () => {
  for (const [field, value] of [["interval",0],["expiresIn",Infinity],["deviceCode","bad"],["userCode",secret]]) {
    const f = fixture(); f.responses[0].body[field] = value;
    await assert.rejects(f.core.startAuthorization(input), { code: "INVALID_RESPONSE" });
    assert.ok(!JSON.stringify(f.logs).includes(secret));
  }
});

test("installation shared concurrent calls, persisted winner reused", async () => {
  const other = "f1c24a61-2c65-40d1-8634-18f7897b2a18";
  let writes=0;
  const f = fixture({ installationStore: { load: async () => null, saveIfAbsent: async () => { writes++; return other; } } });
  assert.deepEqual(await Promise.all([f.core.getInstallationId(),f.core.getInstallationId()]), [other,other]);
  assert.equal(writes,1); assert.equal(await f.core.getInstallationId(),other);
});
test("existing installation never generated; corrupt identity rejected", async () => {
  const f = fixture({ installationStore: { load: async () => id, saveIfAbsent: async () => { throw new Error(); } } });
  assert.equal(await f.core.getInstallationId(),id); assert.equal(f.generated(),0);
  const bad = fixture({ installationStore: { load: async () => "not-uuid", saveIfAbsent: async () => id } });
  await assert.rejects(bad.core.getInstallationId(), { code: "INSTALLATION_STORE_FAILED" });
});

test("forget only deletes local key; no secret returned by public API", async () => {
  const f = fixture(); await f.core.startAuthorization(input); await f.time.advance(5000);
  assert.equal(await f.core.hasStoredAuthorization(),true);
  await f.core.forgetLocalAuthorization(); assert.equal(await f.core.hasStoredAuthorization(),false);
  assert.deepEqual(f.deletes[0], { trustedOrigin: "https://api.no8do.example", installationId:id });
  assert.equal(f.requests.length,2);
  assert.ok(!Object.keys(f.core).includes("loadStoredAuthorization"));
});
test("concurrent start and forget during flow rejected", async () => {
  const f=fixture(); await f.core.startAuthorization(input);
  await assert.rejects(f.core.startAuthorization(input), { code:"FLOW_BUSY" });
  await assert.rejects(f.core.forgetLocalAuthorization(), { code:"FLOW_BUSY" });
  f.core.cancelAuthorization(); await f.core.waitForAuthorization();
});
test("invalid host not inferred", async () => {
  const f=fixture(); await assert.rejects(f.core.startAuthorization({ ...input, hostType:"UNKNOWN" }), { code:"INVALID_INPUT" });
  assert.equal(f.requests.length,0);
});
test("FetchTransport omits cookies and rejects redirects", async () => {
  const previous = globalThis.fetch; let captured;
  globalThis.fetch = async (url, options) => { captured={url,options}; return new Response("{}",{status:201}); };
  try {
    await new FetchTransport().send({url:"https://api.no8do.example/api/integration-authorizations/bootstrap",method:"POST",body:"{}",signal:new AbortController().signal,redirect:"error",credentials:"omit"});
    assert.equal(captured.options.redirect,"error"); assert.equal(captured.options.credentials,"omit");
    assert.equal(captured.options.headers.Authorization,undefined);
  } finally { globalThis.fetch=previous; }
});

test("forget reserves exclusivity before await; start cannot race pending deletion", async () => {
  const hold = deferred(); let calls = 0;
  const f = fixture({ credentialStore: {
    save: async () => {}, load: async () => null,
    delete: async () => { calls++; await hold.promise; }
  } });
  const forgetting = f.core.forgetLocalAuthorization();
  await assert.rejects(f.core.startAuthorization(input), { code: "FLOW_BUSY" });
  await assert.rejects(f.core.forgetLocalAuthorization(), { code: "FLOW_BUSY" });
  await flush(); assert.equal(calls, 1); assert.equal(f.requests.length, 0);
  hold.resolve(); await forgetting;
  await f.core.startAuthorization(input); await f.time.advance(5000);
  assert.equal((await f.core.waitForAuthorization()).state, "CONNECTED");
});

test("forget failure releases exclusivity and sanitizes provider error", async () => {
  const f = fixture({ credentialStore: {
    save: async () => {}, load: async () => null, delete: async () => { throw new Error(secret); }
  } });
  await assert.rejects(f.core.forgetLocalAuthorization(), { code: "CREDENTIAL_STORE_FAILED" });
  await f.core.startAuthorization(input); await f.time.advance(5000);
  assert.equal((await f.core.waitForAuthorization()).state, "CONNECTED");
});

test("cancelled non-cooperative save blocks start/forget until settlement", async () => {
  const hold = deferred();
  const f = fixture({ credentialStore: { save: () => hold.promise, load: async () => null, delete: async () => {} } });
  await f.core.startAuthorization(input); await f.time.advance(5000);
  f.core.cancelAuthorization(); await f.core.waitForAuthorization();
  await assert.rejects(f.core.startAuthorization(input), { code: "FLOW_BUSY" });
  await assert.rejects(f.core.forgetLocalAuthorization(), { code: "FLOW_BUSY" });
  hold.resolve(); await flush();
  await f.core.forgetLocalAuthorization();
});
