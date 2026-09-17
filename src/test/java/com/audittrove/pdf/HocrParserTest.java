package com.audittrove.pdf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tesseract'in hOCR ciktisinin okunmasi. Ornek, taranmis is sozlesmesinin ilk satirlari. */
class HocrParserTest {

    private static final String HOCR = """
            <div class='ocr_page' id='page_1' title='image "s.png"; bbox 0 0 1000 2000; ppageno 0'>
             <p class='ocr_par'>
              <span class='ocr_line' id='line_1' title="bbox 100 200 800 240; baseline 0 -8">
               <span class='ocrx_word' title='bbox 100 200 400 240'>BEL&#304;RS&#304;Z</span>
               <span class='ocrx_word' title='bbox 410 200 800 240'>S&#214;ZLE&#350;ME</span>
              </span>
              <span class='ocr_line' id='line_2' title="bbox 100 300 600 340; baseline 0 -2">
               <span class='ocrx_word' title='bbox 100 300 600 340'>Y&#252;r&#252;rl&#252;k Tarihi</span>
              </span>
             </p>
            </div>
            """;

    @Test
    void readsLinesAndTheirBoxes() {
        List<PageText.Line> lines = HocrParser.lines(HOCR);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).text()).isEqualTo("BELİRSİZ SÖZLEŞME");
        // bbox 100 200 800 240 -> x=0.1, genislik 0.7; y biraz yukaridan baslar (kutuya pay eklenir).
        assertThat(lines.get(0).rect().x()).isEqualTo(0.1);
        assertThat(lines.get(0).rect().w()).isEqualTo(0.7);
        assertThat(lines.get(0).rect().y()).isLessThan(0.1000);
        assertThat(lines.get(1).text()).isEqualTo("Yürürlük Tarihi");
    }

    @Test
    void emptyOrBrokenOutputGivesNoLines() {
        assertThat(HocrParser.lines(null)).isEmpty();
        assertThat(HocrParser.lines("")).isEmpty();
        assertThat(HocrParser.lines("<html><body>metin yok</body></html>")).isEmpty();
    }

    @Test
    void linesWithoutTextAreSkipped() {
        String hocr = "<div class='ocr_page' title='bbox 0 0 100 100'>"
                + "<span class='ocr_line' title=\"bbox 1 1 50 10\"> </span></div>";
        assertThat(HocrParser.lines(hocr)).isEmpty();
    }
}
