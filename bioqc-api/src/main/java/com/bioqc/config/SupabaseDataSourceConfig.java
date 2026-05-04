package com.bioqc.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("prod")
public class SupabaseDataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(Environment environment) {
        SupabaseDatabaseSettings settings = SupabaseDatabaseSettings.from(environment);

        HikariConfig config = new HikariConfig();
        config.setPoolName("SupabasePool");
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl(settings.jdbcUrl());

        if (hasText(settings.username())) {
            config.setUsername(settings.username());
        }
        if (hasText(settings.password())) {
            config.setPassword(settings.password());
        }

        config.setMaximumPoolSize(getInt(environment, "DB_POOL_MAX_SIZE", 10));
        config.setMinimumIdle(getInt(environment, "DB_POOL_MIN_IDLE", 2));
        config.setConnectionTimeout(getLong(environment, "DB_CONNECTION_TIMEOUT_MS", 30_000L));
        config.setIdleTimeout(getLong(environment, "DB_IDLE_TIMEOUT_MS", 600_000L));
        config.setMaxLifetime(getLong(environment, "DB_MAX_LIFETIME_MS", 1_800_000L));
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        config.addDataSourceProperty(
            "ApplicationName",
            hasText(environment.getProperty("RAILWAY_SERVICE_NAME"))
                ? environment.getProperty("RAILWAY_SERVICE_NAME")
                : "bioqc-api"
        );

        return new HikariDataSource(config);
    }

    private static int getInt(Environment environment, String key, int fallback) {
        String value = environment.getProperty(key);
        return hasText(value) ? Integer.parseInt(value) : fallback;
    }

    private static long getLong(Environment environment, String key, long fallback) {
        String value = environment.getProperty(key);
        return hasText(value) ? Long.parseLong(value) : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
