package com.no8do.api.agent;

import java.util.List;
import org.springframework.stereotype.Component;

/** Pure code-versioned provider of the canonical No8do agent protocol. */
@Component
public final class No8doAgentProtocolProvider {
    private static final No8doAgentProtocol CURRENT = buildCurrent();

    public No8doAgentProtocol current() { return CURRENT; }

    private static No8doAgentProtocol buildCurrent() {
        ReplayAgentGuidance guidance = new ReplayAgentGuidance(
                "Pesquise conhecimento existente antes de resolver ou registrar trabalho técnico relevante.",
                true, true, true, true, true, true, true, true, true, true);
        AgentCapabilityManifest capabilities = new AgentCapabilityManifest(List.of(
                AgentCapability.REPLAY_CATALOG_LIST,
                AgentCapability.REPLAY_SEARCH,
                AgentCapability.REUSABLE_KNOWLEDGE_DISCOVERY,
                AgentCapability.REPLAY_READ,
                AgentCapability.REPLAY_VERSION_READ,
                AgentCapability.REPLAY_QUALITY_READ,
                AgentCapability.REPLAY_RELATIONS,
                AgentCapability.REPLAY_CREATE,
                AgentCapability.REPLAY_UPDATE,
                AgentCapability.REPLAY_USAGE_HISTORY_READ,
                AgentCapability.REPLAY_USAGE_RECORD,
                AgentCapability.SEMANTIC_DUPLICATE_SEARCH,
                AgentCapability.HYBRID_RETRIEVAL,
                AgentCapability.CONTEXT_PACKAGE_ASSEMBLY,
                AgentCapability.CONTEXT_RENDERING));
        AgentPolicyManifest policies = new AgentPolicyManifest(List.of(
                new AgentPolicy("workspace-isolation-required", "Operações de Replay devem respeitar o workspace autorizado.", AgentPolicyEnforcement.ENFORCED),
                new AgentPolicy("secrets-forbidden", "Não armazene segredos em conhecimento reutilizável.", AgentPolicyEnforcement.ADVISORY),
                new AgentPolicy("credentials-forbidden", "Não armazene credenciais em conhecimento reutilizável.", AgentPolicyEnforcement.ADVISORY),
                new AgentPolicy("semantic-duplicate-check-before-create", "Verifique conhecimento equivalente antes de criar um Replay.", AgentPolicyEnforcement.ADVISORY),
                new AgentPolicy("evidence-required-for-validated", "Use VALIDATED somente quando houver evidência adequada.", AgentPolicyEnforcement.ADVISORY),
                new AgentPolicy("material-usage-required-for-usage-record", "Registre uso somente quando o Replay influenciar materialmente o trabalho.", AgentPolicyEnforcement.ADVISORY)));
        return new No8doAgentProtocol("no8do-agent-protocol", 1, "No8do",
                "Camada de memória e conhecimento técnico reutilizável para agentes.", guidance, capabilities, policies);
    }
}
