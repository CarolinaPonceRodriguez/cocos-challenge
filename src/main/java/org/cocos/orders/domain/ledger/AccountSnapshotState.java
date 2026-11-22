package org.cocos.orders.domain.ledger;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.cocos.orders.domain.entity.Order;

final class AccountSnapshotState {

    private BigDecimal cash = BigDecimal.ZERO;

    private BigDecimal reservedCash = BigDecimal.ZERO;

    private final Map<Integer, PositionAccumulator> positions = new LinkedHashMap<>();

    private boolean hasIncompleteHistory;

    void apply(Order order) {
        if (isApplicable(order)) {
            record(order);
        } else {
            hasIncompleteHistory = true;
        }
    }

    AccountSnapshot toSnapshot() {
        Map<Integer, PositionSnapshot> snapshots = new LinkedHashMap<>();
        positions.forEach((instrumentId, position) -> snapshots.put(instrumentId, position.toSnapshot()));

        return new AccountSnapshot(cash, reservedCash, snapshots, hasIncompleteHistory);
    }

    private boolean isApplicable(Order order) {
        return order.hasPositiveSize()
                && order.hasSide()
                && meetsSideRequirements(order);
    }

    private boolean meetsSideRequirements(Order order) {
        return switch (order.getSide()) {
            case CASH_IN, CASH_OUT -> true;
            case BUY, SELL -> order.referencesInstrument() && order.hasPriceWhenRequired();
        };
    }

    private void record(Order order) {
        switch (order.getSide()) {
            case CASH_IN -> cash = cash.add(BigDecimal.valueOf(order.getSize()));
            case CASH_OUT -> cash = cash.subtract(BigDecimal.valueOf(order.getSize()));
            case BUY -> recordBuy(order);
            case SELL -> recordSell(order);
        }
    }

    private void recordBuy(Order order) {
        if (order.isSettled()) {
            settleBuy(order);
        } else {
            reservedCash = reservedCash.add(tradeAmount(order));
        }
    }

    private void recordSell(Order order) {
        if (order.isSettled()) {
            settleSell(order);
        } else {
            positionFor(order.getInstrumentId()).reserve(order.getSize());
        }
    }

    private void settleBuy(Order order) {
        BigDecimal amount = tradeAmount(order);

        positionFor(order.getInstrumentId()).buy(order.getSize(), amount);
        cash = cash.subtract(amount);
    }

    private void settleSell(Order order) {
        positionFor(order.getInstrumentId()).sell(order.getSize());
        cash = cash.add(tradeAmount(order));
    }

    private PositionAccumulator positionFor(Integer instrumentId) {
        return positions.computeIfAbsent(instrumentId, ignored -> new PositionAccumulator());
    }

    private BigDecimal tradeAmount(Order order) {
        return order.getPrice().multiply(BigDecimal.valueOf(order.getSize()));
    }
}
