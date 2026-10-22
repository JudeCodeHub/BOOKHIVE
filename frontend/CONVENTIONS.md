# Frontend conventions

Rules for writing BookHive frontend code, so every page is built the same way.

## Folders

| Folder                                             | What goes there                                                                                                                          |
| -------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `src/app/`                                         | Routes only: `page.tsx`, `layout.tsx`, `loading.tsx`, `error.tsx`. Keep them thin.                                                       |
| `src/app/[locale]/(public\|member\|staff\|admin)/` | One route group per area, each with its own layout.                                                                                      |
| `src/features/<feature>/`                          | Everything for one feature (e.g. `catalog/`, `loans/`, `auth/`): components, hooks, API calls, Zod schemas. Mirrors the backend modules. |
| `src/components/ui/`                               | shadcn/ui primitives (Button, Input...). We own and may edit them.                                                                       |
| `src/components/` (other folders)                  | Shared app components: `layout/`, `brand/`, `forms/`, `theme/`, `providers/`.                                                            |
| `src/lib/`                                         | Framework-free helpers: `api.ts`, `query-client.ts`, `utils.ts`.                                                                         |
| `src/i18n/`, `messages/`                           | next-intl routing/config and the `en`/`si`/`ta` translation files.                                                                       |

- File names are `kebab-case.tsx`; components are `PascalCase`.
- Import with the `@/` alias (`@/components/ui/button`), never `../../..`.
- Use `Link`, `redirect`, `useRouter` and `usePathname` from `@/i18n/navigation`, **not** from `next/link` / `next/navigation`: they keep the current language in the URL (`/si/...`).

## Server vs client components

- **Server components are the default.** Pages and layouts render on the server: faster, better SEO, no JavaScript sent for static parts.
- Add `"use client"` **only** when a component needs state, effects, event handlers or browser APIs (`useState`, `onClick`, `usePathname`, `useQuery`...).
- Push `"use client"` down to the smallest leaf: a page stays a server component and renders a small client `<RenewButton>`, instead of the whole page becoming client-side.
- Client components can't receive functions from server components as props. Pass data or JSX elements instead (see `SidebarNav`'s `icon`).

## Data fetching

| Where                                             | How                                                                            |
| ------------------------------------------------- | ------------------------------------------------------------------------------ |
| Server component, public data (book page, search) | `await api.get(...)` directly in the component. Good for SEO.                  |
| Client component, user data (my loans, fines)     | `useQuery({ queryKey, queryFn: () => api.get(...) })`                          |
| Changing data (renew, pay, checkout)              | `useMutation`, then `queryClient.invalidateQueries(...)` for the affected keys |

- **Every backend call goes through `src/lib/api.ts`.** Never call `fetch` for the backend directly.
- **Never fetch in `useEffect`.** Use TanStack Query: it handles loading, errors, caching and retries.
- Query keys are arrays that start with the feature: `["loans"]`, `["books", id]`, `["search", params]`.
- Backend DTO types come from the generated schema: `type Book = Schema<"BookResponse">`. Run `pnpm api:types` after backend changes and commit `src/lib/api-schema.d.ts`. Never hand-write DTO types.

## Errors

- Failed calls throw `ApiError`. Decide what to show by **`error.code`** (e.g. `LOAN_LIMIT_REACHED`), never by the English `message`: messages are translated from the code.
- Form validation errors from the backend go back onto the fields with `applyServerErrors(error, form.setError)`.
- Unexpected errors are caught by the route's `error.tsx`. Show `error.requestId` (if any) so a user can report it.

## Forms

- React Hook Form + a Zod schema (`zodResolver`). Keep the schema in the feature folder and match the backend's validation rules.
- Wrap every field in `<FormField>`: it connects the label, hint and error message for screen readers.
- Disable the submit button while submitting; show success with a toast (`toast.success(...)`).

## Styling

- Tailwind with the design tokens from `globals.css`: `bg-primary`, `text-muted-foreground`, `text-overdue`... **No raw colours** (`#F59E0B`, `bg-amber-500`): dark mode and rebranding only work through tokens.
- Merge classes with `cn()`. Let Prettier sort them (`pnpm format`).
- Mobile-first for public and member pages (`sm:`/`md:` add desktop styles); the staff/admin consoles are desktop-first but must not break on a tablet.

## Text and languages

- **No hard-coded UI text.** Every string lives in `messages/en.json` (+ `si.json`, `ta.json`) and is read with `useTranslations` / `getTranslations`.
- Format money and dates with `Intl` / next-intl formatters (LKR, local dates), never by hand.

## Accessibility (WCAG 2.1 AA)

- Every input has a label (`<FormField>` does it); icon-only buttons have `aria-label`.
- Use real elements: `<button>` for actions, `<Link>` for navigation, headings in order.
- Everything works with the keyboard and shows the focus ring. Don't remove outlines.
- Don't rely on colour alone: status badges also have text.

## Quality checks

`pnpm lint` · `pnpm typecheck` · `pnpm format:check` · `pnpm build` must all pass before committing.
