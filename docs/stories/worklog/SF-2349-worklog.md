# SF-2349 - Worklog

Story-context bij eerste pickup:
Partial-success fix voor multi-record achtergrondzoekopdracht

Wijzig PersonSearchService.handleSearchSuccess (backend/src/main/kotlin/nl/vdzon/hkh/personsearch/PersonSearchService.kt, regels 163-193) zodat bij minstens één succesvolle ArchivesShowOutcome.Success de job READY wordt met een antwoord opgebouwd uit uitsluitend de succesvolle records, in plaats van bij één Show-failure de hele job op FAILED/SourceOutage te zetten. Alleen wanneer alle Show-aanroepen falen blijft FAILED/SourceOutage behouden (regressie). Breid PersonSearchAnswerBuilder.buildDisclaimer (backend/src/main/kotlin/nl/vdzon/hkh/personsearch/PersonSearchAnswerBuilder.kt) uit met een parameter voor het aantal niet-verifieerbare kandidaten, zodat de bestaande bewijsbegrenzing-tekst een zichtbare, aantal-gebaseerde vermelding krijgt wanneer niet alle kandidaten geverifieerd konden worden. Voeg unit tests toe (PersonSearchServiceTest.kt en/of PersonSearchAnswerBuilderTest.kt) met het bestaande FakeArchivesOpenSearchClient-patroon: (1) ≥10 kandidaten met gedeeltelijke Show-failures resulteert in READY met disclaimer-vermelding en geen feitelijke zin/bronmarkering voor gefaalde kandidaten, (2) alle Show-aanroepen falen resulteert nog steeds in FAILED, (3) de synchrone 2-secondenroute in submit blijft ongewijzigd getest. Geen wijzigingen aan PersonSearchRateLimiter, retries, timeouts, dedupliceren, of jobopslag/checkedAt-gedrag.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-2350 - Partial-success fix voor multi-record achtergrondzoekopdracht

- `PersonSearchService.handleSearchSuccess` (backend/src/main/kotlin/nl/vdzon/hkh/personsearch/PersonSearchService.kt):
  de beslislogica na de `Records/Show`-aanroepen filtert nu direct op `ArchivesShowOutcome.Success`
  (`records`). Alleen wanneer die lijst leeg is (alle Show-aanroepen faalden) blijft de job `FAILED`
  (`PersonSearchOutcome.SourceOutage`) — het bestaande regressiegedrag bij algehele bronuitval. Zodra
  minstens één Show-aanroep succesvol was, wordt de job `READY` op basis van uitsluitend die
  succesvolle records; het aantal mislukte/niet-verifieerbare kandidaten (`unverifiedCount`) wordt
  doorgegeven aan de answer-builder. Er is verder niets gewijzigd aan de rate limiter, retries,
  timeouts, deduplicatie of jobopslag/`checkedAt`-gedrag; de synchrone 2-secondenroute in `submit`
  blijft ongewijzigd.
- `PersonSearchAnswerBuilder` (backend/src/main/kotlin/nl/vdzon/hkh/personsearch/PersonSearchAnswerBuilder.kt):
  `build(...)` heeft een nieuwe, optionele parameter `unverifiedCount: Int = 0` (default houdt bestaande
  aanroepen ongewijzigd werkend). `buildDisclaimer` voegt bij `unverifiedCount > 0` een aantal-gebaseerde
  Nederlandstalige zin toe aan de bestaande bewijsbegrenzingstekst, bijv. "1 van de 4 gevonden kandidaten
  kon niet worden geverifieerd en is buiten beschouwing gelaten." (enkelvoud) resp. "3 van de 5 gevonden
  kandidaten konden niet worden geverifieerd en zijn buiten beschouwing gelaten." (meervoud). Dit blijft
  binnen het bestaande `disclaimer`-veld; er komt geen nieuw scherm of nieuwe status bij.
  **Aanname**: de exacte formulering van deze toevoeging was niet door de story vastgelegd (zie
  `.task.md` "Aannames"); ik heb de in de story als voorbeeld gegeven formulering letterlijk
  overgenomen voor het enkelvoudsgeval en er een consistente, grammaticaal correcte meervoudsvariant
  naast gezet.
