package org.cocos.instruments.application;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.cocos.instruments.domain.entity.Instrument;
import org.cocos.instruments.domain.enums.InstrumentType;
import org.cocos.instruments.infrastructure.dto.request.InstrumentRequest;
import org.cocos.instruments.infrastructure.repository.InstrumentRepository;
import org.cocos.instruments.infrastructure.repository.InstrumentSpecifications;
import org.cocos.shared.domain.exception.BusinessException;
import org.cocos.shared.domain.exception.BusinessException.Code;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstrumentService {

    private final InstrumentRepository repository;

    @Transactional(readOnly = true)
    public Page<Instrument> search(InstrumentRequest request) {
        Specification<Instrument> specification = InstrumentSpecifications.isStock()
                .and(InstrumentSpecifications.matchesTickerOrName(request.getQ()));
        return repository.findAll(specification,
                PageRequest.of(request.getPage(), request.getSize(), Sort.by("ticker", "id")));
    }

    @Transactional(readOnly = true)
    public void ensureTradableStock(Integer instrumentId) {
        Instrument instrument = repository.findById(instrumentId)
                .orElseThrow(() -> new BusinessException(Code.INSTRUMENT_NOT_FOUND,
                        "Instrument %d was not found.".formatted(instrumentId)));

        if (instrument.getType() != InstrumentType.ACCIONES) {
            throw new BusinessException(Code.INSTRUMENT_NOT_TRADABLE,
                    "Instrument %d is not tradable.".formatted(instrumentId));
        }
    }

    @Transactional(readOnly = true)
    public Map<Integer, Instrument> findForPortfolioValuation(Collection<Integer> instrumentIds) {
        if (instrumentIds.isEmpty()) {
            return Map.of();
        }

        return repository.findByIdIn(instrumentIds).stream()
                .collect(Collectors.toMap(Instrument::getId, Function.identity()));
    }
}
