package com.clinicready.document;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.audit.AuditAction;
import com.clinicready.audit.AuditService;
import com.clinicready.common.InvalidRequestException;
import com.clinicready.common.NotFoundException;
import com.clinicready.document.DocumentStorage.StoredFile;
import com.clinicready.document.FileTypeDetector.FileType;
import com.clinicready.document.dto.NewDocument;
import com.clinicready.patient.PatientService;
import com.clinicready.security.AuthorizationService;
import com.clinicready.security.CurrentUserService;
import com.clinicready.security.Permission;
import com.clinicready.security.UserDirectory;

/**
 * Documents of the current user's clinic. Patient visibility is delegated to {@link PatientService},
 * so a document is reachable only by users who may see its patient (a practitioner: patients they
 * have an appointment with). Anything else is reported as not found.
 *
 * <p>Never logs file names, contents or patient details, only ids.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final String FALLBACK_FILENAME = "document";

    /** A document's metadata with an open stream of its bytes. The caller closes the stream. */
    public record DocumentContent(Document document, InputStream stream) {
    }

    /** Text read from a document's file. Never log it. */
    public record ExtractedText(String text, int pageCount, boolean truncated) {
    }

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final DocumentProperties properties;
    private final PatientService patients;
    private final CurrentUserService currentUser;
    private final AuthorizationService authorization;
    private final DocumentTextRepository texts;
    private final ApplicationEventPublisher events;
    private final DocumentMetadataRepository metadata;
    private final AuditService audit;
    private final UserDirectory userDirectory;
    private final DocumentExtractionRepository extractions;

    DocumentService(DocumentRepository documents, DocumentStorage storage, DocumentProperties properties,
            PatientService patients, CurrentUserService currentUser, AuthorizationService authorization,
            DocumentTextRepository texts, ApplicationEventPublisher events, DocumentMetadataRepository metadata,
            AuditService audit, UserDirectory userDirectory, DocumentExtractionRepository extractions) {
        this.documents = documents;
        this.storage = storage;
        this.properties = properties;
        this.patients = patients;
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.texts = texts;
        this.events = events;
        this.metadata = metadata;
        this.audit = audit;
        this.userDirectory = userDirectory;
        this.extractions = extractions;
    }

    /**
     * Stores a new version of a patient's document of the given category.
     *
     * @throws NotFoundException            patient is in another clinic or not visible to this user
     * @throws UnsupportedFileTypeException not a PDF, JPEG or PNG (judged by content)
     * @throws FileTooLargeException        over the configured size limit
     */
    @Transactional
    public Document upload(UUID patientId, NewDocument request, InputStream content) throws IOException {
        authorization.require(Permission.DOCUMENT_UPLOAD);
        patients.get(patientId); // 404 for another clinic's or an invisible patient
        UUID clinicId = currentUser.clinicId();
        long maxBytes = properties.maxSize().toBytes();
        if (request.declaredSize() > maxBytes) {
            throw new FileTooLargeException();
        }

        BufferedInputStream in = new BufferedInputStream(content);
        FileType type = detectType(in, request.declaredContentType());

        UUID id = UUID.randomUUID();
        String storageKey = clinicId + "/" + id;
        StoredFile stored = storage.store(storageKey, in, maxBytes);

        try {
            int version = documents.maxVersion(clinicId, patientId, request.category()) + 1;
            Document document = new Document(id, clinicId, patientId, request.category(),
                    request.source() != null ? request.source() : DocumentSource.STAFF_UPLOAD, version,
                    sanitizeFilename(request.filename()), type.mimeType(), stored.sizeBytes(), stored.sha256(),
                    storageKey, currentUser.userId());
            documents.saveAndFlush(document);
            events.publishEvent(new DocumentUploaded(id)); // text extraction starts after commit
            log.info("Document {} uploaded: patient={} category={} version={}", id, patientId,
                    request.category(), version);
            return document;
        } catch (DataIntegrityViolationException e) {
            storage.delete(storageKey);
            throw new UploadConflictException();
        } catch (RuntimeException e) {
            storage.delete(storageKey);
            throw e;
        }
    }

    /** Metadata of the patient's documents, grouped by category, newest version first. */
    @Transactional(readOnly = true)
    public List<Document> listForPatient(UUID patientId) {
        authorization.require(Permission.DOCUMENT_READ);
        patients.get(patientId);
        return documents.findAllByClinicIdAndPatientIdOrderByCategoryAscVersionDesc(currentUser.clinicId(),
                patientId);
    }

    @Transactional(readOnly = true)
    public Document get(UUID id) {
        authorization.require(Permission.DOCUMENT_READ);
        Document document = documents.findByIdAndClinicId(id, currentUser.clinicId())
                .orElseThrow(() -> new NotFoundException("Document"));
        patients.get(document.getPatientId());
        return document;
    }

    /**
     * The document's extracted text, after the same checks as {@link #get}. Empty while extraction is
     * pending or if it failed: check {@link Document#getExtractionStatus()} to tell which.
     */
    @Transactional(readOnly = true)
    public Optional<ExtractedText> extractedText(UUID id) {
        Document document = get(id);
        return texts.findByDocumentIdAndClinicId(document.getId(), document.getClinicId())
                .map(t -> new ExtractedText(t.getContent(), t.getPageCount(), t.isTruncated()));
    }

    /**
     * What the model observed about the document (category, date, issuer, confidence, model), after the
     * same checks as {@link #get}. Empty while classification is pending or when no model is configured.
     * An observation only: it never decides readiness, and staff corrections are kept in {@link #metadata}.
     */
    @Transactional(readOnly = true)
    public Optional<DocumentExtraction> aiExtraction(UUID id) {
        Document document = get(id);
        return extractions.findByDocumentIdAndClinicId(document.getId(), document.getClinicId());
    }

    /** The document's corrected fields after the same checks as {@link #get}; empty if never edited. */
    @Transactional(readOnly = true)
    public Optional<DocumentMetadata> metadata(UUID id) {
        Document document = get(id);
        return metadata.findByDocumentIdAndClinicId(document.getId(), document.getClinicId());
    }

    /**
     * Applies a partial edit: each entry sets a field, a null or blank value clears it, fields not
     * mentioned keep their value. Records who edited and when, and an audit event naming the changed
     * fields (never their values, which can identify the patient).
     *
     * @throws InvalidRequestException unknown field name, or a value that is too long or not a valid date
     */
    @Transactional
    public Optional<DocumentMetadata> updateMetadata(UUID id, Map<String, String> patch) {
        authorization.require(Permission.DOCUMENT_METADATA_EDIT);
        Document document = get(id);

        Map<String, String> changes = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : patch.entrySet()) {
            DocumentMetadataField field = DocumentMetadataField.fromKey(entry.getKey())
                    .orElseThrow(() -> new InvalidRequestException("Unknown metadata field"));
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!value.isEmpty() && !field.accepts(value)) {
                throw new InvalidRequestException("Invalid value for " + field.key());
            }
            changes.put(field.key(), value);
        }

        DocumentMetadata row = metadata.findByDocumentIdAndClinicId(id, document.getClinicId()).orElse(null);
        Map<String, String> merged = new TreeMap<>(row == null ? Map.of() : row.getFields());
        List<String> changedFields = new ArrayList<>();
        changes.forEach((key, value) -> {
            String before = merged.get(key);
            String after = value.isEmpty() ? null : value;
            if (Objects.equals(before, after)) {
                return;
            }
            changedFields.add(key);
            if (after == null) {
                merged.remove(key);
            } else {
                merged.put(key, after);
            }
        });
        if (changedFields.isEmpty()) {
            // Nothing changed: keep the previous editor and timestamp. A never-edited document stays empty.
            return Optional.ofNullable(row);
        }
        if (row == null) {
            row = new DocumentMetadata(id, document.getClinicId(), merged, currentUser.userId());
        } else {
            row.replaceFields(merged, currentUser.userId());
        }
        row = metadata.saveAndFlush(row);
        audit.record(AuditAction.DOCUMENT_METADATA_UPDATED, "Document", id, Map.of("changedFields", changedFields));
        log.info("Document {} metadata updated: fields={}", id, changedFields);
        return Optional.of(row);
    }

    /** Display name of whoever last edited the fields, if that user still belongs to this clinic. */
    @Transactional(readOnly = true)
    public Optional<String> editorName(DocumentMetadata metadata) {
        return userDirectory.fullName(metadata.getUpdatedBy(), currentUser.clinicId());
    }

    /** Opens the file. The only way to read stored bytes: callers must pass the same checks as {@link #get}. */
    @Transactional(readOnly = true)
    public DocumentContent open(UUID id) {
        Document document = get(id);
        try {
            return new DocumentContent(document, storage.open(document.getStorageKey()));
        } catch (NoSuchFileException e) {
            log.error("Document {} has metadata but no stored file", id);
            throw new NotFoundException("Document file");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read stored document " + id, e);
        }
    }

    private static FileType detectType(BufferedInputStream in, String declaredContentType) throws IOException {
        in.mark(FileTypeDetector.HEADER_BYTES);
        byte[] header = in.readNBytes(FileTypeDetector.HEADER_BYTES);
        in.reset();
        if (header.length == 0) {
            throw new InvalidFileException("The file is empty");
        }
        FileType type = FileTypeDetector.detect(header, header.length)
                .orElseThrow(UnsupportedFileTypeException::new);
        if (!FileTypeDetector.matchesDeclared(type, declaredContentType)) {
            throw new UnsupportedFileTypeException();
        }
        return type;
    }

    /** Display name only: base name, no control characters, bounded length. Never used as a path. */
    static String sanitizeFilename(String raw) {
        if (raw == null) {
            return FALLBACK_FILENAME;
        }
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            return FALLBACK_FILENAME;
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
