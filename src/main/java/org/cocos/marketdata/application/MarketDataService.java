package org.cocos.marketdata.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.cocos.marketdata.domain.entity.MarketData;
import org.cocos.marketdata.infrastructure.repository.MarketDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final MarketDataRepository repository;

    @Transactional(readOnly = true)
    public Optional<BigDecimal> findLatestPositiveClose(Integer instrumentId) {
        return repository.findFirstByInstrumentIdAndDateIsNotNullOrderByDateDescIdDesc(instrumentId)
                .map(MarketData::getClose)
                .filter(this::isPositive);
    }

    private boolean isPositive(BigDecimal close) {
        return close.signum() > 0;
    }

    @Transactional(readOnly = true)
    public Map<Integer, MarketData> findLatestForPortfolioValuation(Collection<Integer> instrumentIds) {
        if (instrumentIds.isEmpty()) {
            return Map.of();
        }

        return repository.findLatestByInstrumentIdIn(instrumentIds).stream()
                .collect(Collectors.toMap(MarketData::getInstrumentId, Function.identity()));
    }
}
