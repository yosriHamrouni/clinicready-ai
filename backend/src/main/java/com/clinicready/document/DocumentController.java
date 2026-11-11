package com.clinicready.document;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.clinicready.document.DocumentService.DocumentContent;
import com.clinicready.document.dto.DocumentResponse;
import com.clinicready.document.dto.NewDocument;

/**
 * Upload and read documents. Files are served only here, after authentication, the role check and
 * the patient/clinic scoping in {@link DocumentService}; there is no public file URL.
 */
@RestController
@RequestMapping("/api/v1")
class DocumentController {

    private final DocumentService documents;

    DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(path = "/patients/{patientId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentResponse> upload(@PathVariable UUID patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam DocumentCategory category,
            @RequestParam(required = false) DocumentSource source) throws IOException {
        NewDocument request = new NewDocument(file.getOriginalFilename(), file.getContentType(), file.getSize(),
                category, source);
        Document document;
        try (var content = file.getInputStream()) {
            document = documents.upload(patientId, request, content);
        }
        return ResponseEntity.created(URI.create("/api/v1/documents/" + document.getId()))
                .body(DocumentResponse.from(document));
    }

    @GetMapping("/patients/{patientId}/documents")
    List<DocumentResponse> list(@PathVariable UUID patientId) {
        return documents.listForPatient(patientId).stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/documents/{id}")
    DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(documents.get(id));
    }

    @GetMapping("/documents/{id}/content")
    ResponseEntity<Resource> content(@PathVariable UUID id) {
        DocumentContent content = documents.open(id);
        Document document = content.document();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .contentLength(document.getSizeBytes())
                .eTag("\"" + document.getSha256() + "\"")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(document.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(content.stream()));
    }
}
