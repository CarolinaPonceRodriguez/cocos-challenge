package org.cocos.instruments.infrastructure.dto.response;

import org.cocos.instruments.domain.entity.Instrument;
import org.cocos.instruments.domain.enums.InstrumentType;
import org.springframework.data.domain.Page;

public record InstrumentResponse(Integer id, String ticker, String name, InstrumentType type) {

    public static InstrumentResponse from(Instrument instrument) {
        return new InstrumentResponse(instrument.getId(), instrument.getTicker(),
                instrument.getName(), instrument.getType());
    }

    public static Page<InstrumentResponse> from(Page<Instrument> instruments) {
        return instruments.map(InstrumentResponse::from);
    }
}
