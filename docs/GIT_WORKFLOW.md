# Git Workflow

TitanCore uses Git Flow.

## Branches

```text
main
develop
feature/*
fix/*
refactor/*
perf/*
docs/*
hotfix/*
```

## Rules

- `main` contains production releases only.
- `develop` contains integrated work for the next release.
- New features start from `develop`.
- Bug fixes use `fix/*`.
- Optimizations use `perf/*`.
- Refactors use `refactor/*`.
- Documentation uses `docs/*`.
- Emergency production fixes use `hotfix/*`.

## Process

1. Create issue.
2. Suggest branch name.
3. Wait for approval.
4. Create branch.
5. Implement work.
6. Build project.
7. Run tests.
8. Run static analysis.
9. Run security review.
10. Run performance review.
11. Update documentation.
12. Open pull request.
13. Wait for approval.
14. Merge into `develop` only after approval.

## Conventional Commits

Examples:

- `feat(auth): implement jwt authentication`
- `fix(websocket): reconnect issue`
- `perf(redis): optimize cache lookup`
- `refactor(game-engine): simplify battle engine`
- `docs(readme): update installation guide`
- `test(auth): add integration tests`
- `chore(ci): configure github actions`

