package com.bioqc.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SupabaseDatabaseSettingsTest {

    @Test
    @DisplayName("deve converter DATABASE_URL postgres para JDBC e extrair credenciais")
    void shouldConvertDatabaseUrlToJdbc() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty(
                "DATABASE_URL",
                "postgresql://postgres.user:secret%402024@aws-0-sa-east-1.pooler.supabase.com:5432/postgres"
            );

        SupabaseDatabaseSettings settings = SupabaseDatabaseSettings.from(environment);

        assertThat(settings.jdbcUrl())
            .isEqualTo("jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:5432/postgres?sslmode=require");
        assertThat(settings.username()).isEqualTo("postgres.user");
        assertThat(settings.password()).isEqualTo("secret@2024");
    }

    @Test
    @DisplayName("deve preservar sslmode quando já existir na URL JDBC")
    void shouldPreserveSslModeWhenAlreadyPresent() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty(
                "SUPABASE_JDBC_URL",
                "jdbc:postgresql://db.project.supabase.co:5432/postgres?sslmode=require"
            )
            .withProperty("DATABASE_USERNAME", "postgres")
            .withProperty("DATABASE_PASSWORD", "secret");

        SupabaseDatabaseSettings settings = SupabaseDatabaseSettings.from(environment);

        assertThat(settings.jdbcUrl())
            .isEqualTo("jdbc:postgresql://db.project.supabase.co:5432/postgres?sslmode=require");
        assertThat(settings.username()).isEqualTo("postgres");
        assertThat(settings.password()).isEqualTo("secret");
    }

    @Test
    @DisplayName("deve montar JDBC a partir dos campos separados do Supabase")
    void shouldBuildJdbcFromDiscreteSupabaseFields() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("SUPABASE_DB_HOST", "db.project.supabase.co")
            .withProperty("SUPABASE_DB_PORT", "5432")
            .withProperty("SUPABASE_DB_NAME", "postgres")
            .withProperty("SUPABASE_DB_USER", "postgres")
            .withProperty("SUPABASE_DB_PASSWORD", "secret");

        SupabaseDatabaseSettings settings = SupabaseDatabaseSettings.from(environment);

        assertThat(settings.jdbcUrl())
            .isEqualTo("jdbc:postgresql://db.project.supabase.co:5432/postgres?sslmode=require");
        assertThat(settings.username()).isEqualTo("postgres");
        assertThat(settings.password()).isEqualTo("secret");
    }
}
