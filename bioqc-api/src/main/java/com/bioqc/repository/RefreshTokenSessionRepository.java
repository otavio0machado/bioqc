package com.bioqc.repository;

import com.bioqc.entity.RefreshTokenSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, UUID> {

    Optional<RefreshTokenSession> findByTokenId(UUID tokenId);

    List<RefreshTokenSession> findByFamilyIdAndRevokedAtIsNull(UUID familyId);

    List<RefreshTokenSession> findByUser_IdAndRevokedAtIsNull(UUID userId);
}
