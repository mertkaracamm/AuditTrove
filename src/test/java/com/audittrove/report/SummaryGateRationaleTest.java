package com.audittrove.report;

import com.audittrove.api.AuditResponse;
import com.audittrove.financial.Lang;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryGateRationaleTest {

    private static AuditResponse.Risk risk(String severity, String source) {
        return new AuditResponse.Risk("Baslik", severity, "bulgu", "kanit", List.of(1), source, "alinti");
    }

    @Test
    void aDocumentWithNoFindingAtAllIsCalledClean() {
        assertThat(SummaryGate.rationale(List.of(), Lang.of("tr"))).contains("temiz");
    }

    @Test
    void observationsAreNotCalledClean() {
        // Skora giren bulgu yok ama ekranda ek gozlem duruyor; "belge temiz" demek yaniltiyordu.
        String tr = SummaryGate.rationale(List.of(risk("MEDIUM", AuditResponse.Risk.MODEL)), Lang.of("tr"));
        assertThat(tr).doesNotContain("temiz");
        assertThat(tr).contains("gözlemler");
        assertThat(SummaryGate.rationale(List.of(risk("MEDIUM", AuditResponse.Risk.MODEL)), Lang.of("en")))
                .doesNotContain("clean");
    }

    @Test
    void aScoredFindingStillDrivesTheRationale() {
        String tr = SummaryGate.rationale(List.of(
                risk("HIGH", AuditResponse.Risk.RUBRIC),
                risk("LOW", AuditResponse.Risk.MODEL)), Lang.of("tr"));
        assertThat(tr).contains("yüksek");
        assertThat(tr).doesNotContain("temiz");
    }
}
