package com.bioqc.security;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenBlacklistService {

    private final Map<UUID, Instant> blacklistedTokens = new ConcurrentHashMap<>();

    public void blacklist(UUID tokenId, Instant expiresAt) {
        cleanupExpiredEntries();
        if (tokenId != null && expiresAt != null) {
            blacklistedTokens.put(tokenId, expiresAt);
        }
    }

    public boolean isBlacklisted(UUID tokenId) {
        cleanupExpiredEntries();
        return tokenId != null && blacklistedTokens.containsKey(tokenId);
    }

    private void cleanupExpiredEntries() {
        Instant now = Instant.now();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() == null || !entry.getValue().isAfter(now));
    }
}
