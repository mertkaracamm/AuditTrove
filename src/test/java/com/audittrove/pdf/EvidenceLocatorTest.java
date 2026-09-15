package com.audittrove.pdf;

import com.audittrove.api.AuditResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Alıntının belge üzerindeki yeri: satır sonuna denk gelse de bulunmalı, rapor dilinden etkilenmemeli. */
class EvidenceLocatorTest {

    private static PageText.Line line(String text, double y) {
        return new PageText.Line(text, new AuditResponse.Rect(0.0857, y, 0.8295, 0.0063));
    }

    /** Araç satış sözleşmesi: alıntı iki satıra bölünmüş ("... sold \"as" / "is\"."). */
    private static final PageText VEHICLE_PAGE = new PageText(1, List.of(
            line("Clause 2 - Condition of the Vehicle", 0.3900),
            line("2.1. The buyer accepts the vehicle as seen, inspected and test driven. The vehicle is sold \"as", 0.4059),
            line("is\".", 0.4338),
            line("2.2. The seller gives no undertaking as to the accuracy of the recorded mileage.", 0.4775)));

    @Test
    void quoteSplitAcrossTwoLinesIsFound() {
        List<AuditResponse.Rect> rects = EvidenceLocator.locateLiteral(
                "The vehicle is sold \"as is\".", VEHICLE_PAGE);
        assertThat(rects).hasSize(1);
        // İki satır birleşerek tek kutu olur: üstte 2.1 satırı, altta devamı.
        assertThat(rects.get(0).y()).isEqualTo(0.4059);
        assertThat(rects.get(0).y() + rects.get(0).h()).isGreaterThan(0.4338);
    }

    @Test
    void quoteIsFoundWhateverTheReportLanguage() {
        // Alıntı belgenin dilinde kalır; Türkçe rapor da aynı yeri göstermeli.
        List<AuditResponse.Rect> rects = EvidenceLocator.locateLiteral(
                "the vehicle is sold \"as is\"", VEHICLE_PAGE);
        assertThat(rects).hasSize(1);
        assertThat(rects.get(0).y()).isEqualTo(0.4059);
    }

    @Test
    void curlyQuotesAndDoubleSpacesDoNotBreakTheMatch() {
        List<AuditResponse.Rect> rects = EvidenceLocator.locateLiteral(
                "The  vehicle is sold “as is”.", VEHICLE_PAGE);
        assertThat(rects).isNotEmpty();
    }

    @Test
    void quoteThatIsNotOnThePageFindsNothing() {
        assertThat(EvidenceLocator.locateLiteral(
                "The seller provides a twelve month warranty.", VEHICLE_PAGE)).isEmpty();
    }

    @Test
    void tooShortQuoteIsNotSearchedLiterally() {
        // Kısa parça sayfada rastgele yerlere denk gelir; birebir arama için en az 12 karakter istenir.
        assertThat(EvidenceLocator.locateLiteral("as is", VEHICLE_PAGE)).isEmpty();
    }
}
