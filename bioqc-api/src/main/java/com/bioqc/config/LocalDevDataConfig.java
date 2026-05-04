package com.bioqc.config;

import com.bioqc.entity.AreaQcMeasurement;
import com.bioqc.entity.AreaQcParameter;
import com.bioqc.entity.HematologyBioRecord;
import com.bioqc.entity.HematologyQcMeasurement;
import com.bioqc.entity.HematologyQcParameter;
import com.bioqc.entity.QcExam;
import com.bioqc.entity.QcReferenceValue;
import com.bioqc.entity.User;
import com.bioqc.repository.AreaQcMeasurementRepository;
import com.bioqc.repository.AreaQcParameterRepository;
import com.bioqc.repository.HematologyBioRecordRepository;
import com.bioqc.repository.HematologyQcMeasurementRepository;
import com.bioqc.repository.HematologyQcParameterRepository;
import com.bioqc.repository.QcExamRepository;
import com.bioqc.repository.QcReferenceValueRepository;
import com.bioqc.repository.UserRepository;
import java.time.LocalDate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("local")
public class LocalDevDataConfig {

    @Bean
    CommandLineRunner localDevSeeder(
        UserRepository userRepository,
        QcExamRepository qcExamRepository,
        QcReferenceValueRepository qcReferenceValueRepository,
        AreaQcParameterRepository areaQcParameterRepository,
        AreaQcMeasurementRepository areaQcMeasurementRepository,
        HematologyQcParameterRepository hematologyQcParameterRepository,
        HematologyQcMeasurementRepository hematologyQcMeasurementRepository,
        HematologyBioRecordRepository hematologyBioRecordRepository,
        PasswordEncoder passwordEncoder
    ) {
        return args -> {
            seedAdminUser(userRepository, passwordEncoder);
            seedExamAndReference(qcExamRepository, qcReferenceValueRepository);
            seedAreaQcModules(areaQcParameterRepository, areaQcMeasurementRepository);
            seedHematologyModule(
                hematologyQcParameterRepository,
                hematologyQcMeasurementRepository,
                hematologyBioRecordRepository
            );
        };
    }

    private void seedAdminUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        if (userRepository.existsByUsername("admin")) {
            return;
        }

