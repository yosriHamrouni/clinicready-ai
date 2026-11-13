package com.clinicready.document;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Reads the text layer of a stored file. Plain Java, no Spring, no I/O beyond the bytes it is given.
 *
 * <p>Only PDFs have a text layer. Images, and PDFs that are just scanned pages, come back as
 * {@link ExtractionFailure#NO_TEXT_LAYER}: they need OCR, which is a separate decision (YOS-22).
 */
final class TextExtractor {

    /** Fewer letters or digits than this is treated as "no text layer" (a scan with a stray glyph). */
    static final int MIN_MEANINGFUL_CHARS = 10;

    sealed interface Result {
    }

    record Extracted(String text, int pageCount, boolean truncated) implements Result {
    }

    record Failed(ExtractionFailure reason) implements Result {
    }

    private final int maxPages;
    private final int maxChars;

    TextExtractor(int maxPages, int maxChars) {
        if (maxPages < 1 || maxChars < 1) {
            throw new IllegalArgumentException("Limits must be positive");
        }
        this.maxPages = maxPages;
        this.maxChars = maxChars;
    }

    /** @param contentType the type detected at upload ({@code application/pdf}, {@code image/png}, …) */
    Result extract(String contentType, byte[] bytes) {
        if (!"application/pdf".equals(contentType)) {
            return new Failed(ExtractionFailure.NO_TEXT_LAYER);
        }
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            int pages = pdf.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(Math.min(pages, maxPages));
            String text = clean(stripper.getText(pdf));
            if (meaningfulChars(text) < MIN_MEANINGFUL_CHARS) {
                return new Failed(ExtractionFailure.NO_TEXT_LAYER);
            }
            boolean truncated = pages > maxPages;
            if (text.length() > maxChars) {
                text = text.substring(0, maxChars);
                truncated = true;
            }
            return new Extracted(text, pages, truncated);
        } catch (InvalidPasswordException e) {
            return new Failed(ExtractionFailure.ENCRYPTED);
        } catch (IOException | RuntimeException e) {
            // PDFBox reports malformed files with both checked and unchecked exceptions.
            return new Failed(ExtractionFailure.UNREADABLE);
        }
    }

    /** PostgreSQL TEXT rejects NUL; other control characters (except line breaks and tabs) are noise. */
    static String clean(String raw) {
        String text = raw.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        text = text.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }

    private static long meaningfulChars(String text) {
        return text.codePoints().filter(Character::isLetterOrDigit).count();
    }
}
