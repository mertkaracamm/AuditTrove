package com.audittrove.diff;

import com.audittrove.api.AuditResponse;
import com.audittrove.financial.Lang;
import com.audittrove.pdf.PageText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** İki sözleşme sürümü: farklar kodla bulunur, sınıflanır, sıralanır. PDF yok, satırlar elle verilir. */
class DiffEngineTest {

    private static PageText.Line line(String text, int row) {
        return new PageText.Line(text, new AuditResponse.Rect(0.1, 0.1 + row * 0.02, 0.8, 0.015));
    }

    // Satırlar arasında ekstra boşluk: paragraf sınırı (0.02 adım yerine 0.05).
    private static PageText.Line lineGap(String text, int row) {
        return new PageText.Line(text, new AuditResponse.Rect(0.1, 0.1 + row * 0.02 + 0.03, 0.8, 0.015));
    }

    private static final Map<Integer, PageText> OLD = Map.of(1, new PageText(1, List.of(
            line("KİRA SÖZLEŞMESİ", 0),
            line("Madde 3 - Kira bedeli. Aylık kira bedeli 42.500 TL olup her ayın 5'ine kadar peşin", 1),
            line("ödenir. Yıllık artış TÜFE oranında uygulanır.", 2),
            line("Madde 4 - Depozito. Kiracı 85.000 TL depozito verir.", 3),
            line("Madde 5 - Gecikme. Geciken her gün için aylık kiranın %0,5'i oranında gecikme bedeli uygulanır.", 4),
            line("Madde 6 - Giderler. Aidat, elektrik, su ve internet giderleri kiracıya aittir.", 5),
            line("Madde 7 - Süre. Sözleşme 12 ay sürelidir.", 6))));

    private static final Map<Integer, PageText> NEW = Map.of(1, new PageText(1, List.of(
            line("KİRA SÖZLEŞMESİ", 0),
            line("Madde 3 - Kira bedeli. Aylık kira bedeli 47.000 TL olup her ayın 5'ine kadar peşin", 1),
            line("ödenir. Yıllık artış TÜFE oranında uygulanır.", 2),
            line("Madde 4 - Depozito. Kiracı 85.000 TL depozito verir.", 3),
            line("Madde 5 - Gecikme. Geciken her gün için aylık kiranın %1'i oranında gecikme bedeli uygulanır.", 4),
            line("Madde 6 - Giderler. Aidat, elektrik, su, doğalgaz ve internet giderleri kiracıya aittir.", 5),
            line("Madde 7 - Süre. Sözleşme 12 ay sürelidir.", 6),
            line("Madde 8 - Evcil hayvan. Kiracı evcil hayvan besleyemez.", 7))));

    @Test
    void headersSplitUnitsAndParagraphGapsToo() {
        var units = DiffEngine.units(OLD);
        // Başlık + Madde 3 (iki satır) + 4 + 5 + 6 + 7 = 6 birim; Madde 3'ün devam satırı aynı birimde.
        assertThat(units).hasSize(6);
        assertThat(units.get(1).text()).contains("42.500 TL").contains("TÜFE");
        assertThat(units.get(1).clauseKey()).isEqualTo("madde 3");

        var gapped = DiffEngine.units(Map.of(1, new PageText(1, List.of(
                line("Taraflar bu sözleşmeyi karşılıklı olarak imzalamıştır.", 0),
                lineGap("Uyuşmazlıklarda İstanbul mahkemeleri yetkilidir.", 1)))));
        assertThat(gapped).hasSize(2);
    }

