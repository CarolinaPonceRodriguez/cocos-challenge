package org.cocos.instruments.infrastructure.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstrumentRequest {

    @NotBlank(message = "Search text is required.")
    private String q;

    @Min(value = 0, message = "page must not be negative.")
    private int page = 0;

    @Min(value = 1, message = "size must be at least 1.")
    @Max(value = 100, message = "size must not exceed 100.")
    private int size = 20;
}
