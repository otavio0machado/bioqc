package com.bioqc.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "qc_records")
public class QcRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_id")
    @JsonIgnore
    private QcReferenceValue reference;

    @Column(name = "exam_name", nullable = false)
    private String examName;

    @Builder.Default
    @Column(nullable = false)
    private String area = "bioquimica";

    @Column(nullable = false)
    private LocalDate date;

    private String level;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(nullable = false)
    private Double value;

    @Builder.Default
    @Column(name = "target_value", nullable = false)
    private Double targetValue = 0D;

    @Builder.Default
    @Column(name = "target_sd", nullable = false)
    private Double targetSd = 0D;

    @Builder.Default
    private Double cv = 0D;

    @Builder.Default
    @Column(name = "cv_limit")
    private Double cvLimit = 10D;

    @Builder.Default
    @Column(name = "z_score")
    private Double zScore = 0D;

    private String equipment;

    private String analyst;

    @Builder.Default
    @Column(nullable = false)
    private String status = "APROVADO";

    @Builder.Default
    @Column(name = "needs_calibration", nullable = false)
    private Boolean needsCalibration = Boolean.FALSE;

    @Builder.Default
    @OneToMany(mappedBy = "qcRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<WestgardViolation> violations = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
