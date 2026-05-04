package com.bioqc.repository;

import com.bioqc.entity.AreaQcMeasurement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaQcMeasurementRepository extends JpaRepository<AreaQcMeasurement, UUID> {

    List<AreaQcMeasurement> findByAreaOrderByDataMedicaoDesc(String area);

    List<AreaQcMeasurement> findByAreaAndAnalitoIgnoreCaseOrderByDataMedicaoDesc(String area, String analito);

    List<AreaQcMeasurement> findByAreaAndDataMedicaoBetweenOrderByDataMedicaoDesc(
        String area, LocalDate start, LocalDate end
    );
}
