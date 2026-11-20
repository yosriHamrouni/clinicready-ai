package com.clinicready.document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.seed.DemoClinicConfig;
import com.clinicready.seed.DemoData;

/**
 * Gives the demo clinic's upcoming appointments synthetic documents, so the demo shows ready, incomplete,
 * to-review and expired files side by side. Lives in this package because storage is package-private.
 *
 * <p>Each appointment gets one of six situations by its position among the scheduled ones: all documents (3 of
 * 6), one mandatory document missing, one low-confidence scan, one document older than its validity. A patient
 * keeps the first document inserted for a category, so appointments sharing a patient may differ slightly from
 * their nominal situation. Every PDF only says it is a synthetic demonstration document: no name, no real data.
 * Insert-only and keyed on deterministic ids, so running it again changes nothing.
 */
@Service
@Profile("demo")
public class DemoDocumentSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDocumentSeeder.class);
    private static final long MAX_BYTES = 1_000_000;

    private static final Map<String, String> LABELS = Map.of(
            "ID_DOCUMENT", "Pièce d'identité",
            "INSURANCE_CARD", "Carte d'assurance",
            "PRESCRIPTION", "Ordonnance",
            "REFERRAL_LETTER", "Lettre d'orientation",
            "CONSENT_FORM", "Formulaire de consentement",
            "LAB_RESULT", "Résultat d'analyse");
    private static final Map<String, String> FILES = Map.of(
            "ID_DOCUMENT", "piece-identite.pdf",
            "INSURANCE_CARD", "carte-assurance.pdf",
            "PRESCRIPTION", "ordonnance.pdf",
            "REFERRAL_LETTER", "lettre-orientation.pdf",
            "CONSENT_FORM", "consentement.pdf",
            "LAB_RESULT", "resultat-analyse.pdf");

    private enum Quality { GOOD, LOW_CONFIDENCE, OLD }

    private record Planned(UUID patientId, String category, Quality quality, Integer validityDays) {
    }

    private final JdbcTemplate jdbc;
    private final DocumentStorage storage;

    DemoDocumentSeeder(JdbcTemplate jdbc, DocumentStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Transactional
    public void seed(DemoData data) {
        UUID clinicId = data.clinic().id();
        UUID reception = data.users().stream().filter(u -> u.role().equals("RECEPTIONIST")).map(DemoData.User::id)
                .findFirst().orElseThrow();
        Map<UUID, String> typeCodes = data.appointmentTypes().stream()
                .collect(Collectors.toMap(DemoData.AppointmentType::id, DemoData.AppointmentType::code));

        List<Planned> plan = new ArrayList<>();
        Set<String> planned = new HashSet<>();
        int index = 0;
        for (DemoData.Appointment a : data.appointments()) {
            if (!a.status().equals("SCHEDULED")) {
                continue;
            }
            int situation = index++ % 6;
            List<DemoClinicConfig.Rule> mandatory = DemoClinicConfig.rulesFor(typeCodes.get(a.appointmentTypeId()))
                    .stream().filter(DemoClinicConfig.Rule::mandatory).toList();
            // The requirement this appointment's problem is about: the last mandatory one.
            DemoClinicConfig.Rule target = mandatory.get(mandatory.size() - 1);
            for (DemoClinicConfig.Rule rule : mandatory) {
                boolean isTarget = rule == target;
                if (situation == 2 && isTarget) {
                    continue; // missing
                }
                Quality quality = Quality.GOOD;
                if (situation == 3 && isTarget) {
                    quality = Quality.LOW_CONFIDENCE;
                } else if (situation == 4 && isTarget && rule.validityDays() != null) {
                    quality = Quality.OLD;
                }
                if (planned.add(a.patientId() + "/" + rule.category())) {
                    plan.add(new Planned(a.patientId(), rule.category(), quality, rule.validityDays()));
                }
            }
        }

        int inserted = 0;
        for (Planned p : plan) {
            inserted += insert(clinicId, reception, p);
        }
        log.info("Demo documents: {} planned, {} inserted", plan.size(), inserted);
    }

    private int insert(UUID clinicId, UUID uploader, Planned p) {
        UUID id = UUID.nameUUIDFromBytes(
                ("clinicready-demo/document/" + p.patientId() + "/" + p.category()).getBytes(StandardCharsets.UTF_8));
        String key = "demo/" + id + ".pdf";
        byte[] pdf = pdf(p.category());
        DocumentStorage.StoredFile stored = ensureStored(key, pdf);

        Instant uploadedAt = p.quality() == Quality.OLD
                ? Instant.now().minus(Duration.ofDays(p.validityDays() + 10L))
                : Instant.now().minus(Duration.ofDays(5));
        int rows = jdbc.update("""
                INSERT INTO document (id, clinic_id, patient_id, category, source, status, version, original_filename,
                    content_type, size_bytes, sha256, storage_key, uploaded_by, uploaded_at, extraction_status,
                    extracted_at)
                VALUES (?, ?, ?, ?, 'STAFF_UPLOAD', ?, 1, ?, 'application/pdf', ?, ?, ?, ?, ?, 'EXTRACTED', ?)
                ON CONFLICT (id) DO NOTHING""",
                id, clinicId, p.patientId(), p.category(),
                p.quality() == Quality.LOW_CONFIDENCE ? "NEEDS_REVIEW" : "UPLOADED", FILES.get(p.category()),
                stored.sizeBytes(), stored.sha256(), key, uploader, Timestamp.from(uploadedAt),
                Timestamp.from(uploadedAt));
        if (rows == 0) {
            return 0;
        }
        jdbc.update("""
                INSERT INTO document_text (document_id, clinic_id, content, page_count, truncated)
                VALUES (?, ?, ?, 1, FALSE)""", id, clinicId, text(p.category()));
        boolean low = p.quality() == Quality.LOW_CONFIDENCE;
        jdbc.update("""
                INSERT INTO document_extraction (document_id, clinic_id, status, category, document_date, issuer,
                    confidence, model_name, review_reason)
                VALUES (?, ?, 'SUCCEEDED', ?, ?, ?, ?, 'demo-seed', ?)""",
                id, clinicId, p.category(), Date.valueOf(LocalDate.ofInstant(uploadedAt, java.time.ZoneOffset.UTC)),
                "Clinique Démo Nabeul", low ? 0.4 : 0.95, low ? "LOW_CONFIDENCE" : null);
        return 1;
    }

    /** Writes the file unless it is already there (a recreated volume gets its files back). */
    private DocumentStorage.StoredFile ensureStored(String key, byte[] pdf) {
        try {
            try (var in = storage.open(key)) {
                in.readAllBytes();
                return new DocumentStorage.StoredFile(pdf.length, sha256(pdf));
            } catch (NoSuchFileException e) {
                return storage.store(key, new ByteArrayInputStream(pdf), MAX_BYTES);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(String category) {
        return "DOCUMENT DE DEMONSTRATION - DONNEES SYNTHETIQUES\n" + LABELS.get(category)
                + "\nAucune donnee reelle. Genere pour la demonstration de ClinicReady.";
    }

    private static byte[] pdf(String category) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                content.newLineAtOffset(60, 740);
                for (String line : text(category).split("\n")) {
                    content.showText(line);
                    content.newLineAtOffset(0, -24);
                }
                content.endText();
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
