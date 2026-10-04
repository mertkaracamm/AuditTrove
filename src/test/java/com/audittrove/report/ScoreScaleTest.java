package com.audittrove.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Skor ölçeğinin sabitliği: aynı belge her seferinde aynı skoru almalı, bulgu seti biraz oynasa da. */
class ScoreScaleTest {

    private static final int CRITICAL = 4, HIGH = 3, MEDIUM = 2, LOW = 1;

    @Test
    void fiveFixedValues() {
        assertThat(List.of(
                ScoreScale.of(CRITICAL), ScoreScale.of(HIGH), ScoreScale.of(MEDIUM),
                ScoreScale.of(LOW), ScoreScale.of(0)))
                .containsExactly(12, 36, 60, 82, 95);
    }

    @Test
    void bandIsSetByTheMostSevereFindingAlone() {
        // Bulgu sayisi skoru degistirmez: tek yuksek onemli bulgu da bes tane de ayni bandi verir.
        assertThat(ScoreScale.of(HIGH)).isEqualTo(36);
        assertThat(ScoreScale.of(LOW)).isEqualTo(82);
    }

    @Test
    void noFindingsIsTheCleanScore() {
        assertThat(ScoreScale.of(0)).isEqualTo(95);
    }

    @Test
    void anUnknownRankIsTreatedAsNoFinding() {
        assertThat(ScoreScale.of(-1)).isEqualTo(95);
        assertThat(ScoreScale.of(9)).isEqualTo(95);
    }
}
