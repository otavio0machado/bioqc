package com.bioqc.entity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Status canonicos para um {@link ReagentLot} apos o refator v3.
 *
 * Conjunto vigente: {@code em_estoque}, {@code em_uso}, {@code vencido}, {@code inativo}.
 *
 * <p>O status {@code fora_de_estoque} (terminal automatico do refator-v2) foi
 * descontinuado pela migracao V14 (refator-reagentes-v3): toda linha com esse status
 * e reclassificada como {@code inativo} (terminal manual). Estoque zero deixou de ser
 * terminal automatico — o lote permanece {@code em_estoque} ate intervencao manual
 * via {@code POST /api/reagents/{id}/archive}.</p>
 *
 * <p>O conjunto antigo (v1: {@code ativo}, {@code inativo}, {@code quarentena}) foi tratado
 * pela V13 e continua bloqueado por CHECK constraint. Audit_log historico citando
 * literais como {@code ativo}, {@code inativo} (pre-v2) e {@code fora_de_estoque} (v2)
 * permanece preservado — auditor externo precisa reconhecer todas as eras.</p>
 */
public final class ReagentStatus {

    public static final String EM_ESTOQUE = "em_estoque";
    public static final String EM_USO = "em_uso";
    public static final String VENCIDO = "vencido";
    public static final String INATIVO = "inativo";

    public static final Set<String> ALL = Set.of(EM_ESTOQUE, EM_USO, VENCIDO, INATIVO);

    private ReagentStatus() {
        // utilitaria
    }

    public static boolean isValid(String value) {
        return value != null && ALL.contains(value);
    }

    public static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    public static String humanList() {
        return Arrays.stream(new String[] {EM_ESTOQUE, EM_USO, VENCIDO, INATIVO})
            .collect(Collectors.joining(", "));
    }
}
