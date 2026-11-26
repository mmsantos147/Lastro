package io.matheusms.lastro.adapter.in.rest.entry;

import io.matheusms.lastro.adapter.in.rest.CurrentUser;
import io.matheusms.lastro.application.TransferService;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Transfer;
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
@RequestMapping("/api/transfers")
class TransferController {

    private final TransferService transfers;

    TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @GetMapping
    List<TransferResponse> list(@CurrentUser Long userId) {
        return transfers.list(userId).stream().map(TransferResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TransferResponse create(@CurrentUser Long userId, @Valid @RequestBody TransferRequest request) {
        var transfer = new Transfer(null, userId, request.assetId(), request.fromInstitutionId(),
                request.toInstitutionId(), request.date(), new Quantity(request.quantity()),
                new Quantity(request.fee() == null ? BigDecimal.ZERO : request.fee()), EntrySource.MANUAL, null);
        return TransferResponse.from(transfers.register(transfer));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser Long userId, @PathVariable Long id) {
        transfers.delete(userId, id);
    }

    /** @param fee in units of the transferred asset; zero when omitted */
    record TransferRequest(
            @NotNull Long assetId,
            @NotNull Long fromInstitutionId,
            @NotNull Long toInstitutionId,
            @NotNull LocalDate date,
            @NotNull BigDecimal quantity,
            BigDecimal fee) {
    }

    record TransferResponse(
            Long id,
            Long assetId,
            Long fromInstitutionId,
            Long toInstitutionId,
            LocalDate date,
            BigDecimal quantity,
            BigDecimal fee,
            EntrySource source,
            String externalId) {

        static TransferResponse from(Transfer transfer) {
            return new TransferResponse(transfer.id(), transfer.assetId(), transfer.fromInstitutionId(),
                    transfer.toInstitutionId(), transfer.date(), transfer.quantity().value(), transfer.fee().value(),
                    transfer.source(), transfer.externalId());
        }
    }
}
