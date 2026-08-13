# hkh-136 - Worklog

## Testverificatie

- Preview-omgeving niet beschikbaar: `SF_PREVIEW_URL` en de preview-template zijn leeg.
- Gerichte backend-contracttests: `HistoricalSearchTest`, `HistoricalMetadataContractTest` en
  `OpenArchievenMetadataAdapterTest`; 55 tests, 0 failures, 0 errors.
- Gerichte frontendtests: `historical_search_test.dart`, `historical_context_detail_test.dart` en
  `historical_follow_up_test.dart`; 31 tests, 0 failures, 0 errors.
- Gecontroleerd: Heemskerk-requestmapping met afzonderlijke `name`/`archive_code`, vaste responsevelden,
  fail-closed ongeldige responses, lege response, transportfout, rechtenmapping, user-agent/rate limit,
  publieke snake_case-mapping en weergave van bronmetadata/link.
