# BookHive Frontend

Next.js (App Router) + TypeScript + Tailwind CSS web app for BookHive.

## Run locally

Needs Node.js 24 and pnpm.

```bash
pnpm install
pnpm dev      # http://localhost:3000
```

## Scripts

| Command             | What it does                                                                 |
| ------------------- | ---------------------------------------------------------------------------- |
| `pnpm dev`          | Start the dev server with hot reload                                         |
| `pnpm build`        | Production build                                                             |
| `pnpm start`        | Run the production build                                                     |
| `pnpm lint`         | Check code with ESLint                                                       |
| `pnpm typecheck`    | Generate route types and check TypeScript                                    |
| `pnpm format`       | Format all files with Prettier (sorts Tailwind classes too)                  |
| `pnpm format:check` | Fail if any file isn't formatted (used in CI)                                |
| `pnpm api:types`    | Regenerate `src/lib/api-schema.d.ts` from the running backend's OpenAPI spec |
