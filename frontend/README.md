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

## Accessible homepage statuses

The service and latest-news flows expose one polite `SemanticsRole.status` node for each current
state. Their labels are:

- service: `De historische omgeving wordt voorbereid.`, `De HKH-service is niet bereikbaar.` and
  `Service beschikbaar.`;
- latest news: `Laatste nieuws wordt geladen.`, `Het laatste nieuws kon niet worden geladen.`,
  `Laatste nieuws geladen.` and `Er zijn nog geen nieuwsberichten.`.

Visible copies, progress indicators and decorative icons do not create duplicate status nodes.
Status changes do not receive or move focus. Each error's `Opnieuw proberen` action follows its
message in natural reading and Tab order, displays a three-pixel focus border and supports Enter
and Space. Widget tests cover these semantics and keyboard behaviors. A release check should also
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
