# SF-2318 - Worklog

Story-context bij eerste pickup:
Backend personsearch-module + vier frontend-schermen voor live persoonszoekopdracht

Bouw de nieuwe backendmodule nl.vdzon.hkh.personsearch (route-gebonden sessie voor anonieme bezoekers, job-creatie met cryptografisch random job-id, idempotentiesleutel = sessie-id + genormaliseerde vraag + Heemskerk-betekenis, synchrone uitvoering met een harde 2000ms-deadline die de achtergrondtaak niet annuleert, ArchivesOpenSearchClient voor Records/Search en Records/Show met de exacte queryparameters, beschrijvende User-Agent, gzip, max. 4 req/s rate limiting, korte timeouts, begrensde back-off en fail-closed validatie op HTTP-status/JSON/verplichte velden/error_code, deduplicatie op archive_code+identifier, number_found>100-afhandeling zonder Records/Show en met verfijningsverzoek, antwoordopbouw uitsluitend uit Person/Event/RelationEP/Source van gevalideerde Show-records met genummerde bronmarkeringen inclusief checkedAt en links, maximaal twee vervolgsporen uit RelationEP, en een Wikidata-Context-aanroep die nooit zelfstandig een archiefbewering draagt). Implementeer en test expliciet het gecontroleerde Nicolaas Jacobus Sinnige-voorbeeld. Bouw daarnaast de vier nieuwe Flutter-schermen (live-search, supported-answer, followed-connection, source-outage) met elk een desktop- en mobile-uitwerking volgens de aangeleverde artifacts, en breid person_query_page.dart uit zodat een geldige interpretatie de job indient en op basis van het resultaattype naar het juiste scherm schakelt. Schrijf hierbij alle bijbehorende backend- en Flutter-tests (unit, widget, integratie), inclusief sessie-scheiding, idempotentie, alle bronvalidatiefouten, number_found>100, toetsenbordnavigatie en 320px-layout.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Repo-conventies bestudeerd (`externalverification`, `personquery`,
  `ModulithArchitectureTest`, `development.md`, `technical-spec.md`).
- Backendmodule `nl.vdzon.hkh.personsearch` gebouwd: sessiecookie, job + idempotentie,
  `ArchivesOpenSearchClient` (Search/Show, rate limiting, gzip, timeouts, begrensde
  retries, fail-closed validatie), dedup, `number_found>100` → `PARTIAL`,
  antwoordopbouw met genummerde bronmarkeringen, Wikidata-Context, 2000ms-deadline
  zonder de achtergrondtaak te annuleren.
- Backendtests: unit- en fixture-tests (JDK `HttpServer`), inclusief het
  gecontroleerde Nicolaas Jacobus Sinnige-voorbeeld end-to-end via de controller.
- Vier nieuwe Flutter-schermen (`live-search`, `supported-answer`,
  `followed-connection`, `source-outage`), elk met desktop- en mobile-uitwerking, en
  `person_query_page.dart` uitgebreid zodat een geldige interpretatie de job indient
  en op basis van het resultaattype naar het juiste scherm schakelt.
- Flutter-tests (widget/unit) voor de nieuwe schermen en de uitgebreide pagina.
- Volledig vangnet uit `development.md` gedraaid.

Belangrijkste keuzes en aannames (expliciet vastgelegd, geen vraag aan PO):
- **Sessiecookie**: nieuwe, lichte cookie `hkh_person_search_session`
  (cryptografisch random, HttpOnly, SameSite=Lax, geen login), losstaand van het
  bestaande admin/Google-authenticatiemechanisme. Alleen gebruikt om jobs en
  idempotentiesleutels aan een bezoeker te binden.
- **Idempotentie**: sleutel = sha256(sessie-id + genormaliseerde vraag + gekozen
  Heemskerk-betekenis). Een bestaande job (ongeacht terminaal of niet) voor dezelfde
  sleutel wordt altijd hergebruikt — er is in deze story geen TTL/opschoning, dus een
  eerder resultaat opnieuw teruggeven is veiliger en simpeler dan alleen bij
  "nog niet terminaal" te hergebruiken, en voorkomt sowieso een tweede
  bronraadpleging (expliciet vereist door de AC).
- **2s-deadline**: de job draait op een gedeelde `ExecutorService`; het HTTP-request
  wacht met `Future.get(2000ms)`. Bij een timeout retourneert het request status
  `RUNNING` (het contract van deze story vereist geen vervolg-UI hiervoor); de
  achtergrondberekening loopt onafhankelijk door omdat hij al aan de executor is
  aangeboden vóór de wacht-`get`.
- **Followed-connection zonder nieuwe backend-aanroep**: het `supported-answer`
  antwoord bevat alle vervolgsporen (rol + naam, max. 2, volgorde uit `RelationEP`)
  al in de payload. Het `followed-connection`-scherm is daarom volledig
  client-side (geen nieuw endpoint), conform de story-aanname dat hiervoor geen
  extra externe aanroep nodig is.
