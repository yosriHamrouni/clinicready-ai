package com.clinicready.document;

/** Why no usable observation was recorded. Either way a person must review the document. */
public enum AiExtractionFailure {
    /** The model call itself failed (network, quota, refusal, empty answer). */
    MODEL_ERROR,
    /** The model answered, but not with JSON that matches the extraction schema. */
    INVALID_OUTPUT
}
