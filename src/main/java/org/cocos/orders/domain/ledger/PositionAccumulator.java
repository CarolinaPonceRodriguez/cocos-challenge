package org.cocos.orders.domain.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PositionAccumulator {

    private long quantity;

    private long reserved;

    private BigDecimal averageCost = BigDecimal.ZERO;

    private boolean consistent = true;

    void buy(int size, BigDecimal amount) {
        if (consistent) {
            averageCost = averageCostAfterBuying(size, amount);
        }

        quantity += size;
    }

    void sell(int size) {
        if (exceedsHolding(size)) {
            consistent = false;
        }

        quantity -= size;
    }

    void reserve(int size) {
        reserved += size;
    }

    PositionSnapshot toSnapshot() {
        return new PositionSnapshot(quantity, reserved, averageCost, consistent);
    }

    private boolean exceedsHolding(int size) {
        return size > quantity;
    }

    private BigDecimal averageCostAfterBuying(int size, BigDecimal amount) {
        BigDecimal heldCost = averageCost.multiply(BigDecimal.valueOf(quantity));
        BigDecimal quantityAfterBuying = BigDecimal.valueOf(quantity + size);

        return heldCost.add(amount).divide(quantityAfterBuying, PositionSnapshot.DIVISION_SCALE, RoundingMode.HALF_UP);
    }
}
