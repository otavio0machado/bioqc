package com.bioqc.service;

import com.bioqc.dto.request.PostCalibrationRequest;
import com.bioqc.entity.PostCalibrationRecord;
import com.bioqc.entity.QcRecord;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.PostCalibrationRecordRepository;
import com.bioqc.repository.QcRecordRepository;
import com.bioqc.util.NumericUtils;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostCalibrationService {

    private final PostCalibrationRecordRepository postCalibrationRecordRepository;
    private final QcRecordRepository qcRecordRepository;

    public PostCalibrationService(
        PostCalibrationRecordRepository postCalibrationRecordRepository,
        QcRecordRepository qcRecordRepository
    ) {
        this.postCalibrationRecordRepository = postCalibrationRecordRepository;
        this.qcRecordRepository = qcRecordRepository;
    }

    @Transactional
    public PostCalibrationRecord createPostCalibration(UUID qcRecordId, PostCalibrationRequest request) {
        QcRecord original = qcRecordRepository.findById(qcRecordId)
            .orElseThrow(() -> new ResourceNotFoundException("Registro de CQ não encontrado"));
        validatePostCalibrationCreation(original, qcRecordId);

        PostCalibrationRecord record = PostCalibrationRecord.builder()
            .qcRecord(original)
            .date(request.date())
            .examName(original.getExamName())
            .originalValue(original.getValue())
            .originalCv(original.getCv())
            .postCalibrationValue(request.postCalibrationValue())
            .postCalibrationCv(NumericUtils.calculateCv(request.postCalibrationValue(), original.getTargetValue()))
            .targetValue(original.getTargetValue())
            .analyst(request.analyst())
            .notes(request.notes())
            .build();

        original.setNeedsCalibration(Boolean.FALSE);
        qcRecordRepository.save(original);
        return postCalibrationRecordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public Optional<PostCalibrationRecord> getByQcRecord(UUID qcRecordId) {
        return postCalibrationRecordRepository.findByQcRecordId(qcRecordId);
    }

    private void validatePostCalibrationCreation(QcRecord original, UUID qcRecordId) {
        if (!Boolean.TRUE.equals(original.getNeedsCalibration())) {
            throw new BusinessException(
                "A pós-calibração só pode ser registrada quando existe pendência corretiva ativa no registro de CQ."
            );
        }
        if (postCalibrationRecordRepository.findByQcRecordId(qcRecordId).isPresent()) {
            throw new BusinessException("Já existe uma pós-calibração registrada para este evento de CQ.");
        }
    }
}
