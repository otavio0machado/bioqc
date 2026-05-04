package com.bioqc.service;

import com.bioqc.dto.request.AdminResetPasswordRequest;
import com.bioqc.dto.request.AdminUpdateUserRequest;
import com.bioqc.dto.request.AdminUserRequest;
import com.bioqc.dto.response.UserResponse;
import com.bioqc.entity.Permission;
import com.bioqc.entity.Role;
import com.bioqc.entity.User;
import com.bioqc.exception.BusinessException;
import com.bioqc.exception.ResourceNotFoundException;
import com.bioqc.repository.UserRepository;
import com.bioqc.util.ResponseMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
            .map(ResponseMapper::toUserResponse)
            .toList();
    }

    @Transactional
    public UserResponse createUser(AdminUserRequest request) {
        String normalizedUsername = request.username().trim().toLowerCase();
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new BusinessException("Já existe um usuário com este nome de usuário");
        }

        Role role = AuthService.parseRole(request.role());
        Set<Permission> permissions = parsePermissions(request.permissions());

        User user = User.builder()
            .username(normalizedUsername)
            .email(request.email() != null ? request.email().trim() : null)
            .passwordHash(passwordEncoder.encode(request.password()))
            .name(request.name().trim())
            .role(role)
            .permissions(permissions)
            .isActive(Boolean.TRUE)
            .build();

        User saved = userRepository.save(user);
        auditService.log("CRIAR_USUARIO", "User", saved.getId(),
            java.util.Map.of("username", saved.getUsername(), "role", saved.getRole().name()));
        return ResponseMapper.toUserResponse(saved);
    }

    @Transactional
    public UserResponse updateUser(UUID id, AdminUpdateUserRequest request, UUID requestingUserId) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (request.isActive() != null && !request.isActive() && id.equals(requestingUserId)) {
            throw new BusinessException("Não é possível desativar o próprio usuário");
        }

        if (request.name() != null) {
            user.setName(request.name().trim());
        }
        if (request.role() != null) {
            user.setRole(AuthService.parseRole(request.role()));
        }
        if (request.isActive() != null) {
            user.setIsActive(request.isActive());
        }
        if (request.email() != null) {
            user.setEmail(request.email().trim().isEmpty() ? null : request.email().trim());
        }
        if (request.permissions() != null) {
            user.setPermissions(parsePermissions(request.permissions()));
        }

        User updated = userRepository.save(user);
        auditService.log("EDITAR_USUARIO", "User", updated.getId(),
            java.util.Map.of("username", updated.getUsername(), "role", updated.getRole().name(), "ativo", updated.getIsActive()));
        return ResponseMapper.toUserResponse(updated);
    }

    @Transactional
    public void resetPassword(UUID id, AdminResetPasswordRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        auditService.log("RESETAR_SENHA", "User", id, java.util.Map.of("username", user.getUsername()));
    }

    static Set<Permission> parsePermissions(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new HashSet<>();
        }
        Set<Permission> result = new HashSet<>();
        for (String value : raw) {
            try {
                result.add(Permission.valueOf(value.trim().toUpperCase()));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("Permissão inválida: " + value);
            }
        }
        return result;
    }
}
