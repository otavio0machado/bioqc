package com.bioqc.service.reports.v2;

import com.bioqc.config.ReportsV2Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.bioqc.dto.reports.v2.GenerateReportV2Request;
import com.bioqc.dto.reports.v2.PreviewReportV2Request;
import com.bioqc.dto.reports.v2.PreviewResponse;
import com.bioqc.dto.reports.v2.ReportDefinitionResponse;
import com.bioqc.dto.reports.v2.ReportExecutionResponse;
import com.bioqc.dto.reports.v2.SetReportLabelsRequest;
import com.bioqc.dto.reports.v2.SignReportV2Request;
import com.bioqc.dto.reports.v2.VerifyReportResponse;
import com.bioqc.entity.LabSettings;
import com.bioqc.entity.ReportRun;
import com.bioqc.entity.ReportSignatureLog;
import com.bioqc.entity.Role;
import com.bioqc.entity.User;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.MaintenanceRecordRepository;
import com.bioqc.repository.ReportRunRepository;
import com.bioqc.repository.ReportSignatureLogRepository;
import com.bioqc.repository.UserRepository;
import com.bioqc.service.LabSettingsService;
import com.bioqc.service.ReportRunService;
import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.catalog.ReportDefinition;
import com.bioqc.service.reports.v2.catalog.ReportDefinitionRegistry;
import com.bioqc.service.reports.v2.catalog.ReportFormat;
import com.bioqc.service.reports.v2.generator.GenerationContext;
import com.bioqc.service.reports.v2.generator.ReportArtifact;
import com.bioqc.service.reports.v2.generator.ReportFilters;
import com.bioqc.service.reports.v2.generator.ReportGenerator;
import com.bioqc.service.reports.v2.generator.ReportGeneratorRegistry;
import com.bioqc.service.reports.v2.generator.ReportPreview;
import com.bioqc.service.reports.v2.storage.ReportStorage;
import com.bioqc.util.ReportV2Mapper;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestrador Reports V2. Une catalogo, validator, generator, storage,
 * signer e run-service. <strong>Nao</strong> substitui nada de V1 —
 * {@code PdfReportService}/{@code ReportController} permanecem intocados.
 */
@Service
@ConditionalOnProperty(prefix = "reports.v2", name = "enabled", havingValue = "true")
public class ReportServiceV2 {

