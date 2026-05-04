package com.bioqc.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.env.Environment;

record SupabaseDatabaseSettings(String jdbcUrl, String username, String password) {

    static SupabaseDatabaseSettings from(Environment environment) {
        String sslMode = firstNonBlank(
            environment.getProperty("SUPABASE_DB_SSL_MODE"),
            environment.getProperty("DB_SSL_MODE"),
            "require"
        );
        String username = firstNonBlank(
            environment.getProperty("DATABASE_USERNAME"),
            environment.getProperty("SUPABASE_DB_USER"),
            environment.getProperty("DB_USER")
        );
        String password = firstNonBlank(
            environment.getProperty("DATABASE_PASSWORD"),
            environment.getProperty("SUPABASE_DB_PASSWORD"),
            environment.getProperty("DB_PASSWORD")
        );

        String connectionValue = firstNonBlank(
            environment.getProperty("SUPABASE_JDBC_URL"),
            environment.getProperty("JDBC_DATABASE_URL"),
            environment.getProperty("DATABASE_URL"),
            environment.getProperty("SUPABASE_DB_URL")
        );

        if (hasText(connectionValue)) {
            return fromConnectionValue(connectionValue, username, password, sslMode);
        }

        String host = firstNonBlank(
            environment.getProperty("SUPABASE_DB_HOST"),
            environment.getProperty("DB_HOST")
        );
        String port = firstNonBlank(
            environment.getProperty("SUPABASE_DB_PORT"),
            environment.getProperty("DB_PORT"),
            "5432"
        );
        String database = firstNonBlank(
            environment.getProperty("SUPABASE_DB_NAME"),
            environment.getProperty("DB_NAME"),
            "postgres"
        );

        if (!hasText(host)) {
            throw new IllegalStateException(
                "Configure DATABASE_URL, SUPABASE_DB_URL, SUPABASE_JDBC_URL ou SUPABASE_DB_HOST para o Supabase."
            );
        }

        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database + "?sslmode=" + sslMode;
        return new SupabaseDatabaseSettings(jdbcUrl, username, password);
    }

    private static SupabaseDatabaseSettings fromConnectionValue(
        String connectionValue,
        String username,
        String password,
        String sslMode
    ) {
        String normalized = connectionValue.trim();

        if (normalized.startsWith("jdbc:postgresql://")) {
            return new SupabaseDatabaseSettings(ensureSslMode(normalized, sslMode), username, password);
        }

        if (normalized.startsWith("postgres://") || normalized.startsWith("postgresql://")) {
            URI uri = URI.create(normalized);
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/postgres" : uri.getPath();
            String query = uri.getRawQuery();
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + resolvePort(uri) + path;

            if (hasText(query)) {
                jdbcUrl += "?" + query;
            }
            jdbcUrl = ensureSslMode(jdbcUrl, sslMode);

            if ((!hasText(username) || !hasText(password)) && hasText(uri.getRawUserInfo())) {
                String[] parts = uri.getRawUserInfo().split(":", 2);
                if (!hasText(username) && parts.length >= 1) {
                    username = decode(parts[0]);
                }
                if (!hasText(password) && parts.length == 2) {
                    password = decode(parts[1]);
                }
            }

            return new SupabaseDatabaseSettings(jdbcUrl, username, password);
        }

        throw new IllegalStateException(
            "A URL do banco deve ser JDBC (jdbc:postgresql://...) ou URI Postgres (postgresql://... / postgres://...)."
        );
    }

    private static String ensureSslMode(String jdbcUrl, String sslMode) {
        if (jdbcUrl.contains("sslmode=")) {
            return jdbcUrl;
        }
        return jdbcUrl.contains("?")
            ? jdbcUrl + "&sslmode=" + sslMode
            : jdbcUrl + "?sslmode=" + sslMode;
    }

    private static int resolvePort(URI uri) {
        return uri.getPort() > 0 ? uri.getPort() : 5432;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
