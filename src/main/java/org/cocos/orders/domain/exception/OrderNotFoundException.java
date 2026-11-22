package org.cocos.orders.domain.exception;

import org.cocos.shared.domain.exception.BusinessException;

public class OrderNotFoundException extends BusinessException {

    public OrderNotFoundException(int userId, int orderId) {
        super(Code.ORDER_NOT_FOUND, "Order %d was not found for user %d.".formatted(orderId, userId));
    }
}
