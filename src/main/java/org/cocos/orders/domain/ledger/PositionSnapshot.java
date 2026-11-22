package org.cocos.orders.domain.ledger;

import java.math.BigDecimal;

public record PositionSnapshot(long quantity, long reserved, BigDecimal averageCost, boolean consistent) {

    public long availableQuantity() {
        return quantity - reserved;
    }

    public boolean visible() {
        return quantity != 0;
    }
}
