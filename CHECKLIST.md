# 🐝 BookHive — Build Checklist

> From the Build Plan in `docs/BookHive_PRD.md` §17. One step at a time, in order.
> Tick a step (`[x]`) when it's done and committed.


## Phase 0 — Setup & Tooling (1–20)

- [x] **1.** Install JDK 25 (OpenJDK) and check with `java -version`
- [x] **2.** Install Node.js 24 LTS + pnpm
- [x] **3.** Install Docker + Docker Compose
- [x] **4.** Install IntelliJ IDEA Community (or VS Code + Java Extension Pack)
- [x] **5.** Configure Git (name, email, SSH key on GitHub)
- [x] **6.** Create GitHub repo `bookhive` (monorepo)
- [x] **7.** Create folders `backend/ frontend/ infra/ docs/`
- [x] **8.** Write root README (pitch, stack, status)
- [x] **9.** Add `.gitignore` for Java, Node, IDE files and `.env*`
- [x] **10.** Add `.editorconfig`
- [x] **11.** Put this PRD in `docs/BookHive_PRD.md`
- [ ] **12.** Create a GitHub Project board (Backlog / Todo / In progress / Done)
- [ ] **13.** Create labels (backend, frontend, infra, docs, bug, P0/P1/P2)
- [ ] **14.** Create milestones M0–M5
- [ ] **15.** Branching rule: `main` protected, work on `feat/*` branches, merge by PR
- [x] **16.** Adopt Conventional Commits (`feat:`, `fix:`, `chore:`)
- [x] **17.** Create `infra/docker-compose.yml` with Postgres 16
- [x] **18.** Add Redis and Mailpit to compose
- [ ] **19.** Install DBeaver (or pgAdmin)
- [x] **20.** `docker compose up -d`, connect to everything, commit

## Phase 1 — Design (21–55): removed

> Skipped on purpose. We build the app first; design work is done step by step when Jude asks for it.

## Phase 2 — Backend Foundation (56–90)

- [x] **56.** Generate the project at start.spring.io: Gradle Kotlin, Spring Boot 4.1.1, Java 25, group `lk.bookhive`; deps Web, Validation, Data JPA, PostgreSQL, Flyway, Security, Actuator, DevTools
- [ ] **57.** Put it in `backend/`, run `./gradlew bootRun`
- [ ] **58.** Create module packages: `auth, member, catalog, search, circulation, hold, fine, payment, notification, event, community, report, admin, common`
- [ ] **59.** Inside each module: `api/ domain/ service/ repository/ event/`
- [ ] **60.** `application.yml` with profiles `dev`, `test`, `prod`
- [ ] **61.** Read secrets from env vars; add `.env.example`
- [ ] **62.** Connect to Postgres from compose
- [ ] **63.** Set `ddl-auto: validate`
- [ ] **64.** Flyway `V1__extensions.sql` (`pgcrypto`, `citext`, `pg_trgm`)
- [ ] **65.** Create `BaseEntity` (UUID id, createdAt, updatedAt, `@Version`)
- [ ] **66.** Enable JPA auditing (`@EnableJpaAuditing`)
- [ ] **67.** Add global API prefix `/api/v1`
- [ ] **68.** Use RFC 7807 `ProblemDetail` for errors with a `code` field
- [ ] **69.** `GlobalExceptionHandler` (`@RestControllerAdvice`)
- [ ] **70.** Custom exceptions: `NotFoundException`, `BusinessRuleException`, `ConflictException`
- [ ] **71.** Bean Validation on DTOs + field error mapping
- [ ] **72.** `PageResponse<T>` wrapper (content, page, size, total)
- [ ] **73.** Add MapStruct
- [ ] **74.** Add springdoc-openapi (Swagger UI)
- [ ] **75.** Actuator: expose only `health` and `info`
- [ ] **76.** Request ID filter + MDC logging; JSON logs in prod
- [ ] **77.** CORS config for the frontend origin
- [ ] **78.** Inject a `java.time.Clock` bean
- [ ] **79.** Store timestamps in UTC; business dates in Asia/Colombo
- [ ] **80.** Money = `BigDecimal` (scale 2, LKR)
- [ ] **81.** Add Spotless (google-java-format)
- [ ] **82.** Test setup: JUnit 5, AssertJ, Testcontainers Postgres
- [ ] **83.** First test: context loads
- [ ] **84.** ArchUnit test: modules can't use other modules' repositories
- [ ] **85.** Backend Dockerfile (multi-stage, layered jar, non-root user)
- [ ] **86.** Add the backend to compose (optional profile)
- [ ] **87.** GitHub Actions: backend CI (build + test)
- [ ] **88.** Branch protection: require CI to pass
- [ ] **89.** Dev seed-data runner stub (`dev` profile only)
- [ ] **90.** Commit → PR → merge

