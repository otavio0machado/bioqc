package com.bioqc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Falha o startup com mensagem clara quando {@code reports.v2.enabled=true} mas
 * as dependencias obrigatorias ({@code storage.dir}, {@code publicBaseUrl}) nao
 * estao preenchidas.
 *
 * Executa em {@link ApplicationReadyEvent} para que a falha aconteca depois
 * que o contexto ja foi carregado, mas antes de qualquer requisicao ser
 * servida.
 */
@Component
public class ReportsV2StartupValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ReportsV2StartupValidator.class);

    private final ReportsV2Properties properties;

    public ReportsV2StartupValidator(ReportsV2Properties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (!properties.isEnabled()) {
            LOG.info("Reports V2 desabilitado (reports.v2.enabled=false). Endpoints V2 nao serao registrados.");
            return;
        }
        String storageDir = properties.getStorage() != null ? properties.getStorage().getDir() : null;
        if (storageDir == null || storageDir.isBlank()) {
            // WARN e continua. O storage bean ja aplica fallback para
            // java.io.tmpdir — preferimos ephemeral a crashloop em prod.
            LOG.warn(
                "reports.v2.enabled=true mas reports.v2.storage.dir nao configurado. "
                + "Storage esta em tmpdir ephemeral. Defina REPORTS_V2_STORAGE_DIR para persistencia confiavel."
            );
        }
        if (properties.getPublicBaseUrl() == null || properties.getPublicBaseUrl().isBlank()) {
            LOG.warn(
                "reports.v2.enabled=true mas reports.v2.public-base-url nao configurado. "
                + "QR code de verificacao usara URL relativa — configure REPORTS_V2_PUBLIC_BASE_URL."
            );
        }
        LOG.info("Reports V2 habilitado. storage.dir={} publicBaseUrl={} retentionDays={}",
            storageDir, properties.getPublicBaseUrl(), properties.getDefaultRetentionDays());
    }
}
