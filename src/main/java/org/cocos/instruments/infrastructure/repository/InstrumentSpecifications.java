package org.cocos.instruments.infrastructure.repository;

import java.util.Locale;

import org.cocos.instruments.domain.entity.Instrument;
import org.cocos.instruments.domain.enums.InstrumentType;
import org.springframework.data.jpa.domain.Specification;

public final class InstrumentSpecifications {

    public static Specification<Instrument> isStock() {
        return (root, query, builder) -> builder.equal(root.get("type"), InstrumentType.ACCIONES);
    }

    public static Specification<Instrument> matchesTickerOrName(String text) {
        String literal = text.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        String pattern = "%" + literal + "%";
        return (root, query, builder) -> builder.or(
                builder.like(builder.lower(root.get("ticker")), pattern, '!'),
                builder.like(builder.lower(root.get("name")), pattern, '!'));
    }
}