    @Test
    void changesAreClassifiedAndOrdered() {
        var al = DiffEngine.align(DiffEngine.units(OLD), DiffEngine.units(NEW));
        var changes = DiffEngine.changes(al);
        // Değişen: Madde 3 (sayı), Madde 5 (yüzde), Madde 6 (ifade), Madde 8 (eklendi). Değişmeyen: başlık, 4, 7.
        assertThat(changes).hasSize(4);
        assertThat(DiffEngine.unchangedCount(al)).isEqualTo(3);

        var rent = changes.get(0);
        assertThat(rent.kind()).isEqualTo(DiffResponse.Change.NUMBER);
        assertThat(rent.oldNumbers()).containsExactly("42.500");
        assertThat(rent.newNumbers()).containsExactly("47.000");
        assertThat(rent.pagesA()).containsExactly(1);
        assertThat(rent.anchorsA()).hasSize(1);
        // Madde 3 iki satır: çıpa iki satırı kapsar.
        assertThat(rent.anchorsA().get(0).rects().get(0).h()).isCloseTo(0.035, org.assertj.core.data.Offset.offset(1e-9));

        var delay = changes.get(1);
        assertThat(delay.kind()).isEqualTo(DiffResponse.Change.NUMBER);
        assertThat(delay.oldNumbers()).containsExactly("0,5");
        assertThat(delay.newNumbers()).containsExactly("1");

        var utilities = changes.get(2);
        assertThat(utilities.kind()).isEqualTo(DiffResponse.Change.TEXT);
        assertThat(utilities.newText()).contains("doğalgaz");

        var pets = changes.get(3);
        assertThat(pets.kind()).isEqualTo(DiffResponse.Change.ADDED);
        assertThat(pets.pagesA()).isEmpty();
        assertThat(pets.pagesB()).containsExactly(1);
    }

    @Test
    void removedClauseAndRenumberingStillAlignByContent() {
        var newer = Map.of(1, new PageText(1, List.of(
                line("KİRA SÖZLEŞMESİ", 0),
                line("Madde 3 - Kira bedeli. Aylık kira bedeli 42.500 TL olup her ayın 5'ine kadar peşin", 1),
                line("ödenir. Yıllık artış TÜFE oranında uygulanır.", 2),
                // Depozito maddesi çıkarıldı; sonrakiler yeniden numaralandı.
                line("Madde 4 - Gecikme. Geciken her gün için aylık kiranın %0,5'i oranında gecikme bedeli uygulanır.", 3),
                line("Madde 5 - Giderler. Aidat, elektrik, su ve internet giderleri kiracıya aittir.", 4),
                line("Madde 6 - Süre. Sözleşme 12 ay sürelidir.", 5))));
        var changes = DiffEngine.changes(DiffEngine.align(DiffEngine.units(OLD), DiffEngine.units(newer)));
        // Numara değişimi sayı farkı sayılır ("Madde 5" → "Madde 4"), ama içerik eşleşir; depozito REMOVED olur.
        var removed = changes.stream().filter(c -> c.kind().equals(DiffResponse.Change.REMOVED)).toList();
        assertThat(removed).hasSize(1);
        assertThat(removed.get(0).oldText()).contains("Depozito");
        assertThat(changes.stream().filter(c -> c.kind().equals(DiffResponse.Change.ADDED)).toList()).isEmpty();
    }

    @Test
    void narrativeNumbersMustComeFromTheClauseText() {
        var c = new DiffResponse.Change(DiffResponse.Change.NUMBER, null, "", "",
                "Aylık kira bedeli 42.500 TL", "Aylık kira bedeli 47.000 TL", List.of("42.500"), List.of("47.000"),
                List.of(1), List.of(1), List.of(), List.of());
        assertThat(DocumentDiffService.explanationIsGrounded("Kira 42.500 TL'den 47.000 TL'ye çıkmış.", c)).isTrue();
        assertThat(DocumentDiffService.explanationIsGrounded("Kira 42,500 TL'den 47,000 TL'ye çıkmış (%10,6 artış).", c)).isFalse();
        assertThat(DocumentDiffService.fallbackExplanation(c, Lang.TR)).isEqualTo("Bu maddede 42.500 → 47.000 olarak değişmiş.");
        assertThat(DocumentDiffService.fallbackTitle(new DiffResponse.Change(DiffResponse.Change.TEXT, null, "", "",
                "Madde 6 - Giderler. Aidat kiracıya aittir.", "Madde 6 - Giderler. Aidat ve doğalgaz kiracıya aittir.",
                List.of(), List.of(), List.of(1), List.of(1), List.of(), List.of()), Lang.TR)).isEqualTo("Madde 6");
        assertThat(DocumentDiffService.summary(List.of(c), 1, Lang.TR)).isEqualTo("1 değişiklik: 1 sayı/tutar, 0 ifade, 0 eklenen, 0 çıkarılan.");
    }
}
