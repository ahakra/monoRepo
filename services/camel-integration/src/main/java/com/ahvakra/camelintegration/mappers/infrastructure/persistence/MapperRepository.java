package com.ahvakra.camelintegration.mappers.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapperRepository extends JpaRepository<MapperDefinitionEntity, UUID> {
    List<MapperDefinitionEntity> findAllByOrderByUpdatedAtDesc();

    @Query(
        "select distinct mapper from MapperDefinitionEntity mapper join mapper.outputs output where output.fieldDefinitionId = :id"
    )
    List<MapperDefinitionEntity> findByFieldDefinitionId(@Param("id") UUID id);
}