## Phase 3 — Frontend Foundation (91–120)

- [ ] **91.** `pnpm create next-app frontend` (TypeScript, App Router, Tailwind, ESLint, `src/`)
- [ ] **92.** Prettier + `prettier-plugin-tailwindcss`
- [ ] **93.** Route groups: `(public)`, `(member)`, `(staff)`, `(admin)` + `components/`, `features/`, `lib/`
- [ ] **94.** Put design tokens into CSS variables + Tailwind theme
- [ ] **95.** Load Noto fonts with `next/font`
- [ ] **96.** Init shadcn/ui
- [ ] **97.** Add base components: Button, Input, Card, Badge, Dialog, Table, Toast
- [ ] **98.** Build the Logo + HexLoader components
- [ ] **99.** Public layout: header (search, language switcher, login), footer
- [ ] **100.** Member layout with sidebar
- [ ] **101.** Staff layout (dense, desktop-first)
- [ ] **102.** Admin layout
- [ ] **103.** Dark mode with `next-themes`
- [ ] **104.** Typed API client (`lib/api.ts`, base URL, error parsing)
- [ ] **105.** Generate TS types from OpenAPI (`openapi-typescript`)
- [ ] **106.** Add the TanStack Query provider
- [ ] **107.** Write `docs/frontend-conventions.md` (server vs client components, data fetching rules)
- [ ] **108.** React Hook Form + Zod
- [ ] **109.** `FormField` wrapper (label, input, error, hint)
- [ ] **110.** `loading.tsx` / `error.tsx` / `not-found.tsx`
- [ ] **111.** i18n with `next-intl` (`/en`, `/si`, `/ta`)
- [ ] **112.** Move all UI text into message files
- [ ] **113.** `.env.local.example`
- [ ] **114.** Vitest + React Testing Library
- [ ] **115.** Playwright setup
- [ ] **116.** Frontend Dockerfile (`output: 'standalone'`)
- [ ] **117.** Frontend CI (lint, typecheck, test, build)
- [ ] **118.** Build the static home page from the design
- [ ] **119.** Language switcher working
- [ ] **120.** Commit → PR → merge

## Phase 4 — Authentication & Accounts (121–163)

