package org.cocos.orders.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.cocos.orders.domain.enums.OrderSide;
import org.cocos.orders.domain.enums.OrderStatus;
import org.cocos.orders.domain.enums.OrderType;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "instrumentid")
    private Integer instrumentId;

    @Column(name = "userid")
    private Integer userId;

    private Integer size;

    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime datetime;

    public Order(Integer userId, Integer instrumentId, int size, BigDecimal price, OrderSide side, OrderType type,
                 OrderStatus status, LocalDateTime datetime) {
        this.userId = userId;
        this.instrumentId = instrumentId;
        this.size = size;
        this.price = price;
        this.side = side;
        this.type = type;
        this.status = status;
        this.datetime = datetime;
    }

    public void markCancelled() {
        this.status = OrderStatus.CANCELLED;
    }

    public boolean hasPositiveSize() {
        return getSize() != null && getSize() > 0;
    }

    public boolean hasSide() {
        return getSide() != null;
    }

    public boolean referencesInstrument() {
        return getInstrumentId() != null;
    }

    public boolean hasPositivePrice() {
        return getPrice() != null && getPrice().signum() > 0;
    }

    public boolean hasPriceWhenRequired() {
        return isPendingSale() || hasPositivePrice();
    }

    public boolean isPendingSale() {
        return getSide() == OrderSide.SELL && getStatus() == OrderStatus.NEW;
    }

    public boolean hasFinalStatus() {
        return getStatus() != OrderStatus.NEW;
    }

    public boolean isSettled() {
        return getStatus() == OrderStatus.FILLED;
    }
}
