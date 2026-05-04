package com.bioqc.service.reports.v2.generator;

import com.bioqc.entity.LabSettings;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * Contexto de execucao passado a todo generator. Agrega dados do usuario
 * autenticado, relogio/timezone, configuracoes do laboratorio e identificadores
 * de correlacao para logging.
 */
public record GenerationContext(
    UUID userId,
    String username,
    Set<String> userRoles,
    Instant now,
    ZoneId zone,
    LabSettings labSettings,
    String correlationId,
    String requestId
) {
    public GenerationContext {
        userRoles = userRoles == null ? Set.of() : Set.copyOf(userRoles);
        if (now == null) throw new IllegalArgumentException("GenerationContext.now obrigatorio");
        if (zone == null) throw new IllegalArgumentException("GenerationContext.zone obrigatorio");
    }
}
