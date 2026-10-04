package com.audittrove.pdf;

import com.audittrove.api.AuditResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF'ten satır satır metin ve her satırın sayfa üzerindeki yeri. Metin çıkarımıyla aynı PDFBox akışı;
 * fark, satırların atılmayıp konumlarıyla saklanması. Taranmış belgelerde de çalışır, çünkü uygulama
 * OCR metnini görüntünün üstüne konumlu (görünmez) katman olarak yazar.
 */
public final class PdfGeometry {
    private PdfGeometry() {}

    public static Map<Integer, PageText> read(byte[] content) throws IOException {
        Map<Integer, PageText> out = new LinkedHashMap<>();
        try (PDDocument document = Loader.loadPDF(content)) {
            document.setAllSecurityToBeRemoved(true);
            int total = document.getNumberOfPages();
            for (int p = 1; p <= total; p++) {
                PDPage page = document.getPage(p - 1);
                PDRectangle box = page.getCropBox();
                float width = box.getWidth(), height = box.getHeight();
                int rotation = page.getRotation();
                if (rotation == 90 || rotation == 270) {
                    float t = width; width = height; height = t;
                }
                LineCollector collector = new LineCollector(width, height);
                collector.setSortByPosition(true);
                collector.setStartPage(p);
                collector.setEndPage(p);
                collector.getText(document);
                out.put(p, new PageText(p, collector.lines()));
            }
        }
        return out;
    }

    // PDFTextStripper her satırı kelime gruplarıyla writeString'e verir, satır bitince writeLineSeparator çağırır.
    // Gruplar biriktirilir, satır sonunda tek kutu ve tek metin olur.
    private static final class LineCollector extends PDFTextStripper {
        private final float pageWidth, pageHeight;
        private final List<PageText.Line> lines = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;

        LineCollector(float pageWidth, float pageHeight) throws IOException {
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
        }

        List<PageText.Line> lines() {
            flush();
            return lines;
        }

        @Override
        protected void writeString(String string, List<TextPosition> positions) {
            if (positions == null || positions.isEmpty()) {
                text.append(string);
                return;
            }
            for (TextPosition tp : positions) {
                float x = tp.getXDirAdj();
                float w = tp.getWidthDirAdj();
                float h = tp.getHeightDir();
                float baseline = tp.getYDirAdj();
                if (w <= 0 && h <= 0) continue;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x + w);
                top = Math.min(top, baseline - h);
                bottom = Math.max(bottom, baseline);
            }
            text.append(string);
        }

        @Override
        protected void writeWordSeparator() {
            text.append(' ');
        }

        @Override
        protected void writeLineSeparator() {
            flush();
        }

        @Override
        protected void writePageEnd() {
            flush();
        }

        private void flush() {
            String line = text.toString().trim();
            if (!line.isEmpty() && maxX > minX && bottom > top && pageWidth > 0 && pageHeight > 0) {
                // Satır kutusu biraz nefes alsın; metin yüksekliği glif yüksekliğidir, boyama satırı kaplasın.
                float pad = (bottom - top) * 0.2f;
                double x = clamp(minX / pageWidth), y = clamp((top - pad) / pageHeight);
                double w = clamp((maxX - minX) / pageWidth), h = clamp((bottom - top + 2 * pad) / pageHeight);
                lines.add(new PageText.Line(line, new AuditResponse.Rect(round(x), round(y), round(w), round(h))));
            }
            text.setLength(0);
            minX = Float.MAX_VALUE; maxX = -Float.MAX_VALUE; top = Float.MAX_VALUE; bottom = -Float.MAX_VALUE;
        }

        private static double clamp(double v) {
            return Math.max(0, Math.min(1, v));
        }

        private static double round(double v) {
            return Math.round(v * 10000) / 10000.0;
        }
    }
}