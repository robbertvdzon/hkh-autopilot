# SF-2356 - Worklog

Story-context bij eerste pickup:
Onderscheid geldig nul-resultaat van inconsistente Records/Search-respons

In RestClientArchivesOpenSearchClient.search() (backend/src/main/kotlin/nl/vdzon/hkh/personsearch/ArchivesOpenSearchClient.kt, regel 50): wanneer numberFound == 0 en docs ontbreekt/null is, return ArchivesSearchOutcome.Success(numberFound = 0, results = emptyList()) in plaats van Failure. Wanneer numberFound > 0 en docs ontbreekt/null is, blijft dit terecht Failure (ongewijzigd). Alle overige bestaande fail-closed checks (HTTP niet-2xx, lege body, errorCode != null, item-mapping-mismatch) blijven ongewijzigd. Geen wijzigingen nodig in PersonSearchService.kt, PersonSearchAnswer.kt of de Flutter-frontend: die bestaande paden (NoResults -> NO_EVIDENCE, _buildNoReliableSourceVariant) verwerken de gecorrigeerde Success(0, emptyList()) al correct. Voeg als onderdeel van dit ontwikkelwerk twee testscenario's toe aan RestClientArchivesOpenSearchClientTest.kt met het bestaande embedded-JDK-HttpServer-testpatroon: (1) number_found=0 met ontbrekend/null docs -> Success(0, emptyList()); (2) number_found>0 met ontbrekend/null docs -> Failure (regressietest). Controleer ook dat bestaand gedrag voor HTTP-niet-2xx, time-out, ongeldige JSON en gevuld error_code ongewijzigd blijft, en voeg eventueel ontbrekende regressiechecks daarvoor toe.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-2357 - Onderscheid geldig nul-resultaat van inconsistente Records/Search-respons

- Gewijzigd: `backend/src/main/kotlin/nl/vdzon/hkh/personsearch/ArchivesOpenSearchClient.kt`,
  `search()`: wanneer `docs` ontbreekt/`null` is en `numberFound == 0`, wordt nu
  `ArchivesSearchOutcome.Success(0, emptyList())` teruggegeven in plaats van `Failure`. Voor
  `numberFound > 0` met ontbrekend/`null` `docs` blijft het onveranderd `Failure`
  (fail-closed-regel voor inconsistente respons blijft gelden).
- Geen wijzigingen nodig in `PersonSearchService.kt`, `PersonSearchAnswer.kt` of de Flutter-
  frontend (`person_query_page.dart`): het bestaande `NoResults`/`NO_EVIDENCE`-pad en de
  bestaande no-reliable-source-schermvariant verwerken de gecorrigeerde
  `Success(0, emptyList())`-uitkomst al correct, zoals de story-aanname stelt.
- Tests toegevoegd aan `RestClientArchivesOpenSearchClientTest.kt`:
  - `number_found=0` met ontbrekend `docs`-veld -> `Success(0, emptyList())`.
  - `number_found=0` met `docs: null` -> `Success(0, emptyList())`.
  - `number_found=1` (>0) met ontbrekend `docs`-veld -> `Failure` (regressietest: blijft
    bronuitval).
  - Bestaand gedrag voor HTTP-niet-2xx, time-out (retries), ongeldige JSON en gevuld
    `error_code` was al gedekt door bestaande tests in dit bestand en is ongewijzigd gebleven
    (geen aanpassing nodig).
- Volledig vangnet uit `docs/factory/development.md` gedraaid, allemaal exitcode 0 / 0
  failures / 0 errors:
  - `(cd backend && mvn -B --no-transfer-progress clean verify)` -> 264 tests, 0 failures, 0
    errors, BUILD SUCCESS.
  - `(cd frontend && flutter analyze)` -> geen meldingen.
  - `(cd frontend && flutter test)` -> 74 tests, alle geslaagd.
  - `(cd frontend && flutter build web)` -> succesvolle build.
  - `(cd frontend-admin && flutter analyze)` -> geen meldingen.
  - `(cd frontend-admin && flutter test)` -> 22 tests, alle geslaagd.
- `.factory/verification.yaml` gecontroleerd: dekt deze wijziging al correct (backend-
  pathPrefix `backend/`), geen aanpassing nodig.
- Geen bestaande rode tests aangetroffen; boyscout-herstel was niet nodig.
