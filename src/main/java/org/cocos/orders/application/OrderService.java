package org.cocos.orders.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cocos.instruments.application.InstrumentService;
import org.cocos.marketdata.application.MarketDataService;
import org.cocos.orders.domain.ledger.AccountSnapshot;
import org.cocos.orders.domain.entity.Order;
import org.cocos.orders.domain.enums.OrderStatus;
import org.cocos.orders.domain.enums.OrderType;
import org.cocos.orders.domain.enums.TradeSide;
import org.cocos.orders.domain.exception.OrderNotFoundException;
import org.cocos.orders.infrastructure.dto.request.CreateOrderRequest;
import org.cocos.orders.infrastructure.repository.OrderRepository;
import org.cocos.shared.domain.exception.BusinessException;
import org.cocos.shared.domain.exception.BusinessException.Code;
import org.cocos.user.application.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final BigDecimal MAX_QUANTITY = BigDecimal.valueOf(Integer.MAX_VALUE);

    private final UserService users;

    private final InstrumentService instruments;

    private final MarketDataService marketData;

    private final OrderRepository orders;

    private final AccountSnapshotService accountSnapshots;

    private final Clock clock;

    @Transactional(readOnly = true)
    public Order get(Integer userId, Integer orderId) {
        users.ensureExists(userId);

        return findOrder(userId, orderId);
    }

    @Transactional
    public Order create(Integer userId, CreateOrderRequest request) {
        instruments.requireTradableStock(request.getInstrumentId());
        Optional<BigDecimal> latestMarketClose = findLatestMarketClose(request);

        users.lockForOrderChanges(userId);

        var snapshot = accountSnapshots.snapshot(userId);
        ensurePositionIsConsistent(userId, request, snapshot);

        BigDecimal price = resolvePrice(request, latestMarketClose);
        int quantity = resolveQuantity(request, price);
        var status = resolveStatus(request, price, quantity, snapshot);

        var order = orders.save(new Order(
                userId,
                request.getInstrumentId(),
                quantity,
                price,
                request.getSide().toOrderSide(),
                request.getType(),
                status,
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)));

        log.info("Order {} created for user {}: {} {} on instrument {} is {}.", order.getId(), userId,
                order.getType(), order.getSide(), order.getInstrumentId(), order.getStatus());
        return order;
    }

    private Optional<BigDecimal> findLatestMarketClose(CreateOrderRequest request) {
        return request.getType() == OrderType.MARKET
                ? marketData.findLatestPositiveClose(request.getInstrumentId())
                : Optional.empty();
    }

    private BigDecimal resolvePrice(CreateOrderRequest request, Optional<BigDecimal> latestMarketClose) {
        return switch (request.getType()) {
            case LIMIT -> request.getPrice().setScale(2);
            case MARKET -> latestMarketClose.orElseThrow(() -> new BusinessException(Code.MARKET_PRICE_UNAVAILABLE,
                    "A market price is unavailable for instrument %d.".formatted(request.getInstrumentId())));
        };
    }

    private int resolveQuantity(CreateOrderRequest request, BigDecimal price) {
        if (request.getQuantity() != null) {
            return request.getQuantity();
        }

        BigDecimal shares = request.getAmount().divideToIntegralValue(price);

        if (shares.signum() == 0) {
            throw new BusinessException(Code.AMOUNT_TOO_SMALL,
                    "The amount is too small to trade one share of instrument %d."
                            .formatted(request.getInstrumentId()));
        }
        if (shares.compareTo(MAX_QUANTITY) > 0) {
            throw new BusinessException(Code.SIZE_OUT_OF_RANGE,
                    "The calculated share quantity exceeds the supported range.");
        }

        return shares.intValueExact();
    }

    private void ensurePositionIsConsistent(Integer userId, CreateOrderRequest request, AccountSnapshot snapshot) {
        if (snapshot.hasInconsistentPosition(request.getInstrumentId())) {
            log.warn("Order request rejected for user {}: inconsistent position in instrument {}.", userId,
                    request.getInstrumentId());
            throw new BusinessException(Code.INCONSISTENT_POSITION,
                    "User %d has an inconsistent position in instrument %d."
                            .formatted(userId, request.getInstrumentId()));
        }
    }

    private OrderStatus resolveStatus(CreateOrderRequest request, BigDecimal price, int quantity,
                                      AccountSnapshot snapshot) {
        return hasSufficientResources(request, price, quantity, snapshot)
                ? acceptedStatus(request)
                : OrderStatus.REJECTED;
    }

    private OrderStatus acceptedStatus(CreateOrderRequest request) {
        return switch (request.getType()) {
            case MARKET -> OrderStatus.FILLED;
            case LIMIT -> OrderStatus.NEW;
        };
    }

    private boolean hasSufficientResources(CreateOrderRequest request, BigDecimal price, int quantity,
                                           AccountSnapshot snapshot) {
        return request.getSide() == TradeSide.BUY
                ? snapshot.canAfford(price, quantity)
                : snapshot.canDeliver(request.getInstrumentId(), quantity);
    }

    @Transactional
    public Order cancel(Integer userId, Integer orderId) {
        users.lockForOrderChanges(userId);

        var order = findOrder(userId, orderId);

        ensureCancellable(order);
        order.markCancelled();
        log.info("Order {} cancelled for user {}.", orderId, userId);
        return order;
    }

    private void ensureCancellable(Order order) {
        if (order.hasFinalStatus()) {
            throw new BusinessException(Code.ORDER_NOT_CANCELLABLE,
                    "Only NEW orders can be cancelled. Order %d has status %s."
                            .formatted(order.getId(), order.getStatus()));
        }
    }

    private Order findOrder(Integer userId, Integer orderId) {
        return orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(userId, orderId));
    }
}
