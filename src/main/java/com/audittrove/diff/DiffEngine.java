package com.audittrove.diff;

import com.audittrove.api.AuditResponse;
import com.audittrove.financial.NumberText;
import com.audittrove.pdf.PageText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * İki belge sürümü arasındaki farkı kodla bulur. Sayfa satırları önce madde/paragraf birimlerine bölünür,
 * birimler benzerlikle eşleştirilir, eşleşenlerde sayı ve ifade farkı ayrılır. Model burada yok; aynı iki
 * belge her koşuda aynı fark listesini verir.
 */
public final class DiffEngine {
    private DiffEngine() {}

    /** Bir belge birimi: madde ya da paragraf. lines: sayfadaki satırları (konum için). */
    public record Unit(int page, String text, List<PageText.Line> lines, String clauseKey) {
        Set<String> tokens() { return tokensOf(text); }
        String normalized() { return normalize(text); }
        List<AuditResponse.Anchor> anchors() {
            if (lines.isEmpty()) return List.of(new AuditResponse.Anchor(page, List.of()));
            AuditResponse.Rect r = lines.get(0).rect();
            for (PageText.Line l : lines) r = union(r, l.rect());
            return List.of(new AuditResponse.Anchor(page, List.of(r)));
        }
    }

    /** Eşleşme sonucu: pairs aynı maddenin iki sürümü; onlyA eskiden çıkanlar; onlyB yeni eklenenler. */
    public record Alignment(List<Unit[]> pairs, List<Unit> onlyA, List<Unit> onlyB) {}

    // "Madde 5", "MADDE 5.2 -", "Article 3", "Section 4.1", "5.", "5.2)", "(a)", "a)"
    private static final Pattern HEADER = Pattern.compile(
            "^\\s*(?:(madde|article|section|clause|bölüm|kısım|dipnot|not|note|footnote)\\s*(\\d+(?:[.,]\\d+)*)|(\\d+(?:\\.\\d+)*)[.)]\\s+\\p{L}|\\(?([a-zçğıöşü])\\)\\s+\\p{L})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern WORD = Pattern.compile("\\p{L}{3,}");
    private static final Pattern NUMBER = Pattern.compile("\\d(?:[\\d.,]|[ \\u00a0](?=\\d{3}(?!\\d)))*\\d|\\d");
    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final double PARAGRAPH_GAP = 1.6;
    private static final double SHORT_LINE = 0.6;
    private static final double MATCH_THRESHOLD = 0.35;
    private static final double CLAUSE_KEY_THRESHOLD = 0.12;
    static final int MAX_UNIT_TEXT = 700;

    /** Sayfa satırlarını birimlere böler: madde başlığı, paragraf boşluğu ve tablo satırı birim sınırıdır. */
    public static List<Unit> units(Map<Integer, PageText> pages) {
        List<Unit> out = new ArrayList<>();
        for (Map.Entry<Integer, PageText> e : pages.entrySet()) {
            int page = e.getKey();
            List<PageText.Line> lines = e.getValue().lines();
            double fullWidth = 0;
            for (PageText.Line l : lines) fullWidth = Math.max(fullWidth, l.rect().w());
            List<PageText.Line> current = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                PageText.Line line = lines.get(i);
                String text = line.text().strip();
                if (text.isEmpty()) continue;
                PageText.Line prev = current.isEmpty() ? null : current.get(current.size() - 1);
                boolean startsNew = prev == null
                        || HEADER.matcher(text).find()
                        || looksLikeTableRow(text)
                        || looksLikeTableRow(prev.text())
                        || gapBefore(prev, line) > PARAGRAPH_GAP
                        || endsParagraph(prev, fullWidth, text);
                if (startsNew && !current.isEmpty()) {
                    flush(out, page, current);
                    current = new ArrayList<>();
                }
                current.add(line);
            }
            flush(out, page, current);
        }
        return out;
    }

    private static void flush(List<Unit> out, int page, List<PageText.Line> lines) {
        if (lines.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (PageText.Line l : lines) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(l.text().strip());
        }
        String text = sb.toString();
        // Sayfa numarası, tek harf, boş başlık gibi kırıntılar birim değildir.
        if (text.length() < 4 || text.matches("[\\d\\s.\\-–/]+")) return;
        out.add(new Unit(page, text, List.copyOf(lines), clauseKey(text)));
    }

