package com.audittrove.chat;

import com.audittrove.api.AuditResponse;
import com.audittrove.audit.InvalidDocumentException;
import com.audittrove.financial.Lang;
import com.audittrove.financial.NumberText;
import com.audittrove.llm.OpenAiAuditLlmClient;
import com.audittrove.report.LanguageCheck;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rapora soru sor. Model yalnızca gönderilen rapor ve sayfa metinlerinden cevap verir; kod cevabı doğrular:
 * anılan sayfalar gönderilenler arasında olmalı, cevaptaki sayılar o sayfalarda ya da raporda geçmeli.
 * Geçmiyorsa cevap "dayanaksız" işaretlenir, uydurulmuş sayı arayüze güvenilir diye çıkmaz.
 */
@Service
public class ReportChatService {
    private static final Logger log = LoggerFactory.getLogger(ReportChatService.class);

    static final int MAX_QUESTION_CHARS = 500;
    static final int MAX_PAGES_IN_PROMPT = 6;
    static final int MAX_PAGE_CHARS = 7000;
    static final int MAX_TOTAL_CHARS = 32000;

    private static final Pattern WORD = Pattern.compile("\\p{L}{3,}");
    private static final Set<String> STOP = Set.of(
            "bu", "şu", "ve", "ile", "için", "gibi", "kadar", "olan", "olarak", "mi", "mı", "mu", "mü", "ne", "nedir",
            "kaç", "hangi", "var", "yok", "the", "and", "for", "with", "what", "how", "much", "many", "which", "does",
            "this", "that", "are", "was", "were", "have", "has", "belge", "belgede", "rapor", "raporda", "document", "report");

    private final OpenAiAuditLlmClient llm;

    public ReportChatService(OpenAiAuditLlmClient llm) {
        this.llm = llm;
    }

    public ChatResponse answer(ChatRequest request) {
        if (request == null || request.question().isEmpty()) {
            throw new InvalidDocumentException("Soru boş");
        }
        if (request.question().length() > MAX_QUESTION_CHARS) {
            throw new InvalidDocumentException("Soru çok uzun");
        }
        Lang lang = Lang.of(request.language());
        AuditResponse report = request.report();
        List<AuditResponse.PageContent> selected = selectPages(request.question(), request.pages(), report);

        String system = systemPrompt(lang);
        String user = userPrompt(request.question(), report, selected, lang);
        JsonNode node = llm.completeJson("report_chat_answer", schema(), system, user);

        String answer = node.path("answer").asText("").strip();
        boolean grounded = node.path("grounded").asBoolean(false);
        List<Integer> pages = new ArrayList<>();
        for (JsonNode p : node.path("pages")) if (p.isInt()) pages.add(p.intValue());

        // Dil sapmışsa bir kez sıkı talimatla tekrar denenir; cevap yine sapıksa olduğu gibi kalır (cevap düşürülmez).
        Lang detected = LanguageCheck.detect(answer);
        if (detected != null && detected != lang) {
            JsonNode retry = llm.completeJson("report_chat_answer", schema(),
                    system + "\n\n" + (lang.isTurkish()
                            ? "ZORUNLU: Cevap tamamen Türkçe olmalı."
                            : "MANDATORY: The answer must be entirely in English."), user);
            String again = retry.path("answer").asText("").strip();
            if (!again.isEmpty() && LanguageCheck.detect(again) != (lang.isTurkish() ? Lang.EN : Lang.TR)) {
                answer = again;
                grounded = retry.path("grounded").asBoolean(false);
                pages.clear();
                for (JsonNode p : retry.path("pages")) if (p.isInt()) pages.add(p.intValue());
            }
        }

        return verify(answer, pages, grounded, selected, report);
    }

