package com.bioqc.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record HematologyBioRequest(
    @NotNull LocalDate dataBio,
    LocalDate dataPad,
    String registroBio,
    String registroPad,
    String modoCi,
    Double bioHemacias,
    Double bioHematocrito,
    Double bioHemoglobina,
    Double bioLeucocitos,
    Double bioPlaquetas,
    Double bioRdw,
    Double bioVpm,
    Double padHemacias,
    Double padHematocrito,
    Double padHemoglobina,
    Double padLeucocitos,
    Double padPlaquetas,
    Double padRdw,
    Double padVpm,
    Double ciMinHemacias,
    Double ciMaxHemacias,
    Double ciMinHematocrito,
    Double ciMaxHematocrito,
    Double ciMinHemoglobina,
    Double ciMaxHemoglobina,
    Double ciMinLeucocitos,
    Double ciMaxLeucocitos,
    Double ciMinPlaquetas,
    Double ciMaxPlaquetas,
    Double ciMinRdw,
    Double ciMaxRdw,
    Double ciMinVpm,
    Double ciMaxVpm,
    Double ciPctHemacias,
    Double ciPctHematocrito,
    Double ciPctHemoglobina,
    Double ciPctLeucocitos,
    Double ciPctPlaquetas,
    Double ciPctRdw,
    Double ciPctVpm
) {
}
