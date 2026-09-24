package com.no8do.api.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Functional capability supported by the current No8do Replay backend. */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum AgentCapability {
    REPLAY_CATALOG_LIST("Lista o catálogo de Replays.", true),
    REPLAY_SEARCH("Pesquisa Replays no workspace por texto e ordena correspondências lexicalmente.", true),
    REUSABLE_KNOWLEDGE_DISCOVERY("Sugere Replays reutilizáveis por relevância lexical determinística.", true),
    REPLAY_READ("Lê conteúdo completo de Replay.", true),
    REPLAY_VERSION_READ("Lê versões históricas imutáveis de Replay.", true),
    REPLAY_QUALITY_READ("Lê avaliação derivada de qualidade do Replay.", true),
    REPLAY_RELATIONS("Lista, cria e remove relações entre Replays.", false),
    REPLAY_CREATE("Cria Replays.", false),
    REPLAY_UPDATE("Atualiza Replays.", false),
    REPLAY_USAGE_HISTORY_READ("Lê o histórico de uso de um Replay.", true),
    REPLAY_USAGE_RECORD("Registra uso de Replay.", false),
    SEMANTIC_DUPLICATE_SEARCH("O backend oferece busca vetorial de possíveis duplicatas semânticas.", true),
    HYBRID_RETRIEVAL("O backend combina descoberta lexical e vetorial.", true),
    CONTEXT_PACKAGE_ASSEMBLY("O backend monta pacotes a partir de fontes recuperadas e aprovadas por budget.", true),
    CONTEXT_RENDERING("O backend renderiza pacotes de contexto com referências de fonte.", true);

    private final String description;
    private final boolean readOnly;

    AgentCapability(String description, boolean readOnly) {
        this.description = description;
        this.readOnly = readOnly;
    }

    @JsonProperty("id") public String id() { return name(); }
    @JsonProperty("description") public String description() { return description; }
    @JsonProperty("readOnly") public boolean readOnly() { return readOnly; }
}
