package com.audittrove.report;

import com.audittrove.api.AuditResponse;
import com.audittrove.financial.Lang;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Sayfa atfı ve dil sözleşmesinin belgeden bağımsız invariantları. */
class ReportContractTest {

    @Test
    void everyMarkerFormIsStrippedAndParsed() {
        var p = PageRefs.strip("Söz konusu varlıklar yönetimin tahminlerine bağlıdır [REPORT PAGE 6-7]. (Page 6)");
        assertThat(p.text()).isEqualTo("Söz konusu varlıklar yönetimin tahminlerine bağlıdır.");
        assertThat(p.pages()).containsExactly(6, 7);

        assertThat(PageRefs.strip("Hedge losses of 24,833,723 thousand TL [REPORT PAGES 5-6]").pages()).containsExactly(5, 6);
        assertThat(PageRefs.strip("Net borç arttı (Sayfa 5, 11)").pages()).containsExactly(5, 11);
        assertThat(PageRefs.strip("Net borç arttı (Rapor Sayfa 8)").pages()).containsExactly(8);
        assertThat(PageRefs.strip("Costs rose (Report Page 10) and (pp. 12-13)").pages()).containsExactly(10, 12, 13);
        assertThat(PageRefs.strip("Costs rose [report page 3, 9]").pages()).containsExactly(3, 9);
    }

    @Test
    void unreasonableRangeKeepsOnlyEndpoints() {
        assertThat(PageRefs.strip("x [REPORT PAGES 3-40]").pages()).containsExactly(3, 40);
    }

    @Test
    void textWithoutMarkersIsUntouched() {
        var p = PageRefs.strip("Sales to related parties constitute 80% of total sales (2023: 75%).");
        assertThat(p.text()).isEqualTo("Sales to related parties constitute 80% of total sales (2023: 75%).");
        assertThat(p.pages()).isEmpty();
    }

    @Test
    void languageCheckNamesTheFieldThatLeaks() {
        var r = new AuditResponse(60, "The report shows a decline in profit and rising leverage.",
                "The consolidated statements for 2024 show a marked decline in operating profit and net income.",
                List.of(new AuditResponse.Risk("Related party concentration", "MEDIUM", "",
                        "Grup'un satışlarının %80'ini ilişkili taraflara yapmakta olduğunu ve alacaklarının %65'inin bu taraflardan oluştuğunu açıklayan dipnota dikkat çekilir.")),
                List.of("Evaluate the credit risk arising from the concentration of sales with related parties."),
                List.of(), List.of(), List.of());
        assertThat(LanguageCheck.mismatches(r, Lang.EN)).containsExactly("risk[1].evidence");
        assertThat(LanguageCheck.mismatches(r, Lang.TR)).contains("summary", "scoreRationale", "recommendation[1]");
    }

    @Test
    void shortOrNumericTextIsNotJudged() {
        assertThat(LanguageCheck.detect("28.984.491 63.551.075")).isNull();
        assertThat(LanguageCheck.detect("Operating profit 2024")).isNull();
    }
}