    private static final Logger LOG = LoggerFactory.getLogger(ReportServiceV2.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ReportDefinitionRegistry definitionRegistry;
    private final ReportGeneratorRegistry generatorRegistry;
    private final FilterValidator filterValidator;
    private final ReportStorage reportStorage;
    private final ReportSigner reportSigner;
    private final ReportRunService reportRunService;
    private final ReportRunRepository reportRunRepository;
    private final ReportSignatureLogRepository signatureLogRepository;
    private final UserRepository userRepository;
    private final LabSettingsService labSettingsService;
    private final ReportsV2Properties properties;
    private final MaintenanceRecordRepository suggestionRepository;

    private final com.bioqc.repository.ReportDownloadLogRepository downloadLogRepository;
    private final ReportV2Metrics metrics;

    public ReportServiceV2(
        ReportDefinitionRegistry definitionRegistry,
        ReportGeneratorRegistry generatorRegistry,
        FilterValidator filterValidator,
        ReportStorage reportStorage,
        ReportSigner reportSigner,
        ReportRunService reportRunService,
        ReportRunRepository reportRunRepository,
        ReportSignatureLogRepository signatureLogRepository,
        UserRepository userRepository,
        LabSettingsService labSettingsService,
        ReportsV2Properties properties,
        MaintenanceRecordRepository suggestionRepository,
        com.bioqc.repository.ReportDownloadLogRepository downloadLogRepository,
        ReportV2Metrics metrics
    ) {
        this.definitionRegistry = definitionRegistry;
        this.generatorRegistry = generatorRegistry;
        this.filterValidator = filterValidator;
        this.reportStorage = reportStorage;
        this.reportSigner = reportSigner;
        this.reportRunService = reportRunService;
        this.reportRunRepository = reportRunRepository;
        this.signatureLogRepository = signatureLogRepository;
        this.userRepository = userRepository;
        this.labSettingsService = labSettingsService;
        this.properties = properties;
        this.suggestionRepository = suggestionRepository;
        this.downloadLogRepository = downloadLogRepository;
        this.metrics = metrics;
    }

    // ---------- Catalogo ----------

    public List<ReportDefinitionResponse> listCatalog(Authentication auth) {
        Set<String> roles = extractRoles(auth);
        return definitionRegistry.forUserRoles(roles).stream()
            .map(ReportV2Mapper::toResponse)
            .collect(Collectors.toUnmodifiableList());
    }

    public ReportDefinitionResponse getDefinition(ReportCode code, Authentication auth) {
        Set<String> roles = extractRoles(auth);
        if (!definitionRegistry.canAccess(code, roles)) {
            throw new AccessDeniedException("Role nao autorizada a acessar " + code);
        }
        return ReportV2Mapper.toResponse(definitionRegistry.resolve(code));
    }

    // ---------- Fluxo A: /generate ----------

    @Transactional
    public ReportExecutionResponse generate(GenerateReportV2Request request, Authentication auth) {
        if (request == null || request.code() == null) {
            throw new InvalidFilterException("Campo 'code' obrigatorio");
        }
        Set<String> roles = extractRoles(auth);
        if (!definitionRegistry.canAccess(request.code(), roles)) {
            throw new AccessDeniedException("Role nao autorizada a gerar " + request.code());
        }
        ReportDefinition definition = definitionRegistry.resolve(request.code());

        ReportFormat format = request.format() == null ? ReportFormat.PDF : request.format();
        if (!definition.supportedFormats().contains(format)) {
            throw new InvalidFilterException("Formato " + format + " nao suportado para " + request.code());
        }

        Map<String, Object> rawFilters = request.filters() == null ? Map.of() : request.filters();
        filterValidator.validate(definition.filterSpec(), rawFilters, request.code());

        ReportGenerator generator = generatorRegistry.resolve(request.code());
        GenerationContext ctx = buildContext(auth);

        // Metrica: duracao total de generate (inclui storage + ReportRun record)
        long startNs = System.nanoTime();
        try {
            ReportArtifact artifact = generator.generate(new ReportFilters(rawFilters), ctx);
            String yearMonth = yearMonthFromReportNumber(artifact.reportNumber());
            String storageKey = reportStorage.save(
                artifact.bytes(),
                new ReportStorage.StorageKeyHint(
                    request.code().name(),
                    yearMonth,
                    artifact.reportNumber(),
                    extensionFor(format)
                )
            );
            ReportRun run = reportRunService.recordSuccessV2(
                artifact, definition, storageKey, ctx, rawFilters, format
            );
            metrics.recordGeneration(request.code(), true,
                java.time.Duration.ofNanos(System.nanoTime() - startNs));
            // warnings sao transientes do request atual. Persistir em ReportRun
            // exigiria migration; como sao apenas metadata de UX (frontend
            // mostra toast e banner), ficam expostos apenas na resposta imediata.
            return ReportV2Mapper.toResponse(run, properties.getPublicBaseUrl(), artifact.warnings());
        } catch (InvalidFilterException | ReportCodeNotFoundException | AccessDeniedException ex) {
            metrics.recordGeneration(request.code(), false,
                java.time.Duration.ofNanos(System.nanoTime() - startNs));
            reportRunService.recordFailureV2(definition, ctx, rawFilters, format, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            metrics.recordGeneration(request.code(), false,
                java.time.Duration.ofNanos(System.nanoTime() - startNs));
            LOG.error("Falha ao gerar relatorio V2 code={} filters={}", request.code(), rawFilters, ex);
            reportRunService.recordFailureV2(definition, ctx, rawFilters, format, ex.getMessage());
            throw ex;
        } catch (IOException ex) {
            metrics.recordGeneration(request.code(), false,
                java.time.Duration.ofNanos(System.nanoTime() - startNs));
            LOG.error("Falha IO ao persistir artefato V2", ex);
            reportRunService.recordFailureV2(definition, ctx, rawFilters, format, ex.getMessage());
            throw new IllegalStateException("Falha ao persistir artefato no storage", ex);
        }
    }

    // ---------- Fluxo B: /preview ----------

    @Transactional(readOnly = true)
    public PreviewResponse preview(PreviewReportV2Request request, Authentication auth) {
        if (request == null || request.code() == null) {
            throw new InvalidFilterException("Campo 'code' obrigatorio");
        }
        Set<String> roles = extractRoles(auth);
        if (!definitionRegistry.canAccess(request.code(), roles)) {
            throw new AccessDeniedException("Role nao autorizada a preview " + request.code());
        }
        ReportDefinition definition = definitionRegistry.resolve(request.code());
        if (!definition.previewSupported()) {
            throw new InvalidFilterException("Codigo " + request.code() + " nao suporta preview");
        }

        Map<String, Object> rawFilters = request.filters() == null ? Map.of() : request.filters();
        filterValidator.validate(definition.filterSpec(), rawFilters, request.code());
        ReportGenerator generator = generatorRegistry.resolve(request.code());
        GenerationContext ctx = buildContext(auth);
        ReportPreview preview = generator.preview(new ReportFilters(rawFilters), ctx);
        return new PreviewResponse(preview.html(), preview.warnings(), preview.periodLabel());
    }

    // ---------- Fluxo C: /sign ----------

    @Transactional
    public ReportExecutionResponse sign(UUID executionId, SignReportV2Request request, Authentication auth) {
        Set<String> roles = extractRoles(auth);
        if (!roles.contains("ADMIN") && !roles.contains("VIGILANCIA_SANITARIA")) {
            throw new AccessDeniedException("Apenas ADMIN/VIGILANCIA_SANITARIA podem assinar relatorios");
        }
        ReportRun run = reportRunRepository.findById(executionId)
            .orElseThrow(() -> new ResourceNotFoundException("Execucao V2 nao encontrada: " + executionId));
        if (run.getReportCode() == null) {
            throw new ResourceNotFoundException("Execucao nao e V2 (sem reportCode)");
        }
        if (run.getSignatureHash() != null || ReportRunService.STATUS_SIGNED.equals(run.getStatus())) {
            throw new ReportAlreadySignedException("Execucao ja foi assinada: " + executionId);
        }
        if (run.getStorageKey() == null) {
            throw new ResourceNotFoundException("Execucao nao possui artefato para assinar");
        }
        if (run.getExpiresAt() != null && Instant.now().isAfter(run.getExpiresAt())) {
            throw new ReportExpiredException("Execucao expirou em " + run.getExpiresAt());
        }

        try {
            byte[] original = reportStorage.load(run.getStorageKey());
            LabSettings settings = labSettingsService.getOrCreateSingleton();
            String signerName = firstNonBlank(
                request == null ? null : request.signerName(),
                settings.getResponsibleName(),
                auth == null ? null : auth.getName()
            );
            String signerReg = firstNonBlank(
                request == null ? null : request.signerRegistration(),
                settings.getResponsibleRegistration()
            );
            // Ressalva 8: laudo sem registro profissional (CRBM/CRM) nao tem
            // valor juridico no Brasil. Falha explicita antes de qualquer I/O.
            if (signerReg == null || signerReg.isBlank()) {
                throw new InvalidSignerException(
                    "Nao e possivel assinar: responsavel tecnico sem registro profissional cadastrado. "
                    + "Configure LabSettings.responsibleRegistration ou informe signerRegistration no request."
                );
            }
            ReportSigner.SignatureResult signed = reportSigner.sign(
                original,
                new ReportSigner.SignatureRequest(
                    signerName, signerReg,
                    properties.getPublicBaseUrl(),
                    run.getSha256()
                )
            );
            // Ressalva 1: NAO sobrescrever o artefato original. Salvamos a versao
            // assinada em uma storageKey separada para preservar a cadeia de
            // custodia (sha256 do original continua valido e bytes permanecem).
            String originalKey = run.getStorageKey();
            String signedKey = reportStorage.save(
                signed.signedBytes(),
                deriveSignedStorageHint(originalKey, run.getReportNumber())
            );
            UUID userId = resolveUserId(auth);
            ReportRun updated = reportRunService.recordSigned(run.getId(), signed, userId, signedKey);

            // Ressalva 2: registra cadeia imutavel. userId pode ser null se o
            // usuario autenticado nao bater com nenhum User cadastrado (edge
            // case em testes); usamos zero-UUID como fallback para manter
            // constraint NOT NULL — telemetria vai sinalizar esses casos.
            UUID auditUserId = userId != null ? userId : new UUID(0L, 0L);
            ReportSignatureLog log = ReportSignatureLog.builder()
                .id(UUID.randomUUID())
                .reportRunId(run.getId())
                .reportNumber(run.getReportNumber())
                .originalSha256(run.getSha256())
                .signatureHash(signed.signatureHash())
                .signedByUserId(auditUserId)
                .signedByName(signerName == null ? "-" : signerName)
                .signerRegistration(signerReg)
                .signedStorageKey(signedKey)
                .build();
            signatureLogRepository.save(log);

            ReportCode code = safeReportCode(run.getReportCode());
            if (code != null) metrics.recordSignature(code, true);

            return ReportV2Mapper.toResponse(updated, properties.getPublicBaseUrl());
        } catch (InvalidSignerException ex) {
            ReportCode code = safeReportCode(run.getReportCode());
            if (code != null) metrics.recordSignature(code, false);
            throw ex;
        } catch (IOException ex) {
            ReportCode code = safeReportCode(run.getReportCode());
            if (code != null) metrics.recordSignature(code, false);
            throw new IllegalStateException("Falha ao assinar relatorio", ex);
        }
    }

    private ReportStorage.StorageKeyHint deriveSignedStorageHint(String originalKey, String reportNumber) {
        // Chave derivada: reutiliza codigo/yearMonth/reportNumber do original quando
        // possivel. Formato padrao: reports/v2/{yearMonth}/{code}/{reportNumber}.pdf →
        // para signed: reports/v2/{yearMonth}/{code}/{reportNumber}.signed.pdf
        String yearMonth = yearMonthFromReportNumber(reportNumber);
        String code = extractCodeFromKey(originalKey);
        String baseNumber = reportNumber == null ? UUID.randomUUID().toString() : reportNumber;
        // Marcador .signed embutido no reportNumber passado ao storage para
        // garantir nome unico independente da implementacao do LocalFilesystemReportStorage.
        return new ReportStorage.StorageKeyHint(code, yearMonth, baseNumber + ".signed", "pdf");
    }

    private String extractCodeFromKey(String storageKey) {
        // Formato esperado: reports/v2/{yearMonth}/{code}/{arquivo}
        if (storageKey == null) return "UNKNOWN";
        String[] parts = storageKey.split("/");
        if (parts.length >= 4) return parts[3];
        return "UNKNOWN";
    }

    // ---------- Fluxo D: /verify ----------

    /**
     * Verifica um hash (SHA-256 hex) contra os registros de execucao e log
     * imutavel de assinatura.
     *
     * <p>Ressalva 5: NAO lanca 404 quando nao encontra — retorna 200 com
     * {@code valid=false} e campos nulos. Motivacao: o endpoint e publico
     * e consumido por leitores de QR code; 404 confundiria clientes sem
     * valor agregado, ja que o contexto e "esse PDF e meu?".
     */
    @Transactional(readOnly = true)
    public VerifyReportResponse verify(String hash) {
        if (hash == null || hash.isBlank()) {
            return invalidVerifyResponse();
        }
        String trimmed = hash.trim();
        List<ReportRun> runMatches = reportRunRepository.findBySha256OrSignatureHash(trimmed);
        Optional<ReportSignatureLog> logBySignature = signatureLogRepository.findBySignatureHash(trimmed);
        List<ReportSignatureLog> logsByOriginal = signatureLogRepository.findByOriginalSha256OrderBySignedAtDesc(trimmed);

        if (runMatches.isEmpty() && logBySignature.isEmpty() && logsByOriginal.isEmpty()) {
            return invalidVerifyResponse();
        }

        ReportRun run = runMatches.isEmpty() ? null : runMatches.get(0);
        ReportSignatureLog signatureLog = logBySignature.orElse(
            logsByOriginal.isEmpty() ? null : logsByOriginal.get(0)
        );

        String reportNumber = run != null ? run.getReportNumber()
            : (signatureLog != null ? signatureLog.getReportNumber() : null);
        String reportCode = run != null
            ? (run.getReportCode() != null ? run.getReportCode() : run.getType())
            : null;
        String periodLabel = run != null ? run.getPeriodType() : null;
        Instant generatedAt = run != null ? run.getCreatedAt() : null;
        String generatedByName = run != null ? run.getUsername() : null;
        String sha256 = run != null ? run.getSha256()
            : (signatureLog != null ? signatureLog.getOriginalSha256() : null);
        String signatureHash = run != null ? run.getSignatureHash()
            : (signatureLog != null ? signatureLog.getSignatureHash() : null);
        Instant signedAt = run != null ? run.getSignedAt()
            : (signatureLog != null ? signatureLog.getSignedAt() : null);
        String signedByName = signatureLog != null ? signatureLog.getSignedByName() : null;
        boolean signed = signedAt != null;

        // valid: pelo menos um registro coerente foi encontrado e o hash informado
        // bate com sha256 original OU signatureHash conhecido.
        boolean valid = (sha256 != null && sha256.equalsIgnoreCase(trimmed))
            || (signatureHash != null && signatureHash.equalsIgnoreCase(trimmed));

        return new VerifyReportResponse(
            reportNumber,
            reportCode,
            periodLabel,
            generatedAt,
            generatedByName,
            sha256,
            signatureHash,
            signedAt,
            signedByName,
            signed,
            valid
        );
    }

    private VerifyReportResponse invalidVerifyResponse() {
        return new VerifyReportResponse(
            null, null, null, null, null, null, null, null, null, false, false
        );
    }

    // ---------- Fluxo E: /labels ----------

    /**
     * Aplica adicoes e remocoes de rotulos a uma execucao V2. Idempotente:
     * adicionar um rotulo ja presente ou remover um ausente nao gera erro.
     *
     * @throws InvalidFilterException quando algum valor nao pertence ao enum {@link ReportLabel}
     */
    @Transactional
    public ReportExecutionResponse setLabels(UUID executionId, SetReportLabelsRequest request, Authentication auth) {
        Set<String> roles = extractRoles(auth);
        if (!roles.contains("ADMIN") && !roles.contains("VIGILANCIA_SANITARIA")) {
            throw new AccessDeniedException("Apenas ADMIN/VIGILANCIA_SANITARIA podem aplicar rotulos");
        }
        ReportRun run = reportRunRepository.findById(executionId)
            .orElseThrow(() -> new ResourceNotFoundException("Execucao V2 nao encontrada: " + executionId));

        List<String> adds = request == null ? List.of() : request.add();
        List<String> removes = request == null ? List.of() : request.remove();

        List<String> violations = new java.util.ArrayList<>();
        java.util.Set<String> addValues = new java.util.TreeSet<>();
        for (String label : adds) {
            java.util.Optional<ReportLabel> parsed = ReportLabel.parse(label);
            if (parsed.isEmpty()) {
                violations.add("Rotulo invalido em 'add': " + label + " (aceitos: " + ReportLabel.allValues() + ")");
            } else {
                addValues.add(parsed.get().value());
            }
        }
        java.util.Set<String> removeValues = new java.util.TreeSet<>();
        for (String label : removes) {
            java.util.Optional<ReportLabel> parsed = ReportLabel.parse(label);
            if (parsed.isEmpty()) {
                violations.add("Rotulo invalido em 'remove': " + label + " (aceitos: " + ReportLabel.allValues() + ")");
            } else {
                removeValues.add(parsed.get().value());
            }
        }
        if (!violations.isEmpty()) {
            throw new InvalidFilterException(violations);
        }

        java.util.Set<String> current = new java.util.TreeSet<>(com.bioqc.util.ReportV2Mapper.parseLabels(run.getLabels()));
        current.addAll(addValues);
        current.removeAll(removeValues);

        String serialized = com.bioqc.util.ReportV2Mapper.serializeLabels(new java.util.ArrayList<>(current));
        run.setLabels(serialized);
        ReportRun saved = reportRunRepository.save(run);
        return com.bioqc.util.ReportV2Mapper.toResponse(saved, properties.getPublicBaseUrl());
    }

    /**
     * Retorna valores distintos de {@code maintenance_records.equipment}.
     * Usado pelo endpoint de autocomplete {@code /suggestions/equipment}.
     */
    @Transactional(readOnly = true)
    public java.util.List<String> suggestEquipments() {
        return suggestionRepository == null
            ? java.util.List.of()
            : suggestionRepository.findDistinctEquipments();
    }

    // ---------- Listagem e download ----------

    @Transactional(readOnly = true)
    public Page<ReportExecutionResponse> listExecutions(
        ReportCode code, String status, Instant from, Instant to, Authentication auth, Pageable pageable
    ) {
        String codeFilter = code == null ? null : code.name();
        Set<String> roles = extractRoles(auth);
        String usernameFilter = roles.contains("ADMIN") || roles.contains("VIGILANCIA_SANITARIA")
            ? null
            : (auth == null ? null : auth.getName());
        Page<ReportRun> runs = reportRunRepository.findAll(
            executionsSpecification(codeFilter, status, usernameFilter, from, to),
            pageable
        );
        return runs.map(r -> ReportV2Mapper.toResponse(r, properties.getPublicBaseUrl()));
    }

    @Transactional(readOnly = true)
    public ReportExecutionResponse getExecution(UUID id, Authentication auth) {
        ReportRun run = loadForAuthenticatedUser(id, auth);
        return ReportV2Mapper.toResponse(run, properties.getPublicBaseUrl());
    }

    @Transactional(readOnly = true)
    public DownloadResult download(UUID id, Authentication auth) {
        return download(id, auth, null, null);
    }

    /**
     * Variante que recebe IP + user-agent para auditoria de download (ISO 15189 8.4.1).
     * Controller injeta a partir da {@link jakarta.servlet.http.HttpServletRequest}.
     */
    @Transactional
    public DownloadResult download(UUID id, Authentication auth, String clientIp, String userAgent) {
        ReportRun run = loadForAuthenticatedUser(id, auth);
        if (run.getStorageKey() == null) {
            throw new ResourceNotFoundException("Execucao nao possui artefato");
        }
        if (run.getExpiresAt() != null && Instant.now().isAfter(run.getExpiresAt())) {
            throw new ReportExpiredException("Execucao expirou");
        }
        try {
            // Ressalva 1: quando assinado, devolvemos a versao assinada; o header
            // X-Report-Hash passa a refletir o signatureHash (hash dos bytes
            // retornados). O hash original continua disponivel em
            // X-Report-Original-Hash para cadeia de custodia.
            boolean isSigned = run.getSignedStorageKey() != null && run.getSignatureHash() != null;
            String keyToLoad = isSigned ? run.getSignedStorageKey() : run.getStorageKey();
            String hashHeader = isSigned ? run.getSignatureHash() : run.getSha256();
            String originalHashHeader = isSigned ? run.getSha256() : null;

            byte[] bytes = reportStorage.load(keyToLoad);

            // Auditoria (append-only) — ISO 15189:2022 item 8.4.1
            persistDownloadAudit(run, auth, isSigned ? "signed" : "original", bytes.length, clientIp, userAgent);

            // Metrica Prometheus
            ReportCode code = safeReportCode(run.getReportCode());
            if (code != null) {
                metrics.recordDownload(code, isSigned ? "signed" : "original");
            }

            return new DownloadResult(
                bytes,
                mediaTypeFor(run.getFormat()),
                run.getReportNumber() + "." + extensionForName(run.getFormat()),
                run.getReportNumber(),
                hashHeader,
                originalHashHeader
            );
        } catch (IOException ex) {
            throw new ResourceNotFoundException("Artefato nao encontrado no storage");
        }
    }

    private void persistDownloadAudit(ReportRun run, Authentication auth, String version,
                                      long sizeBytes, String clientIp, String userAgent) {
        UUID userId = null;
        String userName = null;
        if (auth != null && auth.isAuthenticated()) {
            userName = auth.getName();
            // best-effort user lookup
            try {
                userId = userRepository.findByUsername(userName)
                    .map(u -> u.getId()).orElse(null);
            } catch (RuntimeException ignored) {
                // auditoria nunca quebra o download
            }
        }
        String correlationId = org.slf4j.MDC.get("correlationId");
        // Trim user-agent para caber em VARCHAR(500)
        String uaTrimmed = userAgent == null ? null : (userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);

        com.bioqc.entity.ReportDownloadLog log = new com.bioqc.entity.ReportDownloadLog(
            UUID.randomUUID(),
            run.getId(),
            run.getReportNumber(),
            "signed".equals(version) ? run.getSignatureHash() : run.getSha256(),
            version,
            sizeBytes,
            userId,
            userName,
            Instant.now(),
            clientIp,
            uaTrimmed,
            correlationId
        );
        if (downloadLogRepository == null) {
            return;
        }
        try {
            downloadLogRepository.save(log);
        } catch (RuntimeException ex) {
            // Auditoria nao deve derrubar download. Log e segue.
            LOG.warn("Falha ao persistir ReportDownloadLog — prosseguindo com download", ex);
        }
    }

    private ReportCode safeReportCode(String name) {
        if (name == null) return null;
        try {
            return ReportCode.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ---------- Internals ----------

    private ReportRun loadForAuthenticatedUser(UUID id, Authentication auth) {
        ReportRun run = reportRunRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Execucao V2 nao encontrada: " + id));
        Set<String> roles = extractRoles(auth);
        if (!roles.contains("ADMIN") && !roles.contains("VIGILANCIA_SANITARIA")) {
            String authName = auth == null ? null : auth.getName();
            if (authName == null || run.getUsername() == null || !run.getUsername().equals(authName)) {
                throw new AccessDeniedException("Execucao pertence a outro usuario");
            }
        }
        return run;
    }

    private Specification<ReportRun> executionsSpecification(
        String code, String status, String username, Instant from, Instant to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("reportCode")));
            if (code != null && !code.isBlank()) {
                predicates.add(cb.equal(root.get("reportCode"), code));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (username != null && !username.isBlank()) {
                predicates.add(cb.equal(
                    cb.lower(root.get("username")),
                    username.toLowerCase(Locale.ROOT)
                ));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private GenerationContext buildContext(Authentication auth) {
        UUID userId = resolveUserId(auth);
        String username = auth == null ? null : auth.getName();
        Set<String> roles = extractRoles(auth);
        LabSettings settings = labSettingsService.getOrCreateSingleton();
        // T4: reaproveita correlationId do MDC (preenchido por
        // CorrelationIdFilter). Fallback: UUID novo se nao houver MDC
        // (execucoes de teste, chamadas fora de HTTP).
        String correlationId = org.slf4j.MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }
        return new GenerationContext(
            userId,
            username,
            roles,
            Instant.now(),
            DEFAULT_ZONE,
            settings,
            correlationId,
            UUID.randomUUID().toString()
        );
    }

    private UUID resolveUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        Optional<User> user = userRepository.findByUsername(auth.getName());
        return user.map(User::getId).orElse(null);
    }

    private Set<String> extractRoles(Authentication auth) {
        if (auth == null) return Set.of();
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        if (authorities == null) return Set.of();
        Set<String> roles = new HashSet<>();
        for (GrantedAuthority a : authorities) {
            String name = a.getAuthority();
            if (name == null) continue;
            if (name.startsWith("ROLE_")) {
                roles.add(name.substring(5));
            } else {
                // Tolerancia para strings nao prefixadas
                for (Role r : Role.values()) {
                    if (r.name().equals(name)) roles.add(name);
                }
            }
        }
        return Set.copyOf(roles);
    }

    private String yearMonthFromReportNumber(String number) {
        // BIO-AAAAMM-NNNNNN → AAAAMM
        if (number == null) return DateTimeFormatter.ofPattern("yyyyMM").format(Instant.now().atZone(DEFAULT_ZONE));
        String[] parts = number.split("-");
        return parts.length >= 2 ? parts[1] : DateTimeFormatter.ofPattern("yyyyMM").format(Instant.now().atZone(DEFAULT_ZONE));
    }

    private String extensionFor(ReportFormat format) {
        return switch (format) {
            case PDF -> "pdf";
            case HTML -> "html";
            case XLSX -> "xlsx";
        };
    }

    private String extensionForName(String formatName) {
        if (formatName == null) return "pdf";
        try {
            return extensionFor(ReportFormat.valueOf(formatName));
        } catch (IllegalArgumentException ex) {
            return "pdf";
        }
    }

    private String mediaTypeFor(String formatName) {
        if (formatName == null) return "application/pdf";
        try {
            ReportFormat f = ReportFormat.valueOf(formatName);
            return switch (f) {
                case PDF -> "application/pdf";
                case HTML -> "text/html";
                case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            };
        } catch (IllegalArgumentException ex) {
            return "application/octet-stream";
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    /**
     * Resultado do download com headers ja resolvidos pelo service.
     *
     * @param sha256         hash dos <em>bytes retornados</em> (= signatureHash
     *                       quando assinado, sha256 original caso contrario)
     * @param originalSha256 hash do PDF pre-assinatura; non-null apenas para
     *                       execucoes ja assinadas (preserva cadeia de custodia)
     */
    public record DownloadResult(
        byte[] bytes,
        String contentType,
        String suggestedFilename,
        String reportNumber,
        String sha256,
        String originalSha256
    ) {}
}
