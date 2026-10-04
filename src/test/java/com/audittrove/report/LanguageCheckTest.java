package com.audittrove.report;

import com.audittrove.financial.Lang;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Turkce raporda Ingilizce cumle kalmamali; emin olunamayan kisa metinlerde de susmali. */
class LanguageCheckTest {

    @Test
    void aShortEnglishSentenceIsCaught() {
        // Tek Ingilizce baglac ("the") tasiyor, esik farkina takilmiyordu ve Turkce raporda kaliyordu.
        assertThat(LanguageCheck.detect("The contract ends automatically after 6 months without renewal."))
                .isEqualTo(Lang.EN);
    }

    @Test
    void turkishSentencesAreNotMistakenForEnglish() {
        assertThat(LanguageCheck.detect("Sözleşme, fesih için 15 gün önceden yazılı bildirim gerektirmektedir."))
                .isEqualTo(Lang.TR);
        assertThat(LanguageCheck.detect("Yol ve konaklama giderleri hizmet alan tarafından karşılanır."))
                .isEqualTo(Lang.TR);
        assertThat(LanguageCheck.detect("Taraflar arasinda duzenlenen bu belge iki nusha olarak imzalanmistir."))
                .isNotEqualTo(Lang.EN);
    }

    @Test
    void shortOrUnclearTextStaysSilent() {
        assertThat(LanguageCheck.detect("Otomatik yenileme")).isNull();
        assertThat(LanguageCheck.detect("Penalty clause")).isNull();
        assertThat(LanguageCheck.detect(null)).isNull();
    }

    @Test
    void aLongerEnglishSentenceIsStillCaught() {
        assertThat(LanguageCheck.detect(
                "The lease renews for one year unless either party gives written notice thirty days in advance."))
                .isEqualTo(Lang.EN);
    }
}
