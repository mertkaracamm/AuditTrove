package com.audittrove.financial;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Sayı okuma: kanıt tarafı ile sayfa tarafı aynı sonucu vermeli, yoksa bulgu yanlış sayfaya bağlanır. */
class NumberTextTest {

    @Test
    void spaceIsAThousandsSeparator() {
        // Türkçe finansal raporlarda binlik ayracı boşluk: "4 180,0" tek sayıdır.
        assertThat(NumberText.tokens("net yükümlülüğü 4 180,0 milyon TL karşılığıdır"))
                .containsExactly("4 180,0");
        assertThat(NumberText.tokens("hasılat 24 860,4 milyon TL'ye ulaşmıştır"))
                .containsExactly("24 860,4");
    }

    @Test
    void anotherNumberIsNotSwallowed() {
        // "1 054,2 milyon TL'si kur farkı" — iki ayrı sayı, boşluk ayracı ikisini birleştirmemeli.
        assertThat(NumberText.tokens("1 840,6 milyon TL kredi faizi, 1 054,2 milyon TL kur farkı"))
                .containsExactly("1 840,6", "1 054,2");
    }

    @Test
    void evidenceAndPageProduceTheSameKey() {
        // Kanıtta ve sayfada aynı tutar yazılı: anahtarlar eşleşmeli.
        String evidence = "Grup'un yabancı para net yükümlülüğü 4 180,0 milyon TL karşılığıdır.";
        String page = "Grup'un yabancı para cinsinden net yükümlülüğü 31 Aralık 2025 itibarıyla 4 180,0 "
                + "milyon TL karşılığıdır (2024: 2 640,0 milyon TL).";
        assertThat(NumberText.digitKeys(evidence)).contains("41800");
        assertThat(NumberText.digitKeys(page)).contains("41800");
    }

    @Test
    void aDifferentAmountDoesNotCollide() {
        // Başka sayfadaki "180,0" ile "4 180,0" karışmamalı; bulgu yanlış sayfaya gitmişti.
        assertThat(NumberText.digitKeys("İskonto oranının 2 puan artması hâlinde 180,0 milyon TL "
                + "değer düşüklüğü oluşacaktır.")).contains("1800").doesNotContain("41800");
        assertThat(NumberText.digitKeys("net yükümlülüğü 4 180,0 milyon TL"))
                .contains("41800").doesNotContain("1800");
    }

    @Test
    void dottedAndCommaFormsShareOneKey() {
        assertThat(NumberText.digits("28.984.491")).isEqualTo("28984491");
        assertThat(NumberText.digits("28,984,491")).isEqualTo("28984491");
    }

    @Test
    void strayeSpacesAroundSeparatorsAreJoined() {
        // Bazı PDF'ler ayraç etrafına boşluk sızdırır.
        assertThat(NumberText.tokens("68 .226 .515 tutarında")).containsExactly("68.226.515");
    }
}
