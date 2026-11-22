package org.cocos.orders.application;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.cocos.orders.domain.enums.OrderStatus;
import org.cocos.orders.domain.ledger.AccountSnapshot;
import org.cocos.orders.infrastructure.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountSnapshotService {

    private static final List<OrderStatus> BALANCE_STATUSES = List.of(OrderStatus.FILLED, OrderStatus.NEW);

    private final OrderRepository orders;

    @Transactional(readOnly = true)
    public AccountSnapshot snapshot(Integer userId) {
        return AccountSnapshot.replay(
                orders.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(userId, BALANCE_STATUSES));
    }
}