        userRepository.save(User.builder()
            .username("admin")
            .name("Local Admin")
            .passwordHash(passwordEncoder.encode("Demo123!"))
            .role(com.bioqc.entity.Role.ADMIN)
            .permissions(java.util.Set.of())
            .isActive(Boolean.TRUE)
            .build());
    }

    private void seedExamAndReference(
        QcExamRepository qcExamRepository,
        QcReferenceValueRepository qcReferenceValueRepository
    ) {
        QcExam exam = qcExamRepository.findByAreaAndIsActiveTrue("bioquimica")
            .stream()
            .filter(item -> "Glicose".equalsIgnoreCase(item.getName()))
            .findFirst()
            .orElseGet(() -> qcExamRepository.save(QcExam.builder()
                .name("Glicose")
                .area("bioquimica")
                .unit("mg/dL")
                .isActive(Boolean.TRUE)
                .build()));

        boolean hasReference = qcReferenceValueRepository
            .findByExam_NameAndLevelAndIsActiveTrue("Glicose", "Normal")
            .isPresent();

        if (hasReference) {
            return;
        }

        qcReferenceValueRepository.save(QcReferenceValue.builder()
            .exam(exam)
            .name("Controle Glicose N1")
            .level("Normal")
            .lotNumber("LOCAL-001")
            .manufacturer("Seed Local")
            .targetValue(100D)
            .targetSd(5D)
            .cvMaxThreshold(10D)
            .validFrom(LocalDate.now().minusDays(7))
            .validUntil(LocalDate.now().plusMonths(6))
            .isActive(Boolean.TRUE)
            .notes("Carga inicial para ambiente local.")
            .build());
    }

    private void seedAreaQcModules(
        AreaQcParameterRepository areaQcParameterRepository,
        AreaQcMeasurementRepository areaQcMeasurementRepository
    ) {
        seedAreaParameterAndMeasurement(
            areaQcParameterRepository,
            areaQcMeasurementRepository,
            "imunologia",
            "TSH",
            "Cobas e411",
            "IMU-001",
            "N1",
            "INTERVALO",
            2.1,
            1.8,
            2.4,
            null,
            2.14
        );
        seedAreaParameterAndMeasurement(
            areaQcParameterRepository,
            areaQcMeasurementRepository,
            "parasitologia",
            "Contagem de cistos",
            "Microscopia A",
            "PAR-014",
            "N1",
            "PERCENTUAL",
            12.0,
            null,
            null,
            10.0,
            11.8
        );
        seedAreaParameterAndMeasurement(
            areaQcParameterRepository,
            areaQcMeasurementRepository,
            "microbiologia",
            "Turbidez",
            "Vitek 2",
            "MIC-031",
            "N2",
            "INTERVALO",
            0.5,
            0.4,
            0.6,
            null,
            0.52
        );
        seedAreaParameterAndMeasurement(
            areaQcParameterRepository,
            areaQcMeasurementRepository,
            "uroanalise",
            "pH",
            "Urisys 2400",
            "URO-009",
            "N1",
            "INTERVALO",
            6.0,
            5.5,
            6.5,
            null,
            6.1
        );
    }

    private void seedAreaParameterAndMeasurement(
        AreaQcParameterRepository areaQcParameterRepository,
        AreaQcMeasurementRepository areaQcMeasurementRepository,
        String area,
        String analito,
        String equipamento,
        String loteControle,
        String nivelControle,
        String modo,
        Double alvoValor,
        Double minValor,
        Double maxValor,
        Double toleranciaPercentual,
        Double valorMedido
    ) {
        AreaQcParameter parameter = areaQcParameterRepository
            .findByAreaAndAnalitoIgnoreCaseAndIsActiveTrueOrderByCreatedAtDesc(area, analito)
            .stream()
            .findFirst()
            .orElseGet(() -> areaQcParameterRepository.save(AreaQcParameter.builder()
                .area(area)
                .analito(analito)
                .equipamento(equipamento)
                .loteControle(loteControle)
                .nivelControle(nivelControle)
                .modo(modo)
                .alvoValor(alvoValor)
                .minValor(minValor)
                .maxValor(maxValor)
                .toleranciaPercentual(toleranciaPercentual)
                .isActive(Boolean.TRUE)
                .build()));

        boolean hasMeasurement = areaQcMeasurementRepository
            .findByAreaAndAnalitoIgnoreCaseOrderByDataMedicaoDesc(area, analito)
            .stream()
            .findAny()
            .isPresent();

        if (hasMeasurement) {
            return;
        }

        double appliedMin = "PERCENTUAL".equalsIgnoreCase(modo)
            ? alvoValor - (alvoValor * (toleranciaPercentual / 100D))
            : minValor;
        double appliedMax = "PERCENTUAL".equalsIgnoreCase(modo)
            ? alvoValor + (alvoValor * (toleranciaPercentual / 100D))
            : maxValor;

        areaQcMeasurementRepository.save(AreaQcMeasurement.builder()
            .parameter(parameter)
            .area(area)
            .dataMedicao(LocalDate.now().minusDays(1))
            .analito(analito)
            .valorMedido(valorMedido)
            .modoUsado(modo)
            .minAplicado(appliedMin)
            .maxAplicado(appliedMax)
            .status(valorMedido >= appliedMin && valorMedido <= appliedMax ? "APROVADO" : "REPROVADO")
            .observacao("Carga inicial local para validação do módulo especializado.")
            .build());
    }

    private void seedHematologyModule(
        HematologyQcParameterRepository hematologyQcParameterRepository,
        HematologyQcMeasurementRepository hematologyQcMeasurementRepository,
        HematologyBioRecordRepository hematologyBioRecordRepository
    ) {
        HematologyQcParameter parameter = hematologyQcParameterRepository.findByAnalitoAndIsActiveTrue("RBC")
            .stream()
            .findFirst()
            .orElseGet(() -> hematologyQcParameterRepository.save(HematologyQcParameter.builder()
                .analito("RBC")
                .equipamento("XN-1000")
                .loteControle("HEMA-001")
                .nivelControle("N1")
                .modo("INTERVALO")
                .alvoValor(4.8)
                .minValor(4.4)
                .maxValor(5.2)
                .toleranciaPercentual(0D)
                .isActive(Boolean.TRUE)
                .build()));

        if (hematologyQcMeasurementRepository.findByParameterIdOrderByDataMedicaoDesc(parameter.getId()).isEmpty()) {
            hematologyQcMeasurementRepository.save(HematologyQcMeasurement.builder()
                .parameter(parameter)
                .dataMedicao(LocalDate.now().minusDays(1))
                .analito("RBC")
                .valorMedido(4.9)
                .modoUsado("INTERVALO")
                .minAplicado(4.4)
                .maxAplicado(5.2)
                .status("APROVADO")
                .observacao("Registro inicial de hematologia para ambiente local.")
                .build());
        }

        if (!hematologyBioRecordRepository.findAllByOrderByDataBioDesc().isEmpty()) {
            return;
        }

        hematologyBioRecordRepository.save(HematologyBioRecord.builder()
            .dataBio(LocalDate.now().minusDays(2))
            .dataPad(LocalDate.now().minusDays(2))
            .registroBio("BIO-LOCAL-01")
            .registroPad("PAD-LOCAL-01")
            .modoCi("intervalo")
            .bioHemacias(4.82)
            .bioHematocrito(42.3)
            .bioHemoglobina(14.1)
            .bioLeucocitos(6.2)
            .bioPlaquetas(255D)
            .bioRdw(13.1)
            .bioVpm(9.2)
            .padHemacias(4.78)
            .padHematocrito(41.9)
            .padHemoglobina(14.0)
            .padLeucocitos(6.0)
            .padPlaquetas(248D)
            .padRdw(12.9)
            .padVpm(9.0)
            .ciMinHemacias(4.4)
            .ciMaxHemacias(5.2)
            .ciMinHematocrito(40.0)
            .ciMaxHematocrito(45.0)
            .ciMinHemoglobina(13.2)
            .ciMaxHemoglobina(15.0)
            .ciMinLeucocitos(4.0)
            .ciMaxLeucocitos(10.0)
            .ciMinPlaquetas(180D)
            .ciMaxPlaquetas(320D)
            .ciMinRdw(11.5)
            .ciMaxRdw(14.5)
            .ciMinVpm(8.0)
            .ciMaxVpm(11.0)
            .build());
    }
}
