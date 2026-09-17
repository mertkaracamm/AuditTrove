package com.audittrove.pdf;

import com.audittrove.api.AuditResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tesseract'ın hOCR çıktısından satırları ve satır kutularını okur. Kutular sayfa piksel boyutuna
 * oranlanır (0..1, orijin sol üst), yani PdfGeometry'nin ürettiğiyle aynı biçim — taranmış belgede
 * de metin ve çıpa aynı kaynaktan gelir, iki ayrı okuma olmaz.
 */
public final class HocrParser {

    // Tesseract sürümleri title'ı kimi yerde tek kimi yerde çift tırnakla yazıyor; ikisi de kabul edilir.
    private static final Pattern PAGE_BBOX = Pattern.compile("class=.ocr_page.[^>]*bbox 0 0 (\\d+) (\\d+)");
    private static final Pattern LINE = Pattern.compile(
            "<span class=.ocr_line.[^>]*?bbox (\\d+) (\\d+) (\\d+) (\\d+)[^>]*>(.*?)</span>\\s*(?=<span class=.ocr_line.|</p>|</div>|</body>)",
            Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private HocrParser() {
    }

    public static List<PageText.Line> lines(String hocr) {
        List<PageText.Line> out = new ArrayList<>();
        if (hocr == null || hocr.isBlank()) return out;
        Matcher page = PAGE_BBOX.matcher(hocr);
        if (!page.find()) return out;
        double width = Double.parseDouble(page.group(1));
        double height = Double.parseDouble(page.group(2));
        if (width <= 0 || height <= 0) return out;
        Matcher line = LINE.matcher(hocr);
        while (line.find()) {
            String text = unescape(SPACES.matcher(TAG.matcher(line.group(5)).replaceAll(" ")).replaceAll(" ").trim());
            if (text.isEmpty()) continue;
            double x1 = Double.parseDouble(line.group(1));
            double y1 = Double.parseDouble(line.group(2));
            double x2 = Double.parseDouble(line.group(3));
            double y2 = Double.parseDouble(line.group(4));
            if (x2 <= x1 || y2 <= y1) continue;
            // Kutu biraz nefes alsın; OCR satır kutusu gliflere yapışık geliyor.
            double pad = (y2 - y1) * 0.15;
            out.add(new PageText.Line(text, new AuditResponse.Rect(
                    round(clamp(x1 / width)),
                    round(clamp((y1 - pad) / height)),
                    round(clamp((x2 - x1) / width)),
                    round(clamp((y2 - y1 + 2 * pad) / height)))));
        }
        return out;
    }

    // Tesseract 5 harfleri ham UTF-8 yaziyor; eski surumler Turkce harfleri sayisal referansla
    // ("&#304;") verebiliyor, ikisi de cozuluyor.
    private static final Pattern NUMERIC_REF = Pattern.compile("&#(x?)([0-9A-Fa-f]+);");

    private static String unescape(String text) {
        String out = text.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
        Matcher ref = NUMERIC_REF.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (ref.find()) {
            int code = Integer.parseInt(ref.group(2), ref.group(1).isEmpty() ? 10 : 16);
            ref.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        ref.appendTail(sb);
        return sb.toString().replace("&amp;", "&");
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
