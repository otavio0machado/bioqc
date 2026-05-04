package com.bioqc.controller;

import com.bioqc.dto.response.GeneratedReport;
import com.bioqc.dto.response.ReportRunResponse;
import com.bioqc.exception.BusinessException;
import com.bioqc.service.PdfReportService;
import com.bioqc.service.ReportRunService;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final PdfReportService pdfReportService;
    private final ReportRunService reportRunService;

    public ReportController(PdfReportService pdfReportService, ReportRunService reportRunService) {
        this.pdfReportService = pdfReportService;
        this.reportRunService = reportRunService;
    }

    @GetMapping("/qc-pdf")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA') or hasRole('FUNCIONARIO')")
    public ResponseEntity<byte[]> generateQcPdf(
        @RequestParam(required = false) String area,
        @RequestParam(required = false) String periodType,
        @RequestParam(required = false) Integer month,
        @RequestParam(required = false) Integer year,
        Authentication authentication
    ) {
        if (month != null && (month < 1 || month > 12)) {
            throw new BusinessException("Mes invalido: deve estar entre 1 e 12");
        }
        if (year != null && (year < 2000 || year > 2100)) {
            throw new BusinessException("Ano invalido: deve estar entre 2000 e 2100");
        }
        long start = System.currentTimeMillis();
        try {
            GeneratedReport report = pdfReportService.generateQcReport(area, periodType, month, year);
            long duration = System.currentTimeMillis() - start;
            reportRunService.recordSuccess(
                ReportRunService.TYPE_QC_PDF, area, periodType, month, year,
                report.reportNumber(), report.sha256(),
                report.content() == null ? 0L : report.content().length,
                duration, authentication
            );
            String filename = report.reportNumber() + ".pdf";
            return pdfResponse(report, filename);
        } catch (RuntimeException ex) {
            long duration = System.currentTimeMillis() - start;
            reportRunService.recordFailure(
                ReportRunService.TYPE_QC_PDF, area, periodType, month, year,
                duration, ex.getMessage(), authentication
            );
            throw ex;
        }
    }

    @GetMapping("/reagents-pdf")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA') or hasRole('FUNCIONARIO')")
    public ResponseEntity<byte[]> generateReagentsPdf(Authentication authentication) {
        long start = System.currentTimeMillis();
        try {
            GeneratedReport report = pdfReportService.generateReagentsReport();
            long duration = System.currentTimeMillis() - start;
            reportRunService.recordSuccess(
                ReportRunService.TYPE_REAGENTS_PDF, null, null, null, null,
                report.reportNumber(), report.sha256(),
                report.content() == null ? 0L : report.content().length,
                duration, authentication
            );
            return pdfResponse(report, report.reportNumber() + ".pdf");
        } catch (RuntimeException ex) {
            long duration = System.currentTimeMillis() - start;
            reportRunService.recordFailure(
                ReportRunService.TYPE_REAGENTS_PDF, null, null, null, null,
                duration, ex.getMessage(), authentication
            );
            throw ex;
        }
    }

    /**
     * Historico de relatorios gerados, ordenados do mais recente para o mais antigo.
     * {@code limit} default 20, maximo 200. Usado pela aba Relatorios do frontend
     * para mostrar quem gerou, quando, tamanho e eventual falha.
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA') or hasRole('FUNCIONARIO')")
    public ResponseEntity<List<ReportRunResponse>> history(
        @RequestParam(required = false, defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(reportRunService.history(limit));
    }

    private ResponseEntity<byte[]> pdfResponse(GeneratedReport report, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.add("X-Report-Number", report.reportNumber());
        headers.add("X-Report-Hash", report.sha256());
        headers.setAccessControlExposeHeaders(java.util.List.of("X-Report-Number", "X-Report-Hash", "Content-Disposition"));
        return ResponseEntity.ok().headers(headers).body(report.content());
    }
}
