# Contributing to BookHive

## Branches

- `main` is protected and always deployable.
- Work on a feature branch, then merge into `main` with a pull request:
  - `feat/<short-name>` for new features (e.g. `feat/auth-login`)
  - `fix/<short-name>` for bug fixes
  - `chore/<short-name>` for setup and tooling
- CI must pass before a PR can be merged (from step 88).

## Commit messages

BookHive uses [Conventional Commits](https://www.conventionalcommits.org/).

```
<type>(<optional scope>): <description>
```

- One line, lowercase, imperative ("add", not "added"), under 72 characters.
- Use a scope when it helps: `auth`, `member`, `catalog`, `search`, `circulation`, `hold`, `fine`, `payment`, `notification`, `event`, `community`, `report`, `admin`, `infra`, `ci`.

| Type | Use it for |
|---|---|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation only |
| `style` | Formatting, no code change |
| `refactor` | Code change that is not a feature or a fix |
| `perf` | Performance improvement |
| `test` | Adding or fixing tests |
| `build` | Build system or dependencies |
| `ci` | CI/CD workflows |
| `chore` | Other maintenance (setup, config) |

Examples:

```
feat(auth): add refresh token rotation
fix(circulation): skip holidays when calculating due date
chore(infra): add redis and mailpit to docker compose
```
