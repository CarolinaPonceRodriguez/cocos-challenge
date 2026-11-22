package org.cocos.orders.infrastructure.dto.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.cocos.orders.domain.enums.TradeSide;
import org.cocos.orders.domain.enums.OrderType;

@Getter
@Setter
public class CreateOrderRequest {

    @NotNull(message = "instrumentId is required.")
    private Integer instrumentId;

    @NotNull(message = "side is required.")
    private TradeSide side;

    @NotNull(message = "type is required.")
    private OrderType type;

    @Positive(message = "quantity must be greater than 0.")
    private Integer quantity;

    @Positive(message = "amount must be greater than 0.")
    @Digits(integer = 12, fraction = 2, message = "amount must have at most 12 integer digits and 2 decimal places.")
    private BigDecimal amount;

    @Positive(message = "price must be greater than 0.")
    @Digits(integer = 8, fraction = 2, message = "price must have at most 8 integer digits and 2 decimal places.")
    private BigDecimal price;

    @AssertTrue(message = "Provide exactly one of quantity or amount.")
    public boolean hasQuantityOrAmount() {
        return (quantity == null) != (amount == null);
    }

    @AssertTrue(message = "A LIMIT order requires a price.")
    public boolean hasPrice() {
        return type != OrderType.LIMIT || price != null;
    }
}
