package com.bioqc.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bioqc.config.ReportsV2Properties;
import com.bioqc.config.ReportsV2StartupValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Garante por inspecao estatica que o controller V2 so e registrado com
 * {@code reports.v2.enabled=true}. Inicializar um {@code @WebMvcTest} com flag
 * OFF e complexo (o context carrega outros controllers e suas dependencias);
 * este teste cobre a garantia no nivel da anotacao.
 */
class ReportV2ControllerDisabledTest {

    @Test
    @DisplayName("ReportV2Controller so sobe quando reports.v2.enabled=true")
    void controllerHasConditionalOnProperty() {
        ConditionalOnProperty annotation =
            ReportV2Controller.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.prefix()).isEqualTo("reports.v2");
        assertThat(annotation.name()).containsExactly("enabled");
        assertThat(annotation.havingValue()).isEqualTo("true");
    }

    @Test
    @DisplayName("ReportsV2Properties default enabled=false")
    void propertiesDefaultDisabled() {
        ReportsV2Properties p = new ReportsV2Properties();
        assertThat(p.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("ReportsV2StartupValidator: enabled=false nao exige storage/publicBaseUrl")
    void startupValidatorSilentWhenDisabled() {
        ReportsV2Properties p = new ReportsV2Properties();
        p.setEnabled(false);
        new ReportsV2StartupValidator(p).validate();
        // sem excecao: passou
    }

    @Test
    @DisplayName("ReportsV2StartupValidator: enabled=true sem storage.dir degrada com WARN (nao lanca)")
    void startupValidatorWarnsWhenMisconfigured() {
        // Regressao: originalmente o validator lancava IllegalStateException quando
        // reports.v2.storage.dir nao estava configurado. Isso derrubava prod em
        // crashloop quando a flag era ligada antes de setar o env var. Agora o
        // validator apenas emite WARN e o LocalFilesystemReportStorage aplica
        // fallback para java.io.tmpdir.
        ReportsV2Properties p = new ReportsV2Properties();
        p.setEnabled(true);
        new ReportsV2StartupValidator(p).validate(); // nao lanca
    }
}
