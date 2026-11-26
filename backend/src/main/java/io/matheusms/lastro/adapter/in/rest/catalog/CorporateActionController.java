package io.matheusms.lastro.adapter.in.rest.catalog;

import io.matheusms.lastro.adapter.in.rest.CurrentUser;
import io.matheusms.lastro.application.CorporateActionService;
import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.model.CorporateActionType;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
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
@RequestMapping("/api/corporate-actions")
class CorporateActionController {

    private final CorporateActionService corporateActions;

    CorporateActionController(CorporateActionService corporateActions) {
        this.corporateActions = corporateActions;
    }

    @GetMapping
    List<CorporateActionResponse> list() {
        return corporateActions.list().stream().map(CorporateActionResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CorporateActionResponse create(@CurrentUser Long userId, @Valid @RequestBody CorporateActionRequest request) {
        var action = new CorporateAction(null, request.assetId(), request.type(), request.recordDate(),
                request.factor(), request.assignedCost() == null ? null : new Money(request.assignedCost(), Currency.BRL),
                request.successorAssetId());
        return CorporateActionResponse.from(corporateActions.register(userId, action));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser Long userId, @PathVariable Long id) {
        corporateActions.delete(userId, id);
    }

    record CorporateActionRequest(
            @NotNull Long assetId,
            @NotNull CorporateActionType type,
            @NotNull LocalDate recordDate,
            @NotNull BigDecimal factor,
            BigDecimal assignedCost,
            Long successorAssetId) {
    }

    record CorporateActionResponse(
            Long id, Long assetId, CorporateActionType type, LocalDate recordDate, BigDecimal factor,
            BigDecimal assignedCost, Long successorAssetId) {

        static CorporateActionResponse from(CorporateAction action) {
            return new CorporateActionResponse(action.id(), action.assetId(), action.type(), action.recordDate(),
                    action.factor(), action.assignedCost() == null ? null : action.assignedCost().amount(),
                    action.successorAssetId());
        }
    }
}
