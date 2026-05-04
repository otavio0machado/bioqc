package com.bioqc.repository;

import com.bioqc.entity.ImportRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRunRepository extends JpaRepository<ImportRun, UUID> {

    List<ImportRun> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
