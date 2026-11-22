package org.cocos.user.infrastructure.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.cocos.user.domain.entity.User;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends Repository<User, Integer> {

    boolean existsById(Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM User user WHERE user.id = :userId")
    Optional<User> lockById(@Param("userId") Integer userId);
}
