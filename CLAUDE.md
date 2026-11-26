# Polaroid

## Visão Geral

Polaroid é um aplicativo Android que centraliza promoções de filmes para a câmera **Instax Mini 12** e inclui uma calculadora de custos e lucro para quem vende fotos instantâneas.

## Funcionalidades

- **Calculadora de custo e lucro** (primeira feature em desenvolvimento):
  - Entradas: valor pago na compra (filmes + frete), quantidade de fotos, preço de venda por foto.
  - Saídas: custo por foto, lucro por foto, lucro total e margem de lucro.
  - Deve tratar quantidade zero/valores negativos (erro), prejuízo e arredondamento em centavos.
- **Agregador de promoções** (futuro): reúne ofertas de filmes compatíveis com a Instax Mini 12.

## Tech Stack

- **App (Android)**: Kotlin + Jetpack Compose, testes unitários com JUnit
- **Backend (futuro, para o agregador)**: Python 3.12 + FastAPI + PostgreSQL 17, em Docker — ver `backend/`

## Estrutura

- `backend/` — API FastAPI + Postgres via Docker Compose (parado por enquanto)
  - Subir: `cd backend && docker compose up -d --build` → `http://localhost:8000/health`
  - Credenciais locais em `backend/.env` (não versionar; modelo em `.env.example`)

## Fluxo de trabalho (obrigatório)

1. Antes de qualquer mudança: explicar o que será feito e pedir autorização.
2. Escrever os testes unitários primeiro.
3. Implementar até todos os testes passarem.
4. Commitar seguindo o padrão abaixo.

## Padrão de commits

Baseado em github.com/GardViktor/Commits: `tipo: descrição`, em português, no imperativo (`adicionar`, `corrigir`), com menos de 50/72 caracteres, commits pequenos e focados.

Tipos: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`.
