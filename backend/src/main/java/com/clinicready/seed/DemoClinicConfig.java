package com.clinicready.seed;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * How the fictional demo clinic works: which documents each appointment type needs, which examinations
 * and advance payment staff would have entered. Synthetic and in French, so the demo shows a realistic
 * mix of ready and incomplete files instead of an empty rule book.
 */
public final class DemoClinicConfig {

    /** A document an appointment type needs. {@code validityDays} null: never expires. */
    public record Rule(String typeCode, String category, boolean mandatory, Integer validityDays) {

        /** Stable id, so re-seeding updates the row in place. */
        public UUID id() {
            return UUID.nameUUIDFromBytes(
                    ("clinicready-demo/rule/" + typeCode + "/" + category).getBytes(StandardCharsets.UTF_8));
        }
    }

    public static final List<Rule> RULES = List.of(
            new Rule("CONSULTATION", "ID_DOCUMENT", true, null),
            new Rule("CONSULTATION", "INSURANCE_CARD", true, null),
            new Rule("CONSULTATION", "REFERRAL_LETTER", false, 90),
            new Rule("FOLLOW_UP", "ID_DOCUMENT", true, null),
            new Rule("ULTRASOUND", "ID_DOCUMENT", true, null),
            new Rule("ULTRASOUND", "PRESCRIPTION", true, 90),
            new Rule("ULTRASOUND", "CONSENT_FORM", false, null),
            new Rule("XRAY", "ID_DOCUMENT", true, null),
            new Rule("XRAY", "PRESCRIPTION", true, 90),
            new Rule("XRAY", "INSURANCE_CARD", false, null),
            new Rule("MRI", "ID_DOCUMENT", true, null),
            new Rule("MRI", "REFERRAL_LETTER", true, 30),
            new Rule("MRI", "CONSENT_FORM", true, null),
            new Rule("MRI", "LAB_RESULT", false, null),
            new Rule("CT_SCAN", "ID_DOCUMENT", true, null),
            new Rule("CT_SCAN", "PRESCRIPTION", true, 90),
            new Rule("CT_SCAN", "CONSENT_FORM", true, null));

    private DemoClinicConfig() {}

    public static List<Rule> rulesFor(String typeCode) {
        return RULES.stream().filter(r -> r.typeCode().equals(typeCode)).toList();
    }

    /** Examinations staff would list for the type; empty means "none expected" (stated, not forgotten). */
    static List<String> examinationsFor(String typeCode) {
        return switch (typeCode) {
            case "ULTRASOUND" -> List.of("Échographie abdominale");
            case "XRAY" -> List.of("Radiographie du thorax");
            case "MRI" -> List.of("IRM du genou");
            case "CT_SCAN" -> List.of("Scanner thoracique");
            default -> List.of();
        };
    }

    /** Advance in Tunisian dinars; 0 means none required. */
    static BigDecimal advanceFor(String typeCode) {
        return switch (typeCode) {
            case "MRI", "CT_SCAN" -> new BigDecimal("50.000");
            case "ULTRASOUND", "XRAY" -> new BigDecimal("20.000");
            default -> new BigDecimal("0.000");
        };
    }
}
