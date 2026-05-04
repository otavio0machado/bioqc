package com.bioqc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de atualizacao de {@code LabSettings}. Campos institucionais foram
 * adicionados em V10 para habilitar cabecalho institucional dos laudos V2.
 *
 * <p>O formato do CNPJ e validado via regex simples (XX.XXX.XXX/XXXX-XX).
 * Nao validamos digitos verificadores para nao bloquear testes com CNPJs
 * ficticios — mas o laudo de producao deve ter CNPJ real.
 */
public record LabSettingsRequest(
    @Size(max = 200) String labName,
    @Size(max = 200) String responsibleName,
    @Size(max = 100) String responsibleRegistration,
    @Size(max = 300) String address,
    @Size(max = 50) String phone,
    @Email @Size(max = 200) String email,

    @Pattern(
        regexp = "^$|^\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}$",
        message = "CNPJ deve estar no formato XX.XXX.XXX/XXXX-XX"
    )
    @Size(max = 20)
    String cnpj,

    @Size(max = 20) String cnes,
    @Size(max = 20) String registrationBody,
    @Size(max = 20) String responsibleCpf,
    @Size(max = 200) String technicalDirectorName,
    @Size(max = 20) String technicalDirectorCpf,
    @Size(max = 100) String technicalDirectorReg,
    @Size(max = 200) String website,
    @Size(max = 50) String sanitaryLicense
) {
}
