package com.bioqc.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "hematology_qc_measurements")
public class HematologyQcMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id", nullable = false)
    @JsonIgnore
    private HematologyQcParameter parameter;

    @Column(name = "data_medicao", nullable = false)
    private LocalDate dataMedicao;

    @Column(nullable = false)
    private String analito;

    @Column(name = "valor_medido", nullable = false)
    private Double valorMedido;

    @Column(name = "modo_usado")
    private String modoUsado;

    @Builder.Default
    @Column(name = "min_aplicado")
    private Double minAplicado = 0D;

    @Builder.Default
    @Column(name = "max_aplicado")
    private Double maxAplicado = 0D;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
