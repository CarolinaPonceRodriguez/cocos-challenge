package org.cocos.orders.domain.ledger;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
import org.cocos.orders.domain.entity.Order;

public record AccountSnapshot(BigDecimal cash, BigDecimal reservedCash,
                              Map<Integer, PositionSnapshot> positions, boolean hasIncompleteHistory) {

    public static AccountSnapshot replay(Iterable<Order> movements) {
        AccountSnapshotState state = new AccountSnapshotState();
        movements.forEach(state::apply);

        return state.toSnapshot();
    }

    public Map<Integer, PositionSnapshot> visiblePositions() {
        return positions.entrySet().stream()
                .filter(entry -> entry.getValue().visible())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
