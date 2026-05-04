package com.bioqc.repository;

import com.bioqc.entity.Role;
import com.bioqc.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Lista usuarios elegiveis a serem "responsavel" por um movimento ou arquivamento
     * (refator v3 — combobox).
     *
     * <p>Filtro: {@code role IN (ADMIN, FUNCIONARIO) AND isActive=TRUE}. Ordem
     * canonica por {@code name ASC} para UX consistente no combobox.</p>
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.isActive = true
          AND u.role IN :roles
        ORDER BY u.name ASC
        """)
    List<User> findActiveResponsibles(@Param("roles") Set<Role> roles);

    /**
     * Existe um usuario ativo com este {@code username} cuja role e elegivel
     * (ADMIN ou FUNCIONARIO)? Usado por {@code archive} para validar autoria.
     *
     * <p>Auditor v3 ressalva 1.1: chave de identidade e {@code username} (estavel),
     * NAO {@code name} (display, pode colidir).</p>
     */
    @Query("""
        SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END
        FROM User u
        WHERE u.isActive = true
          AND u.role IN :roles
          AND u.username = :username
        """)
    boolean existsActiveResponsibleByUsername(
        @Param("username") String username,
        @Param("roles") Set<Role> roles);
}
