import type { AgentProtocol } from "./no8doClient.js";

/** Renders concise connection guidance from the canonical structured protocol. */
export function renderAgentProtocolBootstrap(protocol: AgentProtocol): string {
  const instructions = [
    `Este servidor é ${protocol.systemName}: ${protocol.purpose}`,
    `O Agent Protocol canônico ${protocol.protocolName}, versão ${protocol.protocolVersion}, está disponível em get_agent_protocol.`
  ];
  const guidance = protocol.replayGuidance;
  if (guidance.searchBeforeNonTrivialWork) instructions.push("Antes de trabalho técnico não trivial, pesquise conhecimento existente em Replays.");
  if (guidance.preferExistingKnowledge) instructions.push("Prefira conhecimento existente aplicável em vez de recriá-lo.");
  if (guidance.searchBeforeCreate) instructions.push("Consulte equivalentes antes de criar um Replay.");
  if (guidance.recordUsageOnlyWhenMateriallyUsed) instructions.push("Registre ReplayUsage somente quando um Replay influenciar materialmente o trabalho.");
  instructions.push("Siga as capabilities e policies atuais expostas por get_agent_protocol.");
  return instructions.join("\n");
}