    /**
     * Tablo satırı: rakamla biter, en az iki sayı taşır ve kelimeden çok sayı vardır ("Dönem kârı 88,1 1 520,9").
     * Satır sonunda kalan tek bir sayı ("…toplam finansal borç 31") cümlenin devamıdır, satır değildir.
     */
    static boolean looksLikeTableRow(String text) {
        if (text == null || text.isBlank()) return false;
        String trimmed = text.strip();
        // Eksi tutarlar parantezle yazılır ("(905,7)"), yüzdeler işaretle biter; bunlar da rakamla biter sayılır.
        while (!trimmed.isEmpty() && ")]%\u201d\"'".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty() || !Character.isDigit(trimmed.charAt(trimmed.length() - 1))) return false;
        int numbers = 0, words = 0;
        Matcher n = NUMBER.matcher(trimmed);
        while (n.find()) numbers++;
        Matcher w = WORD.matcher(trimmed);
        while (w.find()) words++;
        return numbers >= 2 && words < 3 * numbers;
    }

    // Paragrafın son satırı kısa kalır; altındaki satır büyük harfle başlıyorsa boşluk olmasa da yeni paragraftır.
    private static boolean endsParagraph(PageText.Line prev, double fullWidth, String nextText) {
        if (fullWidth <= 0 || prev.rect().w() > SHORT_LINE * fullWidth) return false;
        char c = nextText.charAt(0);
        return Character.isUpperCase(c) || Character.isDigit(c);
    }

    // Satırlar arası dikey boşluk, satır yüksekliği cinsinden. Büyükse paragraf değişmiştir.
    private static double gapBefore(PageText.Line prev, PageText.Line line) {
        double h = Math.max(1e-6, prev.rect().h());
        double gap = line.rect().y() - (prev.rect().y() + prev.rect().h());
        return gap / h;
    }

    static String clauseKey(String text) {
        Matcher m = HEADER.matcher(text);
        if (!m.find()) return null;
        if (m.group(2) != null) return m.group(1).toLowerCase(TR) + " " + m.group(2).replace(',', '.');
        if (m.group(3) != null) return "n " + m.group(3);
        return null; // "(a)" tek başına ayırt edici değil
    }

