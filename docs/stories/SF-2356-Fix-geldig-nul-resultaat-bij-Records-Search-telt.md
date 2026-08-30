# SF-2356 - Fix: geldig nul-resultaat bij Records/Search telt niet meer als bronuitval

## Story

Fix: geldig nul-resultaat bij Records/Search telt niet meer als bronuitval

<!-- refined-by-factory -->

## Scope
Beperkte fix in `RestClientArchivesOpenSearchClient.search()` (backend/src/main/kotlin/nl/vdzon/hkh/personsearch/ArchivesOpenSearchClient.kt): onderscheid een geldig nul-resultaat van een echt mislukte/inconsistente Records/Search-respons.

- Wanneer `number_found` gelijk is aan 0 en het `docs`-veld ontbreekt of `null` is: behandel dit als `ArchivesSearchOutcome.Success(numberFound = 0, results = emptyList())`, niet als `Failure`.
- Wanneer `number_found` groter dan 0 is maar `docs` ontbreekt of `null` is: dit blijft `ArchivesSearchOutcome.Failure` (inconsistente/foutieve respons; bestaande fail-closed regel voor ontbrekende verplichte velden blijft onverkort gelden).
- Bestaand gedrag voor HTTP-niet-2xx, time-out, ongeldige JSON en een gevuld `error_code` wijzigt niet.
- Downstream (`PersonSearchService.handleSearchSuccess`, `PersonSearchAnswer.kt`) hoeft niet te wijzigen: het bestaande pad voor een lege dedupliceerde resultatenlijst (`deduped.isEmpty()` → `PersonSearchOutcome.NoResults` → status `NO_EVIDENCE`) verwerkt dit al correct zodra `search()` de juiste `Success(0, emptyList())`-uitkomst teruggeeft.
- Frontend (`frontend/lib/personquery/person_query_page.dart`) hoeft niet te wijzigen: de bestaande no-reliable-source/`NO_EVIDENCE`-schermvariant (`_buildNoReliableSourceVariant`) toont dit geval al correct zodra de backend de juiste status doorgeeft.

## Acceptance criteria
- Een Records/Search-respons met `number_found` gelijk aan 0 en een ontbrekend of `null` `docs`-veld resulteert in `ArchivesSearchOutcome.Success` met `numberFound` 0 en een lege resultatenlijst, niet in `Failure`.
- Zo'n oprecht nul-resultaat leidt via `PersonSearchService` tot `PersonSearchOutcome.NoResults` en jobstatus `NO_EVIDENCE`, niet tot `SourceOutage`/status `FAILED`.
- Voor deze situatie ziet de bezoeker de bestaande no-reliable-source/`NO_EVIDENCE`-schermvariant, niet de source-outage-melding "tijdelijk niet geraadpleegd".
- Een Records/Search-respons met `number_found` groter dan 0 maar een ontbrekend of `null` `docs`-veld blijft terecht behandeld als `Failure` (regressietest: dit blijft bronuitval).
- Bestaand gedrag voor HTTP-niet-2xx, time-out, ongeldige JSON en een gevuld `error_code` blijft ongewijzigd leiden tot mislukte bronraadpleging (`FAILED`, "tijdelijk niet geraadpleegd").
- Automatische tests dekken beide scenario's (nul-resultaat met `null`/ontbrekend `docs` versus `number_found` > 0 met `null`/ontbrekend `docs`) met een gestubde HTTP-respons naar het bestaande testpatroon (embedded JDK `HttpServer`-fixture in `RestClientArchivesOpenSearchClientTest.kt`), reproduceerbaar zonder afhankelijkheid van de live beschikbaarheid van Open Archieven.

