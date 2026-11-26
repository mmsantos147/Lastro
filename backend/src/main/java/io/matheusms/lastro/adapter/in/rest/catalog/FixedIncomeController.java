package io.matheusms.lastro.adapter.in.rest.catalog;

import io.matheusms.lastro.application.FixedIncomeService;
import io.matheusms.lastro.domain.model.FixedIncomeIndexer;
import io.matheusms.lastro.domain.model.FixedIncomeTerms;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Terms of private fixed income assets (CDB, LCI, LCA), which give their value on the curve. */
@RestController
@RequestMapping("/api/fixed-income")
class FixedIncomeController {

    private final FixedIncomeService fixedIncome;

    FixedIncomeController(FixedIncomeService fixedIncome) {
        this.fixedIncome = fixedIncome;
    }

    @GetMapping
    List<TermsResponse> list() {
        return fixedIncome.list().stream().map(TermsResponse::from).toList();
    }

    /** Creates or replaces the terms of the asset and recomputes its curve. */
    @PutMapping("/{assetId}")
    TermsResponse save(@PathVariable Long assetId, @Valid @RequestBody TermsRequest request) {
        return TermsResponse.from(fixedIncome.save(new FixedIncomeTerms(assetId, request.issuer(), request.indexer(),
                request.indexerPercentage(), request.rate() == null ? BigDecimal.ZERO : request.rate(),
                request.maturityDate(), Boolean.TRUE.equals(request.dailyLiquidity()))));
    }

    /**
     * @param indexerPercentage only for CDI and Selic (110 = 110% do CDI)
     * @param rate annual %: fixed rate, real rate over IPCA, or spread over CDI/Selic; empty means 0
     */
    record TermsRequest(
            String issuer,
            @NotNull FixedIncomeIndexer indexer,
            BigDecimal indexerPercentage,
            BigDecimal rate,
            LocalDate maturityDate,
            Boolean dailyLiquidity) {
    }

    record TermsResponse(
            Long assetId, String issuer, FixedIncomeIndexer indexer, BigDecimal indexerPercentage, BigDecimal rate,
            LocalDate maturityDate, boolean dailyLiquidity) {

        static TermsResponse from(FixedIncomeTerms terms) {
            return new TermsResponse(terms.assetId(), terms.issuer(), terms.indexer(), terms.indexerPercentage(),
                    terms.rate(), terms.maturityDate(), terms.dailyLiquidity());
        }
    }
}
