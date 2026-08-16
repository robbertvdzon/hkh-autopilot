# HKH user frontend

This directory contains the Flutter user application for web and Android. The homepage first
checks `GET /actuator/health` and `GET /api/version`. After a successful service check it loads
`GET /api/news` for the "Laatste nieuws" section, keeps the discovery introduction and
product-vision route available, and renders the discover block (`lib/news/discover_section.dart`):
a labeled search field plus entity chips (`PLEK`/`PERSOON`/`GEBEURTENIS`) backed by the same
`GET /api/news` contract (`q`/`entity` query parameters). A search or chip click shows a results
list (title, summary, entity badges, source line) or, for zero results, a non-empty empty state
with suggestion chips; each result opens a detail view (full text, publication date, source) with
a back action.

The `Lees onze productvisie` action opens `ProductVisionPage` with the existing
`MaterialPageRoute`. ProductVisionPage keeps its content and provides the visible, semantic button
`Terug naar startpagina` in the AppBar. It is reachable in the normal Tab order and works with a
pointer, Enter or Space by popping the existing route back to the already opened homepage; no new
homepage or deep-link route is introduced.

The homepage also provides the separate `Historisch zoeken` entry next to the latest-news flow. The
`HistoricalSearchPage` searches public Europeana and Open Archieven records through
`GET /api/historical-search` with optional free text, place, person, event, year range and source
filters. The response exposes an aggregate `state` (`RESULTS`, `NO_RESULTS`,
`PARTIAL_AVAILABILITY` or `SOURCE_FAILURE`) and one technical status per selected source
(`AVAILABLE`, `DISABLED`, `TEMPORARILY_UNAVAILABLE`, `INVALID_RESPONSE`, or for Open Archieven
`TIMEOUT`, `HTTP_ERROR`, `INVALID_JSON` or `MISSING_REQUIRED_FIELDS`). With partial availability the
page keeps results from available sources visible and adds short, source-specific messages. For the four Open
Archieven statuses the fixed messages are `Open Archieven reageerde niet op tijd`, `Open Archieven
gaf een fout bij het opvragen`, `Open Archieven stuurde een onleesbaar antwoord` and `Open Archieven
stuurde een onvolledig antwoord`. Full source failure shows one semantic source-problem status, the safe message for every
failed source and the keyboard-operable actions `Opnieuw proberen` and `Zoekopdracht aanpassen`,
without presenting a misleading result count. `Zoekopdracht aanpassen` keeps the user on the same
route, moves focus deliberately to the existing free-text field and preserves all entered values
until the user edits them. Results show the source
identifier, retrieval time, technical availability, metadata rights, object/media rights, privacy
status and a clearly labeled external source link; source metadata is shown only when the backend
has explicit safe rights and privacy statuses. A card is rendered only when its stable identifier
and absolute HTTP(S) source URL are valid. For Open Archieven, the provider-supplied `source_name`,
`uuid` and `original_source_url` must all be non-empty and consistent with the normalized identity;
otherwise the result has no public card or source link. When metadata rights are `ALLOWED` and
privacy is `CLEAR`, the card uses a non-empty title, falling back to the primary description; it
does not invent a heading when both are absent. Missing, blank, unrecognized or conflicting rights
and privacy values are shown as `Onbekend`. The external action is keyboard-operable and visibly
announces `Externe bron openen in nieuw tabblad`. Each selected source also shows its available result
count for the current visible page. It shows a separate `Lokale Heemskerk-indicatie op basis van
plaatsmetadata` only for certain, explicitly available place metadata; the indication is never
presented as historical proof. Available empty sources show `0`, while disabled, temporarily
unavailable or invalid sources show no numeric count. Available result cards also offer `Context bekijken`.
Each source status also carries nullable `querySemantics` for Open Archieven: the semantic provider
parameters actually present in the adapter request, such as `name` or `eventplace`. Technical
parameters such as `archive_code`, pagination and rate limiting are excluded. The page renders
these values per source as `Zoekinterpretatie: naam (name).` or `Zoekinterpretatie: plaats
(eventplace).`; when Open Archieven was not queried or the semantics are unknown, it renders the
neutral text `Zoekinterpretatie: niet beschikbaar.`. The interpretation is never inferred from the
search term, result metadata, title or URL.
The retry action is also available for partial availability when at least one selected source has
failed, and for retryable transport errors or `SOURCE_FAILURE`. Before each retryable request the
page keeps one normalized snapshot of the request context (all search filters, source choice,
visible page offset and limit). During a retry the previous valid results, source statuses, counts
and entered values remain visible, with text identifying the new attempt; a second retry activation
is ignored. A valid retry replaces the previous response completely. After a failed retry, the
previous valid partial results remain visible and the new safe source/transport error is shown
separately. No raw provider response, exception text or search history is retained in client state.
The detail page shows title, place, period, person, event and the source/rights/privacy metadata,
with explicit `Niet beschikbaar` and `Onzeker` labels for context fields whose status is
`MISSING`/`UNAVAILABLE` or `UNCERTAIN`. It repeats the aggregate search state and the selected
source status so partial availability and source failure remain understandable.