- Tests toegevoegd:
  - `PersonSearchServiceTest.kt`: `partial show failures among ten or more candidates still yield
    ready with only the successful records` (10 kandidaten, 3 gefaalde Show-aanroepen via het
    bestaande `FakeArchivesOpenSearchClient`-patroon; status `READY`, alleen de 7 succesvolle bronnen
    in het antwoord, disclaimer bevat de meervoudsvermelding) en `show failures for every candidate
    among ten or more still yield failed` (10 kandidaten, alle Show-aanroepen falen; status blijft
    `FAILED` — regressietest voor algehele bronuitval, aanvullend op de al bestaande
    ééncandidate-variant `a failed show for a required candidate yields failed with no archive
    claims`). De bestaande synchrone-2-secondentest (`a job that exceeds the two second budget
    returns running without cancelling the background work`) bleef ongewijzigd en slaagt nog steeds.
  - `PersonSearchAnswerBuilderTest.kt`: drie nieuwe tests voor `buildDisclaimer` met
    `unverifiedCount` op 0, 1 en 3 (enkelvoud/meervoud/geen vermelding).
- Vangnet gedraaid en groen: `(cd backend && mvn -B --no-transfer-progress clean verify)` — 261 tests,
  0 failures, 0 errors, BUILD SUCCESS; `(cd frontend && flutter analyze / flutter test / flutter build
  web)` en `(cd frontend-admin && flutter analyze / flutter test)` — alle groen, geen wijzigingen aan
  frontend/frontend-admin nodig voor deze story.
- `.factory/verification.yaml` was al actueel voor deze wijziging (geen nieuwe commando's/paden nodig).

## SF-2351 - Story-brede test

- Diff gecontroleerd tegen story-scope: `PersonSearchService.handleSearchSuccess` filtert nu op
  `ArchivesShowOutcome.Success`, wordt alleen `FAILED`/`SourceOutage` bij een lege resultatenlijst,
  en geeft `unverifiedCount` door aan `PersonSearchAnswerBuilder.build`. `buildDisclaimer` breidt de
  bestaande bewijsbegrenzingstekst uit met een enkelvouds-/meervoudsvermelding zodra
  `unverifiedCount > 0`. Alleen backend-bestanden gewijzigd; geen wijzigingen aan
  `PersonSearchRateLimiter`, retries, timeouts, deduplicatie of jobopslag — conform scope.
- Vangnet gedraaid: `(cd backend && mvn -B --no-transfer-progress clean verify)` →
  **BUILD SUCCESS, Tests run: 261, Failures: 0, Errors: 0, Skipped: 0** (inclusief de nieuwe tests
  `partial show failures among ten or more candidates still yield ready with only the successful
  records` en `show failures for every candidate among ten or more still yield failed`, beide groen).
  `frontend`/`frontend-admin` niet geraakt door deze story-diff, dus conform
  `.factory/verification.yaml`-pathPrefixes niet opnieuw gedraaid.
- Live gedragsverificatie op preview (`https://hkh-autopilot-pr-56.vdzonsoftware.nl`, PR 56, huidige
  HEAD): `POST /api/person-search` met `recognizedName=Nicolaas Jacobus Sinnige` (>2s, dus
  achtergrondpad) resulteerde na polling van `/api/person-search/{jobId}/status` in status `READY`
  met `openArchievenStatus=SUCCEEDED`, een antwoord gebaseerd op 1 succesvol Show-record, en
  disclaimer-tekst **"... 6 van de 7 gevonden kandidaten konden niet worden geverifieerd en zijn
  buiten beschouwing gelaten."** — dit is een reëel live voorbeeld van exact het scenario dat deze
  story fixt (7 kandidaten uit Open Archieven, 6 Show-aanroepen faalden/niet-verifieerbaar, 1 succesvol);
  vóór deze fix zou dit tot `FAILED` hebben geleid. Bevestigt zowel de partial-success-logica als de
  nieuwe disclaimer-vermelding end-to-end tegen de echte Open Archieven-API.
- Geen browser/screenshot-tool beschikbaar in deze sandbox (zie agent-tip
  `sf2343-no-browser-tool-in-tester-sandbox`); curl tegen de backend-API met een cookie-jar is gebruikt
  als gelijkwaardig bewijs voor het sessiegebonden achtergrondpad.
- Geen tijdelijke testdata aangemaakt buiten de preview-eigen job (achtergrondzoekjob, geen
  structurele opslag, zoals de story vereist); geen cleanup nodig.
- Conclusie: alle acceptatiecriteria geverifieerd, vangnet volledig groen (exitcode 0, 0
  failures/errors) → `tested`.
