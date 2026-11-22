package org.cocos.instruments.infrastructure.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cocos.instruments.application.InstrumentService;
import org.cocos.instruments.infrastructure.dto.request.InstrumentRequest;
import org.cocos.instruments.infrastructure.dto.response.InstrumentResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/instruments")
@Tag(name = "Instruments")
public class InstrumentController {

    private final InstrumentService service;

    @GetMapping
    @Operation(summary = "Search stocks by ticker or name")
    public Page<InstrumentResponse> search(@ParameterObject @Valid @ModelAttribute InstrumentRequest request) {
        return InstrumentResponse.from(service.search(request));
    }
}