- [ ] **121.** Migration: `users`, `user_roles`
- [ ] **122.** `User` entity (citext email, password hash, name, phone, language, status)
- [ ] **123.** `Role` enum: MEMBER, LIBRARIAN, ADMIN
- [ ] **124.** `UserRepository`
- [ ] **125.** BCrypt `PasswordEncoder` (strength 12)
- [ ] **126.** Decide auth design: 15 min access JWT + 7 day refresh token in an httpOnly cookie. Write it as an ADR
- [ ] **127.** Migration: `refresh_tokens` (hash, family, expiry, revoked)
- [ ] **128.** `JwtService` with RS256 key pair
- [ ] **129.** `SecurityFilterChain`: stateless, public routes, role rules
- [ ] **130.** `JwtAuthenticationFilter`
- [ ] **131.** `POST /auth/register`
- [ ] **132.** Migration `verification_tokens` + send a verify email (Mailpit)
- [ ] **133.** `POST /auth/verify-email`
- [ ] **134.** `POST /auth/login` returns access token + sets refresh cookie
- [ ] **135.** `POST /auth/refresh` with rotation + reuse detection
- [ ] **136.** `POST /auth/logout` (revoke token, clear cookie)
- [ ] **137.** Forgot password: create token, email the link
- [ ] **138.** Reset password endpoint (single-use token, revokes sessions)
- [ ] **139.** Change password (needs current password)
- [ ] **140.** `GET/PATCH /me`
- [ ] **141.** Rate limit login/register with Bucket4j + Redis
- [ ] **142.** Account lockout after 5 failures for 15 min
- [ ] **143.** Password policy (≥ 8 chars, not in a common-password list)
- [ ] **144.** Enable method security (`@PreAuthorize`)
- [ ] **145.** `audit_logs` table + `AuditService`
- [ ] **146.** Admin endpoint: create staff user (invite email)
- [ ] **147.** Bootstrap an admin from env on first start
- [ ] **148.** Integration tests: register, verify, login, refresh rotation, lockout
- [ ] **149.** Frontend auth design: access token in memory, refresh via cookie
- [ ] **150.** Login page
- [ ] **151.** Register page (Zod rules match backend)
- [ ] **152.** Verify-email result page
- [ ] **153.** Forgot / reset password pages
- [ ] **154.** `useSession` hook (current user + roles)
- [ ] **155.** Silent refresh on 401, then retry the request
- [ ] **156.** Next.js middleware guards `/account`, `/staff`, `/admin`
- [ ] **157.** Role-based navigation
- [ ] **158.** Logout
- [ ] **159.** Profile page (name, phone, language)
- [ ] **160.** Change password page
- [ ] **161.** Toasts for all auth actions
- [ ] **162.** Playwright e2e: register → verify (read Mailpit API) → login
- [ ] **163.** PR → merge

## Phase 5 — Membership (164–189)

- [ ] **164.** Migration `membership_types`
- [ ] **165.** Seed Adult, Junior, Senior, Student with §4.1 defaults
- [ ] **166.** Migration `members` (member_no, type, status, encrypted NIC, DOB, address, guardian, expiry)
- [ ] **167.** Member number generator (`BH-000123` via a DB sequence)
- [ ] **168.** Application endpoint with document upload
- [ ] **169.** Storage abstraction + MinIO in compose
- [ ] **170.** Upload validation (type, size) + private bucket + presigned URLs
- [ ] **171.** NIC column encryption (AES-GCM via JPA converter)
- [ ] **172.** Junior application requires a guardian member
- [ ] **173.** Staff: list pending applications
- [ ] **174.** Staff: approve / reject with reason + email
- [ ] **175.** Status transitions in one place (state machine method)
- [ ] **176.** Renewal endpoint (extend expiry; fee → fine record for payment later)
- [ ] **177.** Suspend / reactivate with reason + audit
- [ ] **178.** Digital card: Code128 barcode + QR with ZXing
- [ ] **179.** Card PDF (OpenPDF)
- [ ] **180.** Tests for status transitions and the guardian rule
- [ ] **181.** Frontend: multi-step application form
- [ ] **182.** File upload component with preview and progress
- [ ] **183.** Application status page
- [ ] **184.** Digital card page (barcode + download)
- [ ] **185.** Staff: applications queue
- [ ] **186.** Staff: review page (view documents, approve/reject)
- [ ] **187.** Staff: member search + profile (tabs: loans, holds, fines, history)
- [ ] **188.** Admin: membership types CRUD page
- [ ] **189.** E2E: apply → staff approves → card visible. PR

## Phase 6 — Catalog (190–231)

