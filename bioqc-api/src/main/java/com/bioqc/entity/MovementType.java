package com.bioqc.entity;

import java.util.Set;

/**
 * Tipos suportados de {@link StockMovement} apos refator v3.
 *
 * <p>Particionamento (decisao 1.3 do contrato v3):</p>
 * <ul>
 *   <li>{@link #ALL_WRITE} — aceitos em {@code POST /api/reagents/{id}/movements}.
 *       Inclui {@code ENTRADA, ABERTURA, FECHAMENTO, CONSUMO, AJUSTE}.</li>
 *   <li>{@link #ALL_READ} — visiveis em listagem/historico, incluindo {@code SAIDA}
 *       legado (movimentos pre-V14 mantidos para auditoria).</li>
 * </ul>
 *
 * <p>{@code SAIDA} permanece no enum (jamais apagar) mas backend recusa em
 * {@code createMovement} com 400 e mensagem indicando o uso de {@code CONSUMO} (uso/descarte)
 * ou {@code AJUSTE} (correcao de inventario).</p>
 *
 * Armazenados em maiusculas no banco para bater com o switch ja existente no
 * servico e com os eventos emitidos pelo frontend.
 */
public final class MovementType {

    public static final String ENTRADA = "ENTRADA";
    public static final String SAIDA = "SAIDA";
    public static final String AJUSTE = "AJUSTE";
    public static final String ABERTURA = "ABERTURA";
    public static final String FECHAMENTO = "FECHAMENTO";
    public static final String CONSUMO = "CONSUMO";

    /** Conjunto aceito em escrita ({@code POST /movements}). */
    public static final Set<String> ALL_WRITE = Set.of(ENTRADA, ABERTURA, FECHAMENTO, CONSUMO, AJUSTE);

    /** Conjunto aceito em leitura — inclui SAIDA legado para historico imutavel. */
    public static final Set<String> ALL_READ = Set.of(ENTRADA, SAIDA, AJUSTE, ABERTURA, FECHAMENTO, CONSUMO);

    private MovementType() {
        // utilitaria
    }

    public static boolean isValidWrite(String value) {
        return value != null && ALL_WRITE.contains(value);
    }

    public static boolean isValidRead(String value) {
        return value != null && ALL_READ.contains(value);
    }

    public static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    public static String humanListWrite() {
        return String.join(", ", ENTRADA, ABERTURA, FECHAMENTO, CONSUMO, AJUSTE);
    }
}
