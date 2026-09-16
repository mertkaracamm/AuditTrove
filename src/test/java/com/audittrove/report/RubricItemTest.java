package com.audittrove.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RubricItemTest {

    @Test
    void generalQuestionsAreAskedForEveryKind() {
        // Danismanlik sozlesmesi "hizmet sozlesmesi" sayilip is sozlesmesi sorularini aliyordu;
        // icindeki tek tarafli fesih hakki hic sorulmadigi icin rapor temiz cikiyordu.
        List<RubricItem> items = RubricItem.withGeneral(RubricItem.forKind(RubricItem.Kind.EMPLOYMENT));
        assertThat(items).contains(RubricItem.GEN_UNILATERAL_TERMINATION, RubricItem.GEN_AUTO_RENEWAL);
        assertThat(items).containsAll(RubricItem.forKind(RubricItem.Kind.EMPLOYMENT));
    }

    @Test
    void aGeneralQuestionIsSkippedWhenTheKindAlreadyAsksIt() {
        List<RubricItem> items = RubricItem.withGeneral(RubricItem.forKind(RubricItem.Kind.RENTAL));
        assertThat(items).contains(RubricItem.RENT_UNILATERAL_TERMINATION, RubricItem.RENT_AUTO_RENEWAL,
                RubricItem.RENT_PENALTIES);
        assertThat(items).doesNotContain(RubricItem.GEN_UNILATERAL_TERMINATION, RubricItem.GEN_AUTO_RENEWAL,
                RubricItem.GEN_PENALTIES);
        // Kirada karsiligi olmayan genel sorular yine sorulur.
        assertThat(items).contains(RubricItem.GEN_LIABILITY_WAIVER, RubricItem.GEN_UNCLEAR_PAYMENT);
    }

    @Test
    void employmentPenaltyClauseSuppressesTheGeneralOne() {
        List<RubricItem> items = RubricItem.withGeneral(RubricItem.forKind(RubricItem.Kind.EMPLOYMENT));
        assertThat(items).contains(RubricItem.EMP_LIQUIDATED_DAMAGES);
        assertThat(items).doesNotContain(RubricItem.GEN_PENALTIES);
    }

    @Test
    void theGeneralListItselfIsNotDuplicated() {
        List<RubricItem> items = RubricItem.withGeneral(RubricItem.forKind(RubricItem.Kind.GENERAL));
        assertThat(items).hasSize(RubricItem.forKind(RubricItem.Kind.GENERAL).size());
        assertThat(items).doesNotHaveDuplicates();
    }

    @Test
    void anEmptyKindStillGetsTheGeneralQuestions() {
        // Hicbir turune oturmayan belge sorusuz kalmamali; sorusuz belge her zaman 95 aliyordu.
        List<RubricItem> items = RubricItem.withGeneral(RubricItem.forKind(RubricItem.Kind.OTHER));
        assertThat(items).isNotEmpty();
        assertThat(items).containsAll(RubricItem.forKind(RubricItem.Kind.GENERAL));
    }

    @Test
    void mergedKindsDoNotRepeatAQuestion() {
        List<RubricItem> merged = new java.util.ArrayList<>(RubricItem.forKind(RubricItem.Kind.RENTAL));
        merged.addAll(RubricItem.forKind(RubricItem.Kind.SUBSCRIPTION));
        assertThat(RubricItem.withGeneral(merged)).doesNotHaveDuplicates();
    }
}
