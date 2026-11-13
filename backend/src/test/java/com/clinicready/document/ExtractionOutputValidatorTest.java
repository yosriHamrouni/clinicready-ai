package com.clinicready.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.clinicready.document.ExtractionOutputValidator.Invalid;
import com.clinicready.document.ExtractionOutputValidator.Valid;

class ExtractionOutputValidatorTest {

    private final ExtractionOutputValidator validator = new ExtractionOutputValidator();

    @Test
    void acceptsAnswerThatMatchesTheSchema() {
        var result = validator.validate("""
                {"category":"PRESCRIPTION","documentDate":"2026-11-02","issuer":"Dr Test","confidence":0.92}""");

        assertThat(result).isEqualTo(new Valid(DocumentCategory.PRESCRIPTION, LocalDate.of(2026, 11, 2), "Dr Test", 0.92));
    }

    @Test
    void unidentifiedValuesStayNull() {
        var result = validator.validate("""
                {"category":null,"documentDate":null,"issuer":null,"confidence":0.2}""");

        assertThat(result).isEqualTo(new Valid(null, null, null, 0.2));
    }

    @Test
    void aBlankIssuerIsUnknownNotAnEmptyString() {
        var result = (Valid) validator.validate("""
                {"category":"OTHER","documentDate":null,"issuer":"   ","confidence":0.8}""");

        assertThat(result.issuer()).isNull();
    }

    @Test
    void toleratesAMarkdownFenceAroundTheJson() {
        var result = validator.validate("""
                ```json
                {"category":"LAB_RESULT","documentDate":null,"issuer":null,"confidence":0.9}
                ```""");

        assertThat(result).isInstanceOf(Valid.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "not json",
            "[]",
            "{}",
            // category outside the enum
            "{\"category\":\"PASSPORT\",\"documentDate\":null,\"issuer\":null,\"confidence\":0.9}",
            // missing key
            "{\"category\":\"OTHER\",\"documentDate\":null,\"confidence\":0.9}",
            // extra key
            "{\"category\":\"OTHER\",\"documentDate\":null,\"issuer\":null,\"confidence\":0.9,\"eligible\":true}",
            // confidence out of range or not a number
            "{\"category\":\"OTHER\",\"documentDate\":null,\"issuer\":null,\"confidence\":1.5}",
            "{\"category\":\"OTHER\",\"documentDate\":null,\"issuer\":null,\"confidence\":\"high\"}",
            // date in the wrong format, or not a real date
            "{\"category\":\"OTHER\",\"documentDate\":\"02/11/2026\",\"issuer\":null,\"confidence\":0.9}",
            "{\"category\":\"OTHER\",\"documentDate\":\"2026-02-30\",\"issuer\":null,\"confidence\":0.9}",
            // text around the JSON is not repaired
            "Here you go: {\"category\":\"OTHER\",\"documentDate\":null,\"issuer\":null,\"confidence\":0.9}"
    })
    void rejectsAnythingElse(String raw) {
        assertThat(validator.validate(raw)).isInstanceOf(Invalid.class);
    }

    @Test
    void rejectsNull() {
        assertThat(validator.validate(null)).isInstanceOf(Invalid.class);
    }
}