The controlled rights fields are fail-closed per result: only explicit `ALLOWED` and `RESTRICTED`
values from the provider become statuses. Missing, blank, unrecognized or conflicting rights values
become `UNKNOWN`; the free-text `rights` field remains source information and is never translated
into a controlled status. Result cards and the context detail page show metadata rights and
object/media rights separately, with the shared keyboard-operable explanation that the two statuses
are independent in both directions and that `UNKNOWN` does not mean allowed or denied.

The detail page derives up to three related results only from the current response page, excluding
the opened result. A relation requires an exactly equal place, person or event after deterministic
trim, Unicode-NFKC, whitespace and case normalization; missing, uncertain and unavailable values
never match. An overlapping period is displayed only as additional information on an existing
relation. Relation cards retain the candidate source label, identifier and original stable URL.

The context detail page can also offer new search-entry buttons for an explicit, non-empty and certain
place, person or event, plus an explicit period whose start and end values are valid four-digit years
in order. Actions are shown only when the result is technically available, metadata rights are
`ALLOWED` and privacy is `CLEAR`; missing, uncertain, contradictory, restricted or derived values
never create an action. Each action passes the original value unchanged to the existing
`HistoricalSearchPage`, omits the source filter so the default Europeana/Open Archieven selection is
used, and starts the search automatically. The follow-up page displays the chosen value and the
warning `Dit is een nieuwe zoekingang en bewijst geen relatie tussen bronnen.` The pushed route keeps
the detail page and then the original results list available through back navigation. No search,
provider payload or click history is stored locally.

## Run and verify

Use Flutter stable 3.44.7 with Dart 3.12.2. The backend defaults to `http://localhost:8080`; set a
different public base URL with the compile-time `API_BASE_URL` define:

```bash
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080
flutter analyze
flutter test
flutter build web --dart-define=API_BASE_URL=https://test.example
```

The web build is written to `build/web/`. `API_BASE_URL` is configuration, not a secret.

The repeatable public-chain smoke contract is in
`test/hkh165_historical_search_smoke_contract_test.dart`. It uses a synthetic route response and
the existing `HistoricalSearchPage` to verify a valid Open Archieven result plus the distinct zero,
partial-availability and complete-source-failure states. It does not call a real provider and is
included automatically by `flutter test`; run it directly with:

```bash
flutter test test/hkh165_historical_search_smoke_contract_test.dart
```

The broader status matrix is in `test/hkh189_historical_search_contract_test.dart`. It verifies
valid and empty responses, partial availability, timeout, HTTP error, invalid JSON, missing or
contradictory required fields, and missing rights/privacy metadata. The test asserts aggregate and
per-source statuses and counts, safe `Onbekend` mapping and metadata redaction, provider identity
and exact source links, safe failure messages, and card visibility. It uses only synthetic responses
and is included automatically by `flutter test`; run it directly with:

```bash
flutter test test/hkh189_historical_search_contract_test.dart
```

## Accessible homepage statuses

The service and latest-news flows expose one polite `SemanticsRole.status` node for each current
state. The historical search route uses the same single status node for loading, results, no
results, partial availability and complete source failure. Its labels include safe source messages,
the available-source count for the current visible page and the explicitly labeled Heemskerk
place-metadata indication where applicable. They also include the per-source query interpretation or
the neutral `Zoekinterpretatie: niet beschikbaar.` text; this remains textually available and is not
communicated through color or icon alone. Their labels are:

- service: `De historische omgeving wordt voorbereid.`, `De HKH-service is niet bereikbaar.` and
  `Service beschikbaar.`;
- latest news: `Laatste nieuws wordt geladen.`, `Het laatste nieuws kon niet worden geladen.`,
  `Laatste nieuws geladen.` and `Er zijn nog geen nieuwsberichten.`.

Visible copies, progress indicators and decorative icons do not create duplicate status nodes; the
historical-search spinner is excluded from semantics for this reason. Status changes do not receive
or move focus. Each error's `Opnieuw proberen` action follows its message in natural reading and Tab
order, displays a three-pixel focus border and supports Enter and Space. On complete historical
source failure, `Zoekopdracht aanpassen` follows the source messages and retry action, is also
keyboard-operable, and is the only intentional focus move. Widget tests cover these semantics,
focus and keyboard behaviors, including preserving the previous result view and entered search
values while a retry is running and using the same normalized request parameters for the retry. A
release check should also
use the repeatable browser and screen-reader scenarios in the active story worklog; DOM/ARIA
inspection does not by itself confirm the announcements a screen reader produces.

## Discover block (search and entity chips)

The discover block is the homepage's only primary discovery action: a labeled search field and
entity chips, both fully keyboard-operable (Tab order, Enter/Space activation, three-pixel focus
border). Its result count is exposed through a `Semantics(liveRegion: true)` node that changes
after every search or chip selection, following the same live-region pattern as the record-intake
form. Entity/type badges use fixed foreground colors (`NewsEntityBadgeColors`) against white with
a contrast ratio of at least 4.5:1. Only `LatestNewsItem`/`NewsEntity`/`AggregatedNewsEntity`
fields are rendered; record-intake, privacy-classification and external-verification data are
never used. Widget/semantics/contrast tests for this block live in
`test/discover_section_test.dart`.
