package org.cocos.orders.infrastructure.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.cocos.orders.domain.entity.Order;
import org.cocos.orders.domain.enums.OrderSide;
import org.cocos.orders.domain.enums.OrderStatus;
import org.cocos.orders.domain.enums.OrderType;

public record OrderResponse(Integer id, Integer userId, Integer instrumentId, OrderSide side, OrderType type,
                            Integer quantity, BigDecimal price, OrderStatus status, LocalDateTime datetime) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getUserId(), order.getInstrumentId(), order.getSide(),
                order.getType(), order.getSize(), order.getPrice(), order.getStatus(), order.getDatetime());
    }
}
