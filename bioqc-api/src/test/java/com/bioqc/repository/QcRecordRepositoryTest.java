package com.bioqc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bioqc.entity.QcExam;
import com.bioqc.entity.QcRecord;
import com.bioqc.entity.QcReferenceValue;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QcRecordRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private QcRecordRepository qcRecordRepository;

    @Test
    @DisplayName("deve buscar histórico Westgard apenas da mesma referência até a data informada")
    void shouldReturnOnlyHistoryFromSameReferenceUpToMeasurementDate() {
        QcExam exam = persistExam("Glicose", "bioquimica");
        QcReferenceValue primaryReference = persistReference(exam, "Ref atual", "L1");
        QcReferenceValue otherReference = persistReference(exam, "Outra referência", "L2");

        QcRecord oldestSameReference = persistRecord(primaryReference, "Glicose", "bioquimica", "Normal", "L1", LocalDate.of(2026, 4, 1));
        QcRecord newestSameReference = persistRecord(primaryReference, "Glicose", "bioquimica", "Normal", "L1", LocalDate.of(2026, 4, 4));
        persistRecord(primaryReference, "Glicose", "bioquimica", "Normal", "L1", LocalDate.of(2026, 4, 6));
        persistRecord(otherReference, "Glicose", "bioquimica", "Normal", "L2", LocalDate.of(2026, 4, 3));

        entityManager.flush();
        entityManager.clear();

        List<QcRecord> history = qcRecordRepository.findWestgardHistory(
            primaryReference.getId(),
            "Glicose",
            "Normal",
            "bioquimica",
            LocalDate.of(2026, 4, 4),
            null,
            PageRequest.of(0, 10)
        );

        assertThat(history)
            .extracting(QcRecord::getId)
            .containsExactly(newestSameReference.getId(), oldestSameReference.getId());
    }

    @Test
    @DisplayName("deve excluir o registro informado ao buscar histórico Westgard")
    void shouldExcludeRequestedRecordFromWestgardHistory() {
        QcExam exam = persistExam("Glicose", "bioquimica");
        QcReferenceValue reference = persistReference(exam, "Ref atual", "L1");

        QcRecord firstRecord = persistRecord(reference, "Glicose", "bioquimica", "Normal", "L1", LocalDate.of(2026, 4, 1));
        QcRecord recordToExclude = persistRecord(reference, "Glicose", "bioquimica", "Normal", "L1", LocalDate.of(2026, 4, 2));

        entityManager.flush();
        entityManager.clear();

        List<QcRecord> history = qcRecordRepository.findWestgardHistory(
            reference.getId(),
            "Glicose",
            "Normal",
            "bioquimica",
            LocalDate.of(2026, 4, 2),
            recordToExclude.getId(),
            PageRequest.of(0, 10)
        );

        assertThat(history)
            .extracting(QcRecord::getId)
            .containsExactly(firstRecord.getId());
    }

    private QcExam persistExam(String name, String area) {
        return entityManager.persistAndFlush(QcExam.builder()
            .name(name)
            .area(area)
            .isActive(Boolean.TRUE)
            .build());
    }

    private QcReferenceValue persistReference(QcExam exam, String name, String lotNumber) {
        return entityManager.persistAndFlush(QcReferenceValue.builder()
            .exam(exam)
            .name(name)
            .level("Normal")
            .lotNumber(lotNumber)
            .targetValue(100D)
            .targetSd(5D)
            .cvMaxThreshold(10D)
            .isActive(Boolean.TRUE)
            .build());
    }

    private QcRecord persistRecord(
        QcReferenceValue reference,
        String examName,
        String area,
        String level,
        String lotNumber,
        LocalDate date
    ) {
        return entityManager.persist(QcRecord.builder()
            .reference(reference)
            .examName(examName)
            .area(area)
            .date(date)
            .level(level)
            .lotNumber(lotNumber)
            .value(100D)
            .targetValue(100D)
            .targetSd(5D)
            .cv(0D)
            .cvLimit(10D)
            .zScore(0D)
            .status("APROVADO")
            .needsCalibration(Boolean.FALSE)
            .build());
    }
}
