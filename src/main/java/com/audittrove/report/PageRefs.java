package com.audittrove.report;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metne sızmış sayfa atıflarını hangi biçimde yazılmış olsa da söker ve sayfa numaralarına çevirir.
 * Kural: sayfa bilgisi metinde değil yapısal alanda taşınır; kullanıcıya giden metinde atıf kalmaz.
 */
public final class PageRefs {
    private PageRefs() {}

    /** Sökülmüş metin ve içinden okunan sayfalar. */
    public record Parsed(String text, List<Integer> pages) {}

    // [REPORT PAGE 5], [REPORT PAGES 5-6], [REPORT PAGE 5, 6], [Report page 5 – 7]
    private static final Pattern BRACKET = Pattern.compile(
            "\\[\\s*REPORT\\s+PAGES?\\s*([0-9]+(?:\\s*[-–,]\\s*[0-9]+)*)\\s*\\]", Pattern.CASE_INSENSITIVE);
    // (Page 5), (Pages 5-6), (Report Page 8), (Sayfa 5, 11), (Rapor Sayfa 8), (S. 5), (p. 5), (pp. 5-6)
    private static final Pattern PAREN = Pattern.compile(
            "\\(\\s*(?:Rapor\\s+)?(?:Report\\s+)?(?:Sayfa|Pages?|S\\.|pp?\\.)\\s*([0-9]+(?:\\s*[-–,]\\s*[0-9]+)*)\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("[0-9]+");
    private static final Pattern RANGE = Pattern.compile("([0-9]+)\\s*[-–]\\s*([0-9]+)");
    // Aralık makul olmalı; "3-40" gibi bir şey atıf değil gürültüdür, uçları alınır.
    private static final int MAX_RANGE = 4;

    public static Parsed strip(String text) {
        if (text == null || text.isEmpty()) return new Parsed("", List.of());
        SortedSet<Integer> pages = new TreeSet<>();
        String out = collect(BRACKET, text, pages);
        out = collect(PAREN, out, pages);
        out = tidy(out);
        return new Parsed(out, List.copyOf(pages));
    }

    private static String collect(Pattern p, String text, SortedSet<Integer> pages) {
        Matcher m = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            pages.addAll(expand(m.group(1)));
            m.appendReplacement(sb, " ");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static List<Integer> expand(String spec) {
        SortedSet<Integer> out = new TreeSet<>();
        Matcher r = RANGE.matcher(spec);
        String rest = spec;
        while (r.find()) {
            int a = Integer.parseInt(r.group(1)), b = Integer.parseInt(r.group(2));
            if (a <= b && b - a <= MAX_RANGE) {
                for (int i = a; i <= b; i++) out.add(i);
            } else {
                out.add(a);
                out.add(b);
            }
        }
        rest = RANGE.matcher(rest).replaceAll(" ");
        Matcher n = NUMBER.matcher(rest);
        while (n.find()) out.add(Integer.parseInt(n.group()));
        return List.copyOf(out);
    }

    // Atıf sökülünce kalan boşluk ve noktalama artıkları: "tahminleri içerir . (" → "tahminleri içerir."
    private static String tidy(String s) {
        return s.replaceAll("\\s{2,}", " ")
                .replaceAll("\\s+([.,;:])", "$1")
                .replaceAll("([.,;:])\\1+", "$1")
                .trim();
    }
}