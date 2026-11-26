package io.matheusms.lastro.domain.service;

import io.matheusms.lastro.domain.model.Money;
import java.time.LocalDate;

/**
 * Money entering (positive) or leaving (negative) the portfolio, in BRL: purchases are contributions; sale proceeds
 * and cash distributions are withdrawals. Used to separate contributions from returns.
 */
public record CashFlow(LocalDate date, Money amount) {
}
