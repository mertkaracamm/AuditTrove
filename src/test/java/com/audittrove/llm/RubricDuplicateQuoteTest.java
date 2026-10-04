package com.audittrove.llm;

import com.audittrove.api.AuditResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RubricDuplicateQuoteTest {

    private static AuditResponse.Risk risk(String title, String severity, String quote) {
        return new AuditResponse.Risk(title, severity, "bulgu", "kanit", List.of(1),
                AuditResponse.Risk.RUBRIC, quote);
    }

    private static final String GIZLILIK =
            "Gizlilik yükümlülüğünün ihlâli hâlinde çalışan, brüt aylık ücretinin on iki katı tutarında cezai şart öder.";

    @Test
    void theSameSentenceIsReportedOnceWithTheHeavierTitle() {
        List<AuditResponse.Risk> out = OpenAiAuditLlmClient.dropRepeatedQuotes(List.of(
                risk("Çalışana yönelik cezai şart", "MEDIUM", GIZLILIK),
                risk("Sorumluluk sınırlaması veya feragat", "HIGH", GIZLILIK)));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).title()).isEqualTo("Sorumluluk sınırlaması veya feragat");
    }

    @Test
    void differentSentencesAreBothKept() {
        List<AuditResponse.Risk> out = OpenAiAuditLlmClient.dropRepeatedQuotes(List.of(
                risk("Cezai şart", "MEDIUM", GIZLILIK),
                risk("Rekabet yasağı", "HIGH", "Çalışan, sözleşmenin sona ermesinden itibaren iki yıl süreyle çalışamaz.")));
        assertThat(out).hasSize(2);
    }

    @Test
    void findingsWithoutAQuoteAreLeftAlone() {
        // Alintisi olmayan bulgular ayirt edilemez; elemeye girmezler.
        List<AuditResponse.Risk> out = OpenAiAuditLlmClient.dropRepeatedQuotes(List.of(
                risk("Bir", "LOW", ""), risk("Iki", "LOW", ""), risk("Uc", "LOW", "kisa")));
        assertThat(out).hasSize(3);
    }

    @Test
    void theOriginalOrderIsKept() {
        List<AuditResponse.Risk> out = OpenAiAuditLlmClient.dropRepeatedQuotes(List.of(
                risk("Rekabet yasağı", "HIGH", "Çalışan, sözleşmenin sona ermesinden itibaren iki yıl süreyle çalışamaz."),
                risk("Cezai şart", "MEDIUM", GIZLILIK),
                risk("Sorumluluk sınırlaması", "HIGH", GIZLILIK)));
        assertThat(out).hasSize(2);
        assertThat(out.get(0).title()).isEqualTo("Rekabet yasağı");
        assertThat(out.get(1).title()).isEqualTo("Sorumluluk sınırlaması");
    }
}
