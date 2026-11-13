package com.clinicready.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import com.clinicready.document.TextExtractor.Extracted;
import com.clinicready.document.TextExtractor.Failed;
import com.clinicready.document.TextExtractor.Result;

class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor(100, 200_000);

    @Test
    void extractsTheTextLayerOfAPdf() throws IOException {
        Result result = extractor.extract("application/pdf",
                pdf("Ordonnance du Dr Test", "Amoxicilline 1 g, 2 fois par jour"));

        assertThat(result).isInstanceOfSatisfying(Extracted.class, e -> {
            assertThat(e.text()).contains("Ordonnance du Dr Test").contains("Amoxicilline 1 g");
            assertThat(e.pageCount()).isEqualTo(2);
            assertThat(e.truncated()).isFalse();
        });
    }

    @Test
    void aPdfWithoutTextIsAScanNotAMissingDocument() throws IOException {
        assertThat(extractor.extract("application/pdf", pdf("")))
                .isEqualTo(new Failed(ExtractionFailure.NO_TEXT_LAYER));
    }

    @Test
    void imagesNeedOcr() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

        assertThat(extractor.extract("image/png", png)).isEqualTo(new Failed(ExtractionFailure.NO_TEXT_LAYER));
        assertThat(extractor.extract("image/jpeg", png)).isEqualTo(new Failed(ExtractionFailure.NO_TEXT_LAYER));
    }

    @Test
    void aCorruptPdfIsUnreadable() {
        byte[] corrupt = "%PDF-1.4\n% truncated synthetic file\n".getBytes();

        assertThat(extractor.extract("application/pdf", corrupt)).isEqualTo(new Failed(ExtractionFailure.UNREADABLE));
    }

    @Test
    void aPasswordProtectedPdfIsReportedAsEncrypted() throws IOException {
        assertThat(extractor.extract("application/pdf", encryptedPdf("Bilan sanguin confidentiel")))
                .isEqualTo(new Failed(ExtractionFailure.ENCRYPTED));
    }

    @Test
    void stopsAtThePageLimitAndMarksTheTextTruncated() throws IOException {
        TextExtractor twoPages = new TextExtractor(2, 200_000);

        Result result = twoPages.extract("application/pdf", pdf("Page un du compte rendu",
                "Page deux du compte rendu", "Page trois du compte rendu"));

        assertThat(result).isInstanceOfSatisfying(Extracted.class, e -> {
            assertThat(e.text()).contains("Page deux").doesNotContain("Page trois");
            assertThat(e.pageCount()).isEqualTo(3);
            assertThat(e.truncated()).isTrue();
        });
    }

    @Test
    void stopsAtTheCharacterLimitAndMarksTheTextTruncated() throws IOException {
        TextExtractor short20 = new TextExtractor(100, 20);

        Result result = short20.extract("application/pdf", pdf("Certificat medical de non contre-indication"));

        assertThat(result).isInstanceOfSatisfying(Extracted.class, e -> {
            assertThat(e.text()).hasSize(20);
            assertThat(e.truncated()).isTrue();
        });
    }

    @Test
    void cleanRemovesNulAndControlCharactersButKeepsLineBreaks() {
        assertThat(TextExtractor.clean("Nom:\u0000 Test\u0007\r\nLigne 2\t \n\n\n\nFin  "))
                .isEqualTo("Nom: Test\nLigne 2\n\nFin");
    }

    // ---- synthetic PDFs ---------------------------------------------------------------------------

    private static byte[] pdf(String... pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (String text : pageTexts) {
                addPage(doc, text);
            }
            return save(doc);
        }
    }

    private static byte[] encryptedPdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            addPage(doc, text);
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret",
                    new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            return save(doc);
        }
    }

    private static void addPage(PDDocument doc, String text) throws IOException {
        PDPage page = new PDPage();
        doc.addPage(page);
        if (text.isEmpty()) {
            return;
        }
        try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(72, 700);
            content.showText(text);
            content.endText();
        }
    }

    private static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }
}
