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

`nl.vdzon.hkh.personsearch` exposes `POST /api/person-search`, the live search-and-answer route for
a recognized person question. It issues a route-scoped, anonymous session cookie
(`hkh_person_search_session`, no login), creates exactly one idempotent, in-memory job per
session-id + normalized query + chosen Heemskerk meaning, and synchronously runs the Open Archieven
Records/Search and Records/Show calls (`ArchivesOpenSearchClient`, rate-limited to 4 requests/second,
fail-closed validation on HTTP status/JSON/required fields/`error_code`) plus an optional Wikidata
context call within a hard 2000ms deadline. `number_found > 100` ends the job as `PARTIAL` with a
refinement request and skips Records/Show. `PersonSearchService.handleSearchSuccess` builds the
answer from whichever candidate records got a valid Show record: only when the required
Records/Search call fails, or *none* of the candidates' Records/Show calls succeed, does the job end
as `FAILED` with Open Archieven reported as unavailable. If at least one Show call succeeds while
others fail, the job still ends as `READY`, using only the successful records — failed candidates
contribute no sentence or source citation and don't block the rest. When some but not all
candidates were unverifiable, the answer's `disclaimer` also names how many candidates were skipped.
Answer sentences are built only from validated Show fields (`Person`/`Event`/`RelationEP`/`Source`)
with numbered source citations.

The job status contract is worker-independent — `QUEUED, RUNNING, READY, NO_EVIDENCE, PARTIAL,
FAILED, CANCELLED, EXPIRED` — and runs on the ordinary shared executor, no Agent Runtime involved.
`GET /{jobId}/status` returns status, `createdAt`, `updatedAt` and per-source consultation status
(Open Archieven, Wikidata), with the full outcome only once the job is terminal; a status request
for another session's job behaves as if it doesn't exist (404). `POST /{jobId}/cancel` sets
`CANCELLED`, blocks further outgoing source calls for that job and deletes the temporary payload
immediately; `POST /{jobId}/open` marks a `READY` job as opened; `GET /session` returns the running
and ready-unopened job counts/ids for the current session only. The original query and the answer
payload are kept in-memory but encrypted at rest (`PersonSearchPayloadCipher`, AES-256-GCM,
`HKH_PERSON_SEARCH_PAYLOAD_KEY`, fails closed without a configured key); a scheduled cleanup task
(`PersonSearchRetentionCleanupTask`, `@EnableScheduling`) purges the payload and marks the job
`EXPIRED` after 60 minutes of session inactivity or 24 hours since submission, whichever comes
first. Configuration and behavior are documented in
[factory/technical-spec.md](factory/technical-spec.md) and
[factory/secrets-local.md](factory/secrets-local.md).

`nl.vdzon.hkh.placesearch` exposes `POST /api/place-search`, the synchronous search-and-answer route
for a recognized place/building question (e.g. "Wat is Kasteel Assumburg?"). Unlike `personsearch`,
it has no session-scoped background job infrastructure: a single request runs
`wbsearchentities` (`language=nl`, `type=item`, `limit=5`) followed by a live `Special:EntityData`
fetch per candidate QID, all within a hard 2000ms total deadline (`PlaceSearchService`, its own
`placeSearchExecutor` bean plus `Future.get(timeout)`). A candidate only counts within Heemskerk when
its P131 claim (optionally resolved one level further) equals `Q9926`, or its P625 coordinates fall
inside a fixed, code-documented bounding box (`PlaceSearchWikidataClient`, own geometric assumption,
no official Wikidata geometry, no SPARQL/Query Service call). Exactly one match builds an answer from
label/description/P571/P149/P84/P1435, each sentence with its own numbered `PlaceSearchSourceCitation`
(QID, `wikidata.org/wiki/{QID}` link, `checkedAt`); zero or more than one match returns `NO_MATCH`,
with candidate labels as a refinement suggestion when there is more than one. Images come from
Wikimedia Commons via P373 (category) or a P18 fallback, deduplicated by filename to a maximum of 6,
each with its file URL, license and file-page link; a Commons-only failure leaves the Wikidata answer
in place with `commonsOutage=true`. Any Wikidata/Commons failure, invalid JSON or deadline overrun
yields fail-closed `OUTAGE`, with no answer constructed. Fetched Wikidata entities and Commons
imageinfo responses are cached in-memory only, with a 5-minute TTL (`PlaceSearchCache`) — no
structural database storage. Configuration and behavior are documented in
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

The "Stel je vraag over Heemskerk" action on the homepage opens `lib/personquery/`, a fully
client-side start/meaning-selection/no-reliable-source flow that deterministically interprets a
question and disambiguates the word "Heemskerk" via `PersonQueryInterpreter`, calling Wikidata
directly (with a static fallback on failure) through `WikidataMeaningClient`; it never calls Open
Archieven Records/Search/Show itself. On a supported submission it hands off to `lib/personsearch/`,
which posts to `POST /api/person-search` (`PersonSearchClient`) and switches between the
`live-search`, `supported-answer`, `followed-connection` and `source-outage` screens based on the
job outcome, each with a desktop and mobile layout. When the job is not terminal within the 2s
synchronous budget, it switches to `background-search` (original query, start time, per-source
progress, an action to ask another question without interrupting the running job, and a stop
action) and, once the job reaches `READY`, to `search-ready` (completion time, consulted sources,
and exactly one action that opens the answer). A `SessionIndicatorBadge` in the app bar shows the
running and ready-unopened job counts for the current session on every screen of the route, and
`PersonQueryPage` automatically resumes status polling for non-terminal or not-yet-opened `READY`
jobs after in-app navigation, reload or return within the same session; an expired or deleted job
shows a clear "no longer available" screen offering to resubmit the question instead of a stale
answer.

`PersonQueryInterpreter.interpret` also recognizes a place/building candidate (a landmark keyword —
`kasteel, kerk, molen, toren, gemaal, station, brug, huis, hof, plein, sluis, kapel, klooster` —
directly next to a capitalized word), which takes priority over the person route. A recognized
candidate is submitted synchronously (no polling) to `lib/placesearch/` (`PlaceSearchClient`, `POST
/api/place-search`), switching between the `place-answer`, `place-empty` and `place-outage` screens
based on the response, each reusing `person_query_widgets.dart` for focus/status styling and each
with a desktop and mobile layout.

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
