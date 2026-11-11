package com.clinicready.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SanitizeFilenameTest {

    @Test
    void keepsOnlyTheBaseName() {
        assertThat(DocumentService.sanitizeFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentService.sanitizeFilename("C:\\Users\\x\\scan.pdf")).isEqualTo("scan.pdf");
        assertThat(DocumentService.sanitizeFilename("ordonnance.pdf")).isEqualTo("ordonnance.pdf");
    }

    @Test
    void stripsControlCharactersAndFallsBackWhenEmpty() {
        assertThat(DocumentService.sanitizeFilename("a\r\nb.pdf")).isEqualTo("ab.pdf");
        assertThat(DocumentService.sanitizeFilename(null)).isEqualTo("document");
        assertThat(DocumentService.sanitizeFilename("   ")).isEqualTo("document");
        assertThat(DocumentService.sanitizeFilename("dir/")).isEqualTo("document");
    }

    @Test
    void keepsArabicNamesAndBoundsTheLength() {
        assertThat(DocumentService.sanitizeFilename("وصفة طبية.pdf")).isEqualTo("وصفة طبية.pdf");
        assertThat(DocumentService.sanitizeFilename("a".repeat(400))).hasSize(255);
    }
}
