package com.bioqc.repository;

import com.bioqc.entity.StockMovement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findByReagentLotIdOrderByCreatedAtDesc(UUID lotId);

    boolean existsByReagentLotId(UUID lotId);

    long countByReagentLotId(UUID lotId);
}
