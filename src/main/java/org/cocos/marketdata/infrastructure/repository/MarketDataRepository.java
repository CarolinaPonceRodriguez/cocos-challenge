package org.cocos.marketdata.infrastructure.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.cocos.marketdata.domain.entity.MarketData;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface MarketDataRepository extends Repository<MarketData, Integer> {

    Optional<MarketData> findFirstByInstrumentIdAndDateIsNotNullOrderByDateDescIdDesc(Integer instrumentId);

    @Query("""
            SELECT marketData
            FROM MarketData marketData
            WHERE marketData.instrumentId IN :instrumentIds
              AND marketData.date IS NOT NULL
              AND marketData.date = (
                  SELECT MAX(candidate.date)
                  FROM MarketData candidate
                  WHERE candidate.instrumentId = marketData.instrumentId
                    AND candidate.date IS NOT NULL
              )
              AND marketData.id = (
                  SELECT MAX(candidate.id)
                  FROM MarketData candidate
                  WHERE candidate.instrumentId = marketData.instrumentId
                    AND candidate.date = marketData.date
              )
            """)
    List<MarketData> findLatestByInstrumentIdIn(@Param("instrumentIds") Collection<Integer> instrumentIds);
}
