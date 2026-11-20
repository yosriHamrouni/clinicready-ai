package com.clinicready.readiness;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.clinicready.document.DocumentCategory;
import com.clinicready.document.DocumentStatus;
import com.clinicready.document.ExtractionStatus;
import com.clinicready.document.ReviewReason;
import com.clinicready.readiness.ReadinessEngine.AppointmentFacts;
import com.clinicready.readiness.ReadinessEngine.Evidence;
import com.clinicready.readiness.ReadinessEngine.Kind;
import com.clinicready.readiness.ReadinessEngine.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Checks the evaluation set (eval/dataset/eval-cases.json, YOS-25) against the real {@link ReadinessEngine}:
 * the shape promised in eval/README.md, and the expected readiness of every case. The expectations are the
 * ground truth for the evaluation run (YOS-39), so they must agree with the code.
 */
class EvalDatasetTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");
    private static final Path DATASET = Path.of("..", "eval", "dataset", "eval-cases.json");

    private final JsonNode root = load();

    private static JsonNode load() {
        try {
            return new ObjectMapper().readTree(Files.readString(DATASET));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + DATASET.toAbsolutePath()
                    + " (tests run from backend/; regenerate with: python eval/generate_cases.py)", e);
        }
    }

    @Test
    void hasFortyCasesAcrossFourTypesAndTwoPractitionersWithEachScenarioAtLeastThreeTimes() {
        List<JsonNode> cases = cases();
        assertThat(cases).hasSize(40);
        assertThat(cases).extracting(c -> c.get("id").asText()).doesNotHaveDuplicates();
        assertThat(cases).extracting(c -> c.get("appointmentType").asText()).containsOnly(
                "CONSULTATION", "ULTRASOUND", "XRAY", "MRI");
        assertThat(cases.stream().map(c -> c.get("appointmentType").asText()).distinct()).hasSize(4);
        assertThat(cases.stream().map(c -> c.get("practitioner").asText()).distinct()).hasSize(2);
        assertThat(cases.stream().map(c -> c.get("patientLanguage").asText()).distinct())
                .containsExactlyInAnyOrder("FR", "AR");
        Map<String, Long> perScenario = cases.stream()
                .collect(Collectors.groupingBy(c -> c.get("scenario").asText(), Collectors.counting()));
        assertThat(perScenario).hasSize(8);
        assertThat(perScenario.values()).allMatch(n -> n >= 3);
    }

    @Test
    void followUpCountsAreConsistentWithTheStatuses() {
        for (JsonNode c : cases()) {
            JsonNode expected = c.get("expected");
            String aggregate = expected.get("aggregate").asText();
            // A follow-up is proposed exactly when a mandatory requirement is MISSING or EXPIRED.
            assertThat(expected.get("followUpTasks").asInt()).as(c.get("id").asText())
                    .isEqualTo(aggregate.equals("INCOMPLETE") ? 1 : 0);
            assertThat(expected.get("requiresHumanReview").asBoolean()).as(c.get("id").asText())
                    .isEqualTo(aggregate.equals("NEEDS_REVIEW"));
            assertThat(c.get("openFollowUp").isNull()).as(c.get("id").asText())
                    .isEqualTo(!c.get("scenario").asText().equals("INCOMPLETE_FOLLOW_UP_OPEN"));
        }
    }

    @TestFactory
    Stream<DynamicTest> everyCaseGetsTheExpectedReadiness() {
        return cases().stream().map(c -> DynamicTest.dynamicTest(
                c.get("id").asText() + " " + c.get("scenario").asText() + " " + c.get("appointmentType").asText(),
                () -> assertReadiness(c)));
    }

    private void assertReadiness(JsonNode c) {
        LocalDate day = LocalDate.parse(root.get("appointmentDay").asText());
        List<Requirement> requirements = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (JsonNode rule : root.get("requirementRules").get(c.get("appointmentType").asText())) {
            DocumentCategory category = DocumentCategory.valueOf(rule.get("category").asText());
            Integer validity = rule.get("validityDays").isNull() ? null : rule.get("validityDays").asInt();
            requirements.add(new Requirement(UUID.randomUUID(), Kind.DOCUMENT, Set.of(category),
                    rule.get("mandatory").asBoolean(), validity, false, null));
            codes.add(category.name());
        }
        List<Evidence> evidence = new ArrayList<>();
        for (JsonNode d : c.get("documents")) {
            JsonNode expiry = d.get("expiryDaysFromAppointment");
            evidence.add(new Evidence(UUID.randomUUID(), DocumentCategory.valueOf(d.get("category").asText()),
                    DocumentStatus.valueOf(d.get("status").asText()),
                    ExtractionStatus.valueOf(d.get("extractionStatus").asText()),
                    d.get("reviewReason").isNull() ? null : ReviewReason.valueOf(d.get("reviewReason").asText()),
                    expiry.isNull() ? null : day.plusDays(expiry.asInt()),
                    day.minusDays(d.get("uploadedDaysBeforeAppointment").asInt()).atTime(12, 0).atZone(TUNIS)
                            .toInstant(),
                    d.get("datesConflict").asBoolean()));
        }

        ReadinessResult result = ReadinessEngine.evaluate(requirements, evidence,
                new AppointmentFacts(day, TUNIS, List.of(), BigDecimal.ZERO, false));

        JsonNode expected = c.get("expected");
        assertThat(result.status()).as("aggregate").hasToString(expected.get("aggregate").asText());
        Map<String, String> actual = new LinkedHashMap<>();
        for (int i = 0; i < codes.size(); i++) {
            actual.put(codes.get(i), result.checks().get(i).status().name());
        }
        Map<String, String> wanted = new LinkedHashMap<>();
        expected.get("checks").fields().forEachRemaining(e -> wanted.put(e.getKey(), e.getValue().asText()));
        assertThat(actual).as("per-requirement statuses").containsExactlyInAnyOrderEntriesOf(wanted);
        assertThat(result.checks().stream().anyMatch(ReadinessCheck::requiresHumanReview))
                .as("requires human review").isEqualTo(expected.get("requiresHumanReview").asBoolean());
        Collection<ReadinessCheck> blocking = result.checks().stream().filter(ReadinessCheck::mandatory)
                .filter(k -> k.status() == CheckStatus.MISSING || k.status() == CheckStatus.EXPIRED).toList();
        assertThat(blocking.size()).as("blocking requirements, one follow-up each")
                .isEqualTo(expected.get("followUpTasks").asInt());
    }

    private List<JsonNode> cases() {
        List<JsonNode> cases = new ArrayList<>();
        root.get("cases").forEach(cases::add);
        return cases;
    }
}
