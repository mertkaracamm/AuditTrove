package com.audittrove.financial;

import java.util.List;

/**
 * LLM'in belgeden doldurduğu gelir tablosu çıkarımı. Tutarlar belgede YAZILDIĞI gibi tutulur;
 * böylece doğrulayıcı onları sayfada harfiyen arayabilir. Yorum yok, sadece okunan değer.
 */
public record StatementExtraction(boolean found, Unit unit, Periods periods, List<Item> items) {

    public StatementExtraction {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** Tablonun beyan ettiği para birimi ve ölçek ("Sunum Para Birimi 1.000 TL" → TRY, thousand). */
    public record Unit(String currency, String scale) {}

    /** Sütun başlıkları; sıra belgeye göre değişir, hangisinin cari olduğunu çıkarım söyler. */
    public record Periods(String current, String previous) {}

    /** Tek kalem: anahtar, belgedeki etiket, iki dönemin yazılı tutarı ve bulunduğu sayfa. */
    public record Item(String key, String label, String current, String previous, int page) {
        public LineItemKey lineItem() {
            return LineItemKey.fromJsonKey(key);
        }
    }

    public static StatementExtraction none() {
        return new StatementExtraction(false, null, null, List.of());
    }
}
