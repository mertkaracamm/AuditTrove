package com.audittrove.report;

/**
 * Skor ölçeği. Bandı en ağır bulgu seçer; band içindeki kademeyi bulguların ağırlık toplamı belirler.
 * Ağırlık kullanılmasının sebebi: bulgular sayıldığında tek bir düşük önemli bulgu eşiği geçirip
 * skoru bir kademe oynatıyordu. Sınırdaki bir maddede modeller fikir değiştirince aynı belge farklı
 * skor alıyordu. Ağırlıkla sınır, gerçek bir ağırlaşma olmadan aşılmıyor.
 */
public final class ScoreScale {

    /** Bu toplama kadar "hafif": tek bir yüksek, ya da bir yüksek ile bir düşük. */
    public static final int LIGHT_WEIGHT_MAX = 4;

    private ScoreScale() {
    }

    /**
     * @param top    en ağır bulgunun derecesi (CRITICAL 4, HIGH 3, MEDIUM 2, LOW 1, bulgu yoksa 0)
     * @param weight skora giren bulguların derece toplamı
     */
    public static int of(int top, int weight) {
        boolean light = weight <= LIGHT_WEIGHT_MAX;
        return switch (top) {
            case 4 -> light ? 22 : 12;   // kırmızı — madde madde
            case 3 -> light ? 47 : 36;   // turuncu — dikkatle
            case 2 -> light ? 72 : 60;   // sarı — gözden geçir
            case 1 -> light ? 88 : 82;   // yeşil — küçük notlar
            default -> 95;               // yeşil — bulgu yok
        };
    }
}
