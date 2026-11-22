package org.cocos.shared.domain.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    public enum Code {
        INVALID_REQUEST,
        RESOURCE_NOT_FOUND,
        METHOD_NOT_ALLOWED,
        UNSUPPORTED_MEDIA_TYPE,
        ORDER_NOT_FOUND,
        USER_NOT_FOUND,
        INSTRUMENT_NOT_FOUND,
        INSTRUMENT_NOT_TRADABLE,
        MARKET_PRICE_UNAVAILABLE,
        SIZE_OUT_OF_RANGE,
        AMOUNT_TOO_SMALL,
        INCONSISTENT_POSITION,
        ORDER_NOT_CANCELLABLE,
        ACCOUNT_BUSY,
        INTERNAL_ERROR
    }

    private final Code code;

    public BusinessException(Code code, String message) {
        super(message);
        this.code = code;
    }
}
