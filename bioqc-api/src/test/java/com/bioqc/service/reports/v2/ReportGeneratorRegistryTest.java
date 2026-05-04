package com.bioqc.service.reports.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.catalog.ReportDefinition;
import com.bioqc.service.reports.v2.catalog.ReportDefinitionRegistry;
import com.bioqc.service.reports.v2.generator.GenerationContext;
import com.bioqc.service.reports.v2.generator.ReportArtifact;
import com.bioqc.service.reports.v2.generator.ReportFilters;
import com.bioqc.service.reports.v2.generator.ReportGenerator;
import com.bioqc.service.reports.v2.generator.ReportGeneratorRegistry;
import com.bioqc.service.reports.v2.generator.ReportPreview;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ReportGeneratorRegistryTest {

    @Test
    @DisplayName("registry indexa generator por codigo")
    void registryIndexesByCode() {
        ReportGenerator g = stubGenerator();
        ReportGeneratorRegistry reg = new ReportGeneratorRegistry(providerOf(List.of(g)));
        assertThat(reg.resolve(ReportCode.CQ_OPERATIONAL_V2)).isSameAs(g);
        assertThat(reg.hasGenerator(ReportCode.CQ_OPERATIONAL_V2)).isTrue();
    }

    @Test
    @DisplayName("duplicata no construtor falha com IllegalStateException")
    void duplicateGeneratorsFail() {
        ReportGenerator g1 = stubGenerator();
        ReportGenerator g2 = stubGenerator();
        assertThatThrownBy(() -> new ReportGeneratorRegistry(providerOf(List.of(g1, g2))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate");
    }

    @Test
    @DisplayName("resolve lanca ReportCodeNotFoundException quando nao ha generator")
    void resolveThrowsWhenMissing() {
        ReportGeneratorRegistry reg = new ReportGeneratorRegistry(providerOf(List.of()));
        assertThatThrownBy(() -> reg.resolve(ReportCode.CQ_OPERATIONAL_V2))
            .isInstanceOf(ReportCodeNotFoundException.class);
    }

    private ReportGenerator stubGenerator() {
        return new ReportGenerator() {
            @Override
            public ReportDefinition definition() {
                return ReportDefinitionRegistry.CQ_OPERATIONAL_V2_DEFINITION;
            }

            @Override
            public ReportArtifact generate(ReportFilters filters, GenerationContext ctx) {
                throw new UnsupportedOperationException("stub");
            }

            @Override
            public ReportPreview preview(ReportFilters filters, GenerationContext ctx) {
                throw new UnsupportedOperationException("stub");
            }
        };
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
}
