package com.bioqc.repository;

import com.bioqc.entity.HematologyQcMeasurement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HematologyQcMeasurementRepository extends JpaRepository<HematologyQcMeasurement, UUID> {

    List<HematologyQcMeasurement> findByParameterIdOrderByDataMedicaoDesc(UUID parameterId);

    List<HematologyQcMeasurement> findByDataMedicaoBetween(LocalDate start, LocalDate end);

    List<HematologyQcMeasurement> findByDataMedicaoBetweenOrderByDataMedicaoDesc(LocalDate start, LocalDate end);
}
