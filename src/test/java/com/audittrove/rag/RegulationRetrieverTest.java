package com.audittrove.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegulationRetrieverTest {
    private final RegulationRetriever retriever = createRetriever();

    @Test
    void findsCreditCardRules() {
        var results = retriever.retrieve(
                "Kredi kartı akdi faiz oranı, gecikme faizi ve asgari ödeme tutarı hesap özetinde yer alır.");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).id()).isIn("TCMB-KART", "BDDK-KART");
        assertThat(results).extracting(RegulationChunk::source)
                .anyMatch(source -> source.contains("TCMB") || source.contains("BDDK"));
    }

    @Test
    void returnsBaselineContextForUnmatchedText() {
        assertThat(retriever.retrieve("xyz")).hasSize(4);
    }

    private static RegulationRetriever createRetriever() {
        try {
            return new RegulationRetriever(new ObjectMapper(), (query, limit) -> java.util.List.of(), 4);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
