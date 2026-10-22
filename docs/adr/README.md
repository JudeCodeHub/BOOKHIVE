# Architecture Decision Records

An ADR records one important technical decision: the problem, what we chose, what we rejected, and the consequences. They are never edited after being accepted; a later decision that changes one gets a new ADR marked "Supersedes".

| # | Decision | Status |
|---|---|---|
| [0001](0001-auth-tokens.md) | Login with a short-lived access JWT + rotating refresh token in an httpOnly cookie | Accepted |

## Template

```markdown
# NNNN. Title

- Status: Proposed | Accepted | Superseded by NNNN
- Date: YYYY-MM-DD

## Context
What problem are we solving, and what constraints matter?

## Decision
What we will do.

## Alternatives considered
What else we looked at, and why not.

## Consequences
What gets easier, what gets harder, what we must remember.
```