    /** Sorudaki kelime ve sayılarla en çok örtüşen sayfalar; bulgu sayfaları da eşit puanlı olsa öne geçer. */
    static List<AuditResponse.PageContent> selectPages(String question, List<AuditResponse.PageContent> pages, AuditResponse report) {
        if (pages.isEmpty()) return List.of();
        Set<String> qWords = contentWords(question);
        Set<String> qNums = NumberText.digitKeys(question);
        Set<Integer> findingPages = new HashSet<>();
        if (report != null) {
            for (AuditResponse.Risk r : report.risks()) {
                if (overlap(qWords, contentWords(r.title() + " " + r.finding())) >= 2) findingPages.addAll(r.pages());
            }
        }
        Map<AuditResponse.PageContent, Integer> score = new LinkedHashMap<>();
        for (AuditResponse.PageContent p : pages) {
            int s = overlap(qWords, contentWords(p.text())) * 2;
            for (String n : qNums) if (n.length() >= 2 && NumberText.digitKeys(p.text()).contains(n)) s += 3;
            if (findingPages.contains(p.page())) s += 4;
            score.put(p, s);
        }
        List<AuditResponse.PageContent> ranked = new ArrayList<>(pages);
        ranked.sort((a, b) -> {
            int d = Integer.compare(score.get(b), score.get(a));
            return d != 0 ? d : Integer.compare(a.page(), b.page());
        });
        List<AuditResponse.PageContent> out = new ArrayList<>();
        int total = 0;
        for (AuditResponse.PageContent p : ranked) {
            if (out.size() >= MAX_PAGES_IN_PROMPT) break;
            // Hiç örtüşmeyen sayfa yalnızca belge küçükse girer; büyük belgede gürültü olur.
            if (score.get(p) == 0 && pages.size() > MAX_PAGES_IN_PROMPT && !out.isEmpty()) break;
            String text = p.text().length() > MAX_PAGE_CHARS ? p.text().substring(0, MAX_PAGE_CHARS) : p.text();
            if (total + text.length() > MAX_TOTAL_CHARS) break;
            total += text.length();
            out.add(new AuditResponse.PageContent(p.page(), text));
        }
        out.sort((a, b) -> Integer.compare(a.page(), b.page()));
        return out;
    }

    /**
     * Kod tarafı doğrulama: sayfalar gönderilenlerden olmalı; cevaptaki her sayı (3+ hane) ya anılan
     * sayfalarda ya raporda geçmeli. Aksi halde grounded=false. Cevap metni değiştirilmez.
     */
    static ChatResponse verify(String answer, List<Integer> pages, boolean grounded,
                               List<AuditResponse.PageContent> sent, AuditResponse report) {
        Set<Integer> sentPages = new TreeSet<>();
        Map<Integer, String> textOf = new HashMap<>();
        for (AuditResponse.PageContent p : sent) { sentPages.add(p.page()); textOf.put(p.page(), p.text()); }
        List<Integer> cited = new ArrayList<>(new TreeSet<>(pages));
        cited.removeIf(p -> !sentPages.contains(p));

        StringBuilder basis = new StringBuilder();
        for (int p : cited) basis.append(textOf.get(p)).append('\n');
        if (cited.isEmpty()) for (String t : textOf.values()) basis.append(t).append('\n');
        if (report != null) {
            basis.append(report.summary()).append('\n');
            for (AuditResponse.Risk r : report.risks()) basis.append(r.finding()).append('\n').append(r.evidence()).append('\n');
            for (AuditResponse.KeyMetric m : report.keyMetrics()) basis.append(m.value()).append('\n');
        }
        Set<String> keys = NumberText.digitKeys(basis.toString());
        Set<String> percents = NumberText.percentKeys(basis.toString());
        Set<String> answerPercents = NumberText.percentKeys(answer);
        for (String k : NumberText.digitKeys(answer)) {
            if (k.length() < 3) continue;
            if (keys.contains(k)) continue;
            if (answerPercents.contains("P" + k) && percents.contains("P" + k)) continue;
            // Sayfa numarası cevapta geçebilir ("sayfa 12"); o sayfa gönderilmişse sayı sayılmaz.
            if (k.length() <= 3 && sentPages.contains(Integer.parseInt(k))) continue;
            log.info("Soru-cevap: cevaptaki {} belgede yok, dayanaksiz isaretlendi", k);
            grounded = false;
            break;
        }
        if (answer.isEmpty()) grounded = false;
        return new ChatResponse(answer, cited, grounded);
    }

