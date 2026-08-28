# SF-2325 - Worklog

Story-context bij eerste pickup:
Worker-onafhankelijk jobstatuscontract, statusendpoint, stopactie, retentie/opschoning en background-search/search-ready-schermen

Backend: vervang het interne statusmodel (RUNNING/SUPPORTED_ANSWER/NO_RESULTS/PARTIAL/SOURCE_OUTAGE) door het contract QUEUED/RUNNING/READY/NO_EVIDENCE/PARTIAL/FAILED/CANCELLED/EXPIRED in PersonSearchJob/PersonSearchOutcome/PersonSearchAnswerBuilder/PersonSearchController, uitvoerbaar door de bestaande gedeelde executor zonder Agent Runtime. Voeg aan PersonSearchJob/PersonSearchJobStore toe: updatedAt, per-bron consultatiestatus (Open Archieven/Wikidata: niet-gestart/bezig/geslaagd/mislukt), laatste sessie-activiteitsmoment, een cancel-vlag die vóór elke uitgaande bronaanroep wordt gecontroleerd, en versleutelde opslag (AES-256-GCM, naar het ExternalVerificationTokenCipher-patroon, fail-closed zonder geconfigureerde sleutel) van jobstatus/bronrecords/antwoordbeweringen - blijft in-memory, geen nieuwe databasetabel. Implementeer GET /api/person-search/{jobId}/status (status, createdAt, updatedAt, per-bron status; volledige uitkomst alleen bij terminale status; sessie-isolatie fail-closed zodat een andere sessie zich gedraagt alsof de job niet bestaat, geen sessie-/job-id in zichtbare bronlinks of analytics) en POST /api/person-search/{jobId}/cancel (zet CANCELLED, blokkeert nieuwe bronaanroepen, verwijdert direct de tijdelijke payload). Implementeer een geplande opschoningstaak die payload verwijdert en status op EXPIRED zet zodra 60 minuten sessie-inactiviteit of 24 uur na createdAt verstrijkt (ook direct bij CANCELLED/EXPIRED); elke indiening/statusaanvraag/stopactie ververst het sessie-activiteitsmoment los van de bestaande 24u-cookie-maxAge. Opgeslagen records bevatten expliciet provider, externe identifier, directe URI en checkedAt; een later binnen dezelfde sessie opnieuw getoonde READY-job markeert dit uitdrukkelijk als 'eerder opgehaald'. Voeg een sessie-indicatorbron toe (aantal lopende + gereedstaande-niet-geopende jobs van uitsluitend de huidige sessie). Frontend: voeg onder frontend/lib/personsearch/ background_search_screen.dart (screenKey background-search: oorspronkelijke vraag, starttijd, status, per-bron voortgang, nieuwe-vraag-actie zonder de lopende job te onderbreken, stopactie) en search_ready_screen.dart (screenKey search-ready: voltooiingstijd, geraadpleegde bronnen, precies één actie die het antwoord opent) toe, elk met exact één desktop- en één mobile-uitwerking (bruikbaar zonder horizontaal scrollen bij 320 CSS-pixels), bedienbaar met Tab/Shift+Tab/Enter, zichtbare focus en kleuronafhankelijke statusweergave. Voeg een vaste, op alle schermen van de route zichtbare sessie-indicatorwidget toe. Implementeer client-side hervattenlogica die na in-app-navigatie, reload of terugkeer binnen dezelfde sessie automatisch statuscontrole hervat voor niet-terminale of nog-niet-geopende READY-jobs en bij READY naar search-ready leidt; na verwijderd/EXPIRED toont de UI een duidelijke niet-meer-beschikbaar-melding met aanbod tot opnieuw indienen. Werk person_search_client.dart/person_search_models.dart bij met status-poll- en stopmethodes en het uitgebreide statusmodel. Werk alle bestaande tests die het oude statusmodel raken mee (PersonSearchServiceTest, PersonSearchAnswerBuilderTest, PersonSearchControllerTest, PersonSearchJobStoreTest, person_search_client_test.dart, person_search_screens_test.dart) en voeg nieuwe unit-/widget-tests toe voor het statusendpoint, de stopactie, sessie-isolatie, retentie/opschoning, versleuteling (fail-closed zonder sleutel) en de twee nieuwe schermen, inclusief een test die aantoont dat de volledige route werkt met alleen de gewone achtergrondworker zonder Agent Runtime.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## Backend

