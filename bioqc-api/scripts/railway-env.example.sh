#!/usr/bin/env bash

cat <<'EOF'
SPRING_PROFILES_ACTIVE=prod
PORT=8080
# Use uma destas opções:
SUPABASE_JDBC_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
# ou
DATABASE_URL=postgresql://postgres.<project-ref>:<senha-encoded>@aws-0-<region>.pooler.supabase.com:5432/postgres

# Opcionais quando a URL não carrega credenciais:
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=<senha-supabase>

# Alternativa por campos separados:
SUPABASE_DB_HOST=db.<project-ref>.supabase.co
SUPABASE_DB_PORT=5432
SUPABASE_DB_NAME=postgres
SUPABASE_DB_USER=postgres
SUPABASE_DB_PASSWORD=<senha-supabase>
SUPABASE_DB_SSL_MODE=require

# Transitório até a governança de migrations versionadas (Flyway/Liquibase).
JPA_DDL_AUTO=update
JWT_SECRET=<chave-64-chars-aleatoria>
GEMINI_API_KEY=<chave-gemini>
CORS_ORIGINS=https://seu-frontend.up.railway.app
APP_FRONTEND_URL=https://seu-frontend.up.railway.app
JAVA_OPTS=-Xmx512m
EOF
