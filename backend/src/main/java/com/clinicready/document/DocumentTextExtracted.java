package com.clinicready.document;

import java.util.UUID;

/** Published inside the transaction that stores a document's text; classification starts once it commits. */
record DocumentTextExtracted(UUID documentId) {
}
