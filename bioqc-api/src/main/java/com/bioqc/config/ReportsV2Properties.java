package com.bioqc.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracao do subsistema Reports V2 (feature-flagged).
 *
 * {@code reports.v2.enabled=false} (default) deixa todo o codigo V2 fora do runtime
 * — o controller V2 nao e registrado via {@code @ConditionalOnProperty}. Quando
 * habilitado, {@code storage.dir} e {@code publicBaseUrl} passam a ser obrigatorios
 * e sao validados no startup.
 */
@Component
@ConfigurationProperties(prefix = "reports.v2")
@Validated
public class ReportsV2Properties {

    private boolean enabled = false;

    @NotNull
    @Valid
    private Storage storage = new Storage();

    /**
     * Base URL publica do frontend (usada para montar {@code verifyUrl} nos
     * QR codes assinados). Obrigatorio quando {@code enabled=true}.
     */
    private String publicBaseUrl;

    /**
     * Retencao padrao em dias do artefato PDF quando a {@code ReportDefinition}
     * nao especifica valor proprio. 5 anos (1825 dias) para alinhar com RDC
     * ANVISA 786/2023 + ISO 15189:2022. Nao afeta auditoria/signature log.
     */
    @Min(1)
    private int defaultRetentionDays = 1825;

    @Min(1)
    private int verifyRateLimitPerMinutePerIp = 10;

    /**
     * Tolerancia minima (em pontos percentuais de CV) para classificar uma
     * calibracao como EFICAZ. Padrao: 0.5 — variacoes menores sao ruido
     * analitico e nao indicam melhora real.
     *
     * <p>Regras de classificacao em {@code CalibracaoPrePostGenerator} e
     * {@code CqOperationalV2Generator}:
     * <ul>
     *   <li>{@code delta <= -tolerancia} → EFICAZ (CV caiu o suficiente)</li>
     *   <li>{@code -tolerancia < delta < +tolerancia} → SEM EFEITO (dentro do ruido)</li>
     *   <li>{@code delta >= +tolerancia} → PIOROU</li>
     * </ul>
     */
    private double calibrationEffectiveDeltaTolerance = 0.5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public int getDefaultRetentionDays() {
        return defaultRetentionDays;
    }

    public void setDefaultRetentionDays(int defaultRetentionDays) {
        this.defaultRetentionDays = defaultRetentionDays;
    }

    public int getVerifyRateLimitPerMinutePerIp() {
        return verifyRateLimitPerMinutePerIp;
    }

    public void setVerifyRateLimitPerMinutePerIp(int verifyRateLimitPerMinutePerIp) {
        this.verifyRateLimitPerMinutePerIp = verifyRateLimitPerMinutePerIp;
    }

    public double getCalibrationEffectiveDeltaTolerance() {
        return calibrationEffectiveDeltaTolerance;
    }

    public void setCalibrationEffectiveDeltaTolerance(double calibrationEffectiveDeltaTolerance) {
        this.calibrationEffectiveDeltaTolerance = calibrationEffectiveDeltaTolerance;
    }

    public static class Storage {

        /**
         * Diretorio raiz para armazenamento local dos PDFs gerados. Obrigatorio
         * quando {@code reports.v2.enabled=true}.
         */
        private String dir;

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }
    }
}
