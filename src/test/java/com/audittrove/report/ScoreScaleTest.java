package com.audittrove.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Skor ölçeğinin sabitliği: aynı belge her seferinde aynı skoru almalı, bulgu seti biraz oynasa da. */
class ScoreScaleTest {

    private static final int CRITICAL = 4, HIGH = 3, MEDIUM = 2, LOW = 1;

    @Test
    void nineFixedValues() {
        assertThat(new int[]{
                ScoreScale.of(CRITICAL, 4), ScoreScale.of(CRITICAL, 12),
                ScoreScale.of(HIGH, 3), ScoreScale.of(HIGH, 9),
                ScoreScale.of(MEDIUM, 2), ScoreScale.of(MEDIUM, 8),
                ScoreScale.of(LOW, 1), ScoreScale.of(LOW, 6),
                ScoreScale.of(0, 0)})
                .containsExactly(22, 12, 47, 36, 72, 60, 88, 82, 95);
    }

    /** Araç satış sözleşmesi: Türkçe sürüm 4 bulgu (2Y+1O+1D), İngilizce sürüm aynı belgenin
     *  çevirisi ama bir koşuda düşük önemli madde düşüyordu. Skor ikisinde de aynı kalmalı. */
    @Test
    void oneLowFindingNoLongerFlipsTheBand() {
        int withLow = ScoreScale.of(HIGH, HIGH + HIGH + MEDIUM + LOW);   // 2 yüksek, 1 orta, 1 düşük
        int withoutLow = ScoreScale.of(HIGH, HIGH + HIGH + MEDIUM);      // düşük olan düştü
        assertThat(withLow).isEqualTo(withoutLow).isEqualTo(36);
    }

    @Test
    void aSingleHighStaysInTheLighterStep() {
        assertThat(ScoreScale.of(HIGH, HIGH)).isEqualTo(47);
        assertThat(ScoreScale.of(HIGH, HIGH + LOW)).isEqualTo(47);
        // İki yüksek artık gerçekten ağır: bant içinde bir kademe aşağı.
        assertThat(ScoreScale.of(HIGH, HIGH + HIGH)).isEqualTo(36);
    }

    @Test
    void bandIsSetByTheMostSevereFindingAlone() {
        // Ağırlık ne olursa olsun en ağır bulgu bandı belirler; düşükler bandı yukarı taşımaz.
        assertThat(ScoreScale.of(LOW, 20)).isEqualTo(82);
        assertThat(ScoreScale.of(CRITICAL, 4)).isEqualTo(22);
    }

    @Test
    void noFindingsIsTheCleanScore() {
        assertThat(ScoreScale.of(0, 0)).isEqualTo(95);
    }
}