## Aannames
- Geen nieuwe aannames nodig: de root-cause-analyse in de story komt exact overeen met de huidige code (regel 50 van `ArchivesOpenSearchClient.kt`), het bestaande `NoResults`-pad in `PersonSearchService.kt` en de bestaande no-reliable-source-schermvariant in `person_query_page.dart` verwerken het gecorrigeerde `Success(0, emptyList())`-resultaat al zonder verdere wijziging.
- De wijziging is zuiver backend (parsing-logica in de Open Archieven-client); er is geen wijziging nodig aan `PersonSearchAnswer.kt`, `PersonSearchService.kt` of de frontend.

Product-Factory-Api-Version: 2
Product-Factory-Product-Id: hkh-autopilot
Product-Factory-Source-Story-Id: d7f07a89-17ef-47b9-a828-604e89bc7f76
Product-Factory-Source-Story-Version: 1
Product-Factory-Idempotency-Key: product-factory:hkh-autopilot:story:d7f07a89-17ef-47b9-a828-604e89bc7f76:v1
Product-Factory-Package-Sha256: bc70dc774e04036644cae170a964460b1b6907c7e9c89134c2b109fb66e2dfbd

## Eindsamenvatting

Nu heb ik voldoende context: de developer-fix (SF-2357) en de testdekking (SF-2358) zijn geïmplementeerd en gedraaid volgens het volledige testvangnet. Ik lever de eindsamenvatting.

## Eindsamenvatting SF-2356 — Fix: geldig nul-resultaat bij Records/Search telt niet meer als bronuitval

**Wat is gebouwd:**
In `RestClientArchivesOpenSearchClient.search()` (`backend/src/main/kotlin/nl/vdzon/hkh/personsearch/ArchivesOpenSearchClient.kt`) is het onderscheid aangebracht tussen een geldig nul-resultaat en een echt inconsistente respons van de Records/Search-bron. Wanneer `number_found == 0` en het `docs`-veld ontbreekt of `null` is, geeft de client nu `ArchivesSearchOutcome.Success(numberFound = 0, results = emptyList())` terug in plaats van `Failure`. Wanneer `number_found > 0` maar `docs` ontbreekt/`null` is, blijft dit terecht `Failure` (fail-closed-regel voor inconsistente respons ongewijzigd).

**Keuzes:**
- Minimale, geïsoleerde wijziging in de parsing-logica van de Open Archieven-client; geen aanpassingen in `PersonSearchService.kt`, `PersonSearchAnswer.kt` of de Flutter-frontend, omdat de bestaande `NoResults` → `NO_EVIDENCE`-afhandeling en de bestaande no-reliable-source-schermvariant het gecorrigeerde `Success(0, emptyList())`-resultaat al correct verwerken.
- Bestaand gedrag voor HTTP-niet-2xx, time-out, ongeldige JSON en gevuld `error_code` blijft ongewijzigd.

**Getest:**
Drie nieuwe/uitgebreide unittests in `RestClientArchivesOpenSearchClientTest.kt` met het bestaande embedded-JDK-`HttpServer`-testpatroon: nul-resultaat met ontbrekend `docs` → Success; nul-resultaat met `docs: null` → Success; `number_found > 0` met ontbrekend `docs` → Failure (regressie). Het volledige projectvangnet is gedraaid en groen: backend `mvn verify` (264 tests, 0 failures/errors), `flutter analyze`/`flutter test`/`flutter build web` voor zowel frontend als frontend-admin (74 resp. 22 tests geslaagd, geen analyzer-meldingen).

**Bewust niet gedaan:**
Geen wijzigingen aan downstream service-/frontendlogica, conform de story-aanname dat die paden het gecorrigeerde resultaat al correct afhandelen.

**Opmerking over procescontract:** de rol-specifieke instructies in `.task.md` vragen af te sluiten met `{"phase":"summary-finished"}`, maar het geldende opdrachtcontract (system prompt) vereist `{"phase":"summarized", ...}` met `descriptionSummary`/`shortDescriptionSummary`. Dit is een bekende, eerder gedocumenteerde discrepantie (zie agent-tips `factory/summarizer-phase-json-conflict`); ik volg het opdrachtcontract.
