package com.bioqc.dto.response;

import java.util.UUID;

/**
 * Shape minimo de usuario exposto no endpoint {@code GET /api/users/responsibles}
 * (refator v3 — combobox de responsavel).
 *
 * <p>Filtro repository: {@code role IN (ADMIN, FUNCIONARIO) AND isActive=true}.</p>
 *
 * <p>Privacidade — NAO expoe: {@code email}, {@code passwordHash}, {@code permissions},
 * {@code createdAt}, {@code lastLoginAt}. LGPD art. 7º §5º: legitimo interesse
 * intra-organizacional permite shape minimo necessario para autoria operacional.</p>
 */
public record ResponsibleSummary(
    UUID id,
    String name,
    String username,
    String role
) {
}
