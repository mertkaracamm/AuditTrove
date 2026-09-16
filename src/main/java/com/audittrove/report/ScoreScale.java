package com.audittrove.report;

/**
 * Skor ölçeği: beş sabit değer, bandı en ağır bulgu belirler.
 *
 * Bir ara band içinde ağırlık toplamına göre ikinci bir kademe vardı (22/12, 47/36 gibi). O kademenin
 * eşiği az bulgulu belgelerde tam ortaya denk geliyordu: tek bir orta önemli madde koşudan koşuya
 * gelip gidince aynı belge 47 ve 36 alıyordu. Gürültülü bir sayının üstündeki her eşik er geç atlanır;
 * eşiği kaldırmak "aynı belge aynı skoru alır" sözünü tutmanın tek yolu.
 */
public final class ScoreScale {

    private ScoreScale() {
    }

    /**
     * @param top en ağır bulgunun derecesi (CRITICAL 4, HIGH 3, MEDIUM 2, LOW 1, bulgu yoksa 0)
     */
    public static int of(int top) {
        return switch (top) {
            case 4 -> 12;   // kırmızı — madde madde
            case 3 -> 36;   // turuncu — dikkatle
            case 2 -> 60;   // sarı — gözden geçir
            case 1 -> 82;   // yeşil — küçük notlar
            default -> 95;  // yeşil — bulgu yok
        };
    }
}
