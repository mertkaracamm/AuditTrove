package com.audittrove.diff;

import com.audittrove.audit.InvalidDocumentException;
import com.audittrove.financial.Lang;
import com.audittrove.financial.NumberText;
import com.audittrove.llm.OpenAiAuditLlmClient;
import com.audittrove.pdf.PageText;
import com.audittrove.pdf.PdfGeometry;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * İki belge sürümünü karşılaştırır. Farklar DiffEngine'de kodla bulunur; model yalnızca her farkı bir cümleyle
 * anlatır ve belgenin sunulduğu taraf için lehte/aleyhte/nötr der. Anlatımdaki sayılar iki metinden birinde
 * geçmiyorsa cümle kodun şablonuna düşer; model erişilemezse rapor şablon cümlelerle döner, iş düşmez.
 */
@Service
public class DocumentDiffService {
    private static final Logger log = LoggerFactory.getLogger(DocumentDiffService.class);
    static final int MAX_CHANGES = 40;

    private final OpenAiAuditLlmClient llm;
    private final long maxPdfBytes;

    public DocumentDiffService(OpenAiAuditLlmClient llm,
                               @Value("${audittrove.max-pdf-bytes:15728640}") long maxPdfBytes) {
        this.llm = llm;
        this.maxPdfBytes = maxPdfBytes;
    }

    public DiffResponse diff(byte[] oldPdf, byte[] newPdf, String language) {
        validate(oldPdf);
        validate(newPdf);
        Lang lang = Lang.of(language);
        Map<Integer, PageText> a, b;
        try {
            a = PdfGeometry.read(oldPdf);
            b = PdfGeometry.read(newPdf);
        } catch (IOException e) {
            throw new InvalidDocumentException("PDF okunamadı", e);
        }
        List<DiffEngine.Unit> ua = DiffEngine.units(a), ub = DiffEngine.units(b);
        if (ua.isEmpty() || ub.isEmpty()) {
            throw new InvalidDocumentException("Belgelerden birinde okunabilir metin yok");
        }
        DiffEngine.Alignment al = DiffEngine.align(ua, ub);
        List<DiffResponse.Change> changes = DiffEngine.changes(al);
        int total = changes.size();
        if (changes.size() > MAX_CHANGES) changes = new ArrayList<>(changes.subList(0, MAX_CHANGES));
        changes = narrate(changes, lang);
        double matched = (double) al.pairs().size() / Math.max(1, Math.max(ua.size(), ub.size()));
        return new DiffResponse(lang.code(), a.size(), b.size(), summary(changes, total, lang), changes,
                DiffEngine.unchangedCount(al), ua.size(), ub.size(), Math.round(matched * 1000) / 1000.0);
    }

    /** Her fark için başlık + tek cümle + etki; model tek çağrıda hepsini alır. Başarısızsa şablon. */
    List<DiffResponse.Change> narrate(List<DiffResponse.Change> changes, Lang lang) {
        List<DiffResponse.Change> out = new ArrayList<>();
        if (changes.isEmpty()) return out;
        JsonNode node = null;
        try {
            node = llm.completeJson("document_diff_narrative", schema(), systemPrompt(lang), userPrompt(changes, lang));
        } catch (Exception e) {
            log.warn("Fark anlatimi alinamadi, sablon cumleler kullaniliyor: {}", e.toString());
        }
        Map<Integer, JsonNode> byIndex = new java.util.HashMap<>();
        if (node != null) for (JsonNode item : node.path("items")) byIndex.put(item.path("index").asInt(-1), item);
        for (int i = 0; i < changes.size(); i++) {
            DiffResponse.Change c = changes.get(i);
            JsonNode item = byIndex.get(i + 1);
            String title = item == null ? "" : item.path("title").asText("").strip();
            String explanation = item == null ? "" : item.path("explanation").asText("").strip();
            String impact = item == null ? "" : item.path("impact").asText("").strip();
            if (!explanationIsGrounded(explanation, c)) explanation = "";
            if (title.isEmpty()) title = fallbackTitle(c, lang);
            if (explanation.isEmpty()) explanation = fallbackExplanation(c, lang);
            if (!Set.of(DiffResponse.Change.FAVORABLE, DiffResponse.Change.UNFAVORABLE, DiffResponse.Change.NEUTRAL).contains(impact)) {
                impact = DiffResponse.Change.NEUTRAL;
            }
            out.add(c.withNarrative(title, explanation, impact));
        }
        return out;
    }

