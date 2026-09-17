package com.audittrove.llm;

import com.audittrove.api.AuditResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Ek gozlem, kontrol listesi bulgusuyla ayni hukme dayaniyorsa rapora girmemeli. */
class SameClauseTest {

    private static final String FESIH =
            "Hizmet alan, sözleşmeyi süre bitiminden önce, gerekçe göstermeksizin ve tazminat ödemeksizin tek taraflı olarak feshedebilir";

    private static AuditResponse.Risk risk(String title, String quote) {
        return new AuditResponse.Risk(title, "MEDIUM", "bulgu", "kanit", List.of(1),
                AuditResponse.Risk.MODEL, quote);
    }

    @Test
    void aShorterQuoteOfTheSameClauseCounts() {
        // Rubrik bulgusu cumlenin tamamini, ek gozlem bir parcasini aliyor; ayni madde.
        List<AuditResponse.Risk> existing = List.of(risk("Tek taraflı fesih", FESIH + ". Fesih, 15 gün önceden yazılı bildirimle yapılır."));
        assertThat(OpenAiAuditLlmClient.sameClauseAsAny(risk("Erken fesih hakkı", FESIH), existing)).isTrue();
    }

    @Test
    void aDifferentClauseDoesNotCount() {
        List<AuditResponse.Risk> existing = List.of(risk("Tek taraflı fesih", FESIH));
        assertThat(OpenAiAuditLlmClient.sameClauseAsAny(
                risk("Giderler", "Yol ve konaklama giderleri belgelendirilmek kaydıyla hizmet alan tarafından karşılanır"),
                existing)).isFalse();
    }

    @Test
    void findingsWithoutAUsableQuoteAreLeftAlone() {
        List<AuditResponse.Risk> existing = List.of(risk("Tek taraflı fesih", FESIH));
        assertThat(OpenAiAuditLlmClient.sameClauseAsAny(risk("Bos", ""), existing)).isFalse();
        assertThat(OpenAiAuditLlmClient.sameClauseAsAny(risk("Kisa", "kur farkı"), existing)).isFalse();
        assertThat(OpenAiAuditLlmClient.sameClauseAsAny(risk("Normal", FESIH), List.of(risk("Bos", "")))).isFalse();
    }
}