- **Wikidata-Context**: minimale, fail-closed backend-`Context`-client
  (zoek + entity-data), nooit een archiefbewering, altijd los van de
  Search/Show-uitkomst; een falende Wikidata-aanroep geeft `context = null` en
  blokkeert nooit het archiefantwoord of de `source-outage`-afhandeling.
- **Gzip**: een `ClientHttpRequestInterceptor` vraagt `Accept-Encoding: gzip` op en
  decomprimeert een gzip-respons transparant.
- **PARTIAL/nul-resultaten schermen**: alleen de vier in de AC genoemde schermen
  (`live-search`, `supported-answer`, `followed-connection`, `source-outage`) hebben
  een eigen desktop/mobile-artifact-eis. Het nul-resultatenscherm en het
  `PARTIAL`-verfijningsverzoek hergebruiken het al bestaande
  `no-reliable-source`-scherm (met aangepaste tekst voor deze context); dit blijft
  binnen de bestaande, al geleverde artifacts en is consistent met "buiten scope:
  ... overige vijf hoofdroute-schermen ... horen bij een andere story".
- **Open Archieven Show-schema**: de story beschrijft zelf de te gebruiken
  Show-velden (`Person`, `Event`, `RelationEP`, `Source`); er is geen bestaand
  DTO-precedent in de repo voor dit endpoint, dus het schema (veldnamen als
  `person.name`, `event.type/date/place`, `relationEP[].role/person`,
  `source.institution/source_type/archive_number/register_number/deed_number/
  record_number/digital_original_url`) is expliciet naar de story-tekst gemodelleerd
  en volledig getest, inclusief het Nicolaas-voorbeeld.

Niet gedaan / bewust buiten scope (volgt in vervolgstory, expliciet genoemd in de
story-tekst zelf): statuspolling-API, hervatten na navigatie/reload,
sessie-indicator met live aantallen, versleutelde opslag met retentie/opschoning
(60 min/24 uur), CANCELLED/EXPIRED-afhandeling, Agent Runtime als
uitvoeringsadapter.

## Reviewronde 2026-08-28: race-condition fix idempotentiecontrole

Bevinding (reviewer, issue comment 3786): `PersonSearchService.submit()` deed
`jobStore.findByIdempotencyKey(...)` en `jobStore.save(...)` als twee losse stappen;
gelijktijdige indieningen met dezelfde idempotentiesleutel (dubbelklik, retry) konden
beide de check "geen bestaande job" passeren vóórdat een van beide had opgeslagen, en
zo elk hun eigen job + bronraadpleging starten. Dit schond de AC dat een herhaalde
indiening met dezelfde sleutel terwijl de job nog loopt geen tweede bronraadpleging
start.

Fix:
- `PersonSearchJobStore` kreeg een nieuwe, atomaire `createIfAbsent(idempotencyKey) { factory }`
  op basis van `ConcurrentHashMap.computeIfAbsent`, die per sleutel gegarandeerd maar
  één winnende aanroep van `factory` toelaat (retourneert `PersonSearchJobCreation(job, created)`).
- `PersonSearchService.submit()` gebruikt nu deze atomaire operatie in plaats van de
  losse check-en-save; alleen de aanroep die de job daadwerkelijk aanmaakt (`created == true`)
  start `runSearch`, alle andere gelijktijdige aanroepen met dezelfde sleutel krijgen
  direct de (mogelijk nog lopende) bestaande job terug.
- Nieuwe tests: `PersonSearchJobStoreTest` (16 threads race op dezelfde sleutel via
  `createIfAbsent`, `factory` wordt exact één keer aangeroepen) en
  `PersonSearchServiceTest` (16 gelijktijdige `submit()`-aanroepen met dezelfde
  idempotentiesleutel via een `CyclicBarrier`, `client.searchCalls == 1` en alle
  resultaten hebben hetzelfde job-id).
- Volledig vangnet (`backend mvn verify`, `frontend`/`frontend-admin` analyze/test/build)
  opnieuw groen gedraaid na de fix.

## Testronde SF-2320 (2026-08-28) — REJECTED

