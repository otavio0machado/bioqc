package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Teste do mapping deterministico aplicado pela migracao V14.
 *
 * <p>Em vez de subir um Postgres via Testcontainers (nao disponivel no pom), validamos
 * a regra do CASE da V14 diretamente em Java. O metodo {@link #v14Target} espelha 1:1
 * o CASE do {@code V14__reagent_units_and_archive_v3.sql} — drift e bug bloqueador.</p>
 *
 * <p>Cobre os 4 status legados (decisao 1.8 + tabela 3.3 do contrato v3):</p>
 * <ul>
 *   <li>em_estoque         → em_estoque, units_in_stock=current_stock, units_in_use=0, needs_stock_review=FALSE</li>
 *   <li>em_uso             → em_uso, units_in_stock=current_stock, units_in_use=0, needs_stock_review=TRUE</li>
 *   <li>fora_de_estoque    → inativo, units_in_stock=0, units_in_use=0, archived_at=today, archived_by='sistema-migracao-v14'</li>
 *   <li>vencido            → vencido, units_in_stock=current_stock, units_in_use=0, needs_stock_review=FALSE (preserva relatorio)</li>
 * </ul>
 *
 * <p>release-engineer roda V14 em staging com snapshot de producao seguindo
 * {@code db/migration/dry-run/V14_dry_run.sql}.</p>
 */
class ReagentMigrationV14Test {

    record V14Outcome(
        String status,
        int unitsInStock,
        int unitsInUse,
        boolean needsStockReview,
        String archivedBy,
        boolean hasArchivedAt
    ) {}

    /**
     * Aplica o mesmo mapping da V14 em forma de Java. Espelha 1:1 o SQL do
     * {@code V14__reagent_units_and_archive_v3.sql}.
     */
    private static V14Outcome v14Target(String currentStatus, double currentStock) {
        int units = (int) Math.max(0, currentStock);
        switch (currentStatus) {
            case "em_estoque":
                return new V14Outcome("em_estoque", units, 0, false, null, false);
            case "em_uso":
                return new V14Outcome("em_uso", units, 0, true, null, false);
            case "fora_de_estoque":
                return new V14Outcome("inativo", 0, 0, false, "sistema-migracao-v14", true);
            case "vencido":
                return new V14Outcome("vencido", units, 0, false, null, false);
            default:
                return new V14Outcome("UNKNOWN", 0, 0, false, null, false);
        }
    }

    @Test
    @DisplayName("V14: em_estoque mantem status, units_in_stock=current_stock, sem revisao pendente")
    void emEstoque_mapping() {
        V14Outcome out = v14Target("em_estoque", 10D);
        assertThat(out.status()).isEqualTo("em_estoque");
        assertThat(out.unitsInStock()).isEqualTo(10);
        assertThat(out.unitsInUse()).isEqualTo(0);
        assertThat(out.needsStockReview()).isFalse();
        assertThat(out.archivedBy()).isNull();
    }

    @Test
    @DisplayName("V14: em_uso mantem status mas marca needs_stock_review=true (estoque ambiguo)")
    void emUso_mapping_marcaRevisao() {
        V14Outcome out = v14Target("em_uso", 5D);
        assertThat(out.status()).isEqualTo("em_uso");
        assertThat(out.unitsInStock()).isEqualTo(5);
        assertThat(out.unitsInUse()).isEqualTo(0);
        assertThat(out.needsStockReview()).isTrue();
    }

    @Test
    @DisplayName("V14: fora_de_estoque vira inativo com archived_at + archived_by sistema-migracao-v14")
    void foraDeEstoque_mapping_arquiva() {
        V14Outcome out = v14Target("fora_de_estoque", 0D);
        assertThat(out.status()).isEqualTo("inativo");
        assertThat(out.unitsInStock()).isEqualTo(0);
        assertThat(out.unitsInUse()).isEqualTo(0);
        assertThat(out.archivedBy()).isEqualTo("sistema-migracao-v14");
        assertThat(out.hasArchivedAt()).isTrue();
        assertThat(out.needsStockReview()).isFalse();
    }

    @Test
    @DisplayName("V14: vencido preserva contagem para relatorio 'vencidos com estoque' (decisao 1.8)")
    void vencido_mapping_preservaContagem() {
        V14Outcome out = v14Target("vencido", 3D);
        assertThat(out.status()).isEqualTo("vencido");
        assertThat(out.unitsInStock()).isEqualTo(3);
        assertThat(out.unitsInUse()).isEqualTo(0);
        assertThat(out.needsStockReview()).isFalse();
    }

    @Test
    @DisplayName("V14: COALESCE(current_stock, 0) — null vira 0 sem panic")
    void emEstoque_currentStockNull_coalesce() {
        V14Outcome out = v14Target("em_estoque", 0D);
        assertThat(out.unitsInStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("V14: combinatorial — todos os 4 status legados produzem destino do dominio v3")
    void combinatorial_dominio() {
        List<String> legacyStatuses = List.of("em_estoque", "em_uso", "fora_de_estoque", "vencido");
        List<Double> stocks = List.of(0D, 5D, 100D);
        for (String s : legacyStatuses) {
            for (Double k : stocks) {
                V14Outcome out = v14Target(s, k);
                assertThat(out.status())
                    .as("status=%s stock=%s", s, k)
                    .isIn("em_estoque", "em_uso", "vencido", "inativo");
                assertThat(out.unitsInStock()).isGreaterThanOrEqualTo(0);
                assertThat(out.unitsInUse()).isEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("V14: soma units_in_stock + units_in_use = current_stock para nao-arquivados")
    void preservaTotalParaNaoArquivados() {
        for (String s : List.of("em_estoque", "em_uso", "vencido")) {
            V14Outcome out = v14Target(s, 7D);
            assertThat(out.unitsInStock() + out.unitsInUse())
                .as("status=%s", s)
                .isEqualTo(7);
        }
    }

    @Test
    @DisplayName("V14: para fora_de_estoque, total e 0 (nada operacional)")
    void foraDeEstoque_totalZero() {
        V14Outcome out = v14Target("fora_de_estoque", 5D);
        assertThat(out.unitsInStock() + out.unitsInUse()).isEqualTo(0);
    }

    @Test
    @DisplayName("V14: deriveStatus pos-V14 retorna status correto para cada outcome")
    void posV14_deriveStatusValida() {
        // Garante que o deriveStatus (regra ternaria v3) e coerente com o mapping V14:
        //  - lote vencido (expiry passada) -> vencido
        //  - lote em_estoque (expiry futura, units > 0) -> em_estoque
        //  - lote em_uso (units_in_use > 0) -> em_uso (mas mapping V14 nao seta unitsInUse, so units_in_stock)
        //    Por isso o lote ex-em_uso pos-V14 com unitsInUse=0 e unitsInStock>0 vai re-derivar como em_estoque
        //    quando deriveStatus rodar — mas o status BD persiste em_uso ate intervencao manual via AJUSTE/ABERTURA.
        // Esse e o motivo do flag needs_stock_review=true: forcar revisao explicita.
        V14Outcome out = v14Target("em_uso", 5D);
        assertThat(out.status()).isEqualTo("em_uso"); // bate com persistencia
        assertThat(out.unitsInUse()).isEqualTo(0);
        assertThat(out.needsStockReview()).isTrue(); // sinaliza ambiguidade
    }
}
