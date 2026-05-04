<div align="center">

# BioQC — Plataforma de Controle de Qualidade Laboratorial

**Sistema de Controle de Qualidade (CQ) para laboratórios clínicos, em produção**
Java/Spring Boot · React/TypeScript · PostgreSQL · Aderente à ISO 15189

[Demo ao Vivo](#-demo-ao-vivo) · [Estudo de Caso](./CASE_STUDY.md) · [Arquitetura](./docs/architecture.md) · [Me Contrate](#-me-contrate)

</div>

---

> **Resumo** — Plataforma real de Controle de Qualidade laboratorial que projetei e implementei de ponta a ponta. Migrei um sistema legado em Python para uma stack moderna Java/React, com motor de regras de Westgard, relatórios PDF assinados digitalmente, trilha de auditoria completa e rastreabilidade ISO 15189. **Este repositório é a versão sanitizada do produto em produção.**

## 🧪 O que é

BioQC é a espinha dorsal de CQ da operação diária de um laboratório clínico. Analistas rodam amostras de controle em cada equipamento (bioquímica, hematologia, imunologia, etc.) antes de liberar resultados de pacientes. O sistema valida essas medições contra valores estatísticos de referência, aplica multirregras de Westgard, sinaliza violações, rastreia lotes de reagentes e produz relatórios PDF assinados digitalmente para auditorias regulatórias.

**Não é um CRUD de tutorial.** Roda em produção em um laboratório clínico brasileiro real.

## ✨ Funcionalidades Principais

### Motor de Controle de Qualidade
- **Avaliação de multirregras de Westgard** — `1-2s`, `1-3s`, `2-2s`, `R-4s`, `4-1s`, `10x` por medição
- **Gráficos de Levey-Jennings** renderizados no servidor (JFreeChart) para relatórios impressos
- **Valores de referência estatísticos** — média, DP, CV com tolerância configurável por analito/equipamento/lote
- **CQ por área** — parâmetros e regras independentes por área (bioquímica, hematologia, imunologia, microbiologia, parasitologia, uroanálise)
- **Rastreio pós-calibração** — fluxo separado para medições após calibração de equipamento
- **Histórico diário de CQ** propagado pelo pacote regulatório

### Rastreabilidade de Reagentes (ISO 15189)
- Ciclo de vida do lote: recebimento → abertura → consumo → final de uso, com tracking por unidade
- Movimentações de estoque com códigos de razão (entrada, consumo, vencimento, arquivar)
- Reclassificação automática de lotes vencidos/zerados
- Fabricante, fornecedor, localização com combobox de autocomplete
- Etiquetas de rastreabilidade (1 reagente = 1 seção no relatório regulatório)

### Relatórios PDF Assinados (Reports V2)
- **Cadeia de assinaturas com hash** — cada PDF assinado referencia o anterior, evidência de adulteração
- **Verificação por QR code** — escaneia do PDF, vai para `/verify/{hash}` e confirma autenticidade
- **Numeração de relatórios** com prefixo anual
- **Log de download** — todo download de PDF registrado para evidência ISO 15189
- **Rate limiting** nos endpoints de assinatura
- **Comentário gerado por IA** (opcional, Gemini API) resumindo tendências do mês em português claro

### Manutenção e Calibração
- Agenda e log de manutenção de equipamento
- Registros de calibração ligados às medições de CQ (tolerância de delta CV configurável)

### Operação
- **JWT** com rotação de refresh tokens, cookies seguros HttpOnly (`SameSite=None`)
- **RBAC** — permissões granulares por recurso e ação
- **Auditoria** — toda mutação persistida com ator, timestamp, antes/depois
- **Reset de senha** via SMTP
- **Métricas Prometheus** + log JSON estruturado (logstash-logback)
- **Correlation IDs** propagados nas requisições
- **Migrations Flyway** — 15 versões shipped em produção

## 📐 Arquitetura

```
┌──────────────────────┐     HTTPS      ┌──────────────────────┐
│  React 18 + Vite     │ ─────────────► │  Spring Boot 3.3     │
│  TanStack Query      │  (JWT + cookie │  Java 21             │
│  React Hook Form     │   refresh)     │  JPA / Hibernate     │
│  Zod validation      │ ◄───────────── │  Flyway migrations   │
│  Tailwind 4 + Recharts                │  Spring Security     │
└──────────────────────┘                └──────────┬───────────┘
       deployed em                                 │
       Vercel                                      │
                                                   ▼
                                        ┌──────────────────────┐
                                        │  PostgreSQL          │
                                        │  (Supabase)          │
                                        └──────────────────────┘
```

## 📊 Métricas

| Métrica | Valor |
|--------|------:|
| Arquivos Java | **282** |
| Arquivos TypeScript/TSX | **116** |
| Testes backend (JUnit 5 + MockMvc) | **406** (em 51 arquivos) |
| Testes frontend (Vitest + Testing Library) | **7+** |
| Migrations Flyway shipped | **15** |
| Controllers REST | **15** |
| Entidades JPA | **30+** |

## 🚀 Demo ao Vivo

> **Experimente →** [bioqc-demo.vercel.app](https://bioqc-demo.vercel.app) *(dados de seed apenas — sem informação real de paciente ou equipe)*
>
> **Credenciais**
> - **Admin:** `admin@demo.bioqc.dev` / `Demo123!`
> - **Analista:** `analyst@demo.bioqc.dev` / `Demo123!`

## 🛠 Rodar Localmente

```bash
# Backend
cd bioqc-api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Frontend
cd bioqc-web && npm install && npm run dev
```

## 📞 Me Contrate

Sou **Otavio Machado**, engenheiro full-stack baseado no Brasil, especializado em domínios regulados e intensivos em dados (lab/saúde, finanças, compliance).

- 📧 **E-mail:** otavio100206@gmail.com
- 💼 **LinkedIn:** [linkedin.com/in/machado-otavio](https://www.linkedin.com/in/machado-otavio/)
- 🐙 **GitHub:** [@otavio0machado](https://github.com/otavio0machado)

**Disponível para:** Backend (Java/Spring, Node) · Frontend (React/TypeScript) · Migrações full-stack · Informática laboratorial · Integrações ISO 15189 / LIS.

---

🇺🇸 **English version:** [README.md](./README.md)
