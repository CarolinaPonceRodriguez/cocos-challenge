package org.cocos.portfolio;

import java.math.BigDecimal;
import java.util.List;
import org.cocos.portfolio.application.PortfolioService;
import org.cocos.portfolio.infrastructure.dto.response.PortfolioResponse.PositionResponse;
import org.cocos.portfolio.infrastructure.dto.response.PortfolioResponse;
import org.cocos.portfolio.infrastructure.controller.PortfolioController;
import org.cocos.user.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PortfolioService service;

    @Test
    void returnsTheCompletePortfolioAsExplicitJson() throws Exception {
        var position = new PositionResponse(47, "PAMP", 41L, new BigDecimal("38000.00"),
                new BigDecimal("2.50"), new BigDecimal("-0.40"));
        when(service.get(1)).thenReturn(new PortfolioResponse(new BigDecimal("626574.15"),
                new BigDecimal("700000.00"), List.of(position)));

        mvc.perform(get("/api/v1/users/1/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash").value(626574.15))
                .andExpect(jsonPath("$.totalAccountValue").value(700000.00))
                .andExpect(jsonPath("$.positions[0].instrumentId").value(47))
                .andExpect(jsonPath("$.positions[0].ticker").value("PAMP"))
                .andExpect(jsonPath("$.positions[0].quantity").value(41))
                .andExpect(jsonPath("$.positions[0].marketValue").value(38000.00))
                .andExpect(jsonPath("$.positions[0].totalReturnPercentage").value(2.50))
                .andExpect(jsonPath("$.positions[0].dailyReturnPercentage").value(-0.40))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void returnsNullValuesWhenThePortfolioCannotBeValued() throws Exception {
        var position = new PositionResponse(31, "BMA", -10L, null, null, new BigDecimal("1.00"));
        when(service.get(1)).thenReturn(new PortfolioResponse(new BigDecimal("100.00"),
                null, List.of(position)));

        mvc.perform(get("/api/v1/users/1/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAccountValue").value(nullValue()))
                .andExpect(jsonPath("$.positions[0].quantity").value(-10))
                .andExpect(jsonPath("$.positions[0].marketValue").value(nullValue()))
                .andExpect(jsonPath("$.positions[0].totalReturnPercentage").value(nullValue()));
    }

    @Test
    void returnsAnEmptyPositionListForAnAccountWithoutHoldings() throws Exception {
        when(service.get(3)).thenReturn(new PortfolioResponse(new BigDecimal("10.00"),
                new BigDecimal("10.00"), List.of()));

        mvc.perform(get("/api/v1/users/3/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions").isEmpty());
    }

    @Test
    void reportsAnUnknownUser() throws Exception {
        when(service.get(99)).thenThrow(new UserNotFoundException(99));

        mvc.perform(get("/api/v1/users/99/portfolio"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User 99 was not found."));
    }

    @Test
    void namesTheUserIdWhenItIsNotANumber() throws Exception {
        mvc.perform(get("/api/v1/users/abc/portfolio"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("userId"));

        verifyNoInteractions(service);
    }
}
