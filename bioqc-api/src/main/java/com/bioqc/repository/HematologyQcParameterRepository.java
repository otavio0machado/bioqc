package com.bioqc.repository;

import com.bioqc.entity.HematologyQcParameter;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HematologyQcParameterRepository extends JpaRepository<HematologyQcParameter, UUID> {

    List<HematologyQcParameter> findByIsActiveTrue();

    List<HematologyQcParameter> findByAnalitoAndIsActiveTrue(String analito);
}
