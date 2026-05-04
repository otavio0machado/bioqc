package com.bioqc.service.reports.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bioqc.service.reports.v2.catalog.ReportCode;
import com.bioqc.service.reports.v2.catalog.ReportDefinition;
import com.bioqc.service.reports.v2.catalog.ReportDefinitionRegistry;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportDefinitionRegistryTest {

    private final ReportDefinitionRegistry registry = new ReportDefinitionRegistry();

    @Test
    @DisplayName("resolve retorna a definicao CQ_OPERATIONAL_V2")
    void resolveReturnsDefinition() {
        ReportDefinition def = registry.resolve(ReportCode.CQ_OPERATIONAL_V2);
        assertThat(def).isNotNull();
        assertThat(def.code()).isEqualTo(ReportCode.CQ_OPERATIONAL_V2);
        assertThat(def.name()).contains("CQ");
        assertThat(def.filterSpec().fields()).isNotEmpty();
    }

    @Test
    @DisplayName("canAccess retorna true para roles autorizadas")
    void canAccessTrueForAuthorizedRoles() {
        assertThat(registry.canAccess(ReportCode.CQ_OPERATIONAL_V2, Set.of("ADMIN"))).isTrue();
        assertThat(registry.canAccess(ReportCode.CQ_OPERATIONAL_V2, Set.of("FUNCIONARIO"))).isTrue();
        assertThat(registry.canAccess(ReportCode.CQ_OPERATIONAL_V2, Set.of("VIGILANCIA_SANITARIA"))).isTrue();
    }

    @Test
    @DisplayName("canAccess retorna false para role VISUALIZADOR")
    void canAccessFalseForVisualizador() {
        assertThat(registry.canAccess(ReportCode.CQ_OPERATIONAL_V2, Set.of("VISUALIZADOR"))).isFalse();
    }

    @Test
    @DisplayName("forUserRoles filtra por intersecao com roleAccess")
    void forUserRolesFiltersIntersection() {
        // Com 7 ReportCodes apos expansao Fase 1; ADMIN tem acesso a todos
        assertThat(registry.forUserRoles(Set.of("ADMIN"))).hasSize(7);
        assertThat(registry.forUserRoles(Set.of("VISUALIZADOR"))).isEmpty();
        assertThat(registry.forUserRoles(Set.of())).isEmpty();
        // FUNCIONARIO tem acesso aos 5 de uso corrente (nao tem MULTI_AREA nem REGULATORIO)
        assertThat(registry.forUserRoles(Set.of("FUNCIONARIO"))).hasSize(5);
    }

    @Test
    @DisplayName("CQ_OPERATIONAL_V2 tem retentionDays=1825 (5 anos, Ressalva 6)")
    void cqOperationalV2RetentionEh1825() {
        ReportDefinition def = registry.resolve(ReportCode.CQ_OPERATIONAL_V2);
        assertThat(def.retentionDays()).isEqualTo(1825);
    }

    @Test
    @DisplayName("resolve dispara ReportCodeNotFoundException para codigo ausente do map")
    void resolveThrowsForMissing() {
        // Nao temos outro ReportCode alem de CQ_OPERATIONAL_V2 hoje; o teste cobre
        // fluxo null-check via API resolveOrNull + uma chamada hipotetica.
        assertThat(registry.resolveOrNull(ReportCode.CQ_OPERATIONAL_V2)).isNotNull();
        // Simula codigo nao-mapeado (null code para forcar null no map)
        assertThatThrownBy(() -> registry.resolve(null))
            .isInstanceOf(ReportCodeNotFoundException.class);
    }
}
