package org.cocos.orders.domain.ledger;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.cocos.orders.domain.entity.Order;

public record AccountSnapshot(BigDecimal cash, BigDecimal reservedCash,
                              Map<Integer, PositionSnapshot> positions, boolean hasIncompleteHistory) {

    public static AccountSnapshot replay(Iterable<Order> movements) {
        var state = new AccountSnapshotState();
        movements.forEach(state::apply);

        return state.toSnapshot();
    }

    public Map<Integer, PositionSnapshot> visiblePositions() {
        Map<Integer, PositionSnapshot> visible = new LinkedHashMap<>();

        positions.forEach((instrumentId, position) -> {
            if (position.visible()) {
                visible.put(instrumentId, position);
            }
        });

        return visible;
    }

    public boolean isReliable() {
        return !hasIncompleteHistory && positions.values().stream().allMatch(PositionSnapshot::consistent);
    }

    public BigDecimal availableCash() {
        return cash.subtract(reservedCash);
    }

    public boolean hasInconsistentPosition(Integer instrumentId) {
        PositionSnapshot position = positions.get(instrumentId);

        return position != null && !position.consistent();
    }

    public boolean canAfford(BigDecimal price, int quantity) {
        return price.multiply(BigDecimal.valueOf(quantity)).compareTo(availableCash()) <= 0;
    }

    public boolean canDeliver(Integer instrumentId, int quantity) {
        PositionSnapshot position = positions.get(instrumentId);

        return position != null && quantity <= position.availableQuantity();
    }
}
