# Development

## Configuration precedence

The backend resolves local configuration in this order:

1. values from root `secrets.env`;
2. real process environment variables, which override file values.

Set `HKH_SECRETS_FILE` to use another file. The parser accepts `KEY=value`, blank lines, comments
and optional surrounding single or double quotes. Invalid lines and missing required keys fail
without logging secret values.

## Backend commands

```bash
mvn -f backend/pom.xml spring-boot:run
mvn -B --no-transfer-progress -f backend/pom.xml clean verify
```

Always run from the repository root, or from `backend`; the loader checks both locations for the
root `secrets.env`.

## Backend modules

Backend features are Spring Modulith modules under `nl.vdzon.hkh`. Each module declares its allowed
dependencies in `package-info.java`, and `ModulithArchitectureTest` verifies the module set during
Maven `verify`.

`nl.vdzon.hkh.linkdossier` is an internal domain-only module: it holds the link dossier model and
`LinkDossierValidator`, a deterministic fail-closed validator that returns a dossier status, a
dossier-wide object media indication and two deduplicated, lexicographically sorted lists of blocking
field paths. It has no allowed dependencies and exposes no controller, repository or migration. The
functional rules are described in [factory/functional-spec.md](factory/functional-spec.md); the
implementation notes in [factory/technical-spec.md](factory/technical-spec.md).

`nl.vdzon.hkh.privacyclassification` is another internal domain-only module: it holds the
`GenealogicalRecord` model, `PrivacyClassifier` (a deterministic, fail-closed classifier that returns
`Processable` only for a deceased person with no detected living-next-of-kin field, and `Blocked`
with a mandatory readable reason otherwise) and `PrivacyPublishGuard`, a standalone, reusable guard
that rejects publication of `Blocked` records. It has no allowed dependencies and exposes no
controller, repository or migration. The classification status is shown in `frontend-admin` with
both a text label and an icon, never color alone. Details are in
[factory/functional-spec.md](factory/functional-spec.md) and
[factory/technical-spec.md](factory/technical-spec.md).

`nl.vdzon.hkh.recordintake` exposes `POST /api/record-intake`, which accepts exactly one collection
record per request under a short-lived RS256 JWT bearer token (own fail-closed verifier, no
dependency on the `auth` module). A valid record is stored with status `intern_concept`
(`V4__record_intake.sql`); an optional external link is only created when a durable URL, a
justification and an uncertainty value are all valid. Token values, headers and claims are never
persisted, logged or returned. Configuration and behavior are documented in
[factory/technical-spec.md](factory/technical-spec.md) and
[factory/secrets-local.md](factory/secrets-local.md).

## User frontend

The user application supports Flutter web and Android. It uses `http://localhost:8080` as its
default backend base URL. Override that public address at compile time when running or building
the application:

```bash
cd frontend
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080
flutter build web --dart-define=API_BASE_URL=https://test.example
```

The homepage calls `GET /actuator/health` and `GET /api/version` for its service check, followed by
`GET /api/news` when the service is available. Its loading, error, success and empty-result states
expose one polite Flutter web status node per active flow. The two retry actions remain in natural
focus order, show a three-pixel focus border and support Enter and Space without moving focus
programmatically.

Run the frontend checks with:

```bash
cd frontend
flutter analyze
flutter test
flutter build web
```

Widget tests verify all status labels and transitions, unique status nodes, focus order and both
keyboard activation keys. For release validation, also exercise the scenarios recorded in the
active story worklog against a real web build. Record the browser, operating system, screen reader,
versions, build revision, announcements and focus behavior; browser DOM/ARIA inspection alone does
not confirm what a screen reader actually announces.

## Local PostgreSQL

Start the isolated PostgreSQL 16 development database on host port 5435:

```bash
docker compose -f docker-compose.dev.yml up -d
```

Flyway applies migrations automatically when the backend starts. Stop the database without losing
data with `docker compose -f docker-compose.dev.yml down`. Removing the named volume is an explicit
destructive development reset and is therefore not part of the normal stop command.
