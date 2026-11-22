package org.cocos.portfolio.infrastructure.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(BigDecimal availableCash, BigDecimal totalAccountValue,
                                List<PositionResponse> positions) {

    public record PositionResponse(Integer instrumentId, String ticker, long quantity, BigDecimal marketValue,
                                   BigDecimal totalReturnPercentage, BigDecimal dailyReturnPercentage) {
    }
}
