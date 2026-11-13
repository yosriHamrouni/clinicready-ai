package com.clinicready.document;

import java.util.UUID;

/** Published inside the upload transaction; text extraction starts once it commits. */
record DocumentUploaded(UUID documentId) {
}
