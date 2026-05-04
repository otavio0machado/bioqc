package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bioqc.dto.request.HematologyMeasurementRequest;
import com.bioqc.dto.request.HematologyParameterRequest;
import com.bioqc.dto.response.HematologyMeasurementResponse;
import com.bioqc.dto.response.HematologyParameterResponse;
import com.bioqc.entity.HematologyQcMeasurement;
import com.bioqc.entity.HematologyQcParameter;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.HematologyBioRecordRepository;
import com.bioqc.repository.HematologyQcMeasurementRepository;
import com.bioqc.repository.HematologyQcParameterRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class HematologyQcServiceTest {

    @Mock
    private HematologyQcParameterRepository parameterRepository;
    @Mock
    private HematologyQcMeasurementRepository measurementRepository;
    @Mock
    private HematologyBioRecordRepository bioRecordRepository;

    @InjectMocks
    private HematologyQcService service;

    private HematologyQcParameter sampleParameter;
    private final UUID parameterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sampleParameter = HematologyQcParameter.builder()
            .id(parameterId)
            .analito("RBC")
            .equipamento("Sysmex XN-1000")
            .loteControle("LOTE-H01")
            .nivelControle("Normal")
            .modo("INTERVALO")
            .alvoValor(4.5)
            .minValor(4.0)
            .maxValor(5.0)
            .toleranciaPercentual(0.0)
            .isActive(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Nested
    class GetParameters {

        @Test
        void returnsAllActiveParametersAsDtos_whenNoAnalitoFilter() {
            when(parameterRepository.findByIsActiveTrue()).thenReturn(List.of(sampleParameter));

            List<HematologyParameterResponse> result = service.getParameters(null);

            assertThat(result).hasSize(1);
            HematologyParameterResponse dto = result.get(0);
            assertThat(dto.id()).isEqualTo(parameterId);
            assertThat(dto.analito()).isEqualTo("RBC");
            assertThat(dto.equipamento()).isEqualTo("Sysmex XN-1000");
            assertThat(dto.loteControle()).isEqualTo("LOTE-H01");
            assertThat(dto.modo()).isEqualTo("INTERVALO");
        }

        @Test
        void filtersParametersByAnalito() {
            when(parameterRepository.findByAnalitoAndIsActiveTrue("RBC")).thenReturn(List.of(sampleParameter));

            List<HematologyParameterResponse> result = service.getParameters("RBC");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).analito()).isEqualTo("RBC");
        }

        @Test
        void returnsEmptyListWhenNoParametersExist() {
            when(parameterRepository.findByIsActiveTrue()).thenReturn(List.of());

            assertThat(service.getParameters(null)).isEmpty();
        }
    }

    @Nested
    class CreateParameter {

        @Test
        void createsParameterAndReturnsDtoWithAllFields() {
            HematologyParameterRequest request = new HematologyParameterRequest(
                "HGB", "Sysmex XN-1000", "LOTE-H02", "Alto", "PERCENTUAL",
                13.5, 0.0, 0.0, 5.0
            );

            HematologyQcParameter saved = HematologyQcParameter.builder()
                .id(UUID.randomUUID())
                .analito("HGB")
                .equipamento("Sysmex XN-1000")
                .loteControle("LOTE-H02")
                .nivelControle("Alto")
                .modo("PERCENTUAL")
                .alvoValor(13.5)
                .minValor(0.0)
                .maxValor(0.0)
                .toleranciaPercentual(5.0)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

            when(parameterRepository.save(any())).thenReturn(saved);

            HematologyParameterResponse result = service.createParameter(request);

            assertThat(result.analito()).isEqualTo("HGB");
            assertThat(result.modo()).isEqualTo("PERCENTUAL");
            assertThat(result.toleranciaPercentual()).isEqualTo(5.0);
            assertThat(result.isActive()).isTrue();
        }
    }

    @Nested
    class CreateMeasurement {

        @Test
        void approvesMeasurementWithinInterval_andReturnsDtoWithParameterData() {
            when(parameterRepository.findById(parameterId)).thenReturn(Optional.of(sampleParameter));

            HematologyMeasurementRequest request = new HematologyMeasurementRequest(
                parameterId, LocalDate.of(2026, 4, 4), "RBC", 4.5, "Dentro da faixa"
            );

            HematologyQcMeasurement savedEntity = HematologyQcMeasurement.builder()
                .id(UUID.randomUUID())
                .parameter(sampleParameter)
                .dataMedicao(request.dataMedicao())
                .analito("RBC")
                .valorMedido(4.5)
                .modoUsado("INTERVALO")
                .minAplicado(4.0)
                .maxAplicado(5.0)
                .status("APROVADO")
                .observacao("Dentro da faixa")
                .createdAt(Instant.now())
                .build();

            when(measurementRepository.save(any())).thenReturn(savedEntity);

            HematologyMeasurementResponse result = service.createMeasurement(request);

            assertThat(result.status()).isEqualTo("APROVADO");
            assertThat(result.parameterId()).isEqualTo(parameterId);
            assertThat(result.parameterEquipamento()).isEqualTo("Sysmex XN-1000");
            assertThat(result.parameterLoteControle()).isEqualTo("LOTE-H01");
            assertThat(result.parameterNivelControle()).isEqualTo("Normal");
            assertThat(result.modoUsado()).isEqualTo("INTERVALO");
            assertThat(result.minAplicado()).isEqualTo(4.0);
            assertThat(result.maxAplicado()).isEqualTo(5.0);
        }

        @Test
        void rejectsMeasurementOutsideInterval() {
            when(parameterRepository.findById(parameterId)).thenReturn(Optional.of(sampleParameter));

            HematologyMeasurementRequest request = new HematologyMeasurementRequest(
                parameterId, LocalDate.of(2026, 4, 4), "RBC", 5.5, null
            );

            HematologyQcMeasurement savedEntity = HematologyQcMeasurement.builder()
                .id(UUID.randomUUID())
                .parameter(sampleParameter)
                .dataMedicao(request.dataMedicao())
                .analito("RBC")
                .valorMedido(5.5)
                .modoUsado("INTERVALO")
                .minAplicado(4.0)
                .maxAplicado(5.0)
                .status("REPROVADO")
                .createdAt(Instant.now())
                .build();

            when(measurementRepository.save(any())).thenReturn(savedEntity);

            HematologyMeasurementResponse result = service.createMeasurement(request);

            assertThat(result.status()).isEqualTo("REPROVADO");
        }

        @Test
        void appliesPercentualModeCorrectly() {
            HematologyQcParameter pctParameter = HematologyQcParameter.builder()
                .id(parameterId)
                .analito("HGB")
                .equipamento("Sysmex XN-1000")
                .loteControle("LOTE-H01")
                .nivelControle("Normal")
                .modo("PERCENTUAL")
                .alvoValor(13.0)
                .toleranciaPercentual(5.0)
                .minValor(0.0)
                .maxValor(0.0)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

            when(parameterRepository.findById(parameterId)).thenReturn(Optional.of(pctParameter));

            HematologyMeasurementRequest request = new HematologyMeasurementRequest(
                parameterId, LocalDate.of(2026, 4, 4), "HGB", 13.0, null
            );

            ArgumentCaptor<HematologyQcMeasurement> captor = ArgumentCaptor.forClass(HematologyQcMeasurement.class);

            HematologyQcMeasurement savedEntity = HematologyQcMeasurement.builder()
                .id(UUID.randomUUID())
                .parameter(pctParameter)
                .dataMedicao(request.dataMedicao())
                .analito("HGB")
                .valorMedido(13.0)
                .modoUsado("PERCENTUAL")
                .minAplicado(12.35)
                .maxAplicado(13.65)
                .status("APROVADO")
                .createdAt(Instant.now())
                .build();

            when(measurementRepository.save(any())).thenReturn(savedEntity);

            HematologyMeasurementResponse result = service.createMeasurement(request);

            verify(measurementRepository).save(captor.capture());
            HematologyQcMeasurement captured = captor.getValue();
            assertThat(captured.getModoUsado()).isEqualTo("PERCENTUAL");
            // alvo 13.0 ± 5% => min 12.35, max 13.65
            assertThat(captured.getMinAplicado()).isEqualTo(13.0 - (13.0 * 5.0 / 100.0));
            assertThat(captured.getMaxAplicado()).isEqualTo(13.0 + (13.0 * 5.0 / 100.0));
            assertThat(result.status()).isEqualTo("APROVADO");
        }

        @Test
        void throwsWhenParameterNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(parameterRepository.findById(unknownId)).thenReturn(Optional.empty());

            HematologyMeasurementRequest request = new HematologyMeasurementRequest(
                unknownId, LocalDate.now(), "RBC", 4.5, null
            );

            assertThatThrownBy(() -> service.createMeasurement(request))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void responseMeasurementIncludesParameterTraceability() {
            when(parameterRepository.findById(parameterId)).thenReturn(Optional.of(sampleParameter));

            HematologyMeasurementRequest request = new HematologyMeasurementRequest(
                parameterId, LocalDate.of(2026, 4, 4), "RBC", 4.5, null
            );

            HematologyQcMeasurement savedEntity = HematologyQcMeasurement.builder()
                .id(UUID.randomUUID())
                .parameter(sampleParameter)
                .dataMedicao(request.dataMedicao())
                .analito("RBC")
                .valorMedido(4.5)
                .modoUsado("INTERVALO")
                .minAplicado(4.0)
                .maxAplicado(5.0)
                .status("APROVADO")
                .createdAt(Instant.now())
                .build();

            when(measurementRepository.save(any())).thenReturn(savedEntity);

            HematologyMeasurementResponse result = service.createMeasurement(request);

            // Key assertion: parameter traceability fields are present
            assertThat(result.parameterId()).isNotNull();
            assertThat(result.parameterEquipamento()).isNotNull();
            assertThat(result.parameterLoteControle()).isNotNull();
            assertThat(result.parameterNivelControle()).isNotNull();
        }
    }

    @Nested
    class GetMeasurements {

        @Test
        void returnsAllMeasurementsAsDtos_whenNoParameterIdFilter() {
            HematologyQcMeasurement m = HematologyQcMeasurement.builder()
                .id(UUID.randomUUID())
                .parameter(sampleParameter)
                .dataMedicao(LocalDate.of(2026, 4, 4))
                .analito("RBC")
                .valorMedido(4.5)
                .modoUsado("INTERVALO")
                .minAplicado(4.0)
                .maxAplicado(5.0)
                .status("APROVADO")
                .createdAt(Instant.now())
                .build();

            when(measurementRepository.findAll(any(Sort.class))).thenReturn(List.of(m));

            List<HematologyMeasurementResponse> result = service.getMeasurements(null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).parameterId()).isEqualTo(parameterId);
            assertThat(result.get(0).parameterEquipamento()).isEqualTo("Sysmex XN-1000");
        }

        @Test
        void filtersMeasurementsByParameterId() {
            HematologyQcMeasurement m = HematologyQcMeasurement.builder()
                .id(UUID.randomUUID())
                .parameter(sampleParameter)
                .dataMedicao(LocalDate.of(2026, 4, 4))
                .analito("RBC")
                .valorMedido(4.5)
                .modoUsado("INTERVALO")
                .minAplicado(4.0)
                .maxAplicado(5.0)
                .status("APROVADO")
                .createdAt(Instant.now())
                .build();

            when(measurementRepository.findByParameterIdOrderByDataMedicaoDesc(parameterId))
                .thenReturn(List.of(m));

            List<HematologyMeasurementResponse> result = service.getMeasurements(parameterId);

            assertThat(result).hasSize(1);
        }
    }
}