    /** Anlatımdaki her sayı (3+ hane) eski ya da yeni metinde geçmeli; aksi halde cümle güvenilmez. */
    static boolean explanationIsGrounded(String explanation, DiffResponse.Change c) {
        if (explanation == null || explanation.isEmpty()) return false;
        Set<String> keys = NumberText.digitKeys(c.oldText() + "\n" + c.newText());
        Set<String> pct = NumberText.percentKeys(c.oldText() + "\n" + c.newText());
        Set<String> ePct = NumberText.percentKeys(explanation);
        for (String k : NumberText.digitKeys(explanation)) {
            if (k.length() < 3) continue;
            if (keys.contains(k)) continue;
            if (ePct.contains("P" + k) && pct.contains("P" + k)) continue;
            return false;
        }
        return true;
    }

    static String fallbackTitle(DiffResponse.Change c, Lang lang) {
        String base = c.newText().isEmpty() ? c.oldText() : c.newText();
        String key = DiffEngine.clauseKey(base);
        if (key != null) {
            // "madde 5" → "Madde 5"; "n 5.2" → "5.2"
            String k = key.startsWith("n ") ? key.substring(2) : Character.toUpperCase(key.charAt(0)) + key.substring(1);
            return k;
        }
        // Tablo satırında başlık kalemin adıdır; rakamlar başlığa girmez ("Hasılat 5 21 305,1 15 980,2" → "Hasılat").
        int cut = base.length();
        java.util.regex.Matcher firstNumber = java.util.regex.Pattern.compile("\\d").matcher(base);
        // Rakamdan önceki kısım iki kelimeden azsa ("Dipnot 9 - ...") başlık cümlenin ilk parçası olur.
        if (firstNumber.find() && firstNumber.start() > 2
                && (DiffEngine.looksLikeTableRow(base)
                    || base.substring(0, firstNumber.start()).strip().split("\\s+").length >= 2)) {
            cut = firstNumber.start();
        }
        for (char stop : new char[]{'.', ':', ';'}) { int i = base.indexOf(stop); if (i > 8 && i < cut) cut = i; }
        String head = base.substring(0, Math.min(cut, 60)).strip();
        return head.isEmpty() ? (lang.isTurkish() ? "Değişiklik" : "Change") : head;
    }

    static String fallbackExplanation(DiffResponse.Change c, Lang lang) {
        boolean tr = lang.isTurkish();
        switch (c.kind()) {
            case DiffResponse.Change.NUMBER: {
                String olds = String.join(", ", c.oldNumbers()), news = String.join(", ", c.newNumbers());
                if (olds.isEmpty()) return tr ? "Bu maddeye " + news + " değeri eklenmiş." : "The value " + news + " was added to this clause.";
                if (news.isEmpty()) return tr ? "Bu maddedeki " + olds + " değeri çıkarılmış." : "The value " + olds + " was removed from this clause.";
                return tr ? "Bu maddede " + olds + " → " + news + " olarak değişmiş." : "In this clause " + olds + " changed to " + news + ".";
            }
            case DiffResponse.Change.ADDED: return tr ? "Yeni sürüme eklenmiş." : "Added in the new version.";
            case DiffResponse.Change.REMOVED: return tr ? "Yeni sürümde çıkarılmış." : "Removed in the new version.";
            default: return tr ? "Bu maddenin ifadesi değişmiş." : "The wording of this clause changed.";
        }
    }

