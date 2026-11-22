package org.cocos.instruments;

import java.util.List;

import org.cocos.instruments.application.InstrumentService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.cocos.instruments.domain.entity.Instrument;
import org.cocos.instruments.domain.enums.InstrumentType;
import org.cocos.instruments.infrastructure.controller.InstrumentController;
import org.cocos.instruments.infrastructure.dto.request.InstrumentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InstrumentController.class)
class InstrumentRequestValidationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InstrumentService service;

    @Test
    void appliesDefaultsBeforeCallingTheService() throws Exception {
        var instrument = mock(Instrument.class);
        when(instrument.getId()).thenReturn(31);
        when(instrument.getTicker()).thenReturn("BMA");
        when(instrument.getName()).thenReturn("Banco Macro S.A.");
        when(instrument.getType()).thenReturn(InstrumentType.ACCIONES);
        when(service.search(any(InstrumentRequest.class)))
                .thenReturn(new PageImpl<>(List.of(instrument), PageRequest.of(0, 20), 1));
        mvc.perform(get("/api/v1/instruments").param("q", "macro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(31))
                .andExpect(jsonPath("$.content[0].ticker").value("BMA"))
                .andExpect(jsonPath("$.content[0].name").value("Banco Macro S.A."))
                .andExpect(jsonPath("$.content[0].type").value("ACCIONES"))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0));
        verify(service).search(argThat(request -> request.getQ().equals("macro")
                && request.getPage() == 0 && request.getSize() == 20));
    }

    @Test
    void passesTheRequestedPage() throws Exception {
        String query = "macro";
        when(service.search(any(InstrumentRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 2), 3));
        mvc.perform(get("/api/v1/instruments").param("q", query)
                        .param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
        verify(service).search(argThat(request -> request.getQ().equals(query)
                && request.getPage() == 1 && request.getSize() == 2));
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "size,101", "page,abc"})
    void rejectsInvalidPagingBeforeCallingTheService(String field, String value) throws Exception {
        mvc.perform(get("/api/v1/instruments").param("q", "macro").param(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankQueryBeforeCallingTheService(String query) throws Exception {
        mvc.perform(get("/api/v1/instruments").param("q", query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("q"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingQuery() throws Exception {
        mvc.perform(get("/api/v1/instruments"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.errors[0].message").value("Search text is required."));
        verifyNoInteractions(service);
    }

}
