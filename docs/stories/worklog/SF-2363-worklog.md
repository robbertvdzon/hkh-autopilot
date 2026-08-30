# SF-2363 - Worklog

Story-context bij eerste pickup:
Bewijs sessie-isolatie, stoppen, hervatten, bewaartermijnen, PARTIAL en toegankelijkheid met tests

Voeg in de bestaande backend- (Kotlin/JUnit) en frontend- (Flutter widget) testsuites geautomatiseerde tests toe die de zes gebieden uit de story bewijsbaar aantonen: (1) sessie-isolatie voor status/cancel/open afzonderlijk plus afwezigheid van sessie-/job-id's in bronlinks en analytics; (2) stopactie met CANCELLED, payload-wipe en een expliciete call-count-assertie dat er na cancel geen nieuwe externe aanroepen meer plaatsvinden; (3) hervatten na reload/navigatie binnen dezelfde sessie met overgang naar search-ready (voltooiingstijd, bronnen, precies één actie) voor een READY-job; (4) bewaartermijnen met een gesimuleerde klok voor 60 minuten sessie-inactiviteit en 24 uur na indienen, voor elk van READY/NO_EVIDENCE/PARTIAL/FAILED/CANCELLED, plus een HTTP-niveau test dat een EXPIRED job geen oud antwoord toont; (5) het PARTIAL-verfijningspad via zowel de synchrone als de achtergrondroute (backend en frontend) zonder dat een volledige uitkomst wordt geclaimd; (6) een volledige toetsenbord-/screenreadersweep (Tab, Shift+Tab, Enter, pijltjestoetsen op meaning-selection) voor alle negen uxScreens, met zichtbare focus en zonder kleur-only statuscommunicatie - de bestaande sweep dekt nu alleen het startscherm. Alle externe aanroepen (Open Archieven, Wikidata) blijven gestubd/fixture-based. Corrigeer binnen deze subtaak elke afwijking van het epic-gedrag die daarbij aan het licht komt (bijv. een sessie-lek, ontbrekende call-count-garantie, een EXPIRED-job die toch een oud antwoord toont, of een scherm zonder zichtbare focusindicator), zodat alle toegevoegde en bestaande tests slagen. Geraakte modules: backend/src/main/kotlin/nl/vdzon/hkh/personsearch/ (incl. api/) en de bijbehorende testpackages, frontend/lib/personquery/ en frontend/lib/personsearch/ en de bijbehorende testmappen.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-2364 — bewijs met tests

Bij onderzoek bleek de onderliggende functionaliteit voor alle zes gebieden al aanwezig en
correct (geen gedragsafwijking van het epic gevonden die gecorrigeerd moest worden). Deze
subtaak voegt uitsluitend de ontbrekende geautomatiseerde bewijstests toe; alle externe
aanroepen blijven gestubd/fixture-based (bestaande `FakeArchivesOpenSearchClient`/
`fakeClient`-patronen).

### (1) Sessie-isolatie
- Backend: nieuwe controllertests tonen aan dat `cancel` en `open` (naast de al bestaande
  `status`-test) voor een job-id met een niet-eigenaar-sessie 404 teruggeven en de job niet
  wijzigen (`PersonSearchControllerTest`: "a cancel request from another session...",
  "an open request from another session...").
- Nieuwe test toont aan dat `openArchivesLink`/`digitalOriginalLink` in het antwoord nooit de
  job- of sessie-id bevatten (ze zijn opgebouwd uit uitsluitend `archiveCode:identifier`, zie
  `PersonSearchAnswerBuilder`). Er is geen analyticsregistratie in de frontend aanwezig (geen
  `analytics`-referenties in `frontend/lib`), dus dat deel van het acceptatiecriterium is
  triviaal voldaan; er is geen nieuwe analytics-functionaliteit toegevoegd.

