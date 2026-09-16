package com.audittrove.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QuotePageChoiceTest {

    @Test
    void aSingleMatchGivesThePage() {
        assertThat(OpenAiAuditLlmClient.pageFromQuoteHits(List.of(4), Set.of())).containsExactly(4);
    }

    @Test
    void noMatchGivesNoPage() {
        assertThat(OpenAiAuditLlmClient.pageFromQuoteHits(List.of(), Set.of(2))).isEmpty();
    }

    @Test
    void severalMatchesPickThePageTheModelReported() {
        // Ek bolumlu sozlesmede ayni madde 1, 3 ve 5. sayfada yaziyor; hangisi oldugunu alinti degil
        // modelin bildirdigi sayfa soyluyor.
        assertThat(OpenAiAuditLlmClient.pageFromQuoteHits(List.of(1, 3, 5), Set.of(3))).containsExactly(3);
    }

    @Test
    void severalMatchesWithNoReportedPageLeaveThePageOpen() {
        // Sayfa burada bulunamaz ama bulgu dusmez; sayfa asagidaki yollardan aranir.
        assertThat(OpenAiAuditLlmClient.pageFromQuoteHits(List.of(1, 3, 5), Set.of())).isEmpty();
        assertThat(OpenAiAuditLlmClient.pageFromQuoteHits(List.of(1, 3, 5), Set.of(9))).isEmpty();
    }

    @Test
    void theEarliestMatchingPageWinsWhenTheModelNamesSeveral() {
        assertThat(OpenAiAuditLlmClient.pageFromQuoteHits(List.of(1, 3, 5), Set.of(5, 3))).containsExactly(3);
    }
}