- `PersonSearchStatus` vervangen door het nieuwe contract
  `QUEUED, RUNNING, READY, NO_EVIDENCE, PARTIAL, FAILED, CANCELLED, EXPIRED` (mapping conform de
  aannames in het issue); `PersonSearchOutcome.toStatus()` bijgewerkt.
- `PersonSearchJob` uitgebreid met `updatedAt`, `openArchievenStatus`/`wikidataStatus`
  (`PersonSearchSourceConsultationStatus`: `NOT_STARTED/IN_PROGRESS/SUCCEEDED/FAILED`),
  `encryptedOriginalQuery`, `encryptedOutcome` en `openedAt`. Twee expliciete velden in plaats van
  een generieke bronnenkaart, omdat er precies twee vaste bronnen zijn (Open Archieven, Wikidata).
- Nieuw `PersonSearchPayloadCipher` (AES-256-GCM, `hkh.personsearch.payload-key` /
  `HKH_PERSON_SEARCH_PAYLOAD_KEY`, fail-closed zonder sleutel), naar het patroon van
  `ExternalVerificationTokenCipher` maar een eigen `@Component` binnen de module (die
  `allowedDependencies = {}` heeft). Versleutelt de oorspronkelijke vraag en de opgeslagen
  antwoordpayload (`PersonSearchStoredPayload`: refinementMessage/answer/context), via een eigen
  `JsonMapper` (`tools.jackson.databind.json.JsonMapper` + `kotlinModule()`) zodat er geen Spring
  `ObjectMapper`-bean-afhankelijkheid nodig is.
- `PersonSearchJobStore` uitgebreid met sessie-activiteit (`touchSessionActivity`, los van de
  bestaande 24u-cookie-`maxAge`), `cancel`/`isCancelled`/`markOpened`/`sessionIndicator` en
  `purgeExpired()` (60 min sessie-inactiviteit of 24u na `createdAt`, wat eerder komt; zet
  `EXPIRED` en wist de payload). `findById` is een tweede, sessie-ongebonden lookup uitsluitend
  voor de achtergrondtaak zelf; de controller gebruikt altijd `findByIdForSession` (fail-closed).
- `PersonSearchService.submit()`: job start op `QUEUED`, wordt `RUNNING` zodra de achtergrondtaak
  daadwerkelijk op de executor start; controleert `jobStore.isCancelled(jobId)` vóór elke
  Open Archieven-/Wikidata-aanroep (ook halverwege de Show-lus, dankzij een non-lokale `return`
  in de inline `map`-lambda). Een uitkomst wordt zowel in `whenComplete` als — voor het geval die
  dependent stage nog niet is afgerond wanneer `future.get()` al terugkomt — direct na een
  succesvolle synchrone afronding gepersisteerd (`persistOutcome`, idempotent op een reeds
  terminale job): zonder die tweede plek zag een onmiddellijk volgende statusaanvraag soms nog de
  oude, niet-terminale status (ontdekt via een falende testassertie tijdens het schrijven van de
  sessie-indicatortest).
- Nieuwe endpoints op `PersonSearchController`: `GET /{jobId}/status`, `POST /{jobId}/cancel`,
  `POST /{jobId}/open` (markeert een `READY`-job als geopend, voor de sessie-indicator) en
  `GET /session` (aantal + job-ids van lopende en gereedstaande-niet-geopende jobs). Alle vier
  zijn sessiegebonden fail-closed (404 bij onbekende job of andere sessie). De job-ids in de
  sessie-indicatorrespons zijn nodig zodat de client na een volledige herlading weet welke
  statuscontrole te hervatten; dit zijn geen zichtbare bronlinks/analyticswaarden.
