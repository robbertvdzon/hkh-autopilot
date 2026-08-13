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
persisted, logged or returned. The module now declares explicit, non-wildcard
`allowedDependencies` on `externalverification` and `privacyclassification`: `POST
/api/record-intake/external-archive-preview` recognizes whether a durable URL follows the
`http://opendata.archieven.nl/id/<adtid>/<guid>` pattern and, if so, previews the matching
`ArchivesNlClient` fields without persisting anything; on confirmed save (`V8__record_intake_
deceased_status_and_archive_data.sql` adds `deceased_status`, `next_of_kin_confirmed` and the
non-personal `archive_*` columns), the service re-fetches the external source itself (never
trusting the earlier preview) and stores name/birth date/death date only when both a local and an
externally-derived temporary `GenealogicalRecord` classify as `Processable` via the existing
`PrivacyClassifier`; license, source URI and fetched-at are always stored on a successful fetch
regardless of that outcome, and the raw external response is never persisted. The module now also
declares an explicit, non-wildcard dependency on `auth`: a new admin-only action, `POST
/api/admin/record-intake/{localIdentifier}/confirm`, reuses `AdminAuthenticator` to set the new
nullable `confirmedBy`/`confirmedAt` fields (`V9__record_intake_confirmation.sql`) — filling the
`archive_*` fields alone is not enough, this confirmation is a separate, deliberate step. A new
public, unauthenticated route, `GET /api/records/{localIdentifier}`, derives a `RecordPublicStatus`
(`NO_INTAKE`/`SAVED_WITHOUT_SOURCE`/`CONFIRMED`) on every request via `RecordPublicStatusResolver`:
`CONFIRMED` requires filled archive fields, an explicit confirmation and a freshly re-run
`PrivacyClassifier.classify()` that yields `Processable`; a later reclassification to `Blocked`
degrades the response to `SAVED_WITHOUT_SOURCE` without clearing the stored confirmation, so a
subsequent `Processable` reclassification shows `CONFIRMED` again automatically. The route always
returns HTTP 200 and only the derived fields, never the raw `RecordIntakeRecord`. Configuration and
behavior are documented in [factory/technical-spec.md](factory/technical-spec.md) and
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

The module also provides the reusable, non-persistent `HistoricalMetadataContract` and
`OpenArchievenMetadataAdapter` used for individual external metadata verification. That contract
remains separate from the public search contract below.

The standalone `nl.vdzon.hkh.historicalsearch` module exposes `GET /api/historical-search`. It
normalizes Europeana and Open Archieven results to one response shape with source, stable source
identifier and URL, optional title/description/place/person/event/dates/institution/rights/privacy,
and a server-side UTC `retrievedAt`. Each result also contains `relationships[]` for complete
relationships explicitly supplied by the provider: `type`, `source.name`, `target.name`, an
explicit HTTP(S) `target.uri` and the provider-supplied HTTP(S) `target.link`. Relationships are
kept in provider order, never inferred from metadata overlap or search context, and are removed
when metadata/privacy fail-closed filtering removes content metadata. The query accepts `q`, `place`, `person`, `event`, `fromYear`,
`toYear`, `source`, `start` and `limit`; years must be four digits and be supplied as a pair, and
`limit` is bounded to 100. With no source filter both providers are merged through source cursors
without returning more than the requested page size. The response also contains `state` with one of
`RESULTS`, `NO_RESULTS`, `PARTIAL_AVAILABILITY` or `SOURCE_FAILURE`, plus a `sources` entry for
each selected provider. Each source entry reports `AVAILABLE`, `DISABLED`,
`TEMPORARILY_UNAVAILABLE` or `INVALID_RESPONSE` and a short safe message where applicable.
Available source entries also expose nullable `resultCount` and `heemskerkCount`: the former counts
only that source's safely normalized results on the current visible response page, while the latter
counts only results with `placeStatus == AVAILABLE` whose explicit place metadata equals `Heemskerk`
after trim, Unicode-NFKC normalization, whitespace collapse and case-insensitive comparison. A
successful empty source reports both values as `0`; an unavailable source reports both as `null`.
The Heemskerk value is displayed as a place-metadata indication, never as historical proof.

Each result also exposes `placeStatus`, `personStatus` and `eventStatus` with `AVAILABLE`, `MISSING`,
`UNCERTAIN` or `UNAVAILABLE`. Context values are copied only from explicit provider fields;
conflicting or unsafe values are withheld and marked accordingly. The Flutter result card offers
`Context bekijken` for available results. Its detail page shows all available context and source
metadata, the aggregate search state and the selected source status. Missing or unavailable values
are rendered as `Niet beschikbaar`, uncertain values as `Onzeker`.

The detail page calculates at most three related results from the response's currently visible
`results` list; it does not fetch another provider page. The opened result is excluded. A relation
requires one or more exactly equal, deterministically normalized available fields among place,
person or event (trim, Unicode NFKC, whitespace collapse and case-insensitive comparison). Missing,
uncertain and unavailable fields never match. An overlapping period is only an annotation on an
existing relation, never a relation by itself. Each relation retains the candidate's source,
identifier and original stable URL.
Separately, the detail page shows a `Bronvastgelegde relatie` section only for valid provider-supplied
`relationships[]` entries. It displays the source claim, type, source and target names, stable target
URI and an externally announced link to `target.link`; the original result's `stableUrl` remains the
source link for that result.

