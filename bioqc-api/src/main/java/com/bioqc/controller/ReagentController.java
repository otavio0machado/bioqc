package com.bioqc.controller;

import com.bioqc.dto.request.ArchiveReagentLotRequest;
import com.bioqc.dto.request.DeleteReagentLotRequest;
import com.bioqc.dto.request.ReagentLotRequest;
import com.bioqc.dto.request.StockMovementRequest;
import com.bioqc.dto.request.UnarchiveReagentLotRequest;
import com.bioqc.dto.response.ReagentLabelSummary;
import com.bioqc.dto.response.ReagentLotResponse;
import com.bioqc.dto.response.StockMovementResponse;
import com.bioqc.service.ReagentService;
import com.bioqc.util.ResponseMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/reagents")
public class ReagentController {

    private final ReagentService reagentService;

    public ReagentController(ReagentService reagentService) {
        this.reagentService = reagentService;
    }

    @GetMapping
    public ResponseEntity<List<ReagentLotResponse>> getLots(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(reagentService.getLots(category, status));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<ReagentLotResponse> createLot(@Valid @RequestBody ReagentLotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ResponseMapper.toReagentLotResponse(reagentService.createLot(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<ReagentLotResponse> updateLot(@PathVariable UUID id, @Valid @RequestBody ReagentLotRequest request) {
        return ResponseEntity.ok(ResponseMapper.toReagentLotResponse(reagentService.updateLot(id, request)));
    }

    /**
     * Hard delete v3 — ADMIN-only com confirmacao por digitacao do {@code lotNumber}.
     * Cascade {@code stock_movements} via JPA. Audit
     * {@code REAGENT_LOT_DELETED} com snapshot enumerativo (audit ressalva 1.2).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteLot(
        @PathVariable UUID id,
        @Valid @RequestBody DeleteReagentLotRequest request
    ) {
        reagentService.deleteLot(id, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Arquiva lote (status=inativo). ADMIN ou FUNCIONARIO. Body
     * {@code { archivedAt, archivedBy }}. Audit {@code REAGENT_LOT_ARCHIVED}.
     */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<ReagentLotResponse> archiveLot(
        @PathVariable UUID id,
        @Valid @RequestBody ArchiveReagentLotRequest request
    ) {
        return ResponseEntity.ok(
            ResponseMapper.toReagentLotResponse(reagentService.archiveLot(id, request))
        );
    }

    /**
     * Reativa lote arquivado. Re-deriva status ternaria (decisao 1.7).
     * Audit {@code REAGENT_LOT_UNARCHIVED}.
     */
    @PostMapping("/{id}/unarchive")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<ReagentLotResponse> unarchiveLot(
        @PathVariable UUID id,
        @RequestBody(required = false) UnarchiveReagentLotRequest request
    ) {
        return ResponseEntity.ok(
            ResponseMapper.toReagentLotResponse(reagentService.unarchiveLot(id, request))
        );
    }

    @GetMapping("/{id}/movements")
    public ResponseEntity<List<StockMovementResponse>> getMovements(@PathVariable UUID id) {
        return ResponseEntity.ok(
            reagentService.getMovements(id).stream()
                .map(ResponseMapper::toStockMovementResponse)
                .toList()
        );
    }

    @PostMapping("/{id}/movements")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<StockMovementResponse> createMovement(
        @PathVariable UUID id,
        @Valid @RequestBody StockMovementRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ResponseMapper.toStockMovementResponse(reagentService.createMovement(id, request)));
    }

    @DeleteMapping("/movements/{movId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FUNCIONARIO')")
    public ResponseEntity<Void> deleteMovement(@PathVariable UUID movId) {
        reagentService.deleteMovement(movId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-lot-number")
    public ResponseEntity<List<ReagentLotResponse>> getByLotNumber(@RequestParam String lotNumber) {
        return ResponseEntity.ok(
            reagentService.getByLotNumber(lotNumber).stream()
                .map(ResponseMapper::toReagentLotResponse)
                .toList()
        );
    }

    @GetMapping("/expiring")
    public ResponseEntity<List<ReagentLotResponse>> getExpiringLots(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(reagentService.getExpiringLots(days).stream().map(ResponseMapper::toReagentLotResponse).toList());
    }

    @GetMapping("/labels")
    public ResponseEntity<List<ReagentLabelSummary>> getLabelSummaries() {
        return ResponseEntity.ok(reagentService.getLabelSummaries());
    }

    /**
     * CSV export v3 — header novo:
     * {@code Etiqueta,Lote,Fabricante,Categoria,Validade,Dias Restantes,Em Estoque,
     * Em Uso,Total,Status,Localizacao,Temperatura,Arquivado em,Arquivado por}.
     */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String status
    ) {
        List<ReagentLotResponse> lots = reagentService.getLots(category, status);
        StringBuilder csv = new StringBuilder();
        csv.append("Etiqueta,Lote,Fabricante,Categoria,Validade,Dias Restantes,Em Estoque,Em Uso,Total,Status,Localizacao,Temperatura,Arquivado em,Arquivado por\n");
        for (ReagentLotResponse lot : lots) {
            csv.append(escapeCsv(lot.label())).append(",");
            csv.append(escapeCsv(lot.lotNumber())).append(",");
            csv.append(escapeCsv(lot.manufacturer())).append(",");
            csv.append(escapeCsv(lot.category())).append(",");
            csv.append(lot.expiryDate() != null ? lot.expiryDate() : "").append(",");
            csv.append(lot.daysLeft()).append(",");
            csv.append(lot.unitsInStock() != null ? lot.unitsInStock() : 0).append(",");
            csv.append(lot.unitsInUse() != null ? lot.unitsInUse() : 0).append(",");
            csv.append(lot.totalUnits() != null ? lot.totalUnits() : 0).append(",");
            csv.append(escapeCsv(humanStatus(lot.status()))).append(",");
            csv.append(escapeCsv(lot.location())).append(",");
            csv.append(escapeCsv(lot.storageTemp())).append(",");
            csv.append(lot.archivedAt() != null ? lot.archivedAt() : "").append(",");
            csv.append(escapeCsv(lot.archivedBy())).append("\n");
        }
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=reagentes.csv")
            .header("Content-Type", "text/csv; charset=UTF-8")
            .body(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String humanStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "em_estoque" -> "Em estoque";
            case "em_uso" -> "Em uso";
            case "vencido" -> "Vencido";
            case "inativo" -> "Inativo";
            default -> status;
        };
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
