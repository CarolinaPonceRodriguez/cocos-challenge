package org.cocos.marketdata;

import java.math.BigDecimal;
import java.util.Optional;
import org.cocos.marketdata.application.MarketDataService;
import org.cocos.marketdata.domain.entity.MarketData;
import org.cocos.marketdata.infrastructure.repository.MarketDataRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketDataServiceTest {

    @Test
    void returnsTheLatestPositiveClose() {
        MarketDataRepository repository = mock(MarketDataRepository.class);
        MarketData quote = mock(MarketData.class);
        when(quote.getClose()).thenReturn(new BigDecimal("925.85"));
        when(repository.findFirstByInstrumentIdAndDateIsNotNullOrderByDateDescIdDesc(47))
                .thenReturn(Optional.of(quote));

        Optional<BigDecimal> price = new MarketDataService(repository).findLatestPositiveClose(47);

        assertThat(price).contains(new BigDecimal("925.85"));
    }

    @Test
    void returnsEmptyWhenTheLatestMarketPriceIsMissingOrNotPositive() {
        MarketDataRepository repository = mock(MarketDataRepository.class);
        when(repository.findFirstByInstrumentIdAndDateIsNotNullOrderByDateDescIdDesc(47))
                .thenReturn(Optional.empty());

        MarketDataService service = new MarketDataService(repository);

        assertThat(service.findLatestPositiveClose(47)).isEmpty();
    }
}
