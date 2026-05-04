package com.bioqc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "hematology_bio_records")
public class HematologyBioRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "data_bio", nullable = false)
    private LocalDate dataBio;

    @Column(name = "data_pad")
    private LocalDate dataPad;

    @Column(name = "registro_bio")
    private String registroBio;

    @Column(name = "registro_pad")
    private String registroPad;

    @Builder.Default
    @Column(name = "modo_ci")
    private String modoCi = "bio";

    @Builder.Default
    @Column(name = "bio_hemacias")
    private Double bioHemacias = 0D;

    @Builder.Default
    @Column(name = "bio_hematocrito")
    private Double bioHematocrito = 0D;

    @Builder.Default
    @Column(name = "bio_hemoglobina")
    private Double bioHemoglobina = 0D;

    @Builder.Default
    @Column(name = "bio_leucocitos")
    private Double bioLeucocitos = 0D;

    @Builder.Default
    @Column(name = "bio_plaquetas")
    private Double bioPlaquetas = 0D;

    @Builder.Default
    @Column(name = "bio_rdw")
    private Double bioRdw = 0D;

    @Builder.Default
    @Column(name = "bio_vpm")
    private Double bioVpm = 0D;

    @Builder.Default
    @Column(name = "pad_hemacias")
    private Double padHemacias = 0D;

    @Builder.Default
    @Column(name = "pad_hematocrito")
    private Double padHematocrito = 0D;

    @Builder.Default
    @Column(name = "pad_hemoglobina")
    private Double padHemoglobina = 0D;

    @Builder.Default
    @Column(name = "pad_leucocitos")
    private Double padLeucocitos = 0D;

    @Builder.Default
    @Column(name = "pad_plaquetas")
    private Double padPlaquetas = 0D;

    @Builder.Default
    @Column(name = "pad_rdw")
    private Double padRdw = 0D;

    @Builder.Default
    @Column(name = "pad_vpm")
    private Double padVpm = 0D;

    @Builder.Default
    @Column(name = "ci_min_hemacias")
    private Double ciMinHemacias = 0D;

    @Builder.Default
    @Column(name = "ci_max_hemacias")
    private Double ciMaxHemacias = 0D;

    @Builder.Default
    @Column(name = "ci_min_hematocrito")
    private Double ciMinHematocrito = 0D;

    @Builder.Default
    @Column(name = "ci_max_hematocrito")
    private Double ciMaxHematocrito = 0D;

    @Builder.Default
    @Column(name = "ci_min_hemoglobina")
    private Double ciMinHemoglobina = 0D;

    @Builder.Default
    @Column(name = "ci_max_hemoglobina")
    private Double ciMaxHemoglobina = 0D;

    @Builder.Default
    @Column(name = "ci_min_leucocitos")
    private Double ciMinLeucocitos = 0D;

    @Builder.Default
    @Column(name = "ci_max_leucocitos")
    private Double ciMaxLeucocitos = 0D;

    @Builder.Default
    @Column(name = "ci_min_plaquetas")
    private Double ciMinPlaquetas = 0D;

    @Builder.Default
    @Column(name = "ci_max_plaquetas")
    private Double ciMaxPlaquetas = 0D;

    @Builder.Default
    @Column(name = "ci_min_rdw")
    private Double ciMinRdw = 0D;

    @Builder.Default
    @Column(name = "ci_max_rdw")
    private Double ciMaxRdw = 0D;

    @Builder.Default
    @Column(name = "ci_min_vpm")
    private Double ciMinVpm = 0D;

    @Builder.Default
    @Column(name = "ci_max_vpm")
    private Double ciMaxVpm = 0D;

    @Builder.Default
    @Column(name = "ci_pct_hemacias")
    private Double ciPctHemacias = 0D;

    @Builder.Default
    @Column(name = "ci_pct_hematocrito")
    private Double ciPctHematocrito = 0D;

    @Builder.Default
    @Column(name = "ci_pct_hemoglobina")
    private Double ciPctHemoglobina = 0D;

    @Builder.Default
    @Column(name = "ci_pct_leucocitos")
    private Double ciPctLeucocitos = 0D;

    @Builder.Default
    @Column(name = "ci_pct_plaquetas")
    private Double ciPctPlaquetas = 0D;

    @Builder.Default
    @Column(name = "ci_pct_rdw")
    private Double ciPctRdw = 0D;

    @Builder.Default
    @Column(name = "ci_pct_vpm")
    private Double ciPctVpm = 0D;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
