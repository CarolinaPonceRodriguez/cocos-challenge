package org.cocos.instruments.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.cocos.instruments.domain.enums.InstrumentType;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "instruments")
public class Instrument {

    @Id
    private Integer id;

    private String ticker;

    private String name;

    @Enumerated(EnumType.STRING)
    private InstrumentType type;
}
