# 🐝 BookHive

> Every book in the library is one search away, and every library task is one scan away.

BookHive is a trilingual (English / සිංහල / தமிழ்) web platform for public libraries in Sri Lanka. Many local libraries still run on paper registers: people can't check if a book is in, fines are paid only in cash, and librarians spend hours on manual lists. BookHive moves all of that online.

## Features (planned)

- **Public website**: search the catalog, see live availability and shelf location, library hours, events and news
- **Member portal**: current loans, online renewals, holds (reservations), fines with online payment, digital library card
- **Staff console**: fast barcode-based checkout and returns, cataloging with ISBN lookup, membership approvals
- **Admin console**: library rules, opening hours and holidays, staff accounts, reports, audit logs

## Tech stack

| Layer | Tools |
|---|---|
| Backend | Spring Boot, Java 25, Gradle (Kotlin DSL), Spring Data JPA, Flyway, Spring Security (JWT) |
| Database & cache | PostgreSQL 16, Redis |
| Frontend | Next.js (App Router), TypeScript, Tailwind CSS, shadcn/ui, TanStack Query, next-intl |
| Payments | PayHere (sandbox) |
| Testing | JUnit 5, Testcontainers, ArchUnit, Vitest, Playwright, k6 |
| DevOps | Docker, Docker Compose, GitHub Actions, Terraform, AWS |

The backend is a **modular monolith**: one deployable app split into modules (auth, catalog, circulation, holds, fines, payments, …) with enforced boundaries.

## Project structure

```
bookhive/
├── backend/     # Spring Boot API
├── frontend/    # Next.js web app
├── infra/       # Docker Compose, Terraform
└── .github/     # CI/CD workflows
```

## Status

🚧 **Phase 2: Backend Foundation.** The backend skeleton runs; no features yet.

| Milestone | Scope | Status |
|---|---|---|
| M0 Foundations | Setup, design, app skeletons, CI | 🔄 In progress |
| M1 Core | Auth, membership, catalog, search, public site | ⏳ |
| M2 Circulation | Loans, returns, renewals, holds, desk mode | ⏳ |
| M3 Money & Comms | Fines, PayHere, notifications | ⏳ |
| M4 Community & Admin | Events, news, reviews, settings, reports | ⏳ |
| M5 Quality & Launch | i18n, accessibility, security, AWS deploy | ⏳ |

## Getting started

Setup instructions will be added once the backend and frontend skeletons exist.
