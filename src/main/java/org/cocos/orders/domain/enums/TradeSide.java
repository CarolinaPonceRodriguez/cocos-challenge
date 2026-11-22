package org.cocos.orders.domain.enums;

public enum TradeSide {
    BUY,
    SELL;

    public OrderSide toOrderSide() {
        return OrderSide.valueOf(name());
    }
}