    private static String systemPrompt(Lang lang) {
        if (lang.isTurkish()) {
            return """
                Bir belge inceleme uygulamasının soru-cevap yardımcısısın. Kullanıcı, incelenen belge ve raporu hakkında soru soruyor.
                Kurallar:
                - YALNIZCA verilen rapor ve sayfa alıntılarına dayan. Dışarıdan bilgi, genel hukuk/mevzuat yorumu ya da tahmin ekleme.
                - Cevap verilen materyalde yoksa bunu açıkça söyle ("Belgede bu bilgi yer almıyor") ve grounded=false ver.
                - Kısa ve net yaz: en fazla 4 cümle. Sayıları belgede yazıldığı gibi aktar.
                - Cevabın dayandığı sayfa numaralarını pages alanına yaz (yalnızca verilen sayfalardan).
                - Hukuki, mali ya da yatırım tavsiyesi verme; mevzuata uygunluk değerlendirmesi yapma. Gerekirse uzmana danışılmasını söyle.
                - Cevap tamamen Türkçe olmalı.
                """;
        }
        return """
            You are the Q&A helper of a document review app. The user asks about the reviewed document and its report.
            Rules:
            - Rely ONLY on the report and page excerpts provided. Do not add outside knowledge, legal/regulatory interpretation or guesses.
            - If the answer is not in the material, say so plainly ("The document does not contain this information") and set grounded=false.
            - Be short and clear: at most 4 sentences. Quote numbers exactly as written in the document.
            - Put the page numbers the answer relies on in "pages" (only from the pages provided).
            - Give no legal, financial or investment advice and no compliance assessment; suggest consulting a professional where relevant.
            - The answer must be entirely in English.
            """;
    }

    private static String userPrompt(String question, AuditResponse report, List<AuditResponse.PageContent> pages, Lang lang) {
        StringBuilder sb = new StringBuilder();
        sb.append(lang.isTurkish() ? "SORU:\n" : "QUESTION:\n").append(question).append("\n\n");
        if (report != null) {
            sb.append(lang.isTurkish() ? "RAPOR:\n" : "REPORT:\n");
            sb.append(lang.isTurkish() ? "Skor: " : "Score: ").append(report.riskScore()).append("/100\n");
            if (!report.summary().isBlank()) sb.append(report.summary()).append('\n');
            int i = 1;
            for (AuditResponse.Risk r : report.risks()) {
                sb.append(i++).append(". [").append(r.severity()).append("] ").append(r.title()).append(": ").append(r.finding());
                if (!r.pages().isEmpty()) sb.append(" (").append(lang.isTurkish() ? "sayfa " : "page ").append(r.pages()).append(')');
                sb.append('\n');
            }
            for (AuditResponse.KeyMetric m : report.keyMetrics()) {
                sb.append("- ").append(m.label()).append(": ").append(m.value());
                if (!m.unit().isBlank()) sb.append(' ').append(m.unit());
                sb.append('\n');
            }
            sb.append('\n');
        }
        sb.append(lang.isTurkish() ? "BELGE SAYFALARI:\n" : "DOCUMENT PAGES:\n");
        if (pages.isEmpty()) sb.append(lang.isTurkish() ? "(sayfa metni yok)\n" : "(no page text)\n");
        for (AuditResponse.PageContent p : pages) {
            sb.append("[PAGE ").append(p.page()).append("]\n").append(p.text()).append("\n\n");
        }
        return sb.toString();
    }

    private static Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("answer", "pages", "grounded"),
                "properties", Map.of(
                        "answer", Map.of("type", "string"),
                        "pages", Map.of("type", "array", "items", Map.of("type", "integer")),
                        "grounded", Map.of("type", "boolean")));
    }

    private static Set<String> contentWords(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        Matcher m = WORD.matcher(text.toLowerCase(Locale.forLanguageTag("tr")));
        while (m.find()) {
            String w = m.group();
            if (!STOP.contains(w)) out.add(stem(w));
        }
        return out;
    }

    // Kaba kök: Türkçe eklerin çoğu sondan gelir; ilk 5 harf "kira/kirası/kirayı" gibi biçimleri aynı sayar.
    private static String stem(String w) {
        return w.length() > 5 ? w.substring(0, 5) : w;
    }

    private static int overlap(Set<String> a, Set<String> b) {
        int n = 0;
        for (String w : a) if (b.contains(w)) n++;
        return n;
    }
}
