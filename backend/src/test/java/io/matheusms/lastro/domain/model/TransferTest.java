package io.matheusms.lastro.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransferTest {

    @Test
    void shouldComputeReceivedQuantityNetOfFee() {
        assertThat(transfer(1L, 2L, "0.5", "0.0002").receivedQuantity()).isEqualTo(Quantity.of("0.4998"));
    }

    @Test
    void shouldRejectSameOriginAndDestination() {
        assertThatThrownBy(() -> transfer(1L, 1L, "1", "0")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectFeeGreaterThanQuantity() {
        assertThatThrownBy(() -> transfer(1L, 2L, "1", "1.1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectZeroQuantity() {
        assertThatThrownBy(() -> transfer(1L, 2L, "0", "0")).isInstanceOf(IllegalArgumentException.class);
    }

    private static Transfer transfer(Long from, Long to, String quantity, String fee) {
        return new Transfer(null, 1L, 1L, from, to, LocalDate.of(2026, 1, 5),
                Quantity.of(quantity), Quantity.of(fee), EntrySource.MANUAL, null);
    }
}
