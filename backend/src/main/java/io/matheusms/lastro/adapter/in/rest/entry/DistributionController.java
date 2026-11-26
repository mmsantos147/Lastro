package io.matheusms.lastro.adapter.in.rest.entry;

import io.matheusms.lastro.adapter.in.rest.CurrentUser;
import io.matheusms.lastro.application.DistributionService;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Distribution;
import io.matheusms.lastro.domain.model.DistributionType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/distributions")
class DistributionController {

    private final DistributionService distributions;

    DistributionController(DistributionService distributions) {
        this.distributions = distributions;
    }

    @GetMapping
    List<DistributionResponse> list(@CurrentUser Long userId) {
        return distributions.list(userId).stream().map(DistributionResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DistributionResponse create(@CurrentUser Long userId, @Valid @RequestBody DistributionRequest request) {
        return DistributionResponse.from(distributions.register(request.toDomain(userId)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser Long userId, @PathVariable Long id) {
        distributions.delete(userId, id);
    }

    /** The net amount is derived: {@code grossAmount - withheldTax}. */
    record DistributionRequest(
            @NotNull Long assetId,
            @NotNull Long institutionId,
            @NotNull DistributionType type,
            @NotNull LocalDate recordDate,
            @NotNull LocalDate paymentDate,
            @NotNull BigDecimal baseQuantity,
            @NotNull Currency currency,
            @NotNull BigDecimal grossAmount,
            BigDecimal withheldTax,
            Long paidInAssetId,
            BigDecimal paidQuantity) {

        Distribution toDomain(Long userId) {
            var gross = new Money(grossAmount, currency);
            var tax = new Money(withheldTax == null ? BigDecimal.ZERO : withheldTax, currency);
            return new Distribution(null, userId, assetId, institutionId, type, recordDate, paymentDate,
                    new Quantity(baseQuantity), gross, tax, gross.minus(tax), paidInAssetId,
                    paidQuantity == null ? null : new Quantity(paidQuantity));
        }
    }

    record DistributionResponse(
            Long id,
            Long assetId,
            Long institutionId,
            DistributionType type,
            LocalDate recordDate,
            LocalDate paymentDate,
            BigDecimal baseQuantity,
            Currency currency,
            BigDecimal grossAmount,
            BigDecimal withheldTax,
            BigDecimal netAmount,
            Long paidInAssetId,
            BigDecimal paidQuantity) {

        static DistributionResponse from(Distribution distribution) {
            return new DistributionResponse(distribution.id(), distribution.assetId(), distribution.institutionId(),
                    distribution.type(), distribution.recordDate(), distribution.paymentDate(),
                    distribution.baseQuantity().value(), distribution.grossAmount().currency(),
                    distribution.grossAmount().amount(), distribution.withheldTax().amount(),
                    distribution.netAmount().amount(), distribution.paidInAssetId(),
                    distribution.paidQuantity() == null ? null : distribution.paidQuantity().value());
        }
    }
}
