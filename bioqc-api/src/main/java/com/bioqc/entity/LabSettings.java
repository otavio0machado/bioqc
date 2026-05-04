package com.bioqc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lab_settings")
public class LabSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Builder.Default
    @Column(name = "lab_name", nullable = false)
    private String labName = "";

    @Builder.Default
    @Column(name = "responsible_name", nullable = false)
    private String responsibleName = "";

    @Builder.Default
    @Column(name = "responsible_registration", nullable = false)
    private String responsibleRegistration = "";

    @Builder.Default
    @Column(nullable = false)
    private String address = "";

    @Builder.Default
    @Column(nullable = false)
    private String phone = "";

    @Builder.Default
    @Column(nullable = false)
    private String email = "";

    // ===== Campos institucionais V10 (Reports V2) =====

    /** CNPJ do laboratorio (XX.XXX.XXX/XXXX-XX). Opcional. */
    @Column(length = 20)
    private String cnpj;

    /** Codigo CNES (Cadastro Nacional de Estabelecimentos de Saude). */
    @Column(length = 20)
    private String cnes;

    /** Orgao de registro do responsavel tecnico (CRBM, CRM, etc.). */
    @Column(name = "registration_body", length = 20)
    private String registrationBody;

    /** CPF do responsavel tecnico (opcional). */
    @Column(name = "responsible_cpf", length = 20)
    private String responsibleCpf;

    /** Diretor tecnico (pode diferir do responsavel tecnico). */
    @Column(name = "technical_director_name", length = 200)
    private String technicalDirectorName;

    @Column(name = "technical_director_cpf", length = 20)
    private String technicalDirectorCpf;

    @Column(name = "technical_director_reg", length = 100)
    private String technicalDirectorReg;

    /** Site do laboratorio. */
    @Column(length = 200)
    private String website;

    /** Numero de licenca sanitaria (Vigilancia Sanitaria). */
    @Column(name = "sanitary_license", length = 50)
    private String sanitaryLicense;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
