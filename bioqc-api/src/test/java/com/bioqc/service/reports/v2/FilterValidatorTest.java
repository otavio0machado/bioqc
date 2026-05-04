package com.bioqc.service.reports.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.catalog.ReportFilterField;
import com.bioqc.service.reports.v2.catalog.ReportFilterFieldType;
import com.bioqc.service.reports.v2.catalog.ReportFilterSpec;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FilterValidatorTest {

    private final FilterValidator validator = new FilterValidator();

    @Test
    @DisplayName("campo obrigatorio ausente dispara InvalidFilterException")
    void requiredMissingFails() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("area", ReportFilterFieldType.STRING_ENUM, true,
                List.of("bioquimica", "hematologia"), "Area", null)
        ));
        assertThatThrownBy(() -> validator.validate(spec, Map.of()))
            .isInstanceOf(InvalidFilterException.class)
            .hasMessageContaining("area");
    }

    @Test
    @DisplayName("STRING_ENUM fora de allowedValues falha")
    void enumOutOfRangeFails() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("area", ReportFilterFieldType.STRING_ENUM, true,
                List.of("bioquimica", "hematologia"), "Area", null)
        ));
        assertThatThrownBy(() -> validator.validate(spec, Map.of("area", "urologia")))
            .isInstanceOf(InvalidFilterException.class);
    }

    @Test
    @DisplayName("INTEGER aceita string numerica e valida range mes/ano")
    void integerRangeValidation() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("month", ReportFilterFieldType.INTEGER, false, null, "Mes", null),
            new ReportFilterField("year", ReportFilterFieldType.INTEGER, false, null, "Ano", null)
        ));
        assertThatCode(() -> validator.validate(spec, Map.of("month", "5", "year", 2026))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(spec, Map.of("month", 13)))
            .isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> validator.validate(spec, Map.of("year", 1999)))
            .isInstanceOf(InvalidFilterException.class);
    }

    @Test
    @DisplayName("DATE aceita string ISO e rejeita invalido")
    void dateValidation() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("dateFrom", ReportFilterFieldType.DATE, false, null, "De", null)
        ));
        assertThatCode(() -> validator.validate(spec, Map.of("dateFrom", "2026-04-20"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(spec, Map.of("dateFrom", "hoje")))
            .isInstanceOf(InvalidFilterException.class);
    }

    @Test
    @DisplayName("UUID_LIST aceita lista de strings e rejeita UUID invalido")
    void uuidListValidation() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("examIds", ReportFilterFieldType.UUID_LIST, false, null, "Exames", null)
        ));
        String validId = UUID.randomUUID().toString();
        assertThatCode(() -> validator.validate(spec, Map.of("examIds", List.of(validId)))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(spec, Map.of("examIds", List.of("nao-e-uuid"))))
            .isInstanceOf(InvalidFilterException.class);
    }

    @Test
    @DisplayName("BOOLEAN aceita Boolean e String, rejeita numero")
    void booleanValidation() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("flag", ReportFilterFieldType.BOOLEAN, false, null, "Flag", null)
        ));
        assertThatCode(() -> validator.validate(spec, Map.of("flag", true))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(spec, Map.of("flag", "false"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(spec, Map.of("flag", 1)))
            .isInstanceOf(InvalidFilterException.class);
    }

    @Test
    @DisplayName("spec aceita payload valido completo")
    void validPayloadPasses() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("area", ReportFilterFieldType.STRING_ENUM, true,
                List.of("bioquimica"), "Area", null),
            new ReportFilterField("periodType", ReportFilterFieldType.STRING_ENUM, true,
                List.of("current-month", "specific-month"), "Periodo", null)
        ));
        assertThatCode(() -> validator.validate(spec, Map.of(
            "area", "bioquimica",
            "periodType", "current-month"
        ))).doesNotThrowAnyException();
    }

    // ---------- Ressalva 4: examIds so em bioquimica ----------

    @Test
    @DisplayName("examIds com area=hematologia dispara 422 (Ressalva 4)")
    void examIdsComAreaHematologiaLanca422() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("area", ReportFilterFieldType.STRING_ENUM, true,
                List.of("bioquimica", "hematologia"), "Area", null),
            new ReportFilterField("examIds", ReportFilterFieldType.UUID_LIST, false,
                null, "Exames", null)
        ));
        String validId = UUID.randomUUID().toString();
        assertThatThrownBy(() -> validator.validate(spec, Map.of(
            "area", "hematologia",
            "examIds", List.of(validId)
        )))
            .isInstanceOf(InvalidFilterException.class)
            .hasMessageContaining("examIds")
            .hasMessageContaining("bioquimica");
    }

    @Test
    @DisplayName("examIds com area=bioquimica e valido")
    void examIdsComAreaBioquimicaValido() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("area", ReportFilterFieldType.STRING_ENUM, true,
                List.of("bioquimica", "hematologia"), "Area", null),
            new ReportFilterField("examIds", ReportFilterFieldType.UUID_LIST, false,
                null, "Exames", null)
        ));
        String validId = UUID.randomUUID().toString();
        assertThatCode(() -> validator.validate(spec, Map.of(
            "area", "bioquimica",
            "examIds", List.of(validId)
        ))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("examIds ausente/vazio com area=hematologia e valido")
    void examIdsVazioComAreaHematologiaValido() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("area", ReportFilterFieldType.STRING_ENUM, true,
                List.of("bioquimica", "hematologia"), "Area", null),
            new ReportFilterField("examIds", ReportFilterFieldType.UUID_LIST, false,
                null, "Exames", null)
        ));
        // examIds ausente
        assertThatCode(() -> validator.validate(spec, Map.of(
            "area", "hematologia"
        ))).doesNotThrowAnyException();
        // examIds presente mas lista vazia
        assertThatCode(() -> validator.validate(spec, Map.of(
            "area", "hematologia",
            "examIds", List.of()
        ))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("STRING_ENUM_MULTI aceita valores da allowedValues")
    void stringEnumMultiAcceptsValid() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("rules", ReportFilterFieldType.STRING_ENUM_MULTI, false,
                List.of("1-2s", "1-3s", "R-4s"), "Regras", null)
        ));
        assertThatCode(() -> validator.validate(spec, Map.of(
            "rules", List.of("1-2s", "R-4s")
        ))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("STRING_ENUM_MULTI rejeita valor fora do allowedValues")
    void stringEnumMultiRejectsInvalid() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("rules", ReportFilterFieldType.STRING_ENUM_MULTI, false,
                List.of("1-2s", "1-3s"), "Regras", null)
        ));
        assertThatThrownBy(() -> validator.validate(spec, Map.of(
            "rules", List.of("BOGUS")
        ))).isInstanceOf(InvalidFilterException.class).hasMessageContaining("BOGUS");
    }

    @Test
    @DisplayName("STRING aceita string nao-vazia")
    void stringAcceptsNonBlank() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("equipment", ReportFilterFieldType.STRING, false, null, "Equip", null)
        ));
        assertThatCode(() -> validator.validate(spec, Map.of(
            "equipment", "Vitros"
        ))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REGULATORIO_PACOTE rejeita includeAiCommentary=true")
    void regulatorioPacoteBlocksAi() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("periodType", ReportFilterFieldType.STRING_ENUM, true,
                List.of("current-month"), "Periodo", null)
        ));
        assertThatThrownBy(() -> validator.validate(spec,
            Map.of("periodType", "current-month", "includeAiCommentary", true),
            ReportCode.REGULATORIO_PACOTE))
            .isInstanceOf(InvalidFilterException.class)
            .hasMessageContaining("REGULATORIO_PACOTE");
    }

    @Test
    @DisplayName("periodType=specific-month exige month e year")
    void specificMonthRequiresMonthAndYear() {
        ReportFilterSpec spec = periodSpec();
        assertThatThrownBy(() -> validator.validate(spec, Map.of("periodType", "specific-month")))
            .isInstanceOf(InvalidFilterException.class)
            .hasMessageContaining("month")
            .hasMessageContaining("year");
    }

    @Test
    @DisplayName("periodType=date-range exige datas coerentes")
    void dateRangeRequiresOrderedDates() {
        ReportFilterSpec spec = periodSpec();
        assertThatThrownBy(() -> validator.validate(spec, Map.of(
            "periodType", "date-range",
            "dateFrom", "2026-04-30",
            "dateTo", "2026-04-01"
        )))
            .isInstanceOf(InvalidFilterException.class)
            .hasMessageContaining("dateFrom");
    }

    @Test
    @DisplayName("agrega varias violacoes em uma unica excecao")
    void aggregatesViolations() {
        ReportFilterSpec spec = new ReportFilterSpec(List.of(
            new ReportFilterField("area", ReportFilterFieldType.STRING_ENUM, true,
                List.of("bioquimica"), "Area", null),
            new ReportFilterField("month", ReportFilterFieldType.INTEGER, true, null, "Mes", null)
        ));
        try {
            validator.validate(spec, Map.of("month", 99));
        } catch (InvalidFilterException ex) {
            assertThat(ex.getViolations()).hasSizeGreaterThanOrEqualTo(2);
            return;
        }
        throw new AssertionError("Esperava InvalidFilterException");
    }

    private ReportFilterSpec periodSpec() {
        return new ReportFilterSpec(List.of(
            new ReportFilterField("periodType", ReportFilterFieldType.STRING_ENUM, true,
                List.of("current-month", "specific-month", "year", "date-range"), "Periodo", null),
            new ReportFilterField("month", ReportFilterFieldType.INTEGER, false, null, "Mes", null),
            new ReportFilterField("year", ReportFilterFieldType.INTEGER, false, null, "Ano", null),
            new ReportFilterField("dateFrom", ReportFilterFieldType.DATE, false, null, "De", null),
            new ReportFilterField("dateTo", ReportFilterFieldType.DATE, false, null, "Ate", null)
        ));
    }
}
