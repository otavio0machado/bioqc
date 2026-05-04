package com.bioqc.controller;

import com.bioqc.dto.request.AreaQcMeasurementRequest;
import com.bioqc.dto.request.AreaQcParameterRequest;
import com.bioqc.dto.response.AreaQcMeasurementResponse;
import com.bioqc.dto.response.AreaQcParameterResponse;
import com.bioqc.service.AreaQcService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qc/areas/{area}")
public class AreaQcController {

    private final AreaQcService areaQcService;

    public AreaQcController(AreaQcService areaQcService) {
        this.areaQcService = areaQcService;
    }

    @GetMapping("/parameters")
    public ResponseEntity<List<AreaQcParameterResponse>> getParameters(
        @PathVariable String area,
        @RequestParam(required = false) String analito
    ) {
        return ResponseEntity.ok(areaQcService.getParameters(area, analito));
    }

    @PostMapping("/parameters")
    public ResponseEntity<AreaQcParameterResponse> createParameter(
        @PathVariable String area,
        @Valid @RequestBody AreaQcParameterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(areaQcService.createParameter(area, request));
    }

    @PutMapping("/parameters/{id}")
    public ResponseEntity<AreaQcParameterResponse> updateParameter(
        @PathVariable String area,
        @PathVariable UUID id,
        @Valid @RequestBody AreaQcParameterRequest request
    ) {
        return ResponseEntity.ok(areaQcService.updateParameter(area, id, request));
    }

    @DeleteMapping("/parameters/{id}")
    public ResponseEntity<Void> deleteParameter(@PathVariable String area, @PathVariable UUID id) {
        areaQcService.deleteParameter(area, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/measurements")
    public ResponseEntity<List<AreaQcMeasurementResponse>> getMeasurements(
        @PathVariable String area,
        @RequestParam(required = false) String analito,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(areaQcService.getMeasurements(area, analito, startDate, endDate));
    }

    @PostMapping("/measurements")
    public ResponseEntity<AreaQcMeasurementResponse> createMeasurement(
        @PathVariable String area,
        @Valid @RequestBody AreaQcMeasurementRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(areaQcService.createMeasurement(area, request));
    }
}
