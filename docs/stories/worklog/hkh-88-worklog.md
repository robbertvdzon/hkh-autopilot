# hkh-88 - Worklog

## Tester-run (2026-08-12)

- Previewomgeving niet beschikbaar: `SF_PREVIEW_URL` en de previewtemplate zijn leeg.
- Gerichte backendverificatie groen: `mvn -B --no-transfer-progress -Dtest=HistoricalSearchTest test` — 16 tests, 0 failures, 0 errors.
- Gerichte frontendverificatie groen: `flutter test test/historical_search_test.dart` — 8 tests, 0 failures, 0 errors.
- Gecontroleerd zijn onder meer queryvalidatie, bronisolatie, Europeana/Open Archieven-mapping, URL-herkomst, fail-closed metadata/statussen, Open Archieven User-Agent/rate limiting, paginering, fout- en retrystatussen, semantiek, toetsenbordbediening en de homepage-ingang.
- Geen code, tests of infrastructuur gewijzigd. Het volledige revisiongebonden factory-vangnet wordt na deze run door de harness uitgevoerd.
