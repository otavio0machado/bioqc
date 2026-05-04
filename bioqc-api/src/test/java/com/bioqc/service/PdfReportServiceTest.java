package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bioqc.entity.HematologyBioRecord;
import com.bioqc.entity.HematologyQcMeasurement;
import com.bioqc.entity.PostCalibrationRecord;
import com.bioqc.entity.QcRecord;
import com.bioqc.entity.ReagentLot;
import com.bioqc.repository.AreaQcMeasurementRepository;
import com.bioqc.repository.HematologyBioRecordRepository;
import com.bioqc.repository.HematologyQcMeasurementRepository;
import com.bioqc.repository.LabSettingsRepository;
import com.bioqc.repository.PostCalibrationRecordRepository;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.repository.ReagentLotRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PdfReportServiceTest {

    private PdfReportService pdfReportService;

    @Mock
    private QcRecordRepository qcRecordRepository;

    @Mock
    private PostCalibrationRecordRepository postCalibrationRecordRepository;

    @Mock
    private ReagentLotRepository reagentLotRepository;

    @Mock
    private AreaQcMeasurementRepository areaQcMeasurementRepository;

    @Mock
    private HematologyQcMeasurementRepository hematologyQcMeasurementRepository;

    @Mock
    private HematologyBioRecordRepository hematologyBioRecordRepository;

    @Mock
    private LabSettingsRepository labSettingsRepository;

    private final ReportNumberingService reportNumberingService = new ReportNumberingService(null, null) {
        @Override
        public String reserveNextNumber() {
            return "BIO-202604-000001";
        }
        @Override
        public com.bioqc.entity.ReportAuditLog registerGeneration(
            String reportNumber, String area, String format, String periodLabel, byte[] content, java.util.UUID generatedBy) {
            return null;
        }
    };

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
            .when(labSettingsRepository.findSingleton())
            .thenReturn(java.util.Optional.empty());
        pdfReportService = new PdfReportService(
            qcRecordRepository,
            postCalibrationRecordRepository,
            reagentLotRepository,
            areaQcMeasurementRepository,
            hematologyQcMeasurementRepository,
            hematologyBioRecordRepository,
            labSettingsRepository,
            reportNumberingService
        );
    }

    @Test
    @DisplayName("deve gerar PDF de bioquímica no mês corrido com registros e retornar bytes válidos começando com %PDF")
    void generateQcPdf_bioquimica_mesCorrido_retornaPdfValido() {
        QcRecord record = QcRecord.builder()
            .id(UUID.randomUUID())
            .examName("Glicose")
            .area("bioquimica")
            .date(LocalDate.now())
            .level("N1")
            .lotNumber("L001")
            .value(95.0)
            .targetValue(100.0)
            .targetSd(5.0)
            .cv(5.26)
            .cvLimit(10.0)
            .zScore(1.0)
            .status("APROVADO")
            .needsCalibration(false)
            .build();

        when(qcRecordRepository.findByAreaAndDateRange(eq("bioquimica"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(record));
        when(postCalibrationRecordRepository.findByQcRecordAreaAndDateRange(eq("bioquimica"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        byte[] pdf = pdfReportService.generateQcPdf("bioquimica", "current-month", null, null);

        assertThat(pdf).isNotNull().isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        verify(qcRecordRepository).findByAreaAndDateRange(eq("bioquimica"), any(LocalDate.class), any(LocalDate.class));
        verify(postCalibrationRecordRepository).findByQcRecordAreaAndDateRange(eq("bioquimica"), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("deve gerar PDF de hematologia com medições e retornar bytes válidos começando com %PDF")
    void generateQcPdf_hematologia_retornaPdfValido() {
        HematologyQcMeasurement measurement = HematologyQcMeasurement.builder()
            .id(UUID.randomUUID())
            .dataMedicao(LocalDate.now())
            .analito("WBC")
            .valorMedido(7.5)
            .modoUsado("auto")
            .minAplicado(4.0)
            .maxAplicado(11.0)
            .status("APROVADO")
            .build();

        HematologyBioRecord bioRecord = HematologyBioRecord.builder()
            .id(UUID.randomUUID())
            .dataBio(LocalDate.now())
            .modoCi("bio")
            .bioHemacias(4.5)
            .bioHemoglobina(13.0)
            .bioLeucocitos(7.5)
            .bioPlaquetas(250.0)
            .bioRdw(12.0)
            .bioVpm(8.5)
            .build();

        when(hematologyQcMeasurementRepository.findByDataMedicaoBetweenOrderByDataMedicaoDesc(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(measurement));
        when(hematologyBioRecordRepository.findByDataBioBetweenOrderByDataBioDesc(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(bioRecord));

        byte[] pdf = pdfReportService.generateQcPdf("hematologia", "current-month", null, null);

        assertThat(pdf).isNotNull().isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        verify(hematologyQcMeasurementRepository).findByDataMedicaoBetweenOrderByDataMedicaoDesc(any(LocalDate.class), any(LocalDate.class));
        verify(hematologyBioRecordRepository).findByDataBioBetweenOrderByDataBioDesc(any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("deve gerar PDF sem erro quando não há registros de bioquímica no período")
    void generateQcPdf_semRegistros_retornaPdfVazio() {
        when(qcRecordRepository.findByAreaAndDateRange(eq("bioquimica"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());
        when(postCalibrationRecordRepository.findByQcRecordAreaAndDateRange(eq("bioquimica"), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        byte[] pdf = pdfReportService.generateQcPdf("bioquimica", "current-month", null, null);

        assertThat(pdf).isNotNull().isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("deve gerar PDF de reagentes com lotes cadastrados e retornar bytes válidos")
    void generateReagentsPdf_retornaPdfValido() {
        ReagentLot lot = ReagentLot.builder()
            .id(UUID.randomUUID())
            .name("ALT")
            .lotNumber("L100")
            .manufacturer("BioSystems")
            .status("em_estoque")
            .expiryDate(LocalDate.now().plusDays(30))
            .unitsInStock(50)
            .unitsInUse(0)
            .needsStockReview(false)
            .build();

        when(reagentLotRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(lot));

        byte[] pdf = pdfReportService.generateReagentsPdf();

        assertThat(pdf).isNotNull().isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        verify(reagentLotRepository).findAllByOrderByCreatedAtDesc();
    }
}
