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

## Fixed Architecture And Technology

The project architecture is fixed as:

```text
Android Frontend -> RESTful API over HTTP/JSON -> Spring Boot API Server -> gRPC Recommend Service -> MySQL 8
```

Implementation defaults:

- Java 17.
- Spring Boot.
- Maven.
- gRPC for recommendation RPC.
- MySQL 8.
- Backend local `uploads/` directory for video storage.
- Database initialization starts with `sql/schema.sql`; Flyway is optional only if it fits naturally.

Both the Spring Boot API Server and the gRPC Recommend Service may access MySQL 8. The Android frontend must only call the Spring Boot API Server over RESTful HTTP/JSON.

## Priority Rules

Core P0 course requirements must be completed before P0-lite and Bonus features:

- Login, register, logout.
- Recommended video feed.
- Recommendation rule: `like_count DESC, created_at DESC, id DESC`.
- Videos already visited by the user must not be recommended again.
- Video vertical swipe support.
- Video like support.
- Publish video using local `uploads/` storage.
- View my videos with pagination.
- Delete my videos with permission control.
- `GET /me`.
- `GET /health`.
- MySQL 8 database design.
- Logs for user request input, output, and endpoint duration.
- Security checks: account system and permission control.
- Main app access to the recommendation system must use gRPC.

P0-lite / later implementation but mandatory final demo scenario:

- `GET /api/v1/videos/{videoId}/comments`
- `POST /api/v1/videos/{videoId}/comments`
- `comments` table is included in schema planning.
- Implement comments after core P0 recommendation, like, publish, my videos, delete, and logging work, but before final frontend integration and demonstration.
- Comments are part of the official demonstration flow and must not be treated as Bonus.

Bonus features must not be prioritized before P0 and P0-lite:

- `POST /media-upload-tokens`.
- Favorites.
- Follow / friends feed.
- Share.
- Messages.
- Search.
- Metrics endpoint.

Do not implement `auth_tokens` in the current plan. Logout is simplified: the client deletes the token and `POST /auth/logout` returns success.

## Course Grading And Delivery

Planning and implementation must remain traceable to the course grading points through API contracts, database tables, tests, and demonstration steps.

The two required demonstration flows are:

1. Recommended feed / swipe videos -> like -> view comments -> submit comment.
2. Login / register -> publish video -> paginated my videos -> delete own video.

Final delivery must include:

- Public Git repository URL and complete `.gitignore`.
- Final source package with `README.md` deployment steps.
- Requirements document.
- Technical design document.
- Test document with case design and case list.
- Defense PPT.
- Team division document with workload percentages.
- Internal member score sheet.
- Two-minute demonstration video.

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

Do not modify `frontend/` unless the user explicitly requests frontend work.

Do not process unrelated git deletion items unless the user explicitly asks for that. If `git status` shows unrelated deletions or moved files, leave them alone and mention them only when relevant.

Do not prioritize or implement Bonus features unless the P0 and P0-lite scope is complete or the user explicitly asks for a Bonus feature.
