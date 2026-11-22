package org.cocos.portfolio.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.cocos.instruments.application.InstrumentService;
import org.cocos.instruments.domain.entity.Instrument;
import org.cocos.marketdata.application.MarketDataService;
import org.cocos.marketdata.domain.entity.MarketData;
import org.cocos.orders.application.AccountSnapshotService;
import org.cocos.orders.domain.entity.Order;
import org.cocos.orders.domain.enums.OrderSide;
import org.cocos.orders.domain.enums.OrderStatus;
import org.cocos.orders.domain.ledger.AccountSnapshot;
import org.cocos.orders.domain.ledger.PositionSnapshot;
import org.cocos.orders.infrastructure.repository.OrderRepository;
import org.cocos.portfolio.infrastructure.dto.response.PortfolioResponse;
import org.cocos.user.application.UserService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);

    private final InstrumentService instruments = mock(InstrumentService.class);

    private final MarketDataService quotes = mock(MarketDataService.class);

    private final UserService users = mock(UserService.class);

    private final AccountSnapshotService accountSnapshots = new AccountSnapshotService(orders);

    private final PortfolioService service = new PortfolioService(
            accountSnapshots,
            new PortfolioValuator(),
            instruments,
            quotes,
            users);

    @Test
    void calculatesCashReservationsAndWeightedReturn() {
        List<Order> movements = List.of(
                movement(66, OrderSide.CASH_IN, OrderStatus.FILLED, 2000, "1"),
                movement(1, OrderSide.BUY, OrderStatus.FILLED, 2, "100"),
                movement(1, OrderSide.SELL, OrderStatus.FILLED, 1, "120"),
                movement(1, OrderSide.BUY, OrderStatus.NEW, 1, "50"),
                movement(1, OrderSide.SELL, OrderStatus.NEW, 1, "150"));
        when(orders.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(eq(1), any())).thenReturn(movements);
        Instrument instrument = mock(Instrument.class);
        when(instrument.getId()).thenReturn(1);
        when(instrument.getTicker()).thenReturn("TEST");
        when(instrument.getName()).thenReturn("Test");
        when(instruments.findForPortfolioValuation(any())).thenReturn(Map.of(1, instrument));
        MarketData quote = mock(MarketData.class);
        when(quote.getInstrumentId()).thenReturn(1);
        when(quote.getClose()).thenReturn(new BigDecimal("150"));
        when(quote.getPreviousClose()).thenReturn(new BigDecimal("120"));
        when(quotes.findLatestForPortfolioValuation(any())).thenReturn(Map.of(1, quote));

        PortfolioResponse result = service.get(1);
        assertThat(result.availableCash()).isEqualByComparingTo("1870");
        assertThat(result.totalAccountValue()).isEqualByComparingTo("2070");
        assertThat(result.positions()).singleElement().satisfies(position -> {
            assertThat(position.quantity()).isEqualTo(1);
            assertThat(position.totalReturnPercentage()).isEqualByComparingTo("50");
            assertThat(position.dailyReturnPercentage()).isEqualByComparingTo("25");
        });
    }

    @Test
    void countsOnlySettledDepositsAndReservesPendingWithdrawals() {
        List<Order> movements = List.of(
                movement(66, OrderSide.CASH_IN, OrderStatus.FILLED, 1000, "1"),
                movement(66, OrderSide.CASH_IN, OrderStatus.NEW, 500, "1"),
                movement(66, OrderSide.CASH_OUT, OrderStatus.NEW, 200, "1"));
        when(orders.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(eq(3), any())).thenReturn(movements);

        PortfolioResponse result = service.get(3);

        assertThat(result.availableCash()).isEqualByComparingTo("800");
        assertThat(result.totalAccountValue()).isEqualByComparingTo("1000");
    }

    @Test
    void roundsPercentagesHalfUpAndMoneyToTwoDecimals() {
        List<Order> movements = List.of(
                movement(66, OrderSide.CASH_IN, OrderStatus.FILLED, 800, "1"),
                movement(1, OrderSide.BUY, OrderStatus.FILLED, 1, "800"));
        when(orders.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(eq(1), any())).thenReturn(movements);
        Instrument instrument = mock(Instrument.class);
        when(instrument.getTicker()).thenReturn("TEST");
        when(instruments.findForPortfolioValuation(any())).thenReturn(Map.of(1, instrument));
        MarketData quote = mock(MarketData.class);
        when(quote.getClose()).thenReturn(new BigDecimal("801"));
        when(quote.getPreviousClose()).thenReturn(new BigDecimal("800"));
        when(quotes.findLatestForPortfolioValuation(any())).thenReturn(Map.of(1, quote));

        PortfolioResponse result = service.get(1);

        assertThat(result.availableCash()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.totalAccountValue()).isEqualTo(new BigDecimal("801.00"));
        assertThat(result.positions()).singleElement().satisfies(position -> {
            assertThat(position.marketValue()).isEqualTo(new BigDecimal("801.00"));
            assertThat(position.totalReturnPercentage()).isEqualTo(new BigDecimal("0.13"));
            assertThat(position.dailyReturnPercentage()).isEqualTo(new BigDecimal("0.13"));
        });
    }

    @Test
    void detectsHistoricalOversellingForAnyUserAndInstrument() {
        List<Order> movements = List.of(
                movement(99, OrderSide.BUY, OrderStatus.FILLED, 2, "100"),
                movement(99, OrderSide.SELL, OrderStatus.FILLED, 3, "120"));
        when(orders.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(eq(7), any())).thenReturn(movements);
        PositionSnapshot position = accountSnapshots.snapshot(7).positions().get(99);
        assertThat(position.quantity()).isEqualTo(-1);
        assertThat(position.consistent()).isFalse();
    }

    @Test
    void returnsANullTotalForAnInconsistentPosition() {
        List<Order> movements = List.of(movement(99, OrderSide.BUY, OrderStatus.FILLED, 2, "100"),
                movement(99, OrderSide.SELL, OrderStatus.FILLED, 3, "120"));
        when(orders.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(eq(7), any())).thenReturn(movements);
        Instrument instrument = mock(Instrument.class);
        when(instrument.getId()).thenReturn(99);
        when(instrument.getTicker()).thenReturn("OTHER");
        when(instruments.findForPortfolioValuation(any())).thenReturn(Map.of(99, instrument));
        MarketData quote = mock(MarketData.class);
        when(quote.getInstrumentId()).thenReturn(99);
        when(quote.getClose()).thenReturn(new BigDecimal("150"));
        when(quote.getPreviousClose()).thenReturn(new BigDecimal("120"));
        when(quotes.findLatestForPortfolioValuation(any())).thenReturn(Map.of(99, quote));

        PortfolioResponse result = service.get(7);
        assertThat(result.totalAccountValue()).isNull();
        assertThat(result.positions()).singleElement().satisfies(position -> {
            assertThat(position.quantity()).isEqualTo(-1);
            assertThat(position.totalReturnPercentage()).isNull();
            assertThat(position.dailyReturnPercentage()).isEqualByComparingTo("25");
        });
    }

    @Test
    void returnsANullDailyReturnWhenThePreviousPriceIsUnavailable() {
        List<Order> movements = List.of(
                movement(1, OrderSide.BUY, OrderStatus.FILLED, Integer.MAX_VALUE, "1"),
                movement(1, OrderSide.BUY, OrderStatus.FILLED, 1, "1"));
        when(orders.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(eq(1), any())).thenReturn(movements);
        Instrument instrument = mock(Instrument.class);
        when(instrument.getId()).thenReturn(1);
        when(instrument.getTicker()).thenReturn("TEST");
        when(instrument.getName()).thenReturn("Test");
        when(instruments.findForPortfolioValuation(any())).thenReturn(Map.of(1, instrument));
        MarketData quote = mock(MarketData.class);
        when(quote.getInstrumentId()).thenReturn(1);
        when(quote.getClose()).thenReturn(BigDecimal.ONE);
        when(quote.getPreviousClose()).thenReturn(BigDecimal.ZERO);
        when(quotes.findLatestForPortfolioValuation(any())).thenReturn(Map.of(1, quote));

        PortfolioResponse result = service.get(1);

        assertThat(result.totalAccountValue()).isNotNull();
        assertThat(result.positions()).singleElement().satisfies(position -> {
            assertThat(position.quantity()).isEqualTo((long) Integer.MAX_VALUE + 1);
            assertThat(position.dailyReturnPercentage()).isNull();
        });
    }

    @Test
    void marksTheSnapshotAsIncompleteForAnIncompleteHistoricalTrade() {
        Order movement = movement(1, OrderSide.BUY, OrderStatus.FILLED, 1, null);
        when(movement.getId()).thenReturn(7);
        when(orders.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(eq(1), any())).thenReturn(List.of(movement));

        AccountSnapshot snapshot = accountSnapshots.snapshot(1);

        assertThat(snapshot.hasIncompleteHistory()).isTrue();
        assertThat(snapshot.positions()).isEmpty();
    }

    @Test
    void sortsPositionsByTicker() {
        List<Order> movements = List.of(
                movement(2, OrderSide.BUY, OrderStatus.FILLED, 1, "10"),
                movement(1, OrderSide.BUY, OrderStatus.FILLED, 1, "10"));
        when(orders.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(eq(1), any())).thenReturn(movements);
        Instrument zeta = mock(Instrument.class);
        when(zeta.getTicker()).thenReturn("ZETA");
        Instrument alpha = mock(Instrument.class);
        when(alpha.getTicker()).thenReturn("ALPHA");
        when(instruments.findForPortfolioValuation(any())).thenReturn(Map.of(2, zeta, 1, alpha));
        when(quotes.findLatestForPortfolioValuation(any())).thenReturn(Map.of());

        PortfolioResponse result = service.get(1);

        assertThat(result.positions()).extracting(position -> position.ticker()).containsExactly("ALPHA", "ZETA");
    }

    private static Order movement(int instrument, OrderSide side, OrderStatus status, int size, String price) {
        Order order = mock(Order.class, CALLS_REAL_METHODS);
        when(order.getInstrumentId()).thenReturn(instrument);
        when(order.getSide()).thenReturn(side);
        when(order.getStatus()).thenReturn(status);
        when(order.getSize()).thenReturn(size);
        when(order.getPrice()).thenReturn(price == null ? null : new BigDecimal(price));
        return order;
    }
}