Uitgevoerd:
- Backend `mvn -B --no-transfer-progress clean verify`: BUILD SUCCESS, 237 tests, 0 failures/errors.
- Frontend `flutter analyze`: geen issues.
- Frontend `flutter test`: 56/56 groen (default concurrency toonde
  `test/personsearch/person_search_client_test.dart` niet in de live-output — bekend
  `frontend-flutter-test-concurrency-artifact`; bevestigd als artefact via geïsoleerde
  run van dat bestand (5/5 groen) én een volledige `flutter test -j 1` run
  (alle bestanden, incl. `backend_client_test.dart` en
  `personsearch/person_search_client_test.dart`, zichtbaar en 56/56 groen). Geen echte
  failure.
- Frontend `flutter build web`: succesvol.

Bevinding (blokkerend, terug naar developer):
- `ArchivesOpenSearchModels.kt` / `RestClientArchivesOpenSearchClient.kt` gaan uit van
  een plat JSON-schema voor Open Archieven Records/Search
  (`number_found`/`results` top-level) en Records/Show (`person`/`event`/`relationEP`/
  `source`, lowercase, plat). De **echte publieke** `api.openarchieven.nl/1.1`
  retourneert een ander schema:
  - Search: top-level keys zijn `query` en `response`; `number_found` en `docs`
    (niet `results`) zitten genest onder `response`. Geverifieerd live met
    `curl "https://api.openarchieven.nl/1.1/records/search.json?name=Nicolaas%20Jacobus%20Sinnige%201878&archive_code=nha&eventplace=Heemskerk&lang=nl&number_show=100&start=0"`
    → `{"query": {...}, "response": {"number_found": 1, "docs": [{"identifier": "002ED0F3-F08C-4223-A5EA-BA385D04336E", "archive_code": "nha", ...}]}}`.
    Exact het record uit het gecontroleerde voorbeeld, dus de zoekopdracht zelf klopt —
    alleen het antwoordschema niet.
  - Show: top-level keys zijn hoofdlettergevoelig en anders genest: `Person` (array
    van personen, niet één plat object), `Event` (met geneste `EventDate`-structuur),
    `RelationEP` (met `PersonKeyRef`/`RelationType`, geen directe naam/rol-paren) en
    `Source` (diep genest, o.a. `SourceReference.InstitutionName`,
    `SourceReference.DocumentNumber`, `SourceDigitalOriginal`). Geverifieerd live met
    `curl "https://api.openarchieven.nl/1.1/records/show.json?archive=nha&identifier=002ED0F3-F08C-4223-A5EA-BA385D04336E&lang=nl"`.
- Gevolg: `RestClientArchivesOpenSearchClient.search()` retourneert tegen de echte API
  altijd `ArchivesSearchOutcome.Failure` (regels 49-50: `body.numberFound`/`body.results`
  zijn `null`, want die velden bestaan niet top-level), en `show()` faalt eveneens altijd
  (regel 80 e.v.: `body.person`/`body.event`/... zijn `null`). Met de standaard
  productie-basis-URL (`https://api.openarchieven.nl/1.1`, ongewijzigd in
  `application.properties`/`PersonSearchClientConfiguration.kt`) resulteert **elke**
  vraag altijd in `source-outage`, nooit in `supported-answer` — ook niet het letterlijk
  voorgeschreven Nicolaas Jacobus Sinnige-voorbeeld, terwijl de bijbehorende zoekopdracht
  bij de echte bron wél precies één match met het verwachte `identifier` oplevert.
- Alle backend-tests slagen alleen omdat de test-fixtures (JDK `HttpServer` in
  `PersonSearchNicolaasSinnigeExampleTest`, `RestClientArchivesOpenSearchClientTest`,
  `PersonSearchServiceTest`) zelf het (onjuiste) platte schema simuleren in plaats van
  het echte Open Archieven-schema; ze bevestigen dus alleen interne consistentie, niet
  correctheid tegen de echte bron.
- Impact: schendt de kern-AC's "Records/Search v1.1 wordt aangeroepen ... resultaten
  worden gededupliceerd" en met name het letterlijke, verplicht te implementeren
  Nicolaas-voorbeeld (AC "Voor de vraag 'Wie was Nicolaas Jacobus Sinnige...' levert
  Search ... en toont Show ..."), en maakt `supported-answer`/`followed-connection` in
  productie onbereikbaar.
- Reproductie: start de backend met de standaard `hkh.personsearch.archives-base-url`
  (geen override) en dien de vraag "Wie was Nicolaas Jacobus Sinnige, geboren in
  Heemskerk in 1878?" in via `POST /api/personsearch` (of het frontend-startscherm).
  Verwacht: `SUPPORTED_ANSWER` met geboortezin, Vader/Moeder-bronnen. Werkelijk (te
  verwachten op basis van de code-analyse hierboven): `SOURCE_OUTAGE`, want zowel
  `search()` als `show()` retourneren `Failure` tegen het echte, live API-schema.
- Advies aan developer: DTO's aanpassen aan het echte schema (`response.number_found`,
  `response.docs[].identifier`/`archive_code` voor Search; `Person[]`/`Event`/
  `RelationEP[]`/`Source` met de geneste structuur voor Show, inclusief mapping van
  `RelationType`/`PersonKeyRef` naar de rol+naam die de story vereist), en de bestaande
  fixture-tests bijwerken zodat ze het echte schema simuleren in plaats van het huidige
  aangenomen platte schema.
