package org.cocos.orders.infrastructure.repository;

import java.util.List;
import java.util.Optional;

import org.cocos.orders.domain.entity.Order;
import org.cocos.orders.domain.enums.OrderStatus;
import org.springframework.data.repository.Repository;

public interface OrderRepository extends Repository<Order, Integer> {

    Optional<Order> findByIdAndUserId(Integer id, Integer userId);

    List<Order> findByUserIdAndStatusInOrderByDatetimeAscIdAsc(Integer userId, List<OrderStatus> statuses);

    Order save(Order order);
}
