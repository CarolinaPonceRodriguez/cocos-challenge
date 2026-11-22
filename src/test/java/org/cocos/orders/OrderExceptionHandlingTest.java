package org.cocos.orders;

import org.cocos.orders.application.OrderService;
import org.cocos.orders.domain.exception.OrderNotFoundException;
import org.cocos.orders.infrastructure.controller.OrderController;
import org.cocos.shared.domain.exception.BusinessException;
import org.cocos.shared.domain.exception.BusinessException.Code;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderExceptionHandlingTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService service;

    @Test
    void reportsMissingOrder() throws Exception {
        when(service.cancel(anyInt(), anyInt())).thenThrow(new OrderNotFoundException(1, 2));
        mvc.perform(post("/api/v1/users/1/orders/2/cancel")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order 2 was not found for user 1."));
    }

    @Test
    void translatesCancellationConflictToHttp() throws Exception {
        when(service.cancel(anyInt(), anyInt())).thenThrow(new BusinessException(Code.ORDER_NOT_CANCELLABLE,
                "Only NEW orders can be cancelled. Order 2 has status FILLED."));
        mvc.perform(post("/api/v1/users/1/orders/2/cancel")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
    }

    @Test
    void reportsALockTimeoutAsABusyAccount() throws Exception {
        when(service.cancel(anyInt(), anyInt())).thenThrow(new CannotAcquireLockException("timeout"));
        mvc.perform(post("/api/v1/users/1/orders/2/cancel")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BUSY"));
    }

    @Test
    void hidesUnexpectedErrorsBehindTheErrorEnvelope() throws Exception {
        when(service.cancel(anyInt(), anyInt())).thenThrow(new IllegalStateException("boom"));
        mvc.perform(post("/api/v1/users/1/orders/2/cancel")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
    }

    @Test
    void reportsUnknownRoutesMethodsAndMediaTypesInTheErrorEnvelope() throws Exception {
        mvc.perform(post("/api/v1/unknown")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(put("/api/v1/users/1/orders")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/api/v1/users/1/orders").contentType(MediaType.TEXT_PLAIN).content("order"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        verifyNoInteractions(service);
    }

    @Test
    void namesThePathVariableWithAnInvalidType() throws Exception {
        mvc.perform(post("/api/v1/users/abc/orders/2/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("userId"));

        verifyNoInteractions(service);
    }

    @Test
    void namesTheInvalidFieldsOfTheRequestBody() throws Exception {
        submit("{\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[?(@.field == 'instrumentId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'quantity')]").exists());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAFractionalQuantity() throws Exception {
        submit("{\"instrumentId\":47,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1.5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].message").value("quantity has an invalid value."));

        verifyNoInteractions(service);
    }

    @Test
    void doesNotAcceptCashMovementsAsASide() throws Exception {
        submit("{\"instrumentId\":47,\"side\":\"CASH_IN\",\"type\":\"MARKET\",\"quantity\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("side"))
                .andExpect(jsonPath("$.errors[0].message").value("side has an invalid value."));

        verifyNoInteractions(service);
    }

    @Test
    void namesTheFieldOfEachRuleThatCombinesFields() throws Exception {
        submit("{\"instrumentId\":47,\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1,\"amount\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[?(@.field == 'quantityOrAmount')].message")
                        .value("Provide exactly one of quantity or amount."))
                .andExpect(jsonPath("$.errors[?(@.field == 'price')].message")
                        .value("A LIMIT order requires a price."));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsANonPositiveQuantityAndAnAmountWithMoreThanTwoDecimals() throws Exception {
        submit("{\"instrumentId\":47,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].message").value("quantity must be greater than 0."));
        submit("{\"instrumentId\":47,\"side\":\"BUY\",\"type\":\"MARKET\",\"amount\":1000.123}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("amount must have at most 12 integer digits and 2 decimal places."));

        verifyNoInteractions(service);
    }

    private ResultActions submit(String body) throws Exception {
        return mvc.perform(post("/api/v1/users/1/orders").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
