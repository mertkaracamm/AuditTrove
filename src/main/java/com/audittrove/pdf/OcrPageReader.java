package com.audittrove.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Metin katmanı olmayan (taranmış, telefonla çekilmiş) PDF'leri okur: sayfa görüntüye çevrilir,
 * Tesseract satırları ve satır kutularını verir. Çıktı PdfGeometry ile aynı biçimde olduğu için
 * metin de çıpa da tek kaynaktan gelir.
 *
 * Görüntü ve OCR çıktısı diske yazılmaz; Tesseract'a stdin'den verilip stdout'tan okunur.
 */
@Component
public class OcrPageReader {

    private static final Logger log = LoggerFactory.getLogger(OcrPageReader.class);

    private final boolean enabled;
    private final int dpi;
    private final int maxPages;
    private final int timeoutSeconds;
    private final String languages;
    private volatile Boolean installed;

    public OcrPageReader(@Value("${audittrove.ocr.enabled:true}") boolean enabled,
                         @Value("${audittrove.ocr.dpi:200}") int dpi,
                         @Value("${audittrove.ocr.max-pages:20}") int maxPages,
                         @Value("${audittrove.ocr.timeout-seconds:40}") int timeoutSeconds,
                         @Value("${audittrove.ocr.languages:tur+eng}") String languages) {
        this.enabled = enabled;
        this.dpi = dpi;
        this.maxPages = maxPages;
        this.timeoutSeconds = timeoutSeconds;
        this.languages = languages;
    }

    /** Tesseract kurulu mu. Bir kez bakılır; kurulu değilse OCR yolu sessizce kapalı kalır. */
    public boolean available() {
        if (!enabled) return false;
        Boolean known = installed;
        if (known != null) return known;
        synchronized (this) {
            if (installed == null) {
                installed = run(new String[]{"tesseract", "--version"}, null, 10) != null;
                if (!installed) log.warn("Tesseract bulunamadi, taranmis belgeler okunamayacak");
            }
            return installed;
        }
    }

    /**
     * Belgenin sayfalarını OCR ile okur. Hiçbir sayfadan metin çıkmazsa boş harita döner —
     * çağıran bunu "okunamayan belge" olarak ele alır.
     */
    public Map<Integer, PageText> read(byte[] content) throws IOException {
        Map<Integer, PageText> out = new LinkedHashMap<>();
        if (!available()) return out;
        try (PDDocument document = Loader.loadPDF(content)) {
            document.setAllSecurityToBeRemoved(true);
            PDFRenderer renderer = new PDFRenderer(document);
            int total = Math.min(document.getNumberOfPages(), maxPages);
            for (int p = 1; p <= total; p++) {
                byte[] png = png(renderer.renderImageWithDPI(p - 1, dpi, ImageType.GRAY));
                String hocr = run(new String[]{"tesseract", "-", "-", "--psm", "3", "-l", languages, "hocr"},
                        png, timeoutSeconds);
                if (hocr == null) {
                    log.warn("OCR sayfa {} icin sonuc vermedi", p);
                    continue;
                }
                List<PageText.Line> lines = HocrParser.lines(hocr);
                if (!lines.isEmpty()) out.put(p, new PageText(p, lines));
            }
        }
        if (!out.isEmpty()) log.info("OCR: {} sayfa okundu (dil {})", out.size(), languages);
        return out;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        return buffer.toByteArray();
    }

    // stdin ayrı bir iş parçacığından yazılır, stdout burada okunur: ikisi tek akıştan yürütülürse
    // boru dolduğunda iki taraf da birbirini bekler ve süreç kilitlenir.
    private String run(String[] command, byte[] input, int seconds) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
            Process started = process;
            Thread writer = new Thread(() -> {
                try (OutputStream stdin = started.getOutputStream()) {
                    if (input != null) stdin.write(input);
                } catch (IOException ignored) {
                    // Tesseract girdiyi erken kapatmış olabilir; çıktı yine de okunur.
                }
            }, "ocr-stdin");
            writer.setDaemon(true);
            writer.start();
            byte[] stdout = process.getInputStream().readAllBytes();
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("OCR {} saniyede bitmedi, sayfa atlandi", seconds);
                return null;
            }
            if (process.exitValue() != 0) return null;
            return new String(stdout, StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("OCR calistirilamadi: {}", e.toString());
            return null;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
