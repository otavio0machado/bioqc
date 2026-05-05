# BioQC API

## Execução local

```bash
./mvnw spring-boot:run
```

O backend sobe por padrão na porta `8080` e expõe healthcheck em:

```text
GET /actuator/health
```

## Variáveis principais

```bash
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=<chave-64-chars-aleatoria>
DATABASE_URL=postgresql://postgres.<project-ref>:<senha-encoded>@aws-0-<region>.pooler.supabase.com:5432/postgres
# ou SUPABASE_JDBC_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
CORS_ORIGINS=https://seu-frontend.up.railway.app
APP_FRONTEND_URL=https://seu-frontend.up.railway.app
```

O backend também aceita credenciais separadas (`DATABASE_USERNAME`, `DATABASE_PASSWORD`) e as variantes `SUPABASE_DB_*` / `DB_*`.

Observação operacional:

- `JPA_DDL_AUTO=update` ainda é transitório neste projeto, até a governança final de migrations versionadas.

## Deploy Railway

Artefatos usados pelo serviço:

- `Dockerfile`
- `railway.toml`
- `scripts/railway-env.example.sh`
- `scripts/healthcheck.sh`

Checklist rápido:

1. Criar o banco novo no Supabase com o schema oficial.
2. Subir o serviço `bioqc-api` no Railway com root em `bioqc-api`.
3. Configurar `JWT_SECRET`, conexão com Supabase, `CORS_ORIGINS` e `APP_FRONTEND_URL`.
4. Validar `GET /actuator/health`.
5. Criar o usuário admin inicial.

## Fonte de verdade de deploy

- `PLANS.md`
- `transicao-java/05-DEPLOY.md`
- `scripts/railway-env.example.sh`
- `scripts/healthcheck.sh`
