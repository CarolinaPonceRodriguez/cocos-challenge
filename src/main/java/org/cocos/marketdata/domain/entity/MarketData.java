package org.cocos.marketdata.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "marketdata")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketData {

    @Id
    private Integer id;

    @Column(name = "instrumentid")
    private Integer instrumentId;

    private BigDecimal close;

    @Column(name = "previousclose")
    private BigDecimal previousClose;

    private LocalDate date;
}