    static String summary(List<DiffResponse.Change> changes, int total, Lang lang) {
        int num = 0, text = 0, added = 0, removed = 0;
        for (DiffResponse.Change c : changes) {
            switch (c.kind()) {
                case DiffResponse.Change.NUMBER -> num++;
                case DiffResponse.Change.TEXT -> text++;
                case DiffResponse.Change.ADDED -> added++;
                default -> removed++;
            }
        }
        if (total == 0) return lang.isTurkish() ? "İki sürüm arasında fark bulunmadı." : "No differences were found between the two versions.";
        String s = lang.isTurkish()
                ? total + " değişiklik: " + num + " sayı/tutar, " + text + " ifade, " + added + " eklenen, " + removed + " çıkarılan."
                : total + " changes: " + num + " numeric, " + text + " wording, " + added + " added, " + removed + " removed.";
        if (total > changes.size()) s += lang.isTurkish() ? " İlk " + changes.size() + " tanesi listelendi." : " The first " + changes.size() + " are listed.";
        return s;
    }

    private static String systemPrompt(Lang lang) {
        if (lang.isTurkish()) {
            return """
                Bir belge inceleme uygulamasında iki belge sürümü arasındaki farkları anlatıyorsun. Farklar kodla bulundu; sen yalnızca anlatırsın.
                Her fark için: kısa başlık (en fazla 6 kelime, maddenin konusu), tek cümlelik açıklama (ne değişti; sayıları metinde yazıldığı gibi aktar)
                ve etki: belgenin sunulduğu tarafın (kiracı, çalışan, müşteri, sigortalı, alıcı) gözünden FAVORABLE (lehte), UNFAVORABLE (aleyhte) ya da NEUTRAL.
                Kurallar: yalnızca verilen eski/yeni metne dayan; metinde olmayan sayı ya da bilgi ekleme; hukuki ya da mali tavsiye verme, mevzuata uygunluk yorumu yapma.
                Finansal tablo satırlarında kalem adından sonra gelen tek başına küçük sayı dipnot referansıdır (ör. "Hasılat 5 18 420,6"); tutar değildir, cümlede kullanma.
                Emin değilsen NEUTRAL de. Çıktı tamamen Türkçe.
                """;
        }
        return """
            You describe the differences between two versions of a document in a document review app. The differences were found by code; you only narrate them.
            For each change: a short title (max 6 words, the clause topic), a one-sentence explanation (what changed; quote numbers exactly as written)
            and impact from the perspective of the party the document is presented to (tenant, employee, customer, insured, buyer): FAVORABLE, UNFAVORABLE or NEUTRAL.
            Rules: rely only on the old/new text given; add no number or fact that is not in the text; no legal or financial advice, no compliance assessment.
            In financial statement rows, a small standalone number right after the line item label is a footnote reference (e.g. "Revenue 5 18,420.6"), not an amount; never use it in the sentence.
            When unsure, say NEUTRAL. Output entirely in English.
            """;
    }

    private static String userPrompt(List<DiffResponse.Change> changes, Lang lang) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < changes.size(); i++) {
            DiffResponse.Change c = changes.get(i);
            sb.append("### ").append(i + 1).append(" [").append(c.kind()).append("]\n");
            sb.append(lang.isTurkish() ? "ESKİ: " : "OLD: ").append(c.oldText().isEmpty() ? "-" : c.oldText()).append('\n');
            sb.append(lang.isTurkish() ? "YENİ: " : "NEW: ").append(c.newText().isEmpty() ? "-" : c.newText()).append("\n\n");
        }
        return sb.toString();
    }

    private static Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("items"),
                "properties", Map.of(
                        "items", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", List.of("index", "title", "explanation", "impact"),
                                        "properties", Map.of(
                                                "index", Map.of("type", "integer"),
                                                "title", Map.of("type", "string"),
                                                "explanation", Map.of("type", "string"),
                                                "impact", Map.of("type", "string", "enum", List.of("FAVORABLE", "UNFAVORABLE", "NEUTRAL")))))));
    }

    private void validate(byte[] content) {
        if (content == null || content.length < 5) throw new InvalidDocumentException("PDF dosyası boş veya geçersiz");
        if (content.length > maxPdfBytes) throw new InvalidDocumentException("PDF izin verilen boyutu aşıyor");
        if (content[0] != '%' || content[1] != 'P' || content[2] != 'D' || content[3] != 'F' || content[4] != '-') {
            throw new InvalidDocumentException("Dosya geçerli bir PDF değil");
        }
    }
}