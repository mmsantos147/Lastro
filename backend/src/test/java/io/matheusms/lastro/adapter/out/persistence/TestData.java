package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Cnpj;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.Institution;
import io.matheusms.lastro.domain.model.InstitutionType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.model.TradeFee;
import io.matheusms.lastro.domain.model.TradeType;
import io.matheusms.lastro.domain.model.User;
import io.matheusms.lastro.domain.model.UserRole;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

final class TestData {

    static final Cnpj BROKER_CNPJ = new Cnpj("11.222.333/0001-81");
    static final String PASSWORD_HASH = "{noop}not-a-real-hash";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private TestData() {
    }

    /** A new user with a unique username. */
    static User user() {
        return new User(null, "Matheus", "user" + SEQUENCE.incrementAndGet(), UserRole.ADMIN, true);
    }

    static Institution broker() {
        return new Institution(null, "Broker", InstitutionType.BROKER, BROKER_CNPJ);
    }

    static Institution wallet() {
        return new Institution(null, "Hardware wallet", InstitutionType.WALLET, null);
    }

    static Asset stock(String code) {
        return new Asset(null, code, code + " S.A.", AssetClass.STOCK, null, Currency.BRL, true);
    }

    static Asset crypto(String code) {
        return new Asset(null, code, code, AssetClass.CRYPTO, null, Currency.USD, true);
    }

    static Trade buy(Long userId, Long assetId, Long institutionId, LocalDate date, String quantity) {
        return new Trade(null, userId, assetId, institutionId, TradeType.BUY, date, Quantity.of(quantity),
                Money.brl("10.00"), new TradeFee.InCurrency(Money.brl("1.00")), EntrySource.MANUAL, null);
    }
}
