package com.bioqc.dto.response;

/**
 * Resposta publica das configuracoes do laboratorio. Expoe todos os campos
 * institucionais necessarios para o frontend montar o cabecalho dos laudos.
 */
public record LabSettingsResponse(
    String labName,
    String responsibleName,
    String responsibleRegistration,
    String address,
    String phone,
    String email,
    String cnpj,
    String cnes,
    String registrationBody,
    String responsibleCpf,
    String technicalDirectorName,
    String technicalDirectorCpf,
    String technicalDirectorReg,
    String website,
    String sanitaryLicense
) {
}
