package com.bioqc.repository;

import com.bioqc.entity.AreaQcParameter;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaQcParameterRepository extends JpaRepository<AreaQcParameter, UUID> {

    List<AreaQcParameter> findByAreaAndIsActiveTrueOrderByCreatedAtDesc(String area);

    List<AreaQcParameter> findByAreaAndAnalitoIgnoreCaseAndIsActiveTrueOrderByCreatedAtDesc(String area, String analito);
}
