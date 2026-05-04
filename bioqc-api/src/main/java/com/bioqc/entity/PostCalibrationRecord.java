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
@Table(name = "post_calibration_records")
public class PostCalibrationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qc_record_id", nullable = false)
    @JsonIgnore
    private QcRecord qcRecord;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "exam_name", nullable = false)
    private String examName;

    @Column(name = "original_value", nullable = false)
    private Double originalValue;

    @Builder.Default
    @Column(name = "original_cv")
    private Double originalCv = 0D;

    @Column(name = "post_calibration_value", nullable = false)
    private Double postCalibrationValue;

    @Builder.Default
    @Column(name = "post_calibration_cv")
    private Double postCalibrationCv = 0D;

    @Builder.Default
    @Column(name = "target_value")
    private Double targetValue = 0D;

    private String analyst;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
