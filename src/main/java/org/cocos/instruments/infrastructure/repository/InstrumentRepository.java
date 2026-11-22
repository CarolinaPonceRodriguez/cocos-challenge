package org.cocos.instruments.infrastructure.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.cocos.instruments.domain.entity.Instrument;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

public interface InstrumentRepository extends Repository<Instrument, Integer>,
        JpaSpecificationExecutor<Instrument> {

    Optional<Instrument> findById(Integer id);

    List<Instrument> findByIdIn(Collection<Integer> ids);
}
