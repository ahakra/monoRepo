package com.ahvakra.camelintegration.transformers.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransformerRepository extends JpaRepository<TransformerDefinitionEntity, UUID> {
    List<TransformerDefinitionEntity> findAllByOrderByUpdatedAtDesc();

    @Query(
        "select distinct t from TransformerDefinitionEntity t left join t.inputs i left join t.outputs o where i.fieldDefinitionId = :id or o.fieldDefinitionId = :id"
    )
    List<TransformerDefinitionEntity> findByFieldDefinitionId(@Param("id") UUID id);
}