    /** Eşleştirme: önce metni aynı olanlar (değişmemiş), sonra kalanlar benzerlik puanıyla, madde numarası eşitse eşik düşer. */
    public static Alignment align(List<Unit> a, List<Unit> b) {
        Map<String, List<Integer>> exactB = new LinkedHashMap<>();
        for (int j = 0; j < b.size(); j++) exactB.computeIfAbsent(b.get(j).normalized(), k -> new ArrayList<>()).add(j);
        boolean[] usedA = new boolean[a.size()], usedB = new boolean[b.size()];
        List<Unit[]> pairs = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            List<Integer> js = exactB.get(a.get(i).normalized());
            if (js == null) continue;
            for (int j : js) if (!usedB[j]) { usedA[i] = true; usedB[j] = true; pairs.add(new Unit[]{a.get(i), b.get(j)}); break; }
        }
        record Cand(int i, int j, double s) {}
        List<Cand> cands = new ArrayList<>();
        List<Set<String>> ta = new ArrayList<>(), tb = new ArrayList<>();
        for (Unit u : a) ta.add(u.tokens());
        for (Unit u : b) tb.add(u.tokens());
        for (int i = 0; i < a.size(); i++) {
            if (usedA[i]) continue;
            for (int j = 0; j < b.size(); j++) {
                if (usedB[j]) continue;
                double s = jaccard(ta.get(i), tb.get(j));
                boolean sameClause = a.get(i).clauseKey() != null && a.get(i).clauseKey().equals(b.get(j).clauseKey());
                if (sameClause) s += 0.3;
                if (s >= MATCH_THRESHOLD || (sameClause && s >= CLAUSE_KEY_THRESHOLD + 0.3)) cands.add(new Cand(i, j, s));
            }
        }
        cands.sort((x, y) -> Double.compare(y.s, x.s));
        for (Cand c : cands) {
            if (usedA[c.i] || usedB[c.j]) continue;
            usedA[c.i] = true; usedB[c.j] = true;
            pairs.add(new Unit[]{a.get(c.i), b.get(c.j)});
        }
        List<Unit> onlyA = new ArrayList<>(), onlyB = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) if (!usedA[i]) onlyA.add(a.get(i));
        for (int j = 0; j < b.size(); j++) if (!usedB[j]) onlyB.add(b.get(j));
        return new Alignment(pairs, onlyA, onlyB);
    }

    /** Fark listesi: değişmeyen çiftler atlanır, sayılar değiştiyse NUMBER, yoksa TEXT; tek taraflılar ADDED/REMOVED. */
    public static List<DiffResponse.Change> changes(Alignment al) {
        List<DiffResponse.Change> out = new ArrayList<>();
        for (Unit[] p : al.pairs()) {
            Unit ua = p[0], ub = p[1];
            if (ua.normalized().equals(ub.normalized())) continue;
            List<String> numsA = numbers(ua.text()), numsB = numbers(ub.text());
            List<String> oldOnly = new ArrayList<>(), newOnly = new ArrayList<>();
            List<String> keysB = new ArrayList<>(), keysA = new ArrayList<>();
            for (String n : numsB) keysB.add(NumberText.digits(n));
            for (String n : numsA) keysA.add(NumberText.digits(n));
            List<String> pool = new ArrayList<>(keysB);
            for (String n : numsA) { if (!pool.remove(NumberText.digits(n))) oldOnly.add(n); }
            pool = new ArrayList<>(keysA);
            for (String n : numsB) { if (!pool.remove(NumberText.digits(n))) newOnly.add(n); }
            String kind = oldOnly.isEmpty() && newOnly.isEmpty() ? DiffResponse.Change.TEXT : DiffResponse.Change.NUMBER;
            out.add(new DiffResponse.Change(kind, null, "", "", clip(ua.text()), clip(ub.text()), oldOnly, newOnly,
                    List.of(ua.page()), List.of(ub.page()), ua.anchors(), ub.anchors()));
        }
        for (Unit u : al.onlyA()) {
            out.add(new DiffResponse.Change(DiffResponse.Change.REMOVED, null, "", "", clip(u.text()), "", numbers(u.text()), List.of(),
                    List.of(u.page()), List.of(), u.anchors(), List.of()));
        }
        for (Unit u : al.onlyB()) {
            out.add(new DiffResponse.Change(DiffResponse.Change.ADDED, null, "", "", "", clip(u.text()), List.of(), numbers(u.text()),
                    List.of(), List.of(u.page()), List.of(), u.anchors()));
        }
        // Belge sırası: yeni sürümdeki yer, yoksa eski sürümdeki yer.
        out.sort((x, y) -> {
            int px = x.pagesB().isEmpty() ? x.pagesA().get(0) : x.pagesB().get(0);
            int py = y.pagesB().isEmpty() ? y.pagesA().get(0) : y.pagesB().get(0);
            if (px != py) return Integer.compare(px, py);
            return Double.compare(top(x), top(y));
        });
        return out;
    }

    public static int unchangedCount(Alignment al) {
        int n = 0;
        for (Unit[] p : al.pairs()) if (p[0].normalized().equals(p[1].normalized())) n++;
        return n;
    }

    private static double top(DiffResponse.Change c) {
        List<AuditResponse.Anchor> an = c.anchorsB().isEmpty() ? c.anchorsA() : c.anchorsB();
        if (an.isEmpty() || an.get(0).rects().isEmpty()) return 0;
        return an.get(0).rects().get(0).y();
    }

    /** Metindeki sayılar, belgede yazıldığı biçimiyle ve sırasıyla. */
    static List<String> numbers(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) out.add(m.group().replace('\u00a0', ' ').strip());
        return out;
    }

    static Set<String> tokensOf(String text) {
        Set<String> out = new HashSet<>();
        Matcher m = WORD.matcher(text.toLowerCase(TR));
        while (m.find()) {
            String w = m.group();
            out.add(w.length() > 5 ? w.substring(0, 5) : w);
        }
        for (String n : NumberText.digitKeys(text)) if (n.length() >= 2) out.add("#" + n);
        return out;
    }

    static String normalize(String text) {
        return text.toLowerCase(TR).replaceAll("[^\\p{L}\\p{N}%]+", " ").strip();
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> uni = new LinkedHashSet<>(a);
        uni.addAll(b);
        return (double) inter.size() / uni.size();
    }

    private static String clip(String s) {
        return s.length() <= MAX_UNIT_TEXT ? s : s.substring(0, MAX_UNIT_TEXT - 1) + "…";
    }

    private static AuditResponse.Rect union(AuditResponse.Rect a, AuditResponse.Rect b) {
        double x = Math.min(a.x(), b.x()), y = Math.min(a.y(), b.y());
        double right = Math.max(a.x() + a.w(), b.x() + b.w());
        double bottom = Math.max(a.y() + a.h(), b.y() + b.h());
        return new AuditResponse.Rect(x, y, right - x, bottom - y);
    }
}
