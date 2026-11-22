package org.cocos.user.domain.exception;

import org.cocos.shared.domain.exception.BusinessException;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(int userId) {
        super(Code.USER_NOT_FOUND, "User %d was not found.".formatted(userId));
    }
}
