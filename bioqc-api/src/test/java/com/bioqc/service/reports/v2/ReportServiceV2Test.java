package com.bioqc.service.reports.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bioqc.config.ReportsV2Properties;
import com.bioqc.dto.reports.v2.GenerateReportV2Request;
import com.bioqc.dto.reports.v2.PreviewReportV2Request;
import com.bioqc.dto.reports.v2.PreviewResponse;
import com.bioqc.dto.reports.v2.ReportExecutionResponse;
import com.bioqc.dto.reports.v2.SignReportV2Request;
import com.bioqc.dto.reports.v2.VerifyReportResponse;
import com.bioqc.entity.LabSettings;
import com.bioqc.entity.ReportRun;
import com.bioqc.entity.ReportSignatureLog;
import com.bioqc.repository.ReportRunRepository;
import com.bioqc.repository.ReportSignatureLogRepository;
import com.bioqc.repository.UserRepository;
import com.bioqc.service.LabSettingsService;
import com.bioqc.service.ReportRunService;
import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.catalog.ReportDefinitionRegistry;
import com.bioqc.service.reports.v2.catalog.ReportFormat;
import com.bioqc.service.reports.v2.generator.GenerationContext;
import com.bioqc.service.reports.v2.generator.ReportArtifact;
import com.bioqc.service.reports.v2.generator.ReportFilters;
import com.bioqc.service.reports.v2.generator.ReportGenerator;
import com.bioqc.service.reports.v2.generator.ReportGeneratorRegistry;
import com.bioqc.service.reports.v2.generator.ReportPreview;
import com.bioqc.service.reports.v2.storage.ReportStorage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class ReportServiceV2Test {

    @Mock ReportStorage storage;
    @Mock ReportRunRepository runRepository;
    @Mock ReportSignatureLogRepository signatureLogRepository;
    @Mock UserRepository userRepository;

    // Classes concretas (nao-interfaces) nao podem ser mockadas em Java 25 com
    // a versao atual do Mockito — usamos stubs manuais.
    private final ReportSigner signer = new ReportSigner();

    private LabSettings labSettingsFixture;
    private final LabSettingsService labSettingsService = new LabSettingsService(null, null) {
        @Override public com.bioqc.entity.LabSettings getOrCreateSingleton() {
            return labSettingsFixture;
        }
    };

    private ReportDefinitionRegistry definitionRegistry;
    private ReportGeneratorRegistry generatorRegistry;
    private ReportRunService runService;
    private FilterValidator validator;
    private ReportsV2Properties properties;
    private ReportServiceV2 service;
    private StubGenerator stubGenerator;

    @BeforeEach
    void setUp() {
        labSettingsFixture = LabSettings.builder()
            .responsibleName("Dra Ana")
            .responsibleRegistration("CRF-1")
            .build();
        definitionRegistry = new ReportDefinitionRegistry();
        stubGenerator = new StubGenerator();
        generatorRegistry = new ReportGeneratorRegistry(providerOf(List.of(stubGenerator)));
        validator = new FilterValidator();
        runService = new ReportRunService(runRepository);
        properties = new ReportsV2Properties();
        properties.setPublicBaseUrl("http://localhost:5173");
        service = new ReportServiceV2(
            definitionRegistry, generatorRegistry, validator, storage, signer,
            runService, runRepository, signatureLogRepository, userRepository, labSettingsService, properties,
            null,
            null,  // ReportDownloadLogRepository — ignorado em unit test (auditoria best-effort)
            new ReportV2Metrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
        );
        lenient().when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        lenient().when(runRepository.save(any(ReportRun.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(signatureLogRepository.save(any(ReportSignatureLog.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(signatureLogRepository.findBySignatureHash(any())).thenReturn(Optional.empty());
        lenient().when(signatureLogRepository.findByOriginalSha256OrderBySignedAtDesc(any()))
            .thenReturn(List.of());
    }

    @Test
    @DisplayName("listCatalog filtra por roles")
    void listCatalogFilters() {
        // Apos expansao Fase 1, ADMIN enxerga 7 tipos
        assertThat(service.listCatalog(authWithRole("ADMIN"))).hasSize(7);
        assertThat(service.listCatalog(authWithRole("VISUALIZADOR"))).isEmpty();
    }

    @Test
    @DisplayName("generate: filtros invalidos disparam InvalidFilterException")
    void generateInvalidFilters() {
        GenerateReportV2Request req = new GenerateReportV2Request(
            ReportCode.CQ_OPERATIONAL_V2, ReportFormat.PDF, Map.of() // vazio — area/periodType obrigatorios
        );
        assertThatThrownBy(() -> service.generate(req, authWithRole("ADMIN")))
            .isInstanceOf(InvalidFilterException.class);
    }

    @Test
    @DisplayName("generate: role nao autorizada dispara AccessDeniedException")
    void generateAccessDenied() {
        GenerateReportV2Request req = new GenerateReportV2Request(
            ReportCode.CQ_OPERATIONAL_V2, ReportFormat.PDF,
            Map.of("area", "bioquimica", "periodType", "current-month")
        );
        assertThatThrownBy(() -> service.generate(req, authWithRole("VISUALIZADOR")))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("generate: gera, salva e registra execucao com sucesso")
    void generateHappyPath() throws Exception {
        when(storage.save(any(byte[].class), any(ReportStorage.StorageKeyHint.class)))
            .thenReturn("reports/v2/202604/CQ_OPERATIONAL_V2/BIO-202604-000001.pdf");

        GenerateReportV2Request req = new GenerateReportV2Request(
            ReportCode.CQ_OPERATIONAL_V2, ReportFormat.PDF,
            Map.of("area", "bioquimica", "periodType", "current-month")
        );
        ReportExecutionResponse res = service.generate(req, authWithRole("FUNCIONARIO"));

        assertThat(res).isNotNull();
        assertThat(res.reportCode()).isEqualTo("CQ_OPERATIONAL_V2");
        assertThat(res.format()).isEqualTo("PDF");
        assertThat(res.reportNumber()).isEqualTo("BIO-202604-000001");
        assertThat(res.sha256()).hasSize(64);
        assertThat(res.downloadUrl()).contains("/api/reports/v2/executions/");
        assertThat(res.verifyUrl()).startsWith("http://localhost:5173/r/verify/");
        verify(storage).save(any(byte[].class), any(ReportStorage.StorageKeyHint.class));
        verify(runRepository).save(any(ReportRun.class));
    }

    @Test
    @DisplayName("generate: warnings ficam persistidos e status vira WITH_WARNINGS")
    void generateWithWarningsPersistsWarnings() throws Exception {
        stubGenerator.warnings = List.of("Secao 'KPIs de Manutencao' falhou — conteudo omitido");
        when(storage.save(any(byte[].class), any(ReportStorage.StorageKeyHint.class)))
            .thenReturn("reports/v2/202604/CQ_OPERATIONAL_V2/BIO-202604-000001.pdf");

        GenerateReportV2Request req = new GenerateReportV2Request(
            ReportCode.CQ_OPERATIONAL_V2, ReportFormat.PDF,
            Map.of("area", "bioquimica", "periodType", "current-month")
        );
        ReportExecutionResponse res = service.generate(req, authWithRole("FUNCIONARIO"));

        assertThat(res.status()).isEqualTo(ReportRunService.STATUS_WITH_WARNINGS);
        assertThat(res.warnings()).containsExactly("Secao 'KPIs de Manutencao' falhou — conteudo omitido");
        ArgumentCaptor<ReportRun> captor = ArgumentCaptor.forClass(ReportRun.class);
        verify(runRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReportRunService.STATUS_WITH_WARNINGS);
        assertThat(captor.getValue().getWarnings()).contains("KPIs de Manutencao");
    }

    @Test
    @DisplayName("listExecutions usa Specification V2-only e retorna warnings persistidos")
    void listExecutionsUsesSpecificationAndMapsWarnings() {
        ReportRun run = ReportRun.builder()
            .id(UUID.randomUUID())
            .type("V2")
            .reportCode("CQ_OPERATIONAL_V2")
            .format("PDF")
            .status(ReportRunService.STATUS_WITH_WARNINGS)
            .reportNumber("BIO-202604-000001")
            .sha256("a".repeat(64))
            .username("tester")
            .createdAt(Instant.now())
            .warnings("[\"Pacote parcial\"]")
            .build();
        when(runRepository.findAll(
            ArgumentMatchers.<Specification<ReportRun>>any(),
            any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(run), PageRequest.of(0, 20), 1));

        var page = service.listExecutions(null, null, null, null, authWithRole("ADMIN"), PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).warnings()).containsExactly("Pacote parcial");
        verify(runRepository).findAll(
            ArgumentMatchers.<Specification<ReportRun>>any(),
            any(org.springframework.data.domain.Pageable.class)
        );
    }

    @Test
    @DisplayName("preview retorna HTML sem chamar storage")
    void previewDoesNotPersist() throws Exception {
        PreviewReportV2Request req = new PreviewReportV2Request(
            ReportCode.CQ_OPERATIONAL_V2,
            Map.of("area", "bioquimica", "periodType", "current-month")
        );
        PreviewResponse res = service.preview(req, authWithRole("FUNCIONARIO"));
        assertThat(res.html()).contains("preview-stub");
        verify(storage, never()).save(any(), any());
    }

    // ---------- Verify (Ressalva 5) ----------

    @Test
    @DisplayName("verify: hash desconhecido retorna 200 com valid=false (Ressalva 5)")
    void verifyHashDesconhecidoRetornaValidFalse() {
        when(runRepository.findBySha256OrSignatureHash(eq("zzz"))).thenReturn(List.of());
        VerifyReportResponse res = service.verify("zzz");
        assertThat(res.valid()).isFalse();
        assertThat(res.signed()).isFalse();
        assertThat(res.reportNumber()).isNull();
        assertThat(res.sha256()).isNull();
        assertThat(res.signatureHash()).isNull();
    }

    @Test
    @DisplayName("verify: hash vazio retorna 200 com valid=false")
    void verifyHashVazioRetornaValidFalse() {
        VerifyReportResponse res = service.verify("");
        assertThat(res.valid()).isFalse();
    }

    @Test
    @DisplayName("verify: hash original de run salvo retorna response completo, valid=true")
    void verifyHashOriginalRunRetornaResponseCompleto() {
        String hash = "a".repeat(64);
        ReportRun run = ReportRun.builder()
            .id(UUID.randomUUID())
            .type("V2")
            .reportCode("CQ_OPERATIONAL_V2")
            .reportNumber("BIO-202604-000001")
            .sha256(hash)
            .username("ana")
            .periodType("current-month")
            .status("SUCCESS")
            .createdAt(Instant.now())
            .build();
        when(runRepository.findBySha256OrSignatureHash(eq(hash))).thenReturn(List.of(run));
        VerifyReportResponse res = service.verify(hash);
        assertThat(res.valid()).isTrue();
        assertThat(res.reportNumber()).isEqualTo("BIO-202604-000001");
        assertThat(res.sha256()).isEqualTo(hash);
        assertThat(res.generatedByName()).isEqualTo("ana");
        assertThat(res.signed()).isFalse();
    }

    @Test
    @DisplayName("verify: signatureHash conhecido retorna signed=true e signatureHash batendo")
    void verifySignatureHashBate() {
        String sigHash = "b".repeat(64);
        String origHash = "a".repeat(64);
        ReportRun run = ReportRun.builder()
            .id(UUID.randomUUID())
            .reportNumber("BIO-202604-000001")
            .reportCode("CQ_OPERATIONAL_V2")
            .sha256(origHash)
            .signatureHash(sigHash)
            .signedAt(Instant.now())
            .status("SIGNED")
            .createdAt(Instant.now())
            .build();
        when(runRepository.findBySha256OrSignatureHash(eq(sigHash))).thenReturn(List.of(run));
        VerifyReportResponse res = service.verify(sigHash);
        assertThat(res.valid()).isTrue();
        assertThat(res.signed()).isTrue();
        assertThat(res.signatureHash()).isEqualTo(sigHash);
        assertThat(res.sha256()).isEqualTo(origHash);
    }

    @Test
    @DisplayName("verify: encontra via report_signature_log quando run foi apagado")
    void verifyEncontraViaSignatureLog() {
        String sigHash = "c".repeat(64);
        String origHash = "d".repeat(64);
        UUID runId = UUID.randomUUID();
        ReportSignatureLog log = ReportSignatureLog.builder()
            .id(UUID.randomUUID())
            .reportRunId(runId)
            .reportNumber("BIO-202604-000002")
            .originalSha256(origHash)
            .signatureHash(sigHash)
            .signedByUserId(UUID.randomUUID())
            .signedByName("Dra Ana")
            .signerRegistration("CRF-1")
            .signedStorageKey("reports/v2/202604/X/BIO.signed.pdf")
            .signedAt(Instant.now())
            .build();
        when(runRepository.findBySha256OrSignatureHash(eq(sigHash))).thenReturn(List.of());
        when(signatureLogRepository.findBySignatureHash(eq(sigHash))).thenReturn(Optional.of(log));

        VerifyReportResponse res = service.verify(sigHash);
        assertThat(res.valid()).isTrue();
        assertThat(res.signed()).isTrue();
        assertThat(res.reportNumber()).isEqualTo("BIO-202604-000002");
        assertThat(res.signedByName()).isEqualTo("Dra Ana");
    }

    // ---------- Sign (Ressalvas 1, 2, 8) ----------

    @Test
    @DisplayName("sign: execucao ja assinada dispara 409")
    void signAlreadySigned() {
        ReportRun run = ReportRun.builder()
            .id(UUID.randomUUID())
            .reportCode("CQ_OPERATIONAL_V2")
            .storageKey("key")
            .sha256("abc")
            .signatureHash("already")
            .status("SIGNED")
            .build();
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        assertThatThrownBy(() -> service.sign(run.getId(), null, authWithRole("ADMIN")))
            .isInstanceOf(ReportAlreadySignedException.class);
    }

    @Test
    @DisplayName("sign: role FUNCIONARIO nao pode assinar")
    void signRequiresAdmin() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.sign(id, null, authWithRole("FUNCIONARIO")))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("sign: signerRegistration ausente e presente em LabSettings -> assina com sucesso")
    void signComRegistrationNoLabSettingsAssina() throws Exception {
        // LabSettings ja tem responsibleRegistration="CRF-1" no setUp
        ReportRun run = buildUnsignedRun();
        byte[] originalBytes = buildMinimalPdf();
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(storage.load(eq("reports/v2/202604/CQ_OPERATIONAL_V2/BIO-202604-000001.pdf")))
            .thenReturn(originalBytes);
        when(storage.save(any(byte[].class), any(ReportStorage.StorageKeyHint.class)))
            .thenReturn("reports/v2/202604/CQ_OPERATIONAL_V2/BIO-202604-000001.signed.pdf");

        SignReportV2Request req = new SignReportV2Request(null, null);
        ReportExecutionResponse res = service.sign(run.getId(), req, authWithRole("ADMIN"));

        assertThat(res.signatureHash()).isNotNull().hasSize(64);
        assertThat(res.signedSha256()).isEqualTo(res.signatureHash());
        // original permanece intacto — NAO chamamos storage.replace
        verify(storage, never()).replace(any(), any());
        // nova storageKey salva (para o signed)
        verify(storage).save(any(byte[].class), any(ReportStorage.StorageKeyHint.class));
    }

    @Test
    @DisplayName("sign: signerRegistration null e LabSettings vazio -> 422 InvalidSignerException (Ressalva 8)")
    void signSemRegistrationEmLugarNenhum422() throws Exception {
        labSettingsFixture = LabSettings.builder()
            .responsibleName("Dra Ana")
            .responsibleRegistration("")
            .build();
        ReportRun run = buildUnsignedRun();
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(storage.load(any())).thenReturn(buildMinimalPdf());

        SignReportV2Request req = new SignReportV2Request(null, null);
        assertThatThrownBy(() -> service.sign(run.getId(), req, authWithRole("ADMIN")))
            .isInstanceOf(InvalidSignerException.class)
            .hasMessageContaining("registro profissional");

        verify(signatureLogRepository, never()).save(any());
        verify(storage, never()).save(any(byte[].class), any(ReportStorage.StorageKeyHint.class));
    }

    @Test
    @DisplayName("sign: preserva storageKey original e cria signedStorageKey separado (Ressalva 1)")
    void signPreservaStorageKeyOriginal() throws Exception {
        ReportRun run = buildUnsignedRun();
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(storage.load(eq("reports/v2/202604/CQ_OPERATIONAL_V2/BIO-202604-000001.pdf")))
            .thenReturn(buildMinimalPdf());
        when(storage.save(any(byte[].class), any(ReportStorage.StorageKeyHint.class)))
            .thenReturn("reports/v2/202604/CQ_OPERATIONAL_V2/BIO-202604-000001.signed.pdf");

        service.sign(run.getId(), new SignReportV2Request(null, null), authWithRole("ADMIN"));

        // storageKey original nao foi sobrescrito
        assertThat(run.getStorageKey()).isEqualTo("reports/v2/202604/CQ_OPERATIONAL_V2/BIO-202604-000001.pdf");
        assertThat(run.getSignedStorageKey())
            .isEqualTo("reports/v2/202604/CQ_OPERATIONAL_V2/BIO-202604-000001.signed.pdf");
        assertThat(run.getSha256()).isEqualTo("a".repeat(64)); // hash original intacto
        assertThat(run.getSignatureHash()).isNotNull();
        verify(storage, never()).replace(any(), any());
    }

    @Test
    @DisplayName("sign: persiste exatamente 1 ReportSignatureLog (Ressalva 2)")
    void signPersisteReportSignatureLog() throws Exception {
        ReportRun run = buildUnsignedRun();
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));
        when(storage.load(any())).thenReturn(buildMinimalPdf());
        when(storage.save(any(byte[].class), any(ReportStorage.StorageKeyHint.class)))
            .thenReturn("signedKey");

        service.sign(run.getId(), new SignReportV2Request(null, null), authWithRole("ADMIN"));

        ArgumentCaptor<ReportSignatureLog> captor = ArgumentCaptor.forClass(ReportSignatureLog.class);
        verify(signatureLogRepository).save(captor.capture());
        ReportSignatureLog saved = captor.getValue();
        assertThat(saved.getReportRunId()).isEqualTo(run.getId());
        assertThat(saved.getReportNumber()).isEqualTo("BIO-202604-000001");
        assertThat(saved.getOriginalSha256()).isEqualTo("a".repeat(64));
        assertThat(saved.getSignatureHash()).isNotNull().hasSize(64);
        assertThat(saved.getSignerRegistration()).isEqualTo("CRF-1");
        assertThat(saved.getSignedStorageKey()).isEqualTo("signedKey");
    }

    // ---------- Download (Ressalva 1) ----------

    @Test
    @DisplayName("download: apos assinatura retorna versao assinada e hash signature")
    void downloadAposSignatureRetornaVersaoAssinada() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] signedBytes = "signed-pdf-bytes".getBytes();
        ReportRun run = ReportRun.builder()
            .id(id)
            .reportCode("CQ_OPERATIONAL_V2")
            .reportNumber("BIO-202604-000001")
            .storageKey("orig-key")
            .signedStorageKey("signed-key")
            .sha256("a".repeat(64))
            .signatureHash("b".repeat(64))
            .format("PDF")
            .status("SIGNED")
            .build();
        when(runRepository.findById(id)).thenReturn(Optional.of(run));
        when(storage.load(eq("signed-key"))).thenReturn(signedBytes);

        ReportServiceV2.DownloadResult res = service.download(id, authWithRole("ADMIN"));

        assertThat(res.bytes()).isEqualTo(signedBytes);
        assertThat(res.sha256()).isEqualTo("b".repeat(64));         // hash dos bytes retornados = signature
        assertThat(res.originalSha256()).isEqualTo("a".repeat(64)); // hash original preservado
    }

    @Test
    @DisplayName("download: sem signature retorna versao original e hash original, sem originalSha256")
    void downloadSemSignatureRetornaOriginal() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] origBytes = "orig-pdf-bytes".getBytes();
        ReportRun run = ReportRun.builder()
            .id(id)
            .reportCode("CQ_OPERATIONAL_V2")
            .reportNumber("BIO-202604-000001")
            .storageKey("orig-key")
            .sha256("a".repeat(64))
            .format("PDF")
            .status("SUCCESS")
            .build();
        when(runRepository.findById(id)).thenReturn(Optional.of(run));
        when(storage.load(eq("orig-key"))).thenReturn(origBytes);

        ReportServiceV2.DownloadResult res = service.download(id, authWithRole("ADMIN"));

        assertThat(res.bytes()).isEqualTo(origBytes);
        assertThat(res.sha256()).isEqualTo("a".repeat(64));
        assertThat(res.originalSha256()).isNull();
    }

    // ---------- T8: expired download -> 410 ----------

    @Test
    @DisplayName("T8 — download de execucao expirada lanca ReportExpiredException (mapeada em 410)")
    void downloadExpiredExecutionThrowsExpired() {
        UUID id = UUID.randomUUID();
        ReportRun run = ReportRun.builder()
            .id(id)
            .reportCode("CQ_OPERATIONAL_V2")
            .reportNumber("BIO-202604-000001")
            .storageKey("orig-key")
            .sha256("a".repeat(64))
            .format("PDF")
            .status("SUCCESS")
            // Expirou 1h atras
            .expiresAt(Instant.now().minusSeconds(3600))
            .build();
        when(runRepository.findById(id)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.download(id, authWithRole("ADMIN")))
            .isInstanceOf(ReportExpiredException.class);
    }

    @Test
    @DisplayName("T8 — sign em execucao expirada tambem lanca ReportExpiredException")
    void signExpiredExecutionThrowsExpired() {
        UUID id = UUID.randomUUID();
        ReportRun run = ReportRun.builder()
            .id(id)
            .reportCode("CQ_OPERATIONAL_V2")
            .reportNumber("BIO-202604-000001")
            .storageKey("orig-key")
            .sha256("a".repeat(64))
            .format("PDF")
            .status("SUCCESS")
            .expiresAt(Instant.now().minusSeconds(60))
            .build();
        when(runRepository.findById(id)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.sign(id, null, authWithRole("ADMIN")))
            .isInstanceOf(ReportExpiredException.class);
    }

    // ---------- helpers ----------

    private ReportRun buildUnsignedRun() {
        return ReportRun.builder()
            .id(UUID.randomUUID())
            .type("V2")
            .reportCode("CQ_OPERATIONAL_V2")
            .reportNumber("BIO-202604-000001")
            .storageKey("reports/v2/202604/CQ_OPERATIONAL_V2/BIO-202604-000001.pdf")
            .sha256("a".repeat(64))
            .format("PDF")
            .status("SUCCESS")
            .build();
    }

    /** Gera um PDF minimo valido com uma pagina para que ReportSigner nao falhe no parse. */
    private byte[] buildMinimalPdf() {
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            com.lowagie.text.Document doc = new com.lowagie.text.Document();
            com.lowagie.text.pdf.PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new com.lowagie.text.Paragraph("stub"));
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Authentication authWithRole(String role) {
        return new UsernamePasswordAuthenticationToken(
            "tester", "pw", List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T value) {
        return new ObjectProvider<T>() {
            @Override public T getObject(Object... args) { return value; }
            @Override public T getObject() { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfUnique() { return value; }
        };
    }

    // Stub generator que nao depende de repositorios
    static class StubGenerator implements ReportGenerator {
        List<String> warnings = List.of();

        @Override
        public com.bioqc.service.reports.v2.catalog.ReportDefinition definition() {
            return ReportDefinitionRegistry.CQ_OPERATIONAL_V2_DEFINITION;
        }

        @Override
        public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
            byte[] fakePdf = "%PDF-stub-gen".getBytes();
            return new ReportArtifact(
                fakePdf, "application/pdf", "BIO-202604-000001.pdf",
                1, fakePdf.length, "BIO-202604-000001",
                "a".repeat(64), "Abril/2026", warnings
            );
        }

        @Override
        public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
            return new ReportPreview("<div class=\"preview-stub\">OK</div>", List.of(), "Abril/2026");
        }
    }
}
