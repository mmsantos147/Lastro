package io.matheusms.lastro.domain.model;

public enum ImportStatus {
    /** Every row was recorded, was already recorded, or was correctly left out (informational rows). */
    COMPLETED,
    /** Some rows need the user's attention (unsupported, rejected or recorded with a caveat). */
    COMPLETED_WITH_ISSUES
}
