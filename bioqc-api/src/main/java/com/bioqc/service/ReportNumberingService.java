package com.bioqc.service;

import com.bioqc.entity.ReportAuditLog;
import com.bioqc.entity.ReportSequence;
import com.bioqc.repository.ReportAuditLogRepository;
import com.bioqc.repository.ReportSequenceRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportNumberingService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String HEX_CHARS = "0123456789abcdef";

    private final ReportSequenceRepository reportSequenceRepository;
    private final ReportAuditLogRepository reportAuditLogRepository;

    public ReportNumberingService(
        ReportSequenceRepository reportSequenceRepository,
        ReportAuditLogRepository reportAuditLogRepository
    ) {
        this.reportSequenceRepository = reportSequenceRepository;
        this.reportAuditLogRepository = reportAuditLogRepository;
    }

    /**
     * Gera o proximo numero de laudo no formato BIO-AAAAMM-NNNNNN para a competencia atual.
     * A transacao e REQUIRES_NEW para manter a sequencia monotonica mesmo se o chamador abortar.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String reserveNextNumber() {
        String periodKey = ZonedDateTime.now(DEFAULT_ZONE).format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMM")
        );
        ReportSequence sequence = reportSequenceRepository.findByPeriodKey(periodKey)
            .orElseGet(() -> reportSequenceRepository.save(
                ReportSequence.builder().periodKey(periodKey).lastValue(0L).build()
            ));
        long next = sequence.getLastValue() + 1L;
        sequence.setLastValue(next);
        reportSequenceRepository.save(sequence);
        return String.format("BIO-%s-%06d", periodKey, next);
    }

    @Transactional
    public ReportAuditLog registerGeneration(
        String reportNumber,
        String area,
        String format,
        String periodLabel,
        byte[] content,
        UUID generatedBy
    ) {
        ReportAuditLog log = ReportAuditLog.builder()
            .reportNumber(reportNumber)
            .area(area)
            .format(format)
            .periodLabel(periodLabel)
            .sha256(sha256Hex(content))
            .byteSize((long) content.length)
            .generatedBy(generatedBy)
            .build();
        return reportAuditLogRepository.save(log);
    }

    public String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content);
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(HEX_CHARS.charAt((b >> 4) & 0xF));
                sb.append(HEX_CHARS.charAt(b & 0xF));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }
}
