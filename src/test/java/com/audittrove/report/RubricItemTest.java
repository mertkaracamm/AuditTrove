package com.audittrove.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RubricItemTest {

    @Test
    void generalQuestionsAreAskedForEveryKind() {
        // Danismanlik sozlesmesi "hizmet sozlesmesi" sayilip is sozlesmesi sorularini aliyordu;
        // icindeki tek tarafli fesih hakki hic sorulmadigi icin rapor temiz cikiyordu.
        List<RubricItem> items = RubricItem.withGeneral(RubricItem.Kind.EMPLOYMENT, RubricItem.forKind(RubricItem.Kind.EMPLOYMENT));
        assertThat(items).contains(RubricItem.GEN_UNILATERAL_TERMINATION, RubricItem.GEN_AUTO_RENEWAL);
        assertThat(items).containsAll(RubricItem.forKind(RubricItem.Kind.EMPLOYMENT));
    }

    @Test
    void aGeneralQuestionIsSkippedWhenTheKindAlreadyAsksIt() {
        List<RubricItem> items = RubricItem.withGeneral(RubricItem.Kind.RENTAL, RubricItem.forKind(RubricItem.Kind.RENTAL));
        assertThat(items).contains(RubricItem.RENT_UNILATERAL_TERMINATION, RubricItem.RENT_AUTO_RENEWAL,
                RubricItem.RENT_PENALTIES);
        assertThat(items).doesNotContain(RubricItem.GEN_UNILATERAL_TERMINATION, RubricItem.GEN_AUTO_RENEWAL,
                RubricItem.GEN_PENALTIES);
        // Kirada karsiligi olmayan genel sorular yine sorulur.
        assertThat(items).contains(RubricItem.GEN_LIABILITY_WAIVER, RubricItem.GEN_UNCLEAR_PAYMENT);
    }

    @Test
    void employmentPenaltyClauseSuppressesTheGeneralOne() {
        List<RubricItem> items = RubricItem.withGeneral(RubricItem.Kind.EMPLOYMENT, RubricItem.forKind(RubricItem.Kind.EMPLOYMENT));
        assertThat(items).contains(RubricItem.EMP_LIQUIDATED_DAMAGES);
        assertThat(items).doesNotContain(RubricItem.GEN_PENALTIES);
    }

    @Test
    void theGeneralListItselfIsNotDuplicated() {
        List<RubricItem> items = RubricItem.withGeneral(RubricItem.Kind.GENERAL, RubricItem.forKind(RubricItem.Kind.GENERAL));
        assertThat(items).hasSize(RubricItem.forKind(RubricItem.Kind.GENERAL).size());
        assertThat(items).doesNotHaveDuplicates();
    }

    @Test
    void anEmptyKindStillGetsTheGeneralQuestions() {
        // Hicbir turune oturmayan belge sorusuz kalmamali; sorusuz belge her zaman 95 aliyordu.
        List<RubricItem> items = RubricItem.withGeneral(RubricItem.Kind.OTHER, RubricItem.forKind(RubricItem.Kind.OTHER));
        assertThat(items).isNotEmpty();
        assertThat(items).containsAll(RubricItem.forKind(RubricItem.Kind.GENERAL));
    }

    @Test
    void mergedKindsDoNotRepeatAQuestion() {
        List<RubricItem> merged = new java.util.ArrayList<>(RubricItem.forKind(RubricItem.Kind.RENTAL));
        merged.addAll(RubricItem.forKind(RubricItem.Kind.SUBSCRIPTION));
        assertThat(RubricItem.withGeneral(RubricItem.Kind.RENTAL, merged)).doesNotHaveDuplicates();
    }

    @Test
    void theUnilateralTerminationQuestionExcludesMutualClauses() {
        // "Either party may terminate on the statutory notice period." cumlesi iken model bu maddeye
        // var diyordu: soru yalnizca "karsi taraf fesih edebiliyor mu" diye sordugu icin karsilikli
        // fesih maddesinde de cevap dogru oluyor, baslik ise alintinin tersini soyluyordu.
        String q = RubricItem.GEN_UNILATERAL_TERMINATION.question();
        assertThat(q).contains("the reader does not have");
        assertThat(q).contains("either party");
        assertThat(q).contains("present=false");
    }

    @Test
    void questionsAboutOneSidedRightsSayWhatDoesNotCount() {
        // Baslikta "tek tarafli" gecen her madde, karsilikli duzenlemeyi aciklikla disarida birakmali;
        // yoksa olgu sorusu yerine yorum sorusu sorulmus olur.
        for (RubricItem item : RubricItem.values()) {
            String en = item.title(com.audittrove.financial.Lang.EN).toLowerCase(java.util.Locale.ROOT);
            if (!en.contains("unilateral")) continue;
            assertThat(item.question().toLowerCase(java.util.Locale.ROOT))
                    .as("%s soyle bir soru sormali ki karsilikli madde var sayilmasin", item.id())
                    .containsAnyOf("present=false", "does not have", "without the employee");
        }
    }

    @Test
    void financialDocumentsAreAlsoAskedTheGeneralQuestions() {
        // Rakam yogun bir kredi formu "financial" sanilinca ne finansal ne sozlesme sorusu
        // tutuyor, rapor sifir bulguyla "temiz" cikiyordu. Hicbir tur bu sorulardan muaf degil.
        List<RubricItem> items = RubricItem.withGeneral(RubricItem.Kind.FINANCIAL,
                RubricItem.forKind(RubricItem.Kind.FINANCIAL));
        assertThat(items).containsAll(RubricItem.forKind(RubricItem.Kind.FINANCIAL));
        assertThat(items).containsAll(RubricItem.forKind(RubricItem.Kind.GENERAL));
        assertThat(items).doesNotHaveDuplicates();
    }
}
