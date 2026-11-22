package org.cocos.instruments;

import java.util.Optional;

import org.cocos.instruments.application.InstrumentService;
import org.cocos.instruments.domain.entity.Instrument;
import org.cocos.instruments.domain.enums.InstrumentType;
import org.cocos.instruments.infrastructure.dto.request.InstrumentRequest;
import org.cocos.instruments.infrastructure.repository.InstrumentRepository;
import org.cocos.shared.domain.exception.BusinessException;
import org.cocos.shared.domain.exception.BusinessException.Code;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstrumentServiceTest {

    @Test
    void searchesTheRequestedPageSortedByTicker() {
        var repository = mock(InstrumentRepository.class);
        var request = new InstrumentRequest();
        request.setQ("macro");
        request.setPage(1);
        request.setSize(2);
        new InstrumentService(repository).search(request);

        verify(repository).findAll(ArgumentMatchers.<Specification<Instrument>>any(),
                argThat((Pageable pageable) -> pageable instanceof PageRequest
                        && pageable.getPageNumber() == 1 && pageable.getPageSize() == 2
                        && pageable.getOffset() == 2
                        && pageable.getSort().equals(Sort.by("ticker", "id"))));
    }

    @Test
    void rejectsAnInstrumentThatIsNotAStock() {
        var repository = mock(InstrumentRepository.class);
        var instrument = mock(Instrument.class);
        when(instrument.getType()).thenReturn(InstrumentType.MONEDA);
        when(repository.findById(66)).thenReturn(Optional.of(instrument));

        var service = new InstrumentService(repository);

        assertThatThrownBy(() -> service.requireTradableStock(66))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(Code.INSTRUMENT_NOT_TRADABLE);
    }

    @Test
    void rejectsAnUnknownInstrument() {
        var repository = mock(InstrumentRepository.class);
        when(repository.findById(9999)).thenReturn(Optional.empty());

        var service = new InstrumentService(repository);

        assertThatThrownBy(() -> service.requireTradableStock(9999))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(Code.INSTRUMENT_NOT_FOUND);
    }
}
