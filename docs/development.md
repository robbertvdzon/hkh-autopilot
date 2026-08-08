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
controller, repository or migration. On top of the existing checks, `LivingPersonAgeRule` evaluates
each `NamedPerson` in `GenealogicalRecord.namedPersons` against the FamilySearch 110/95-year rule (an
external, non-legal genealogy rule of thumb, not a GDPR requirement) to fail-closed detect people who
are likely still alive; a single `LIKELY_LIVING` or `UNKNOWN_FAILCLOSED` result blocks the whole
record. Independently of and binding on top of these checks, `GedcomResnRule` evaluates the optional
raw GEDCOM 7.0 source text on `GenealogicalRecord.gedcomSource`: a blocking RESN marker
(`CONFIDENTIAL`, `LOCKED` or `PRIVACY`) anywhere in the record or a nested fact/event, or
syntactically invalid non-empty source text (fail-closed), always forces the outcome to `Blocked`; no
source text leaves the existing classification logic unaffected. The classification status is shown
in `frontend-admin` with both a text label and an icon, never color alone. Details are in
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

`nl.vdzon.hkh.externalverification` exposes `POST /api/external-verification`, which checks whether
one local genealogical record matches the public archieven.nl/Noord-Hollands Archief open data
endpoint (`http://opendata.archieven.nl/id/<adtid>/<guid>`, queried with
`Accept: application/ld+json`, no authorization token unless the endpoint itself explicitly
requires one). Matching name and birth/death date fields yield status `VERIFIED`; no match
(including a non-existent or invalid guid) yields `UNVERIFIED`, for which
`ExternalVerificationPublishGuard` refuses publication. The same response is also evaluated,
per record, for a reuse license (e.g. `CC0`) via `ExternalVerificationLicenseEvaluator` — a
separate, fail-closed status (`LICENSE_KNOWN`/`LICENSE_UNKNOWN`) independent of the match status
and never inferred from another record in the same archive collection. `ExternalVerificationPublishGuard`
also refuses publication when the license status is `LICENSE_UNKNOWN`, regardless of the match
status. Only the minimal verification fields (external URI, matched fields, checked-at timestamp,
status, license status, license value when known, and license checked-at timestamp) are persisted
(`V5__external_verification.sql`, extended by `V6__external_verification_license.sql`) — never the
full external JSON-LD payload. An optional archive access token is encrypted with AES-256-GCM
(`ExternalVerificationTokenCipher`) and never shown, logged or returned in plain text.
`frontend-admin` shows the archive link with an aria-label that announces it opens an external
source in a new tab, plus a separate `LicenseStatusView` status badge (text label + icon) next to
the existing verification/privacy badges. Configuration and behavior are documented in
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
