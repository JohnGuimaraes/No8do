import assert from "node:assert/strict";
import test from "node:test";
import { renderAgentProtocolBootstrap } from "./agentProtocolBootstrap.js";
import type { AgentProtocol } from "./no8doClient.js";

const protocol: AgentProtocol = {
  protocolName: "controlled-agent-protocol",
  protocolVersion: 9,
  systemName: "No8do controlled",
  purpose: "memória técnica e conhecimento reutilizável",
  replayGuidance: {
    summary: "Canonical guidance fixture",
    searchBeforeNonTrivialWork: true,
    preferExistingKnowledge: true,
    searchBeforeCreate: true,
    recordUsageOnlyWhenMateriallyUsed: true,
    validatedRequiresEvidence: false,
    avoidTrivialKnowledge: false,
    avoidDuplicateKnowledge: false,
    neverStoreSecrets: false,
    neverStoreCredentials: false,
    avoidDiscardedAttempts: false
  },
  capabilities: { capabilities: [{ id: "REPLAY_SEARCH", description: "Search Replays", readOnly: true }] },
  policies: { policies: [{ id: "sample-policy", description: "A fixture policy", enforcement: "ADVISORY" }] }
};

test("instructions are short, deterministic and rendered from the supplied protocol", () => {
  const first = renderAgentProtocolBootstrap(protocol);
  const second = renderAgentProtocolBootstrap(protocol);

  assert.ok(first.length > 0);
  assert.equal(first, second);
  assert.match(first, /No8do controlled/);
  assert.match(first, /memória técnica e conhecimento reutilizável/);
  assert.match(first, /versão 9/);
  assert.match(first, /Replay/);
  assert.match(first, /get_agent_protocol/);
  assert.match(first, /influenciar materialmente/);
  assert.doesNotMatch(first, /OpenAI|Anthropic|Claude|ChatGPT|Codex|Cursor|PAT|Bearer|NO8DO_API_TOKEN/);
  assert.ok(first.split("\n").length <= 8);
});

test("only guidance enabled by the supplied protocol is rendered", () => {
  const changedProtocol = {
    ...protocol,
    protocolName: "changed-source",
    replayGuidance: { ...protocol.replayGuidance, searchBeforeNonTrivialWork: false, preferExistingKnowledge: false, searchBeforeCreate: false, recordUsageOnlyWhenMateriallyUsed: false }
  };
  const instructions = renderAgentProtocolBootstrap(changedProtocol);

  assert.match(instructions, /changed-source, versão 9/);
  assert.doesNotMatch(instructions, /Antes de trabalho técnico não trivial|Prefira conhecimento existente aplicável|Consulte equivalentes|Registre ReplayUsage/);
});
