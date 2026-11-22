package org.cocos.portfolio.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.cocos.instruments.domain.entity.Instrument;
import org.cocos.marketdata.domain.entity.MarketData;
import org.cocos.orders.domain.ledger.AccountSnapshot;
import org.cocos.orders.domain.ledger.PositionSnapshot;
import org.cocos.portfolio.infrastructure.dto.response.PortfolioResponse;
import org.cocos.portfolio.infrastructure.dto.response.PortfolioResponse.PositionResponse;
import org.springframework.stereotype.Component;

@Component
class PortfolioValuator {

    private static final int MONEY_SCALE = 2;

    private static final Comparator<PositionResponse> BY_TICKER = Comparator
            .comparing(PositionResponse::ticker, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PositionResponse::instrumentId);

    PortfolioResponse evaluate(AccountSnapshot snapshot, Map<Integer, Instrument> instruments,
                               Map<Integer, MarketData> quotes) {
        List<PositionResponse> positions = snapshot.visiblePositions().entrySet().stream()
                .map(entry -> valuePosition(entry.getKey(), entry.getValue(), instruments.get(entry.getKey()),
                        quotes.get(entry.getKey())))
                .sorted(BY_TICKER)
                .toList();

        return new PortfolioResponse(
                money(snapshot.availableCash()),
                totalAccountValue(snapshot, positions),
                positions);
    }

    private BigDecimal totalAccountValue(AccountSnapshot snapshot, List<PositionResponse> positions) {
        return snapshot.isReliable() && positions.stream().allMatch(position -> position.marketValue() != null)
                ? positions.stream().map(PositionResponse::marketValue).reduce(money(snapshot.cash()), BigDecimal::add)
                : null;
    }

    private PositionResponse valuePosition(Integer instrumentId, PositionSnapshot position,
                                           Instrument instrument, MarketData quote) {
        BigDecimal close = quote != null && isPositive(quote.getClose()) ? quote.getClose() : null;

        return new PositionResponse(
                instrumentId,
                instrument == null ? null : instrument.getTicker(),
                position.quantity(),
                marketValue(position, instrument, close),
                totalReturn(position, close),
                dailyReturn(quote, close));
    }

    private BigDecimal marketValue(PositionSnapshot position, Instrument instrument, BigDecimal close) {
        return close != null && instrument != null
                ? money(close.multiply(BigDecimal.valueOf(position.quantity())))
                : null;
    }

    private BigDecimal totalReturn(PositionSnapshot position, BigDecimal close) {
        return position.quantity() > 0 && position.consistent()
                ? percentageReturn(close, position.averageCost())
                : null;
    }

    private BigDecimal dailyReturn(MarketData quote, BigDecimal close) {
        return quote == null ? null : percentageReturn(close, quote.getPreviousClose());
    }

    private BigDecimal percentageReturn(BigDecimal current, BigDecimal reference) {
        return current != null && isPositive(reference)
                ? current.divide(reference, PositionSnapshot.DIVISION_SCALE, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                : null;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
