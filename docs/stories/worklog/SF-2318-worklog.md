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
