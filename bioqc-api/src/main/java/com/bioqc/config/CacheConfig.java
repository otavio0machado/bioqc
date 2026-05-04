package com.bioqc.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao de cache para endpoints de baixa cardinalidade com TTL curto.
 *
 * <p>Caches registrados:
 * <ul>
 *   <li>{@code reportsV2.suggestions.equipment} - lista de equipamentos distintos
 *       (TTL 5 min). Consumido por autocomplete de filtros Reports V2.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SUGGESTIONS_EQUIPMENT_CACHE = "reportsV2.suggestions.equipment";

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(SUGGESTIONS_EQUIPMENT_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(200));
        return manager;
    }
}
