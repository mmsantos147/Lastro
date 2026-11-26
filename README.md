# Lastro

Monitor pessoal de investimentos: acompanha a carteira (posição, preço médio, proventos, rentabilidade
vs CDI/IBOV), mostra a evolução em gráficos e apoia decisões de aporte. A aplicação **não executa
operações**: as compras são feitas na corretora/exchange e registradas aqui. A análise com o Claude
acontece no claude.ai, via MCP.

## Estrutura

```
backend/    # Spring Boot (Java 25), arquitetura hexagonal
frontend/   # React + TypeScript (Vite)
docker-compose.yml
```

## Arquitetura

- **Hexagonal**: `domain/` (modelo e regras puras, sem Spring/JPA), `application/` (casos de uso) e
  `adapter/` (REST, MCP, JPA, clientes de cotação, importadores). As regras de dependência são
  verificadas por testes com ArchUnit.
- **Trades são a fonte da verdade**: posição, preço médio e rentabilidade são sempre derivados de
  operações, eventos corporativos e transferências.
- **Precisão**: dinheiro e quantidade em `BigDecimal`, com escala centralizada em value objects.
- **Importação idempotente** do Excel da B3 e do histórico da Binance (chave natural por linha).
- **Servidor MCP** (Spring AI) com tools somente leitura da carteira, OAuth 2.1 + PKCE para o
  conector do claude.ai, e o prompt `monthly_analysis`.

## Requisitos

- JDK 25
- Node.js 26
- Docker (PostgreSQL local e Testcontainers)

## Rodando localmente

```bash
cp .env.example .env          # defina LASTRO_ADMIN_USERNAME e LASTRO_ADMIN_PASSWORD (mín. 12 caracteres)
docker compose up -d          # PostgreSQL

cd backend && ./mvnw spring-boot:run       # API em http://localhost:8080 (lê ../.env)
cd frontend && npm install && npm run dev  # site em http://localhost:5173 (proxy de /api para o back-end)
```

O primeiro admin é criado na primeira subida, com as credenciais do `.env`, quando ainda não há usuários.

## Testes

```bash
cd backend && ./mvnw test           # unitários (*Test)
cd backend && ./mvnw clean verify   # unitários + integração com Testcontainers (*IT)
cd frontend && npm test             # Vitest
cd frontend && npm run lint && npm run build
```

## Produção

Imagem única (front servido pelo Spring Boot) e `docker-compose.prod.yml` com PostgreSQL, implantada
numa VPS com Coolify (HTTPS via Traefik).
