package com.bioqc.controller;

import com.bioqc.dto.request.HematologyBioRequest;
import com.bioqc.dto.request.HematologyMeasurementRequest;
import com.bioqc.dto.request.HematologyParameterRequest;
import com.bioqc.dto.response.HematologyBioRecordResponse;
import com.bioqc.dto.response.HematologyMeasurementResponse;
import com.bioqc.dto.response.HematologyParameterResponse;
import com.bioqc.service.HematologyQcService;
import com.bioqc.util.ResponseMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/hematology")
public class HematologyController {

    private final HematologyQcService hematologyQcService;

    public HematologyController(HematologyQcService hematologyQcService) {
        this.hematologyQcService = hematologyQcService;
    }

    @GetMapping("/parameters")
    public ResponseEntity<List<HematologyParameterResponse>> getParameters(@RequestParam(required = false) String analito) {
        return ResponseEntity.ok(hematologyQcService.getParameters(analito));
    }

    @PostMapping("/parameters")
    public ResponseEntity<HematologyParameterResponse> createParameter(
        @Valid @RequestBody HematologyParameterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hematologyQcService.createParameter(request));
    }

    @PutMapping("/parameters/{id}")
    public ResponseEntity<HematologyParameterResponse> updateParameter(
        @PathVariable UUID id,
        @Valid @RequestBody HematologyParameterRequest request
    ) {
        return ResponseEntity.ok(hematologyQcService.updateParameter(id, request));
    }

    @DeleteMapping("/parameters/{id}")
    public ResponseEntity<Void> deleteParameter(@PathVariable UUID id) {
        hematologyQcService.deleteParameter(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/measurements")
    public ResponseEntity<List<HematologyMeasurementResponse>> getMeasurements(@RequestParam(required = false) UUID parameterId) {
        return ResponseEntity.ok(hematologyQcService.getMeasurements(parameterId));
    }

    @PostMapping("/measurements")
    public ResponseEntity<HematologyMeasurementResponse> createMeasurement(
        @Valid @RequestBody HematologyMeasurementRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hematologyQcService.createMeasurement(request));
    }

    @GetMapping("/bio-records")
    public ResponseEntity<List<HematologyBioRecordResponse>> getBioRecords() {
        return ResponseEntity.ok(
            hematologyQcService.getBioRecords().stream()
                .map(ResponseMapper::toHematologyBioRecordResponse)
                .toList()
        );
    }

    @PostMapping("/bio-records")
    public ResponseEntity<HematologyBioRecordResponse> createBioRecord(
        @Valid @RequestBody HematologyBioRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ResponseMapper.toHematologyBioRecordResponse(hematologyQcService.createBioRecord(request)));
    }
}
