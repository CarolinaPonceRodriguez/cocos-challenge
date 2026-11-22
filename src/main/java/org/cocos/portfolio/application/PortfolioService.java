package org.cocos.portfolio.application;

import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.cocos.instruments.application.InstrumentService;
import org.cocos.instruments.domain.entity.Instrument;
import org.cocos.marketdata.application.MarketDataService;
import org.cocos.marketdata.domain.entity.MarketData;
import org.cocos.orders.application.AccountSnapshotService;
import org.cocos.orders.domain.ledger.AccountSnapshot;
import org.cocos.portfolio.infrastructure.dto.response.PortfolioResponse;
import org.cocos.user.application.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final AccountSnapshotService accountSnapshots;

    private final PortfolioValuator valuator;

    private final InstrumentService instruments;

    private final MarketDataService marketData;

    private final UserService users;

    @Transactional(readOnly = true)
    public PortfolioResponse get(Integer userId) {
        users.ensureExists(userId);

        AccountSnapshot snapshot = accountSnapshots.snapshot(userId);
        Set<Integer> positionInstrumentIds = snapshot.visiblePositions().keySet();

        Map<Integer, Instrument> instrumentsById = instruments.findForPortfolioValuation(positionInstrumentIds);
        Map<Integer, MarketData> quotesByInstrument = marketData.findLatestForPortfolioValuation(positionInstrumentIds);

        return valuator.evaluate(snapshot, instrumentsById, quotesByInstrument);
    }
}
