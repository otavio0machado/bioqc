package com.bioqc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Teste estatico do conteudo da migracao V15.
 *
 * <p>Sem Testcontainers no pom, validamos contratos textuais da migracao:
 * <ul>
 *   <li>ADD COLUMN event_date DATE NULL na tabela stock_movements (audit ressalva
 *       v3.1: nullable preserva movimentos pre-V15).</li>
 *   <li>Index parcial idx_stock_movements_event_date com WHERE event_date IS NOT NULL
 *       (audit ressalva v3.1: nao onerar indice com legado NULL).</li>
 *   <li>NAO faz UPDATE em linhas existentes (sem backfill retroativo —
 *       movimentos pre-V15 ficam com NULL preservando rastreio).</li>
 *   <li>Comentario explicito sobre semantica eventDate vs createdAt.</li>
 * </ul>
 */
class ReagentMigrationV15Test {

    private static final Path V15 = Path.of(
        "src/main/resources/db/migration/V15__stock_movement_event_date.sql");

    private String readMigration() throws IOException {
        return Files.readString(V15);
    }

    @Test
    @DisplayName("V15: ADD COLUMN event_date DATE NULL em stock_movements")
    void v15_adicionaColunaEventDate() throws IOException {
        String sql = readMigration();
        assertThat(sql).contains("ALTER TABLE stock_movements");
        assertThat(sql).contains("ADD COLUMN event_date DATE NULL");
    }

    @Test
    @DisplayName("V15: cria index parcial idx_stock_movements_event_date WHERE event_date IS NOT NULL")
    void v15_criaIndexParcial() throws IOException {
        String sql = readMigration();
        assertThat(sql).contains("CREATE INDEX");
        assertThat(sql).contains("idx_stock_movements_event_date");
        assertThat(sql).contains("WHERE event_date IS NOT NULL");
    }

    @Test
    @DisplayName("V15: NAO faz UPDATE em stock_movements (sem backfill — pre-V15 fica NULL)")
    void v15_naoFazBackfillRetroativo() throws IOException {
        String sql = readMigration();
        // Defesa contra drift: UPDATE em stock_movements quebraria a regra de
        // distincao auditavel pre/pos V15.
        assertThat(sql).doesNotContain("UPDATE stock_movements");
    }

    @Test
    @DisplayName("V15: documenta semantica eventDate (declarado) vs createdAt (sistema)")
    void v15_comentarioSemantica() throws IOException {
        String sql = readMigration();
        assertThat(sql).containsIgnoringCase("event_date");
        assertThat(sql).containsIgnoringCase("created_at");
        // Justificativa de dominio nos comentarios da migracao.
        assertThat(sql).containsIgnoringCase("ANVISA");
    }
}
