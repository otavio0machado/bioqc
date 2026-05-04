# Biodiagnóstico Web

Frontend em React + Vite para o sistema de controle de qualidade.

## Desenvolvimento

```bash
npm install
npm run dev
```

Use um arquivo `.env` com:

```env
VITE_API_URL=http://localhost:8080/api
```

## Deploy no Railway

O frontend é servido por Nginx em container próprio.

Variáveis principais:

```env
VITE_API_URL=https://seu-backend.up.railway.app/api
PUBLIC_SITE_URL=https://seu-frontend.up.railway.app
```

Arquivos operacionais:

- `Dockerfile`
- `railway.toml`
- `nginx.conf.template`
- `docker-entrypoint.d/40-generate-seo.sh`

Healthcheck do serviço:

```text
GET /health
```
