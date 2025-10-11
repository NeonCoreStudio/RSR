# Backend Rewrite Plan

## Goals
- Improve modularity, maintainability, and testability.
- Decouple UI and Android components from core logic.
- Replace ad-hoc file operations with clean repositories.
- Make recording, touch capture, and session management robust and observable.

## Proposed Architecture
- `domain/` — pure Kotlin models and use-cases.
- `repository/` — interfaces for persistence and data access.
- `repository/impl/` — file-based implementations (initially wrap existing managers).
- `service/` — coordination layer (recording controller, touch capture controller).

## Phases
1. Scaffolding: add domain models and repository interfaces; adapters delegate to existing `SimpleDataManager` and `SessionDataManager`.
2. Incremental migration: route new writes/reads through repositories; keep old paths working.
3. Observability: add event bus or callbacks for recording state and errors.
4. Persistence upgrade (optional): migrate to Room or structured JSON for metadata.
5. Cleanup: remove direct file writes in managers, consolidate logic.

## Non-Goals (for now)
- UI overhaul.
- Network/cloud sync.
- Breaking changes to file formats.

## Testing Strategy
- Unit tests for repositories and use-cases.
- Integration tests using `test_data/` fixtures.