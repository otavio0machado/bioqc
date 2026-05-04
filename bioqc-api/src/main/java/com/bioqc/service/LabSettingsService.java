package com.bioqc.service;

import com.bioqc.dto.request.LabReportEmailRequest;
import com.bioqc.dto.request.LabSettingsRequest;
import com.bioqc.dto.response.LabReportEmailResponse;
import com.bioqc.dto.response.LabSettingsResponse;
import com.bioqc.entity.LabReportEmail;
import com.bioqc.entity.LabSettings;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.LabReportEmailRepository;
import com.bioqc.repository.LabSettingsRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabSettingsService {

    private final LabSettingsRepository labSettingsRepository;
    private final LabReportEmailRepository labReportEmailRepository;

    public LabSettingsService(
        LabSettingsRepository labSettingsRepository,
        LabReportEmailRepository labReportEmailRepository
    ) {
        this.labSettingsRepository = labSettingsRepository;
        this.labReportEmailRepository = labReportEmailRepository;
    }

    @Transactional
    public LabSettings getOrCreateSingleton() {
        return labSettingsRepository.findSingleton().orElseGet(() -> {
            LabSettings fresh = LabSettings.builder().build();
            return labSettingsRepository.save(fresh);
        });
    }

    @Transactional(readOnly = true)
    public LabSettingsResponse getSettings() {
        LabSettings settings = labSettingsRepository.findSingleton()
            .orElseGet(() -> LabSettings.builder().build());
        return toResponse(settings);
    }

    private static final java.util.regex.Pattern CNPJ_FORMAT =
        java.util.regex.Pattern.compile("^\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}$");
    private static final java.util.regex.Pattern CNES_FORMAT =
        java.util.regex.Pattern.compile("^\\d{7}$");

    @Transactional
    public LabSettingsResponse updateSettings(LabSettingsRequest request) {
        String cnpj = trimOrNull(request.cnpj());
        String cnes = trimOrNull(request.cnes());
        if (cnpj != null && !CNPJ_FORMAT.matcher(cnpj).matches()) {
            throw new BusinessException("CNPJ deve seguir o formato XX.XXX.XXX/XXXX-XX");
        }
        if (cnes != null && !CNES_FORMAT.matcher(cnes).matches()) {
            throw new BusinessException("CNES deve ter exatamente 7 dígitos");
        }

        LabSettings settings = getOrCreateSingleton();
        settings.setLabName(nullToEmpty(request.labName()));
        settings.setResponsibleName(nullToEmpty(request.responsibleName()));
        settings.setResponsibleRegistration(nullToEmpty(request.responsibleRegistration()));
        settings.setAddress(nullToEmpty(request.address()));
        settings.setPhone(nullToEmpty(request.phone()));
        settings.setEmail(nullToEmpty(request.email()));
        // Campos institucionais V10 — permanecem null se nao vierem no payload
        settings.setCnpj(cnpj);
        settings.setCnes(cnes);
        settings.setRegistrationBody(trimOrNull(request.registrationBody()));
        settings.setResponsibleCpf(trimOrNull(request.responsibleCpf()));
        settings.setTechnicalDirectorName(trimOrNull(request.technicalDirectorName()));
        settings.setTechnicalDirectorCpf(trimOrNull(request.technicalDirectorCpf()));
        settings.setTechnicalDirectorReg(trimOrNull(request.technicalDirectorReg()));
        settings.setWebsite(trimOrNull(request.website()));
        settings.setSanitaryLicense(trimOrNull(request.sanitaryLicense()));
        return toResponse(labSettingsRepository.save(settings));
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    @Transactional(readOnly = true)
    public List<LabReportEmailResponse> listEmails() {
        return labReportEmailRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LabReportEmail> activeRecipients() {
        return labReportEmailRepository.findByIsActiveTrueOrderByEmailAsc();
    }

    @Transactional
    public LabReportEmailResponse addEmail(LabReportEmailRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new BusinessException("E-mail obrigatório.");
        }
        String normalized = request.email().trim().toLowerCase();
        labReportEmailRepository.findAll().stream()
            .filter(entry -> entry.getEmail().equalsIgnoreCase(normalized))
            .findFirst()
            .ifPresent(existing -> {
                throw new BusinessException("E-mail já cadastrado.");
            });
        LabReportEmail entity = LabReportEmail.builder()
            .email(normalized)
            .name(request.name() != null ? request.name() : "")
            .isActive(request.isActive() == null ? Boolean.TRUE : request.isActive())
            .build();
        return toResponse(labReportEmailRepository.save(entity));
    }

    @Transactional
    public void removeEmail(UUID id) {
        if (!labReportEmailRepository.existsById(id)) {
            throw new ResourceNotFoundException("E-mail não encontrado.");
        }
        labReportEmailRepository.deleteById(id);
    }

    @Transactional
    public LabReportEmailResponse setEmailActive(UUID id, boolean active) {
        LabReportEmail entity = labReportEmailRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("E-mail não encontrado."));
        entity.setIsActive(active);
        return toResponse(labReportEmailRepository.save(entity));
    }

    private LabSettingsResponse toResponse(LabSettings settings) {
        return new LabSettingsResponse(
            settings.getLabName(),
            settings.getResponsibleName(),
            settings.getResponsibleRegistration(),
            settings.getAddress(),
            settings.getPhone(),
            settings.getEmail(),
            settings.getCnpj(),
            settings.getCnes(),
            settings.getRegistrationBody(),
            settings.getResponsibleCpf(),
            settings.getTechnicalDirectorName(),
            settings.getTechnicalDirectorCpf(),
            settings.getTechnicalDirectorReg(),
            settings.getWebsite(),
            settings.getSanitaryLicense()
        );
    }

    private LabReportEmailResponse toResponse(LabReportEmail entity) {
        return new LabReportEmailResponse(
            entity.getId(),
            entity.getEmail(),
            entity.getName(),
            entity.getIsActive()
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
