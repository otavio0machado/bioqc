package com.bioqc.controller;

import com.bioqc.config.ReportsV2Properties;
import com.bioqc.dto.reports.v2.GenerateReportV2Request;
import com.bioqc.dto.reports.v2.PreviewReportV2Request;
import com.bioqc.dto.reports.v2.PreviewResponse;
import com.bioqc.dto.reports.v2.ReportDefinitionResponse;
import com.bioqc.dto.reports.v2.ReportExecutionResponse;
import com.bioqc.dto.reports.v2.SetReportLabelsRequest;
import com.bioqc.dto.reports.v2.SignReportV2Request;
import com.bioqc.dto.reports.v2.VerifyReportResponse;
import com.bioqc.service.reports.v2.InMemoryRateLimiter;
import com.bioqc.service.reports.v2.RateLimitExceededException;
import com.bioqc.service.reports.v2.ReportServiceV2;
import com.bioqc.service.reports.v2.catalog.ReportCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller Reports V2. Registrado <strong>apenas</strong> quando
 * {@code reports.v2.enabled=true} — com flag off, nenhum endpoint V2 e
 * exposto (404 em todos os paths de {@code /api/reports/v2/**}).
 */
@RestController
@RequestMapping("/api/reports/v2")
@ConditionalOnProperty(prefix = "reports.v2", name = "enabled", havingValue = "true")
public class ReportV2Controller {

    private static final int RATE_LIMIT_GENERATE_PER_MIN_USER = 30;
    private static final int RATE_LIMIT_PREVIEW_PER_MIN_USER = 60;
    private static final int RATE_LIMIT_DOWNLOAD_PER_MIN_USER = 60;
    private static final int RATE_LIMIT_SIGN_PER_MIN_USER = 20;

    private final ReportServiceV2 service;
    private final InMemoryRateLimiter rateLimiter;
    private final ReportsV2Properties properties;

    public ReportV2Controller(
        ReportServiceV2 service,
        InMemoryRateLimiter rateLimiter,
        ReportsV2Properties properties
    ) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    // ---------- Catalogo ----------

    @GetMapping("/catalog")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA') or hasRole('FUNCIONARIO')")
    public ResponseEntity<List<ReportDefinitionResponse>> catalog(Authentication auth) {
        return ResponseEntity.ok(service.listCatalog(auth));
    }

    @GetMapping("/catalog/{code}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA') or hasRole('FUNCIONARIO')")
    public ResponseEntity<ReportDefinitionResponse> definition(
        @PathVariable ReportCode code, Authentication auth
    ) {
        return ResponseEntity.ok(service.getDefinition(code, auth));
    }

    // ---------- Fluxo A: /generate ----------

    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA') or hasRole('FUNCIONARIO')")
    public ResponseEntity<ReportExecutionResponse> generate(
        @Valid @RequestBody GenerateReportV2Request request,
        Authentication auth
    ) {
        enforceRateLimit("generate:" + usernameOf(auth), RATE_LIMIT_GENERATE_PER_MIN_USER);
        ReportExecutionResponse response = service.generate(request, auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ---------- Fluxo B: /preview ----------

    @PostMapping("/preview")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA') or hasRole('FUNCIONARIO')")
    public ResponseEntity<PreviewResponse> preview(
        @Valid @RequestBody PreviewReportV2Request request,
        Authentication auth
    ) {
        enforceRateLimit("preview:" + usernameOf(auth), RATE_LIMIT_PREVIEW_PER_MIN_USER);
        return ResponseEntity.ok(service.preview(request, auth));
    }

    // ---------- Fluxo C: /sign ----------

    @PostMapping("/executions/{id}/sign")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA')")
    public ResponseEntity<ReportExecutionResponse> sign(
        @PathVariable UUID id,
        @RequestBody(required = false) SignReportV2Request request,
        Authentication auth
    ) {
        enforceRateLimit("sign:" + usernameOf(auth), RATE_LIMIT_SIGN_PER_MIN_USER);
        return ResponseEntity.ok(service.sign(id, request, auth));
    }

    // ---------- Fluxo D: /verify ----------

    @GetMapping("/verify/{hash}")
    public ResponseEntity<VerifyReportResponse> verify(
        @PathVariable String hash,
        HttpServletRequest httpRequest
    ) {
        String ip = resolveIp(httpRequest);
        enforceRateLimit("verify:" + ip, properties.getVerifyRateLimitPerMinutePerIp());
        return ResponseEntity.ok(service.verify(hash));
    }

    // ---------- Listagem / download ----------

    @GetMapping("/executions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA') or hasRole('FUNCIONARIO')")
    public ResponseEntity<Page<ReportExecutionResponse>> list(
        @RequestParam(required = false) ReportCode code,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Authentication auth
    ) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(service.listExecutions(code, status, from, to, auth, pageable));
    }

    @GetMapping("/executions/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA') or hasRole('FUNCIONARIO')")
    public ResponseEntity<ReportExecutionResponse> getExecution(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.getExecution(id, auth));
    }

    @GetMapping("/executions/{id}/download")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA') or hasRole('FUNCIONARIO')")
    public ResponseEntity<byte[]> download(
        @PathVariable UUID id,
        Authentication auth,
        HttpServletRequest httpRequest
    ) {
        enforceRateLimit("download:" + usernameOf(auth), RATE_LIMIT_DOWNLOAD_PER_MIN_USER);
        // IP + User-Agent propagados para auditoria de download (ISO 15189 8.4.1)
        String clientIp = httpRequest == null ? null : resolveIp(httpRequest);
        String userAgent = httpRequest == null ? null : httpRequest.getHeader("User-Agent");
        ReportServiceV2.DownloadResult result = service.download(id, auth, clientIp, userAgent);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(result.suggestedFilename()).build());
        if (result.reportNumber() != null) headers.add("X-Report-Number", result.reportNumber());
        // X-Report-Hash sempre reflete o hash dos bytes retornados (signature
        // quando assinado; sha256 do original caso contrario).
        if (result.sha256() != null) headers.add("X-Report-Hash", result.sha256());
        // X-Report-Original-Hash: preserva o hash pre-assinatura quando a execucao
        // ja foi assinada. Permite ao cliente confrontar cadeia de custodia.
        if (result.originalSha256() != null) headers.add("X-Report-Original-Hash", result.originalSha256());
        headers.setAccessControlExposeHeaders(List.of(
            "X-Report-Number", "X-Report-Hash", "X-Report-Original-Hash", "Content-Disposition"
        ));
        return ResponseEntity.ok().headers(headers).body(result.bytes());
    }

    @DeleteMapping("/executions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        // Placeholder para cancelamento/soft-delete. Por seguranca, 501 ate slice dedicado.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    // ---------- Labels ----------

    @PostMapping("/executions/{id}/labels")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA')")
    public ResponseEntity<ReportExecutionResponse> setLabels(
        @PathVariable UUID id,
        @Valid @RequestBody SetReportLabelsRequest request,
        Authentication auth
    ) {
        return ResponseEntity.ok(service.setLabels(id, request, auth));
    }

    // ---------- Suggestions ----------

    /**
     * Autocomplete para o filtro {@code equipment}. Alimentado por
     * {@code SELECT DISTINCT equipment FROM maintenance_records}. Cache de 5 min.
     */
    @GetMapping("/suggestions/equipment")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VIGILANCIA_SANITARIA') or hasRole('FUNCIONARIO')")
    @org.springframework.cache.annotation.Cacheable(cacheNames = "reportsV2.suggestions.equipment")
    public ResponseEntity<Map<String, List<String>>> suggestEquipments() {
        return ResponseEntity.ok(Map.of("items", service.suggestEquipments()));
    }

    // ---------- Helpers ----------

    private void enforceRateLimit(String key, int limit) {
        if (!rateLimiter.tryAcquire(key, limit)) {
            int retry = rateLimiter.secondsUntilReset(key);
            throw new RateLimitExceededException(
                "Limite de " + limit + " requisicoes/minuto excedido", retry
            );
        }
    }

    private String usernameOf(Authentication auth) {
        return auth == null || auth.getName() == null ? "anonymous" : auth.getName();
    }

    private String resolveIp(HttpServletRequest req) {
        if (req == null) return "unknown";
        String header = req.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            int comma = header.indexOf(',');
            return comma > 0 ? header.substring(0, comma).trim() : header.trim();
        }
        return req.getRemoteAddr() == null ? "unknown" : req.getRemoteAddr();
    }
}
