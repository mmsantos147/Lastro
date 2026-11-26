package io.matheusms.lastro.adapter.in.rest.portfolio;

import static io.matheusms.lastro.adapter.in.rest.Ratios.rounded;

import io.matheusms.lastro.adapter.in.rest.CurrentUser;
import io.matheusms.lastro.application.IncomeService;
import io.matheusms.lastro.application.IncomeService.AssetIncome;
import io.matheusms.lastro.application.IncomeService.Income;
import io.matheusms.lastro.domain.service.IncomeReport.MonthlyIncome;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio/income")
@Validated
class IncomeController {

    private final IncomeService income;

    IncomeController(IncomeService income) {
        this.income = income;
    }

    /** Net distributions in BRL per month and yield on cost (ratios: 0.06 = 6%). */
    @GetMapping
    IncomeResponse income(@CurrentUser Long userId,
            @RequestParam(defaultValue = "24") @Min(1) @Max(120) int months) {
        return IncomeResponse.from(income.income(userId, months));
    }

    record IncomeResponse(
            List<MonthResponse> months, BigDecimal lastTwelveMonths, BigDecimal yieldOnCost,
            List<AssetIncomeResponse> byAsset) {

        static IncomeResponse from(Income income) {
            return new IncomeResponse(
                    income.months().stream().map(MonthResponse::from).toList(),
                    income.lastTwelveMonths().roundedToCents().amount(),
                    rounded(income.yieldOnCost()),
                    income.byAsset().stream().map(AssetIncomeResponse::from).toList());
        }
    }

    /** @param month {@code yyyy-MM} */
    record MonthResponse(String month, BigDecimal net, BigDecimal movingAverage) {

        static MonthResponse from(MonthlyIncome month) {
            return new MonthResponse(month.month().toString(), month.net().roundedToCents().amount(),
                    month.movingAverage().roundedToCents().amount());
        }
    }

    record AssetIncomeResponse(Long assetId, String code, BigDecimal lastTwelveMonths, BigDecimal yieldOnCost) {

        static AssetIncomeResponse from(AssetIncome income) {
            return new AssetIncomeResponse(income.asset().id(), income.asset().code(),
                    income.lastTwelveMonths().roundedToCents().amount(), rounded(income.yieldOnCost()));
        }
    }
}
