package com.bioqc.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "hematology_qc_parameters")
public class HematologyQcParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String analito;

    private String equipamento;

    @Column(name = "lote_controle")
    private String loteControle;

    @Column(name = "nivel_controle")
    private String nivelControle;

    @Builder.Default
    @Column(nullable = false)
    private String modo = "INTERVALO";

    @Builder.Default
    @Column(name = "alvo_valor")
    private Double alvoValor = 0D;

    @Builder.Default
    @Column(name = "min_valor")
    private Double minValor = 0D;

    @Builder.Default
    @Column(name = "max_valor")
    private Double maxValor = 0D;

    @Builder.Default
    @Column(name = "tolerancia_percentual")
    private Double toleranciaPercentual = 0D;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Builder.Default
    @OneToMany(mappedBy = "parameter", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dataMedicao DESC")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<HematologyQcMeasurement> measurements = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
