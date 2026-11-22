package org.cocos.shared.infrastructure.exception;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.exc.MismatchedInputException;

import org.cocos.shared.domain.exception.BusinessException;
import org.cocos.shared.domain.exception.BusinessException.Code;
import org.cocos.shared.infrastructure.dto.response.ErrorResponse;
import org.cocos.shared.infrastructure.dto.response.FieldErrorResponse;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception exception) {
        List<FieldErrorResponse> errors = switch (exception) {
            case BindException binding -> binding.getFieldErrors().stream()
                    .map(error -> new FieldErrorResponse(error.getField(), error.isBindingFailure()
                            ? "%s has an invalid value.".formatted(error.getField())
                            : error.getDefaultMessage()))
                    .toList();
            case MethodArgumentTypeMismatchException mismatch -> invalidValue(mismatch.getName());
            case HttpMessageNotReadableException unreadable
                    when unreadable.getCause() instanceof MismatchedInputException input && !input.getPath().isEmpty()
                    -> invalidValue(input.getPath().getLast().getPropertyName());
            default -> List.of();
        };
        log.debug("Request validation failed: {} - {}", errors, exception.getMessage());
        return error(Code.INVALID_REQUEST, "Request validation failed.", errors);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> businessError(BusinessException exception) {
        log.debug("Business rule rejected the request: {} - {}", exception.getCode(), exception.getMessage());
        return error(exception.getCode(), exception.getMessage(), List.of());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> accountBusy(PessimisticLockingFailureException exception) {
        log.warn("Timed out waiting for the account lock: {}", exception.getMessage());
        return error(Code.ACCOUNT_BUSY, "The account is processing another order. Retry shortly.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception) throws Exception {
        if (exception instanceof org.springframework.web.ErrorResponse) {
            throw exception;
        }

        log.error("Unexpected error while handling the request.", exception);
        return error(Code.INTERNAL_ERROR, "An unexpected error occurred.", List.of());
    }

    private List<FieldErrorResponse> invalidValue(String field) {
        return List.of(new FieldErrorResponse(field, "%s has an invalid value.".formatted(field)));
    }

    private ResponseEntity<ErrorResponse> error(Code code, String message, List<FieldErrorResponse> errors) {
        HttpStatus status = switch (code) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case ORDER_NOT_FOUND, USER_NOT_FOUND, INSTRUMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INSTRUMENT_NOT_TRADABLE, SIZE_OUT_OF_RANGE, AMOUNT_TOO_SMALL -> HttpStatus.UNPROCESSABLE_CONTENT;
            case MARKET_PRICE_UNAVAILABLE, INCONSISTENT_POSITION, ORDER_NOT_CANCELLABLE, ACCOUNT_BUSY ->
                    HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(code.name(), message, errors));
    }
}
