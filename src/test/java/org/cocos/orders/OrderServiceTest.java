package org.cocos.orders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.cocos.instruments.application.InstrumentService;
import org.cocos.marketdata.application.MarketDataService;
import org.cocos.orders.application.AccountSnapshotService;
import org.cocos.orders.application.OrderService;
import org.cocos.orders.domain.entity.Order;
import org.cocos.orders.domain.enums.OrderSide;
import org.cocos.orders.domain.enums.OrderStatus;
import org.cocos.orders.domain.enums.OrderType;
import org.cocos.orders.domain.enums.TradeSide;
import org.cocos.orders.infrastructure.dto.request.CreateOrderRequest;
import org.cocos.orders.infrastructure.repository.OrderRepository;
import org.cocos.orders.domain.ledger.PositionSnapshot;
import org.cocos.orders.domain.ledger.AccountSnapshot;
import org.cocos.shared.domain.exception.BusinessException;
import org.cocos.shared.domain.exception.BusinessException.Code;
import org.cocos.user.application.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private final UserService users = mock(UserService.class);

    private final InstrumentService instruments = mock(InstrumentService.class);

    private final MarketDataService marketData = mock(MarketDataService.class);

    private final OrderRepository orders = mock(OrderRepository.class);

    private final AccountSnapshotService accountSnapshots = mock(AccountSnapshotService.class);

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T15:00:00.123456789Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));

    private final OrderService service = new OrderService(users, instruments, marketData, orders, accountSnapshots, clock);

    private static AccountSnapshot snapshot(BigDecimal cash, Map<Integer, PositionSnapshot> positions) {
        return new AccountSnapshot(cash, BigDecimal.ZERO, positions, false);
    }

    @BeforeEach
    void setup() {
        when(marketData.findLatestPositiveClose(47)).thenReturn(Optional.of(new BigDecimal("100.00")));
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void doesNotLockOrReadTheAccountWhenTheInstrumentIsNotTradable() {
        doThrow(new BusinessException(Code.INSTRUMENT_NOT_TRADABLE, "Instrument 47 is not tradable."))
                .when(instruments).requireTradableStock(47);

        assertThatThrownBy(() -> service.create(1, createRequest(TradeSide.BUY, OrderType.MARKET, 1, null)))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(users, accountSnapshots, orders);
    }

    @Test
    void marketAmountUsesWholeSharesAndAvailableCash() {
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(new BigDecimal("1000"), Map.of()));
        var order = service.create(1, createRequest(TradeSide.BUY, OrderType.MARKET, null, new BigDecimal("250.00")));
        assertThat(order.getSize()).isEqualTo(2);
        assertThat(order.getPrice()).isEqualByComparingTo("100.00");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void timestampsTheOrderInTheConfiguredTimeZone() {
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(new BigDecimal("1000"), Map.of()));
        var order = service.create(1, createRequest(TradeSide.BUY, OrderType.MARKET, 1, null));
        assertThat(order.getDatetime()).isEqualTo(LocalDateTime.of(2026, 11, 22, 12, 0));
    }

    @Test
    void storesTheLimitPriceWithTwoDecimals() {
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(new BigDecimal("1000"), Map.of()));
        var request = createRequest(TradeSide.BUY, OrderType.LIMIT, 1, null);
        request.setPrice(new BigDecimal("900"));
        assertThat(service.create(1, request).getPrice()).isEqualTo(new BigDecimal("900.00"));
    }

    @Test
    void marketOrderIgnoresTheSubmittedPriceAndUsesTheLatestClose() {
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(new BigDecimal("1000"), Map.of()));
        var request = createRequest(TradeSide.BUY, OrderType.MARKET, 3, null);
        request.setPrice(new BigDecimal("1.00"));

        var order = service.create(1, request);

        assertThat(order.getPrice()).isEqualByComparingTo("100.00");
        assertThat(order.getSize()).isEqualTo(3);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void marketOrderIsRejectedForLackOfCashAtTheMarketPriceNotTheSubmittedOne() {
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(new BigDecimal("150"), Map.of()));
        var request = createRequest(TradeSide.BUY, OrderType.MARKET, 3, null);
        request.setPrice(new BigDecimal("1.00"));

        var order = service.create(1, request);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void insufficientFundsPersistRejectedOrder() {
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(new BigDecimal("50"), Map.of()));
        var order = service.create(1, createRequest(TradeSide.BUY, OrderType.MARKET, 1, null));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        verify(orders).save(order);
    }

    @Test
    void inconsistentPositionBlocksEitherSideBeforePersisting() {
        var position = new PositionSnapshot(0, 0, BigDecimal.ZERO, false);
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(new BigDecimal("1000"), Map.of(47, position)));
        assertThatThrownBy(() -> service.create(1, createRequest(TradeSide.BUY, OrderType.MARKET, 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(Code.INCONSISTENT_POSITION);
        assertThatThrownBy(() -> service.create(1, createRequest(TradeSide.SELL, OrderType.MARKET, 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(Code.INCONSISTENT_POSITION);
        verify(orders, never()).save(any());
    }

    @Test
    void readsExternalDataBeforeLockingAndPreservesInconsistencyPrecedence() {
        var position = new PositionSnapshot(0, 0, BigDecimal.ZERO, false);
        when(marketData.findLatestPositiveClose(47)).thenReturn(Optional.empty());
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(new BigDecimal("1000"), Map.of(47, position)));

        assertThatThrownBy(() -> service.create(1, createRequest(TradeSide.BUY, OrderType.MARKET, 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(Code.INCONSISTENT_POSITION);

        var order = inOrder(instruments, marketData, users, accountSnapshots);
        order.verify(instruments).requireTradableStock(47);
        order.verify(marketData).findLatestPositiveClose(47);
        order.verify(users).lockForOrderChanges(1);
        order.verify(accountSnapshots).snapshot(1);
    }

    @Test
    void rejectsMarketOrderWhenTheQuoteIsUnavailableAfterReadingTheAccountSnapshot() {
        when(marketData.findLatestPositiveClose(47)).thenReturn(Optional.empty());
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(new BigDecimal("1000"), Map.of()));

        assertThatThrownBy(() -> service.create(1, createRequest(TradeSide.BUY, OrderType.MARKET, 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(Code.MARKET_PRICE_UNAVAILABLE);

        var order = inOrder(users, accountSnapshots);
        order.verify(users).lockForOrderChanges(1);
        order.verify(accountSnapshots).snapshot(1);
        verify(orders, never()).save(any());
    }

    @Test
    void rejectsAnAmountTooSmallForOneShare() {
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(new BigDecimal("1000"), Map.of()));

        assertThatThrownBy(() -> service.create(1,
                createRequest(TradeSide.BUY, OrderType.MARKET, null, new BigDecimal("99.99"))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(Code.AMOUNT_TOO_SMALL);
        verify(orders, never()).save(any());
    }

    @Test
    void rejectsAnAmountBeyondTheSupportedQuantity() {
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(new BigDecimal("1000"), Map.of()));

        assertThatThrownBy(() -> service.create(1,
                createRequest(TradeSide.BUY, OrderType.MARKET, null, new BigDecimal("999999999999.99"))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(Code.SIZE_OUT_OF_RANGE);
        verify(orders, never()).save(any());
    }

    @Test
    void sellsHeldSharesByQuantityOrAmount() {
        var position = new PositionSnapshot(10, 0, new BigDecimal("90"), true);
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(BigDecimal.ZERO, Map.of(47, position)));

        var byQuantity = service.create(1, createRequest(TradeSide.SELL, OrderType.MARKET, 4, null));
        var byAmount = service.create(1, createRequest(TradeSide.SELL, OrderType.MARKET, null, new BigDecimal("350")));

        assertThat(byQuantity.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(byQuantity.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(byAmount.getSize()).isEqualTo(3);
        assertThat(byAmount.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void fillsABuyThatSpendsExactlyTheAvailableCash() {
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(new BigDecimal("300.00"), Map.of()));

        var order = service.create(1, createRequest(TradeSide.BUY, OrderType.MARKET, 3, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void fillsASaleOfExactlyTheAvailableShares() {
        var position = new PositionSnapshot(10, 4, new BigDecimal("90"), true);
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(BigDecimal.ZERO, Map.of(47, position)));

        var order = service.create(1, createRequest(TradeSide.SELL, OrderType.MARKET, 6, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void rejectsASaleOfSharesReservedByPendingOrders() {
        var position = new PositionSnapshot(10, 8, new BigDecimal("90"), true);
        when(accountSnapshots.snapshot(1)).thenReturn(snapshot(BigDecimal.ZERO, Map.of(47, position)));

        var order = service.create(1, createRequest(TradeSide.SELL, OrderType.MARKET, 3, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void cancelsOnlyNewOrders() {
        var pending = new Order(1, 47, 1, new BigDecimal("100"), OrderSide.BUY, OrderType.LIMIT, OrderStatus.NEW,
                LocalDateTime.now(clock));
        when(orders.findByIdAndUserId(10, 1)).thenReturn(Optional.of(pending));
        assertThat(service.cancel(1, 10).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.cancel(1, 10)).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(Code.ORDER_NOT_CANCELLABLE);
    }

    private CreateOrderRequest createRequest(TradeSide side, OrderType type, Integer quantity, BigDecimal amount) {
        var request = new CreateOrderRequest();
        request.setInstrumentId(47);
        request.setSide(side);
        request.setType(type);
        request.setQuantity(quantity);
        request.setAmount(amount);
        return request;
    }
}