- `PersonSearchRetentionCleanupTask` (`@Scheduled(fixedDelay = 60_000)`) roept periodiek
  `jobStore.purgeExpired()` aan; `@EnableScheduling` toegevoegd aan `HkhApplication` (nog niet
  eerder gebruikt in deze repo — geen bestaand `@Scheduled`-patroon om te volgen).
- `hkh.personsearch.payload-key` toegevoegd aan `application.properties` en
  `docs/factory/secrets-local.md`/`secrets.env.example` (env `HKH_PERSON_SEARCH_PAYLOAD_KEY`).
- Bestaande tests (`PersonSearchJobStoreTest`, `PersonSearchServiceTest`,
  `PersonSearchControllerTest`, `PersonSearchNicolaasSinnigeExampleTest`) bijgewerkt naar het
  nieuwe statusmodel/de nieuwe `PersonSearchJob`-vorm; nieuwe tests toegevoegd voor stopactie
  (voor en tijdens uitvoering), sessie-isolatie, retentie/opschoning (24u en 60 min), `QUEUED`
  op een verzadigde executor, per-bron consultatiestatus, encryptie (`PersonSearchPayloadCipherTest`,
  incl. fail-closed) en de sessie-indicator/openactie. `PersonSearchTestFixtures.kt` toegevoegd
  (`testJobStore()`/`testPayloadCipher()`) zodat elke test een geldige testsleutel gebruikt zonder
  die overal te herhalen.

## Frontend

- `person_search_models.dart`: `PersonSearchStatus` uitgebreid naar het nieuwe contract; nieuwe
  `PersonSearchSourceConsultationStatus`, `PersonSearchStatusResult` (statusaanvraag/stopactie/
  openactie-respons) en `PersonSearchSessionIndicator`.
- `person_search_client.dart`: `PersonSearchSource` uitgebreid met `pollStatus`/`cancel`/`open`/
  `sessionIndicator`; nieuwe `PersonSearchStatusException`/`PersonSearchJobUnavailableException`
  (laatste specifiek voor een 404, zodat de UI dat onderscheidt van een tijdelijke netwerkfout).
- Nieuwe schermen `background_search_screen.dart` en `search_ready_screen.dart` (elk één
  responsieve implementatie die bij `kPersonQueryMobileBreakpoint` omschakelt, zoals de bestaande
  schermen; geen aparte desktop/mobile-widgetklassen nodig omdat de layout zelf niet wezenlijk
  verschilt — enkel de container-breedte). Per-bronstatus wordt met zowel een icoon-vorm als tekst
  getoond (kleuronafhankelijk).
- `session_indicator_badge.dart`: klein, zelfverversend widget (eigen `Timer.periodic`, elke 5s)
  in de `AppBar` van `PersonQueryPage`, dus zichtbaar op alle schermen van de route.
- `person_query_page.dart`: nieuwe `_PersonQueryScreen`-waarden `backgroundSearch`, `searchReady`,
  `unavailable`. Bij een submit-resultaat `QUEUED`/`RUNNING` wordt eerst één keer de volledige
  status opgehaald (voor starttijd/per-bron-status) voordat naar `background-search` geschakeld
  wordt; daarna plant een eigen `Timer` (3s, geen acceptatiecriterium op het exacte interval) de
  volgende statuscontrole zolang de job niet terminaal is. "Stel intussen een andere vraag" stopt
  alleen de voorgrondpolling van dat scherm (via de generation-teller) en navigeert naar `start`;
  de achtergrondtaak blijft server-side gewoon doorlopen. "Stop opdracht" roept expliciet de
  cancel-actie aan. `initState` roept `sessionIndicator()` aan om na herlading de eerste lopende of
  gereedstaande-niet-geopende job van deze sessie te hervatten (bij meerdere gelijktijdige jobs
  wordt alleen de eerste hervat in de voorgrond; de sessie-indicator toont wel het juiste totaal).
