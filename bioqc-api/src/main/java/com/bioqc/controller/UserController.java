package com.bioqc.controller;

import com.bioqc.dto.response.ResponsibleSummary;
import com.bioqc.service.ReagentService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de leitura sobre usuarios para uso operacional (combobox de responsavel,
 * etc). RBAC menos restritivo do que {@code AdminController} — qualquer autenticado
 * pode listar responsaveis ativos.
 *
 * <p>Refator v3 (decisao 1.5): {@code GET /api/users/responsibles} retorna shape minimo
 * {@code [{id, name, username, role}]} filtrado por {@code role IN (ADMIN, FUNCIONARIO)
 * AND isActive=true}. NAO expoe email, permissions, createdAt etc — privacidade LGPD
 * (legitimo interesse intra-organizacional).</p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final ReagentService reagentService;

    public UserController(ReagentService reagentService) {
        this.reagentService = reagentService;
    }

    @GetMapping("/responsibles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ResponsibleSummary>> getResponsibles() {
        return ResponseEntity.ok(reagentService.getResponsibles());
    }
}
