package com.audittrove.diff;

import com.audittrove.api.AuditResponse;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;

/**
 * İki belge sürümü arasındaki fark raporu. Farklar kodda bulunur ve sınıflanır; model yalnızca anlatır.
 * language: rapor dili. summary: kodda yazılan sayım cümlesi. unchangedUnits: eşleşip değişmeyen birim sayısı.
 */
public record DiffResponse(String language, int pageCountA, int pageCountB, String summary,
                           List<Change> changes, int unchangedUnits) {
    @JsonCreator
    public DiffResponse {
        language = language == null ? "" : language;
        summary = summary == null ? "" : summary;
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    /**
     * kind: NUMBER (aynı madde, sayılar değişmiş), TEXT (aynı madde, ifade değişmiş), ADDED (yalnız yeni sürümde),
     * REMOVED (yalnız eski sürümde). impact: belgenin sunulduğu tarafın (kiracı, çalışan, müşteri, sigortalı, alıcı)
     * gözünden FAVORABLE / UNFAVORABLE / NEUTRAL; modelin yorumudur, skora girmez.
     * oldNumbers/newNumbers: belgede yazıldığı biçimiyle değişen sayılar, sıra eşleştirilmiş.
     * anchorsA/anchorsB: eski ve yeni belgede maddenin yeri; görüntüleyici iki tarafı da boyar.
     */
    public record Change(String kind, String impact, String title, String explanation,
                         String oldText, String newText,
                         List<String> oldNumbers, List<String> newNumbers,
                         List<Integer> pagesA, List<Integer> pagesB,
                         List<AuditResponse.Anchor> anchorsA, List<AuditResponse.Anchor> anchorsB) {
        public static final String NUMBER = "NUMBER";
        public static final String TEXT = "TEXT";
        public static final String ADDED = "ADDED";
        public static final String REMOVED = "REMOVED";
        public static final String FAVORABLE = "FAVORABLE";
        public static final String UNFAVORABLE = "UNFAVORABLE";
        public static final String NEUTRAL = "NEUTRAL";

        @JsonCreator
        public Change {
            impact = impact == null || impact.isBlank() ? NEUTRAL : impact;
            title = title == null ? "" : title;
            explanation = explanation == null ? "" : explanation;
            oldText = oldText == null ? "" : oldText;
            newText = newText == null ? "" : newText;
            oldNumbers = oldNumbers == null ? List.of() : List.copyOf(oldNumbers);
            newNumbers = newNumbers == null ? List.of() : List.copyOf(newNumbers);
            pagesA = pagesA == null ? List.of() : List.copyOf(pagesA);
            pagesB = pagesB == null ? List.of() : List.copyOf(pagesB);
            anchorsA = anchorsA == null ? List.of() : List.copyOf(anchorsA);
            anchorsB = anchorsB == null ? List.of() : List.copyOf(anchorsB);
        }

        public Change withNarrative(String title, String explanation, String impact) {
            return new Change(kind, impact, title, explanation, oldText, newText, oldNumbers, newNumbers, pagesA, pagesB, anchorsA, anchorsB);
        }
    }
}