- Alle testaanroepen van `PersonQueryPage()` zonder expliciete `personSearchSource` kregen een
  `_FakePersonSearchSource.idle()`: zonder die aanpassing zou `initState`s nieuwe
  `sessionIndicator()`-aanroep via de standaard `_LazyPersonSearchClient` een echte (falende)
  netwerkaanroep doen tijdens elke widgettest.
- Nieuwe tests: client (`pollStatus`/`cancel`/`open`/`sessionIndicator`, inclusief 404 →
  `PersonSearchJobUnavailableException`), schermen (`BackgroundSearchScreen`/`SearchReadyScreen`:
  inhoud, Tab/Enter, 320px zonder overloop) en pagina-niveau (volledige flow QUEUED/RUNNING →
  background-search → search-ready → antwoord; stoppen; "andere vraag stellen" zonder te stoppen;
  niet-meer-beschikbaar-melding met opnieuw-indienen; hervatten na herlading).

## Vangnet (allemaal groen)

- `(cd backend && mvn -B --no-transfer-progress clean verify)`: 256/256 tests, BUILD SUCCESS.
- `(cd frontend && flutter analyze)`: geen meldingen.
- `(cd frontend && flutter test)`: 74/74 tests (via `--concurrency=1 --reporter expanded`).
- `(cd frontend && flutter build web)`: succesvol.
- `(cd frontend-admin && flutter analyze)`: geen meldingen.
- `(cd frontend-admin && flutter test)`: 22/22 tests (ongewijzigd door deze story).

## Niet gedaan / bewust buiten scope

- Geen wijziging aan `POST /api/person-search` qua contract, aan de persoonsnaamherkenning
  (SF-2311) of aan Agent Runtime als uitvoeringsadapter — conform de expliciete scope-uitsluiting.
- Bij meerdere gelijktijdig lopende jobs binnen dezelfde sessie toont de UI er na hervatten na
  herlading maar één in de voorgrond (de eerste uit de sessie-indicatorlijst); dit is een bewuste,
  proportionele keuze gegeven dat de bestaande pagina één actief scherm per keer toont en de
  sessie-indicator alle jobs blijft meetellen.

## Review (SF-2326)

Volledige story-diff (`git diff main...HEAD`) beoordeeld: backend-statuscontract, cipher,
retentietaak, controller/service, en frontend-schermen/hervattenlogica/sessie-indicator.

- Verificatiebewijs: worktree-tree van de developercommit (`85bc1bb`) is exact gelijk aan
  `testedTreeSha` (`585b4e9...`) uit het FACTORY VERIFICATION EVIDENCE-blok; alle vijf
  uitgevoerde commando's groen (backend mvn verify 256/256, flutter analyze/test 74/74/build web).
  Geen skips die als bewijs tellen zijn hierbij nodig (admin-checks correct geskipt,
  ongewijzigde module).
- Statuscontract, statusendpoint, sessie-isolatie (fail-closed 404), stopactie (blokkeert
  vervolgaanroepen mid-executie, geverifieerd met een test die toont dat show-aanroepen na
  cancel uitblijven), retentie/opschoning (24u en 60 min, beide met eigen test) en AES-256-GCM
  fail-closed-encryptie zijn alle aantoonbaar en met gerichte tests gedekt.
- Sessie-id komt nergens in een responsebody voor; job-id wordt alleen gebruikt als
  poll-/hervattenidentifier, niet als zichtbare bronlink/analyticswaarde.
