package org.cocos.shared.infrastructure.dto.response;

import java.util.List;

public record ApiErrorResponse(String code, String message, List<FieldErrorResponse> errors) {
}
