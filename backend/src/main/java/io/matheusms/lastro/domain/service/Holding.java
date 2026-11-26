package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Money;

/** Market value of an open position in BRL, input of the allocation analysis and of the rules engine. */
public record Holding(Long assetId, AssetClass assetClass, Money value) {
}