- Frontend: nieuwe `background-search`/`search-ready`-schermen met 320px- en
  Tab/Shift+Tab/Enter-tests, kleuronafhankelijke per-bronstatus (icoon + tekst),
  sessie-indicator in de AppBar, hervattenlogica na reload met correcte fakes in alle
  bestaande widgettests (voorkomt de eerder in agent-tips genoemde initState-netwerkval).
- Bekende, expliciet gedocumenteerde beperking (bij meerdere gelijktijdige jobs toont hervatten
  na herlading er één in de voorgrond) is proportioneel en buiten de acceptatiecriteria (die
  spreken over "alle" jobs qua statuscontrole-hervatting, niet qua UI-voorgrond); de
  sessie-indicator zelf telt wel alles correct. Geen blocker.

Geen blockers of bugs gevonden in deze ronde.

## Test (SF-2327)

Vangnet opnieuw uitgevoerd op de huidige HEAD (`838694c`), geen preview-URL geconfigureerd
(`preview_url_template` leeg in `docs/factory/deployment.md`), dus lokaal getest; geen docker-CLI
beschikbaar in deze sandbox (bekend, zie agent-tip `docker-cli-missing-but-socket-present`) maar
Testcontainers via de socket werkt gewoon voor de backend-integratietests.

- `(cd backend && mvn -B --no-transfer-progress clean verify)`: 256/256 tests, BUILD SUCCESS.
- `(cd frontend && flutter analyze)`: geen meldingen.
- `(cd frontend && flutter test --concurrency=1 --reporter expanded)`: 74/74 tests, incl. de
  nieuwe `background-search`/`search-ready`-scenario's (volledige flow, stoppen, andere vraag
  stellen zonder te stoppen, niet-meer-beschikbaar-melding, hervatten na herlading, 320px,
  Tab/Shift+Tab/Enter).
- `(cd frontend && flutter build web)`: succesvol.
- `frontend-admin` is door deze story niet gewijzigd (`git diff main...HEAD -- frontend-admin/`
  is leeg); de admin-checks uit `.factory/verification.yaml` triggeren dan ook niet op hun
  pathPrefixes en zijn terecht overgeslagen.

Aanvullend een gerichte codelezing van de story-diff tegen de acceptatiecriteria (naast het
vangnet, want de tester verifieert gedrag, niet alleen groene tests):

- `PersonSearchController`/`PersonSearchService`/`PersonSearchJobStore`: het statusendpoint
  retourneert de volledige payload uitsluitend wanneer `encryptedOutcome` gezet is, en dat veld
  wordt alleen tegelijk met een terminale status gezet (`persistOutcome`, `cancel`,
  `purgeExpired` zetten status én payload synchroon) - dus "volledige uitkomst pas bij terminale
  status" klopt door constructie, niet alleen via een test-assertie.
  `findByIdForSession`/`cancel`/`markOpened` zijn overal sessiegebonden fail-closed; de
  achtergrondtaak gebruikt bewust de aparte, sessie-ongebonden `findById`. `isCancelled` wordt
  vóór elke Open Archieven-/Wikidata-aanroep gecontroleerd, inclusief halverwege de Show-lus.
  `PersonSearchPayloadCipher.secretKey()` gooit fail-closed (`check(base64Key.isNotBlank())`)
  zonder geconfigureerde `HKH_PERSON_SEARCH_PAYLOAD_KEY`.
- `background_search_screen.dart`/`search_ready_screen.dart`/`session_indicator_badge.dart`:
  per-bronstatus toont zowel icoon als tekst (kleuronafhankelijk); `search-ready` heeft precies
  één knop (`search-ready-view-answer`) die het antwoord opent; de sessie-indicator toont alleen
  cijfers van de eigen sessie (backend levert dit al sessiegebonden) en faalt stil terug op de
  laatst bekende waarde bij een ververfout, zonder een oude uitkomst als actueel te presenteren.

Geen bugs of afwijkingen van de story gevonden; vangnet volledig groen.
