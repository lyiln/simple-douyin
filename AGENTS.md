# AGENTS.md

## Project Context

This repository is for the course project of API Design and Implementation.
The project is a simple Douyin-style short video app and video feed recommendation system.

Course requirements have higher priority than `frontend/docs/API_DESIGN.md`.
The current `frontend/docs/API_DESIGN.md` is a REST API draft written by the frontend team. It is useful reference material, but it is not the final source of truth for the course deliverable.

## Required Reading Before Work

Before each task, Codex must first read the planning documents under `docs/`, especially:

- `docs/scope-final.md`
- `docs/api-contract-final.md`
- `docs/database-design.md`
- `docs/rpc-design.md`
- `docs/backend-module-plan.md`
- `docs/task-breakdown.md`
- `docs/test-plan.md`
- `docs/gap-analysis.md`

Use these documents to decide scope, priority, API shape, database design, RPC design, and test expectations.

## Priority Rules

P0 course requirements must be completed before Bonus features:

- Login, register, logout.
- Recommended video feed.
- Recommendation rule: recommend by highest like count.
- Videos already visited by the user must not be recommended again.
- Video vertical swipe support.
- Video like support.
- Publish video.
- View my videos with pagination.
- Delete my videos with permission control.
- Database design.
- Video storage design.
- Logs for user request input, output, and endpoint duration.
- Security checks: account system and permission control.
- Main app access to the recommendation system must use RPC.

Bonus features must not be prioritized before P0:

- Favorites.
- Follow / friends feed.
- Share.
- Messages.
- Search.

## Fixed Architecture

The project architecture is fixed as:

```text
Frontend -> REST API Server -> RPC Recommend Service -> MySQL
```

Do not introduce a complex microservice architecture. Keep the backend to:

- REST API Server.
- RPC Recommend Service.
- MySQL.

The frontend calls REST APIs only. The REST API Server calls the RPC Recommend Service for recommendation results.

## Coding Workflow

Before coding, Codex must explain the plan briefly:

- What files will be changed.
- What behavior will be implemented.
- How the change will be verified.

After coding, Codex must report:

- Which files were changed.
- How to start the relevant service or app.
- How to verify the change.
- Whether tests or builds were run.

## Repository Safety

Do not delete existing frontend code.

Do not process unrelated git deletion items unless the user explicitly asks for that. If `git status` shows unrelated deletions or moved files, leave them alone and mention them only when relevant.

Do not prioritize or implement Bonus features unless the P0 scope is complete or the user explicitly asks for a Bonus feature.