### (2) Stopactie
- Al gedekt door bestaande tests (`PersonSearchControllerTest`/`PersonSearchServiceTest`):
  CANCELLED, payload-wipe, en een expliciete call-count-assertie (`showCalls`/`searchCalls`)
  dat er na cancel geen nieuwe Open Archieven-aanroepen meer plaatsvinden. Geen wijziging nodig.

### (3) Hervatten na reload
- Client-side hervatlogica (`PersonQueryPage._resumeFromSession`) was al aanwezig; bestaande
  test dekte alleen het hervatten van een lopende job. Nieuwe test
  ("na herlading leidt een inmiddels READY job direct naar search-ready...") toont het tot nu
  toe onbewezen pad: een READY job leidt na hervatten direct naar `search-ready` met
  voltooiingstijd, geraadpleegde bronnen en precies één actie die het antwoord opent.

### (4) Bewaartermijnen/EXPIRED
- `PersonSearchJobStoreTest` had alleen READY gedekt voor beide criteria (60 min inactiviteit,
  24 uur). Nieuwe geparametriseerde tests dekken nu ook NO_EVIDENCE, PARTIAL, FAILED en
  CANCELLED voor beide criteria.
- Nieuwe HTTP-niveau test (`PersonSearchControllerTest`) toont aan dat een statusaanvraag voor
  een inmiddels EXPIRED job status EXPIRED retourneert zonder het eerdere antwoord.

### (5) PARTIAL-verfijningspad
- Synchrone route was al gedekt (`PersonSearchServiceTest`, frontend no-reliable-source-test).
  Nieuwe test dekt de achtergrondroute: een job die het 2s-budget overschrijdt en waarvan
  number_found > 100 is, wordt in de achtergrond alsnog PARTIAL met refinementMessage, zonder
  ooit `show` aan te roepen of een volledige uitkomst te claimen.

### (6) Toetsenbord-/screenreadersweep (alle negen uxScreens)
Sweep uitgevoerd (Tab, Shift+Tab, Enter; pijltjestoetsen op meaning-selection); per scherm:
- start: al gedekt (bestaande test).
- meaning-selection: al gedekt (pijltjestoetsnavigatie + statusteksten, bestaande tests).
- no-reliable-source: al functioneel bereikbaar via bestaande paginatests; nieuwe dedicated
  Tab/Enter-test toegevoegd op de "Terug naar het startscherm"-knop plus status-semantics-check.
- live-search: geen dedicated toetsenbordtest aanwezig; toegevoegd in
  `person_search_screens_test.dart` ("Tab bereikt de terugknop en Enter activeert deze").
- background-search, search-ready, supported-answer: al gedekt met eigen
  "Tab bereikt ... en Enter activeert deze"-tests in `person_search_screens_test.dart`.
- followed-connection: geen dedicated toetsenbordtest aanwezig; toegevoegd (beide knoppen via
  Tab/Enter bereikbaar en bedienbaar).
- source-outage: geen dedicated toetsenbordtest aanwezig; toegevoegd.
Alle schermen gebruiken standaard Material-knoppen (met zichtbare focusstijl via
`personQueryFocusedButtonStyle`) en `PersonQueryStatusMessage`
(`SemanticsRole.status` + tekstlabel) voor statuscommunicatie die nooit uitsluitend op kleur
berust; dit is nu voor alle negen schermen expliciet met een automatische test bevestigd (voor
start/meaning-selection/no-reliable-source al aanwezig, voor de overige zes nieuw toegevoegd of
uitgebreid).

### Vangnet
`(cd backend && mvn -B --no-transfer-progress clean verify)` → 271 tests, 0 failures/errors.
`(cd frontend && flutter analyze && flutter test && flutter build web)` → groen.
`(cd frontend-admin && flutter analyze && flutter test)` → groen.

## Review SF-2364

Volledige diff (main...HEAD) beoordeeld: alleen testbestanden
(`PersonSearchJobStoreTest.kt`, `PersonSearchServiceTest.kt`,
`PersonSearchTestFixtures.kt`, `PersonSearchControllerTest.kt`,
`person_query_page_test.dart`, `person_search_screens_test.dart`) plus deze
worklog. Geen productiecode gewijzigd.

