package org.cocos.user.application;

import lombok.RequiredArgsConstructor;
import org.cocos.user.domain.exception.UserNotFoundException;
import org.cocos.user.infrastructure.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

    public void ensureExists(Integer userId) {
        if (isUnknown(userId)) {
            throw new UserNotFoundException(userId);
        }
    }

    private boolean isUnknown(Integer userId) {
        return !repository.existsById(userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForOrderChanges(Integer userId) {
        if (repository.lockById(userId).isEmpty()) {
            throw new UserNotFoundException(userId);
        }
    }
}
