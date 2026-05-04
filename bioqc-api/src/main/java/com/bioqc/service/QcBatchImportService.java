package com.bioqc.service;

import com.bioqc.dto.request.QcRecordRequest;
import com.bioqc.dto.response.BatchImportResult;
import com.bioqc.dto.response.BatchImportResult.RowResult;
import com.bioqc.dto.response.QcRecordResponse;
import com.bioqc.entity.ImportRun;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Importacao em lote de registros de CQ com dois modos:
 *
 * <ul>
 *   <li>{@link ImportRunService#MODE_ATOMIC ATOMIC}: encaminha para
 *       {@link QcService#createRecordsBatch(List)} — comportamento legado.
 *   <li>{@link ImportRunService#MODE_PARTIAL PARTIAL}: executa cada linha em
 *       sua propria transacao. Linhas que falham nao impedem as demais de
 *       serem persistidas; o resultado volta linha-a-linha com mensagem.
 * </ul>
 *
 * Em ambos os modos uma {@link ImportRun} e gravada ao final com as contagens.
 */
@Service
public class QcBatchImportService {

    private static final int MAX_BATCH_SIZE = 1_000;

    private final QcService qcService;
    private final ImportRunService importRunService;
    private final TransactionTemplate transactionTemplate;

    public QcBatchImportService(
        QcService qcService,
        ImportRunService importRunService,
        PlatformTransactionManager transactionManager
    ) {
        this.qcService = qcService;
        this.importRunService = importRunService;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = template;
    }

    public BatchImportResult importPartial(List<QcRecordRequest> requests, Authentication authentication) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException("Nenhum registro foi enviado para importação.");
        }
        if (requests.size() > MAX_BATCH_SIZE) {
            throw new BusinessException("O lote excede o limite de " + MAX_BATCH_SIZE + " registros por importação.");
        }

        long start = System.currentTimeMillis();
        List<RowResult> rowResults = new ArrayList<>(requests.size());
        int success = 0;
        int failure = 0;
        StringBuilder errorSummary = new StringBuilder();

        for (int index = 0; index < requests.size(); index++) {
            QcRecordRequest request = requests.get(index);
            final int idx = index;
            RowResult result = transactionTemplate.execute(status -> {
                try {
                    QcRecordResponse saved = qcService.createRecord(request);
                    return new RowResult(idx, true, null, saved);
                } catch (BusinessException | ResourceNotFoundException ex) {
                    status.setRollbackOnly();
                    return new RowResult(idx, false, ex.getMessage(), null);
                } catch (RuntimeException ex) {
                    status.setRollbackOnly();
                    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    return new RowResult(idx, false, message, null);
                }
            });
            rowResults.add(result);
            if (result != null && result.success()) {
                success++;
            } else {
                failure++;
                if (result != null && result.message() != null && errorSummary.length() < 3_500) {
                    if (errorSummary.length() > 0) errorSummary.append("; ");
                    errorSummary.append("Linha ").append(idx + 1).append(": ").append(result.message());
                }
            }
        }

        long duration = System.currentTimeMillis() - start;
        ImportRun run = importRunService.record(
            ImportRunService.SOURCE_QC_RECORDS,
            ImportRunService.MODE_PARTIAL,
            requests.size(), success, failure,
            duration,
            failure > 0 ? errorSummary.toString() : null,
            authentication
        );
        return new BatchImportResult(
            run.getId(),
            ImportRunService.MODE_PARTIAL,
            requests.size(),
            success,
            failure,
            rowResults
        );
    }

    /**
     * Wrapper atomico: delega ao QcService.createRecordsBatch() e grava um
     * ImportRun com o resumo. Se o lote todo falhar, marca como FAILURE.
     */
    public BatchImportResult importAtomic(List<QcRecordRequest> requests, Authentication authentication) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException("Nenhum registro foi enviado para importação.");
        }
        long start = System.currentTimeMillis();
        try {
            List<QcRecordResponse> saved = qcService.createRecordsBatch(requests);
            long duration = System.currentTimeMillis() - start;
            List<RowResult> rows = new ArrayList<>(saved.size());
            for (int i = 0; i < saved.size(); i++) {
                rows.add(new RowResult(i, true, null, saved.get(i)));
            }
            ImportRun run = importRunService.record(
                ImportRunService.SOURCE_QC_RECORDS,
                ImportRunService.MODE_ATOMIC,
                requests.size(), saved.size(), 0,
                duration, null, authentication
            );
            return new BatchImportResult(run.getId(), ImportRunService.MODE_ATOMIC,
                requests.size(), saved.size(), 0, rows);
        } catch (RuntimeException ex) {
            long duration = System.currentTimeMillis() - start;
            importRunService.record(
                ImportRunService.SOURCE_QC_RECORDS,
                ImportRunService.MODE_ATOMIC,
                requests.size(), 0, requests.size(),
                duration, ex.getMessage(), authentication
            );
            throw ex;
        }
    }

}
