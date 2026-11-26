package io.matheusms.lastro.domain.model;

/** Why the rules engine suggested an amount for an item; the UI and the analysis prompt translate it. */
public enum SuggestionReason {
    /** The bucket is below its target: the amount closes (part of) the gap. */
    BELOW_TARGET,
    /** Every gap is closed; the remainder is split in proportion to the targets. */
    PROPORTIONAL_TO_TARGET,
    /** The class has a target but no asset held yet: the investor chooses which asset to buy. */
    NEW_POSITION_IN_CLASS,
    /** Every asset of the class reached the per-asset cap: the investor chooses a new asset of the class. */
    ASSET_CAP_REACHED
}
