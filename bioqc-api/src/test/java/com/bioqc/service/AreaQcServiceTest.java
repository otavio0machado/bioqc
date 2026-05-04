package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bioqc.dto.request.AreaQcMeasurementRequest;
import com.bioqc.dto.response.AreaQcMeasurementResponse;
import com.bioqc.entity.AreaQcMeasurement;
import com.bioqc.entity.AreaQcParameter;
import com.bioqc.exception.BusinessException;
import com.bioqc.repository.AreaQcMeasurementRepository;
import com.bioqc.repository.AreaQcParameterRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AreaQcServiceTest {

    private AreaQcService areaQcService;

    @Mock
    private AreaQcParameterRepository areaQcParameterRepository;

    @Mock
    private AreaQcMeasurementRepository areaQcMeasurementRepository;

    @BeforeEach
    void setUp() {
        areaQcService = new AreaQcService(areaQcParameterRepository, areaQcMeasurementRepository);
    }

    @Test
    @DisplayName("deve registrar medição com parâmetro explícito e devolver rastreabilidade do parâmetro usado")
    void shouldCreateMeasurementWithExplicitParameterAndTraceability() {
        UUID parameterId = UUID.randomUUID();
        AreaQcParameter parameter = parameter(
            parameterId,
            "imunologia",
            "HIV",
            "EQ-1",
            "L1",
            "N1",
            "INTERVALO",
            1D,
            0.9D,
            1.1D,
            null
        );
        AreaQcMeasurementRequest request = new AreaQcMeasurementRequest(
            LocalDate.of(2026, 4, 4),
            "HIV",
            1.0D,
            parameterId,
            null,
            null,
            null,
            "Controle nominal"
        );

        when(areaQcParameterRepository.findById(parameterId)).thenReturn(Optional.of(parameter));
        when(areaQcMeasurementRepository.save(any(AreaQcMeasurement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AreaQcMeasurementResponse response = areaQcService.createMeasurement("imunologia", request);

        assertThat(response.parameterId()).isEqualTo(parameterId);
        assertThat(response.parameterEquipamento()).isEqualTo("EQ-1");
        assertThat(response.parameterLoteControle()).isEqualTo("L1");
        assertThat(response.parameterNivelControle()).isEqualTo("N1");
        assertThat(response.status()).isEqualTo("APROVADO");
        verify(areaQcMeasurementRepository).save(any(AreaQcMeasurement.class));
    }

    @Test
    @DisplayName("deve bloquear ambiguidade quando mais de um parâmetro compatível existe para a medição")
    void shouldRejectMeasurementWhenMultipleCompatibleParametersExist() {
        AreaQcParameter first = parameter(
            UUID.randomUUID(),
            "imunologia",
            "HIV",
            null,
            null,
            null,
            "INTERVALO",
            1D,
            0.9D,
            1.1D,
            null
        );
        AreaQcParameter second = parameter(
            UUID.randomUUID(),
            "imunologia",
            "HIV",
            null,
            null,
            null,
            "INTERVALO",
            1D,
            0.8D,
            1.2D,
            null
        );

        when(areaQcParameterRepository.findByAreaAndAnalitoIgnoreCaseAndIsActiveTrueOrderByCreatedAtDesc("imunologia", "HIV"))
            .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> areaQcService.createMeasurement(
            "imunologia",
            new AreaQcMeasurementRequest(LocalDate.of(2026, 4, 4), "HIV", 1.0D, null, null, null, null, null)
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Mais de um parâmetro ativo é compatível");
    }

    @Test
    @DisplayName("deve resolver parâmetro único por contexto e reprovar medição fora da faixa")
    void shouldResolveUniqueParameterByContextAndRejectOutOfRangeMeasurement() {
        AreaQcParameter eq1 = parameter(
            UUID.randomUUID(),
            "imunologia",
            "HIV",
            "EQ-1",
            null,
            null,
            "INTERVALO",
            1D,
            0.9D,
            1.1D,
            null
        );
        AreaQcParameter eq2 = parameter(
            UUID.randomUUID(),
            "imunologia",
            "HIV",
            "EQ-2",
            null,
            null,
            "INTERVALO",
            1D,
            0.8D,
            1.2D,
            null
        );

        when(areaQcParameterRepository.findByAreaAndAnalitoIgnoreCaseAndIsActiveTrueOrderByCreatedAtDesc("imunologia", "HIV"))
            .thenReturn(List.of(eq1, eq2));
        when(areaQcMeasurementRepository.save(any(AreaQcMeasurement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AreaQcMeasurementResponse response = areaQcService.createMeasurement(
            "imunologia",
            new AreaQcMeasurementRequest(LocalDate.of(2026, 4, 4), "HIV", 1.25D, null, "EQ-2", null, null, null)
        );

        assertThat(response.parameterId()).isEqualTo(eq2.getId());
        assertThat(response.status()).isEqualTo("REPROVADO");
        assertThat(response.parameterEquipamento()).isEqualTo("EQ-2");
    }

    @Test
    @DisplayName("deve bloquear parâmetro explícito incompatível com o contexto informado")
    void shouldRejectExplicitParameterWhenContextIsIncompatible() {
        UUID parameterId = UUID.randomUUID();
        AreaQcParameter parameter = parameter(
            parameterId,
            "imunologia",
            "HIV",
            "EQ-1",
            "L1",
            "N1",
            "INTERVALO",
            1D,
            0.9D,
            1.1D,
            null
        );

        when(areaQcParameterRepository.findById(parameterId)).thenReturn(Optional.of(parameter));

        assertThatThrownBy(() -> areaQcService.createMeasurement(
            "imunologia",
            new AreaQcMeasurementRequest(LocalDate.of(2026, 4, 4), "HIV", 1.0D, parameterId, "EQ-2", null, null, null)
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("não é compatível");
    }

    private AreaQcParameter parameter(
        UUID id,
        String area,
        String analito,
        String equipamento,
        String lote,
        String nivel,
        String modo,
        double alvo,
        double min,
        double max,
        Double tolerancia
    ) {
        return AreaQcParameter.builder()
            .id(id)
            .area(area)
            .analito(analito)
            .equipamento(equipamento)
            .loteControle(lote)
            .nivelControle(nivel)
            .modo(modo)
            .alvoValor(alvo)
            .minValor(min)
            .maxValor(max)
            .toleranciaPercentual(tolerancia)
            .isActive(Boolean.TRUE)
            .build();
    }
}