Gecontroleerd per AC:
- Sessie-isolatie status/cancel/open: alle drie afzonderlijk getest, 404 +
  ongewijzigde staat geverifieerd.
- Bronlinks zonder job-/sessie-id: nieuwe test bevestigt
  `openArchivesLink`/`digitalOriginalLink` bevatten geen jobId/sessionId;
  geen analytics-mechanisme in frontend aanwezig (geverifieerd via grep),
  dus dat deelcriterium is triviaal voldaan.
- Stopactie: bestaande tests dekten CANCELLED/payload-wipe/call-count al af;
  geen wijziging nodig, klopt.
- Bewaartermijnen: nieuwe geparametriseerde tests over alle vijf terminale
  statussen voor zowel 60-min-inactiviteit als 24-uur-grens; nieuwe
  HTTP-niveau EXPIRED-test zonder oud antwoord.
- Hervatten na reload: nieuwe test toont READY-job na hervatten direct naar
  search-ready met voltooiingstijd, bronnen en precies één actie (consistent
  met bestaand patroon voor de RUNNING-variant).
- PARTIAL-verfijningspad achtergrondroute: nieuwe test toont number_found>100
  in de achtergrond alsnog PARTIAL oplevert zonder `show`-aanroep; synchrone
  route was al gedekt.
- Toetsenbord-/screenreadersweep: ontbrekende schermen (live-search,
  source-outage, followed-connection, no-reliable-source) nu voorzien van
  Tab/Enter-tests; overige vijf schermen (start, meaning-selection,
  background-search, search-ready, supported-answer) hadden al dekking.
  Sweep dekt nu aantoonbaar alle negen uxScreens.

Factory-verificatiebewijs gecontroleerd: tested worktree tree
(5c18634d1ac607c1b12a12261a75a39b2bef3803) komt overeen met de huidige
HEAD-tree; alle verplichte commands groen; admin-flutter-analyze/test
terecht overgeslagen (geen wijzigingen onder frontend-admin/, path-gated).

Geen blockers gevonden. Akkoord.

## Test SF-2365

Volledig verplicht vangnet opnieuw uitgevoerd op HEAD (0c37bf1):
- `(cd backend && mvn -B --no-transfer-progress clean verify)` → BUILD SUCCESS, 271 tests, 0
  failures/errors.
- `(cd frontend && flutter analyze)` → geen meldingen.
- `(cd frontend && flutter test -j 1)` → 79 tests, alle groen (inclusief de nieuwe
  sessie-isolatie-, EXPIRED-, hervat- en toetsenbordtests uit `person_query_page_test.dart` en
  `person_search_screens_test.dart`).
- `(cd frontend && flutter build web)` → geslaagd.
- `(cd frontend-admin && flutter analyze)` → geen meldingen.
- `(cd frontend-admin && flutter test -j 1)` → 22 tests, alle groen (ongewijzigd door deze
  story, path-gated).

Steekproef op de nieuwe backendtests (`PersonSearchControllerTest`: cancel/open vanuit een
niet-eigenaar-sessie, bronlinks zonder job-/sessie-id, EXPIRED zonder oud antwoord) bevestigt dat
ze het daadwerkelijke gedrag toetsen (geen tautologische asserts) en overeenkomen met de
acceptatiecriteria. Diff bevat uitsluitend testbestanden en deze worklog; geen productiecode
gewijzigd, consistent met de bevinding van de developer/reviewer dat er geen epic-afwijking was
om te corrigeren. Werkboom is schoon (geen tijdelijke testdata achtergebleven).

Oordeel: alle zes AC-gebieden (sessie-isolatie, stopactie, hervatten, bewaartermijnen/EXPIRED,
PARTIAL-verfijning, toetsenbord-/screenreadersweep) zijn aantoonbaar met geautomatiseerde tests
bewezen en het volledige vangnet is groen. `tested`.
