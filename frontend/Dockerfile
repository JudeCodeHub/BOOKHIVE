# syntax=docker/dockerfile:1
# BookHive frontend image.  Build from the repo root:
#   docker build -t bookhive-frontend --build-arg NEXT_PUBLIC_API_URL=https://api.example.lk/api/v1 frontend

# ---------- Base: Node 24 + pnpm (version pinned by "packageManager" in package.json) ----------
FROM node:24-alpine AS base
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0 \
    NEXT_TELEMETRY_DISABLED=1
RUN corepack enable
WORKDIR /app

# ---------- Stage 1: install dependencies (cached until the lockfile changes) ----------
FROM base AS deps
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN --mount=type=cache,id=pnpm-store,target=/pnpm/store \
    pnpm install --frozen-lockfile --store-dir /pnpm/store

# ---------- Stage 2: build ----------
FROM base AS build
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# NEXT_PUBLIC_* values are baked into the browser JavaScript at build time,
# so the public API URL must be known here (one image per environment).
ARG NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
RUN pnpm build

# ---------- Stage 3: run (only the standalone server, no source or dev dependencies) ----------
FROM node:24-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# Never run as root
RUN addgroup -S nextjs && adduser -S nextjs -G nextjs

# server.js + the traced node_modules it needs; static assets are not included by default
COPY --from=build --chown=nextjs:nextjs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nextjs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
# API_URL (server-side backend address, e.g. http://backend:8080/api/v1) can be set at runtime
CMD ["node", "server.js"]