- [ ] **190.** Write `docs/adr/catalog-model.md`: Book (title) vs Copy (item)
- [ ] **191.** Migrations: `authors`, `publishers`, `categories` (tree + DDC + section)
- [ ] **192.** Migration: `books`
- [ ] **193.** Migration: `book_authors` (role, position)
- [ ] **194.** Migration: `book_categories`
- [ ] **195.** Migration: `copies` (barcode, status, lending type, condition, shelf, acquisition)
- [ ] **196.** Entities + repositories (use `@EntityGraph` for authors)
- [ ] **197.** Author CRUD API
- [ ] **198.** Publisher CRUD API
- [ ] **199.** Category tree API
- [ ] **200.** Book create / update / get API
- [ ] **201.** Book list API with paging and sorting
- [ ] **202.** Copy add / update / withdraw API
- [ ] **203.** Copy barcode generation + label sheet PDF
- [ ] **204.** Cover upload → thumbnails (Thumbnailator) → storage
- [ ] **205.** ISBN lookup via Open Library
- [ ] **206.** Cache ISBN lookups in Redis (7 days)
- [ ] **207.** Duplicate ISBN check
- [ ] **208.** Withdraw rules (can't withdraw an on-loan copy)
- [ ] **209.** `copy_status_history` + recording on every change
- [ ] **210.** Availability summary (total, available, on loan, on hold shelf, holds waiting)
- [ ] **211.** Bulk CSV import with a row-level error report
- [ ] **212.** Donations module
- [ ] **213.** Purchase suggestions API
- [ ] **214.** Catalog tests
- [ ] **215.** Staff: catalog list (search, filters, paging)
- [ ] **216.** Staff: add book form + "Lookup ISBN" button
- [ ] **217.** Author/publisher combobox with "create new"
- [ ] **218.** Category tree picker
- [ ] **219.** Cover upload with crop
- [ ] **220.** Book edit page
- [ ] **221.** Copies tab: add copies, print labels
- [ ] **222.** Copy detail with status history
- [ ] **223.** CSV import page (upload → preview → errors → confirm)
- [ ] **224.** Donations pages
- [ ] **225.** Suggestions queue page
- [ ] **226.** Public: BookCard component (cover, title, author, availability)
- [ ] **227.** Public: book detail page (SSR, SEO metadata, availability per copy and shelf)
- [ ] **228.** Member: "Suggest a purchase" form
- [ ] **229.** Seed 200 real-looking books (dev profile)
- [ ] **230.** E2E: staff adds a book via ISBN → it appears publicly
- [ ] **231.** PR

## Phase 7 — Search & Public Site (232–259)

- [ ] **232.** Add a `search_vector` tsvector column + GIN index (title, authors, subjects, ISBN)
- [ ] **233.** Trigram index for fuzzy matching
- [ ] **234.** Use the `simple` text config for Sinhala/Tamil
- [ ] **235.** Search API (q, filters, sort, paging)
- [ ] **236.** Facet counts (per category/language/format)
- [ ] **237.** Autocomplete endpoint
- [ ] **238.** New arrivals + popular (loans in the last 90 days)
- [ ] **239.** Redis cache for public endpoints with eviction on update
- [ ] **240.** Search tests with seed data (ranking, typos, filters)
- [ ] **241.** Search page with URL-driven filters (`searchParams`)
- [ ] **242.** Filter sidebar + mobile drawer
- [ ] **243.** Header autocomplete (debounced)
- [ ] **244.** Grid/list toggle + pagination
- [ ] **245.** Empty state: "No results, suggest a purchase?"
- [ ] **246.** Home page wired to real data (hero search, new, popular, categories)
- [ ] **247.** Category browse pages
- [ ] **248.** Author pages
- [ ] **249.** Opening hours + holidays API & "Open now" badge
- [ ] **250.** Visit page (hours, map embed, contact)
- [ ] **251.** Contact form with Cloudflare Turnstile + rate limit
- [ ] **252.** Rules/FAQ page generated from settings
- [ ] **253.** SEO: metadata, `sitemap.xml`, `robots.txt`, OpenGraph images
- [ ] **254.** Use `next/image` everywhere
- [ ] **255.** Lighthouse audit + fixes
- [ ] **256.** Sinhala + Tamil translations for public pages
- [ ] **257.** Privacy policy + terms pages
- [ ] **258.** E2E: search → filter → open book
- [ ] **259.** PR. 🎉 M1 done: people can find books and join

## Phase 8 — Circulation (260–295)

- [ ] **260.** Migration `library_settings` (typed key/value JSONB)
- [ ] **261.** `SettingsService` with caching
- [ ] **262.** Migration `loans`
- [ ] **263.** `Loan` entity + repository
- [ ] **264.** `DueDateCalculator` (loan days by type, skip closed days and holidays)
- [ ] **265.** `EligibilityChecker` returns a list of reasons (status, expiry, limit, fines, reference-only, junior section, held for someone else)
- [ ] **266.** Checkout service: `@Transactional` + pessimistic lock on the copy row
- [ ] **267.** `POST /staff/checkouts` (member barcode + copy barcodes)
- [ ] **268.** Return service: close the loan, compute the fine, decide routing (shelf or hold shelf)
- [ ] **269.** `POST /staff/returns` by copy barcode
- [ ] **270.** Publish `LoanCreated` / `LoanReturned` events
- [ ] **271.** Renew service (max renewals, no holds waiting, member not blocked)
- [ ] **272.** Renew endpoints (member + staff)
- [ ] **273.** Staff override with reason (admin permission + audit)
- [ ] **274.** Mark lost / damaged → charge
- [ ] **275.** "Lost item found" reversal (refund or cancel charge)
- [ ] **276.** Loan history queries (by member, by copy) respecting the history-privacy setting
- [ ] **277.** Staff lists: current and overdue loans
- [ ] **278.** Add ShedLock + a daily overdue-marking job
- [ ] **279.** Unit tests: due dates and eligibility (table-driven, fixed `Clock`)
- [ ] **280.** Concurrency test: two parallel checkouts of one copy → only one succeeds
- [ ] **281.** Desk mode page: a big auto-focused scan input
- [ ] **282.** Scan member → summary card with alerts (fines, expiry, limit)
- [ ] **283.** Scan items → checkout list with due dates and inline errors
- [ ] **284.** Confirm → receipt (print view + email option)
- [ ] **285.** Returns mode: continuous scanning with routing message per item
- [ ] **286.** Keyboard shortcuts + success/error sounds
- [ ] **287.** Camera barcode scanning (BarcodeDetector / zxing-js)
- [ ] **288.** Staff loans list (current / overdue, filters)
- [ ] **289.** Member: My Loans (due badges, Renew button)
- [ ] **290.** Member: Reading history page + privacy toggle
- [ ] **291.** Staff override dialog
- [ ] **292.** Staff member profile: loans tab live
- [ ] **293.** Seed realistic loan history
- [ ] **294.** E2E: checkout → renew → return
- [ ] **295.** PR

## Phase 9 — Holds (296–315)

- [ ] **296.** Migration `holds`
- [ ] **297.** Place-hold rules (none available, max holds, not already borrowed or held)
- [ ] **298.** Queue position calculation (FIFO by `placed_at`)
- [ ] **299.** Listen to `LoanReturned` → assign copy to first hold → READY → set expiry
- [ ] **300.** Checkout respects holds (a READY copy can go only to its holder)
- [ ] **301.** Hold expiry job → EXPIRED → next in queue
- [ ] **302.** Cancel hold (member/staff) → reassign copy if it was READY
- [ ] **303.** Staff hold shelf API
- [ ] **304.** Staff: place a hold for a member
- [ ] **305.** Publish `HoldReady` / `HoldExpiring` events
- [ ] **306.** Tests: queue order, expiry chain, cancel while READY
- [ ] **307.** "Reserve" button on book detail (shows queue length)
- [ ] **308.** Member: My Holds (position, pickup deadline, cancel)
- [ ] **309.** Member dashboard: "Ready for pickup" banner
- [ ] **310.** Staff hold shelf page (sorted by expiry)
- [ ] **311.** Staff: holds tab on member profile
- [ ] **312.** Desk return routing shows the holder's name
- [ ] **313.** E2E: reserve → return → READY → checkout by holder
- [ ] **314.** Edge-case test: a non-holder tries to borrow a READY copy → blocked
- [ ] **315.** PR. 🎉 M2 done: the library can operate on BookHive

## Phase 10 — Fines & Payments (316–352)

- [ ] **316.** Migration `fines` + `fine_transactions` (ledger)
- [ ] **317.** Overdue fine calculator (rate, grace days, cap per item)
- [ ] **318.** Daily accrual job (updates running fines for overdue loans)
- [ ] **319.** Finalise the fine on return
- [ ] **320.** Lost / damage / membership fee charges
- [ ] **321.** Member balance query
- [ ] **322.** Borrowing block above threshold (plugged into `EligibilityChecker`)
- [ ] **323.** Staff: record a cash payment (receipt no.)
- [ ] **324.** Waive with reason + role limits + audit
- [ ] **325.** Read the PayHere docs; create a sandbox merchant account
- [ ] **326.** Migration `payments` + `payment_items`
- [ ] **327.** `POST /me/payments/checkout`: creates a PENDING payment, generates the PayHere hash server-side
- [ ] **328.** `POST /payments/payhere/notify`: verify `md5sig`, then update the payment + fines
- [ ] **329.** Make notify idempotent (unique order_id + status check)
- [ ] **330.** Store the raw payload (JSONB)
- [ ] **331.** Expose local notify via Cloudflare Tunnel / ngrok
- [ ] **332.** Return / cancel URL handling
- [ ] **333.** Reconciliation job for payments stuck in PENDING
- [ ] **334.** Receipt PDF + email
- [ ] **335.** Membership fee paid through the same flow
- [ ] **336.** Publish `PaymentSucceeded`
- [ ] **337.** Tests: hash generation, bad signature rejected, duplicate notify, partial payments
- [ ] **338.** Member: Fines page (list, total, Pay Now)
- [ ] **339.** Pay flow: request checkout params → submit to PayHere
- [ ] **340.** Result pages (success / cancelled / pending with polling)
- [ ] **341.** Payment history + receipt download
- [ ] **342.** Staff fines page (balance, record cash, waive dialog)
- [ ] **343.** Staff desk: fine warning with "take cash" shortcut
- [ ] **344.** Admin: fine settings form
- [ ] **345.** Fines summary on the member dashboard
- [ ] **346.** Manual sandbox test checklist in `docs/payments-testing.md`
- [ ] **347.** E2E with mocked gateway
- [ ] **348.** Security review of the payment code (logging, amounts, currency checks)
- [ ] **349.** Write ADR: why ledger + webhook-first
- [ ] **350.** Document PayHere go-live steps
- [ ] **351.** Refactor and clean up the fines module
- [ ] **352.** PR

## Phase 11 — Notifications (353–378)

- [ ] **353.** Design notifications as event listeners
- [ ] **354.** `@TransactionalEventListener(AFTER_COMMIT)`
- [ ] **355.** Migration `outbox_messages`
- [ ] **356.** Outbox dispatcher job with exponential backoff
- [ ] **357.** Thymeleaf email templates in EN/SI/TA
- [ ] **358.** Email adapter (SMTP: Mailpit in dev, SES in prod)
- [ ] **359.** SMS adapter interface + Notify.lk / Text.lk implementation (feature flag)
- [ ] **360.** Migration `notifications` (in-app) + API
- [ ] **361.** Migration `notification_preferences` + API
- [ ] **362.** Due-soon job (2 days before due)
- [ ] **363.** Overdue notices (day 1, day 7)
- [ ] **364.** Hold ready / expiring notices
- [ ] **365.** Membership approved / expiring (30 days) notices
- [ ] **366.** Payment success notice
- [ ] **367.** Failure log + admin view of failed messages
- [ ] **368.** Tests with fake providers (assert message content + retries)
- [ ] **369.** Bell icon with unread count (poll every 60 s or SSE)
- [ ] **370.** Notifications page (mark read / all read)
- [ ] **371.** Preferences page (per type: email, SMS, in-app)
- [ ] **372.** Admin: template preview page
- [ ] **373.** Check all emails in Mailpit in 3 languages
- [ ] **374.** Test the email layout on Gmail mobile
- [ ] **375.** SPF/DKIM notes for prod in docs
- [ ] **376.** E2E: a due-soon email arrives (using a fixed clock in the test profile)
- [ ] **377.** Refactor
- [ ] **378.** PR. 🎉 M3 done: online payments + reminders

## Phase 12 — Events, News & Community (379–405)

- [ ] **379.** Migration `events` + `event_registrations`
- [ ] **380.** Events CRUD (staff)
- [ ] **381.** Registration with capacity + waitlist auto-promotion
- [ ] **382.** Cancel registration; reminder the day before
- [ ] **383.** QR check-in endpoint
- [ ] **384.** Migration `news` + CRUD
- [ ] **385.** Sanitise rich text server-side (jsoup safelist)
- [ ] **386.** Reviews: only members who borrowed the book; moderation status
- [ ] **387.** Wishlist API
- [ ] **388.** "Readers also borrowed" query (co-borrow counts, cached)
- [ ] **389.** Tests
- [ ] **390.** Events list + calendar view
- [ ] **391.** Event detail + register / waitlist
- [ ] **392.** Member: My Events
- [ ] **393.** Staff: events management + attendee CSV export
- [ ] **394.** Staff: QR check-in page (camera)
- [ ] **395.** News list + detail
- [ ] **396.** Staff: news editor (Tiptap)
- [ ] **397.** Reviews on the book page + write review
- [ ] **398.** Staff: review moderation queue
- [ ] **399.** Wishlist button + page
- [ ] **400.** "Also borrowed" row on book detail
- [ ] **401.** Home page: events + news sections live
- [ ] **402.** Translations for the new pages
- [ ] **403.** E2E: register for event → waitlist promotion
- [ ] **404.** Refactor
- [ ] **405.** PR

## Phase 13 — Admin & Reports (406–430)

- [ ] **406.** Admin settings API (validated groups: loans, fines, holds, notifications)
- [ ] **407.** Opening hours & holidays management API
- [ ] **408.** Staff management (invite, change role, deactivate)
- [ ] **409.** Audit log query API (filter by actor, action, date)
- [ ] **410.** Staff dashboard API (today's KPIs)
- [ ] **411.** Report: circulation by day/month/category
- [ ] **412.** Report: most borrowed / never borrowed
- [ ] **413.** Report: overdue + fines collected/waived
- [ ] **414.** Report: membership growth
- [ ] **415.** Stock-take API: start session → scan shelf → compare → missing list
- [ ] **416.** CSV + PDF export
- [ ] **417.** Optimise report queries (indexes, maybe materialised views refreshed nightly)
- [ ] **418.** Tests for reports with known seed data
- [ ] **419.** Admin settings pages (forms + validation + "last changed by")
- [ ] **420.** Hours & holidays calendar UI
- [ ] **421.** Staff management UI
- [ ] **422.** Audit log page
- [ ] **423.** Reports pages with Recharts
- [ ] **424.** Date range picker + export buttons
- [ ] **425.** Stock-take page (scan mode + progress)
- [ ] **426.** Staff dashboard home (cards + charts)
- [ ] **427.** Admin: library info page
- [ ] **428.** Translations
- [ ] **429.** E2E: admin changes loan days → a new checkout uses them
- [ ] **430.** PR. 🎉 M4 done: full feature set

## Phase 14 — i18n, Accessibility & Performance (431–442)

- [ ] **431.** Finish Sinhala + Tamil translations for member, staff and admin UIs
- [ ] **432.** Backend error codes → translated frontend messages
- [ ] **433.** LKR + local date formatting everywhere (`Intl`)
- [ ] **434.** Font-size toggle (A−/A/A+)
- [ ] **435.** axe accessibility audit + fixes
- [ ] **436.** Keyboard-only walkthrough of every flow
- [ ] **437.** Screen reader test (NVDA / VoiceOver) on the main flows
- [ ] **438.** Real-phone responsive QA (small Android, iPhone)
- [ ] **439.** PWA: installable + offline fallback page
- [ ] **440.** Bundle analysis + code splitting of heavy staff/admin parts
- [ ] **441.** Backend: find N+1 queries (Hibernate statistics) + fix with fetch joins / entity graphs
- [ ] **442.** `EXPLAIN ANALYZE` on hot queries + index tuning

## Phase 15 — Security & Privacy Hardening (443–452)

- [ ] **443.** Security headers (CSP, HSTS, X-Frame-Options, Referrer-Policy) on both apps
- [ ] **444.** CSRF protection for cookie endpoints (`SameSite=Strict` + CSRF token on refresh/logout)
- [ ] **445.** IDOR tests: member A can't read member B's loans, fines or documents
- [ ] **446.** Rate limits on public APIs (search, contact, ISBN lookup)
- [ ] **447.** Dependabot + Trivy / OWASP dependency-check in CI
- [ ] **448.** gitleaks secret scanning in CI
- [ ] **449.** OWASP ZAP baseline scan against staging
- [ ] **450.** Data retention jobs: delete ID documents after 90 days; anonymise history when it's turned off
- [ ] **451.** "Download my data" + "delete my account" requests
- [ ] **452.** Write `docs/security.md` (threat model, controls)

## Phase 16 — Testing Completeness (453–460)

- [ ] **453.** JaCoCo coverage with a 70% gate in CI
- [ ] **454.** OpenAPI diff check in CI (fail on breaking changes)
- [ ] **455.** Component tests for key forms (application, checkout, payment)
- [ ] **456.** Full Playwright suite: public, member, staff and admin journeys
- [ ] **457.** Demo data generator (Datafaker): 2,000 books, 5,000 copies, 300 members, 1 year of loans
- [ ] **458.** k6 load test: search + checkout at 200 virtual users
- [ ] **459.** Run e2e in CI against the compose stack
- [ ] **460.** Bug bash: use it as a librarian for an hour, then fix the list

## Phase 17 — DevOps & AWS Deployment (461–480)

- [ ] **461.** AWS account hardening: MFA, IAM Identity Center, budget alarm
- [ ] **462.** Buy a domain + Route 53 hosted zone
- [ ] **463.** Terraform structure + remote state (S3 + DynamoDB lock)
- [ ] **464.** VPC with public/private subnets
- [ ] **465.** RDS Postgres (private, automated backups, encryption)
- [ ] **466.** S3 buckets: public covers (via CloudFront) + private documents
- [ ] **467.** ECR repositories
- [ ] **468.** ECS Fargate cluster + task definitions (backend, frontend)
- [ ] **469.** ALB + ACM HTTPS; route `/api/*` → backend, everything else → frontend
- [ ] **470.** Secrets Manager / SSM for DB, JWT keys, PayHere and SMS secrets
- [ ] **471.** SES: verify domain, DKIM, request production access
- [ ] **472.** Redis (ElastiCache t4g.micro or Upstash)
- [ ] **473.** CloudFront in front of the frontend + covers
- [ ] **474.** GitHub Actions → AWS via OIDC (no stored keys)
- [ ] **475.** CD pipeline: build → test → Trivy scan → push to ECR → deploy to ECS on merge to `main`
- [ ] **476.** Staging environment (separate Terraform workspace)
- [ ] **477.** Flyway migrations run safely on deploy + rolling deploy with health checks
- [ ] **478.** Observability: Micrometer → CloudWatch (or Prometheus + Grafana), OpenTelemetry traces, log retention
- [ ] **479.** Alarms: 5xx rate, p95 latency, job failures, payment failures, DB CPU
- [ ] **480.** Backup restore drill + cost review + `terraform destroy` script

## Phase 18 — Launch & Documentation (481–490)

- [ ] **481.** Production smoke-test checklist (signup, search, checkout, pay in sandbox, email)
- [ ] **482.** UAT with a librarian (if possible) + fixes
- [ ] **483.** Staff user guide (`docs/staff-guide.md`, with screenshots)
- [ ] **484.** Member help pages on the site
- [ ] **485.** Architecture diagrams (C4: context, container, component)
- [ ] **486.** ADRs reviewed and complete
- [ ] **487.** README polish: screenshots, GIFs, demo logins, architecture, how to run
- [ ] **488.** Demo video (3–5 min walkthrough)
- [ ] **489.** Medium article + LinkedIn post
- [ ] **490.** Tag `v1.0.0` + changelog + retrospective notes. 🎉 M5 done: BookHive v1 live

## Phase 19 — Stretch Goals / v2 (491–500)

- [ ] **491.** Google sign-in
- [ ] **492.** Self-checkout kiosk mode
- [ ] **493.** E-book lending with expiring signed links
- [ ] **494.** Multi-branch support + inter-branch transfers
- [ ] **495.** Study room booking
- [ ] **496.** Kids' reading challenges + badges
- [ ] **497.** Search upgrade to OpenSearch (if Postgres search gets limiting)
- [ ] **498.** Mobile app (Expo / React Native) using the same API + push notifications
- [ ] **499.** Move to Kubernetes (EKS) with Helm + Argo CD GitOps
- [ ] **500.** AI "Ask the Librarian" assistant over the catalog

---

**Progress:** 16 / 465 done (Phase 1 removed)