The detail page can expose `HistoricalFollowUpAction` buttons for explicit, non-empty and certain
place, person and event values, and for a valid explicit period with two ordered four-digit years.
The fail-closed gate requires result technical status `AVAILABLE`, metadata rights `ALLOWED` and
privacy status `CLEAR`; values missing from the explicit provider fields, uncertain, contradictory,
restricted or derived from title, query or URL do not qualify. Actions retain the original metadata
value, push `HistoricalSearchPage` with the existing `HistoricalSearchSource`, omit the source filter
to use the default Europeana/Open Archieven selection, and trigger the search after the follow-up
fields are populated. The page shows the selected value and the warning `Dit is een nieuwe zoekingang
en bewijst geen relatie tussen bronnen.` Standard semantic buttons retain keyboard focus and the
navigation stack returns first to the detail page and then to the original results list. The feature
does not add local storage for searches, provider payloads or click history.

Only sources that remain `AVAILABLE` contribute results and `total`. If a provider fails while a
later cursor page is being fetched, its status and contribution are removed and the merged offset
is rebased so remaining available results stay reachable. When every selected source is unavailable,
the route returns `SOURCE_FAILURE`, an empty result list and `total: 0`; this is distinct from
`NO_RESULTS`, which means all selected sources were available but returned no results.

Europeana is disabled independently when `HKH_EUROPEANA_WSKEY` is absent. Open Archieven uses the
same descriptive user-agent and a process-wide limiter with at least 251 ms between requests.
Provider records without a valid source URL or identifier are omitted, URLs are never constructed
locally, and explicit rights/privacy checks suppress content metadata fail-closed. The route never
stores searches, raw provider responses, media or external personal data.

For each result, `metadataRights` and `objectMediaRights` are mapped independently from explicit
provider rights fields. The public search adapters recognize only `ALLOWED` and `RESTRICTED`; blank,
missing, unrecognized or contradictory values become `UNKNOWN`. The free-text `rights`/`license`
field remains source information and cannot determine either controlled status.

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

The homepage also renders a discover block (`lib/news/discover_section.dart`) below the latest-news
section: a labeled search field and entity chips (`PLEK`/`PERSOON`/`GEBEURTENIS`), both querying
`GET /api/news` with optional `q`/`entity` parameters. A search or chip click shows a results list
(title, summary, entity badges, source line) or, for zero results, a non-empty empty state with
suggestion chips from the same aggregated entity list; each result opens a detail view (full text,
publication date, source) with a back action. It is the homepage's only primary discovery action,
fully keyboard-operable, and exposes its result count through a `Semantics(liveRegion: true)` node
that changes after every search or chip selection.

After the service check, the homepage also offers the separate `Historisch zoeken` entry. It opens
`HistoricalSearchPage` with fields for free text, place, person, event, from/to year and an optional
Europeana/Open Archieven source. The page maps the API aggregate state to distinct results, empty,
partial-availability and full-source-failure states. Partial results remain visible and include a
short message for every unavailable source; full source failure does not show a result count and
offers the keyboard-operable actions `Opnieuw proberen` and `Zoekopdracht aanpassen`. Retry returns
through the loading state before announcing the new outcome. Adjusting the search keeps the same
route, deliberately focuses the existing free-text field and preserves all search values until the
user changes them. For available sources, the result status also includes the per-source page count
and the explicitly labeled local Heemskerk indication. Loading, validation-error and transport
error states remain separate. Pagination uses the server response offset and limit, including after
a source fails during a later page. All states use one `SemanticsRole.status` node; the visible
copy and loading spinner do not create additional status nodes, and automatic status updates do not
move focus. External-link labels remain available. A result shows technical availability, metadata
rights, object/media rights and privacy separately; content metadata is rendered only when the backend
explicitly marks metadata rights as allowed and privacy as clear. Available results expose `Context bekijken`, opening the
context detail page in `lib/historical/historical_context_detail.dart`. That page renders place,
period, person, event and source metadata, shows `Niet beschikbaar` or `Onzeker` from the explicit
context statuses, and repeats the aggregate search/source status. It derives at most three relations
from the current response page using exact NFKC-normalized place/person/event equality; the opened
result and uncertain or unavailable fields never match, and period overlap is only supplementary.
Provider-supplied relationships are rendered separately under `Bronvastgelegde relatie`, with the
explicit source-claim text and an external link to the target record; they are not merged with the
derived metadata-overlap relations or follow-up actions.

Both the result card and context detail page include the shared `HistoricalRightsExplanation` control.
It is a semantic button usable with Tab, Enter and Space, and explains that metadata rights and
object/media rights are assessed independently in both directions. It also explains that `UNKNOWN`
means the source supplied no explicit, verifiable status—not that rights are allowed or denied.

`lib/records/` holds the new public record detail page (`RecordDetailPage`) and its collapsible
"Externe bronverificatie" section, which loads `GET /api/records/{localIdentifier}` via the new
`RecordPublicSource` implemented on `BackendClient`. At `CONFIRMED` it shows a status label (text +
icon), name, birth/death year (year-only, never day-precision), license, an externally-opening
source link (new tab, no `window.opener`, an announced external-link label) and the confirmation
date; every other status shows the identical neutral message, with no fields and no link, so that
no metadata about a possibly earlier publication leaks. The toggle button exposes an explicit
`expanded` state linked to the section content, the Flutter equivalent of
`aria-expanded`/`aria-controls`. It is unrelated to the existing discover block and does not feed
or consume its results. See [factory/technical-spec.md](factory/technical-spec.md) for
implementation details.

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
