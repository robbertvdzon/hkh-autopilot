# SF-2318 - Live persoonszoekopdracht uitvoeren en binnen twee seconden een onderbouwd antwoord tonen

## Story

Live persoonszoekopdracht uitvoeren en binnen twee seconden een onderbouwd antwoord tonen

<!-- refined-by-factory -->

## Scope
Implementeer de live zoek- en antwoordroute die start zodra de (in SF-2311 opgeleverde) vraaginterpretatie een herkende persoonsnaam heeft opgeleverd, inclusief eventuele tweede naam, gebeurtenistype, jaar/periode en gekozen Heemskerk-betekenis.

**Job en idempotentie**
- Bij indiening van een ondersteunde vraag ontstaat precies één job met een cryptografisch random, niet-raadbare job-id, gekoppeld aan een serverside sessie.
- Aanname: er bestaat nog geen sessieconcept voor anonieme bezoekers in de repo. Deze story introduceert een minimale, aan deze route gebonden serverside sessie (bv. een server-uitgegeven, niet-raadbare sessiecookie zonder login), uitsluitend gebruikt om jobs aan te binden en te scheiden tussen bezoekers. Dit is geen uitbreiding van het bestaande admin/Google-authenticatiemechanisme.
- Idempotentiesleutel = sessie-id + genormaliseerde vraagtekst + gekozen Heemskerk-betekenis (indien van toepassing). Een herhaalde indiening met dezelfde sleutel terwijl de job nog niet terminaal is, retourneert dezelfde job-id zonder nieuwe bronraadpleging.
- Buiten scope van deze story (volgt in de vervolgstory): statuspolling-API, hervatten na navigatie/reload, sessie-indicator met aantallen, en de versleutelde bewaartermijnen (60 min inactiviteit / 24 uur hard) met opschoning. Deze story hoeft dus geen persistente, versleutelde jobopslag met TTL te bouwen; een job die het 2-seconden-budget overschrijdt mag in-process doorlopen tot een terminale status, maar er is geen vereiste UI of contract om die later op te halen.

**Synchrone uitvoering (max. 2 seconden)**
- Start direct na jobcreatie de Records/Search-aanroep en de eventueel benodigde Wikidata-contextaanroep.
- Wacht binnen hetzelfde webrequest maximaal 2000 ms op een terminale, volledig gevalideerde uitkomst.
- Binnen budget terminaal: toon direct het passende scherm (`supported-answer`, de no-reliable-source-variant bij nul resultaten, of `source-outage`) zonder `background-search`/`search-ready` als verplichte tussenstap.
- Niet terminaal na 2 seconden: het webrequest retourneert (aanname: met een status die de frontend laat weten dat de opdracht nog loopt, zonder dat deze story er een UI-vervolg aan hoeft te geven); dezelfde job blijft onafhankelijk van dit request doorlopen.

**Open Archieven Records/Search**
- `GET https://api.openarchieven.nl/1.1/records/search.json` met URL-gecodeerde `name` (herkende naam + optioneel tweede naam en jaar/periode), `archive_code=nha`, `eventplace=Heemskerk`, `lang=nl`, `number_show=100`, `start` voor paginering (begin bij 0).
- Beschrijvende User-Agent-header, gzip aangevraagd, maximaal 4 requests/seconde (procesbrede limiter), korte time-outs, begrensde eindige back-off bij transiënte fouten (geen onbegrensde retries).
- Validatie: HTTP 2xx, geldige JSON, verplichte velden (o.a. `number_found`, `results`) aanwezig, en géén gevuld `error_code` (ook niet bij HTTP 200). Elke afwijking = mislukte bronraadpleging.
- Dedupliceer op `archive_code` + `identifier`.

**Meer dan 100 resultaten**
- Bij `number_found > 100` claimt geen enkele route een volledige uitkomst; job eindigt met status `PARTIAL`, gebruiker krijgt een verfijningsverzoek (naam aanvullen, periode of gebeurtenistype opgeven); geen Records/Show-aanroep.

**Open Archieven Records/Show**
- Voor ieder daadwerkelijk getoond kandidaatrecord (na deduplicatie, binnen `number_show=100`): `GET https://api.openarchieven.nl/1.1/records/show.json` met `archive=nha`, `identifier=<record-identifier>`, `lang=nl`. Zelfde validatieregels als Search.
- Alleen `Person`, `Event`, `RelationEP` en `Source` uit een gevalideerd Show-record mogen een feitelijke antwoordzin dragen; zonder geldig, live opgehaald Show-record verschijnt geen archiefbewering voor dat record.

**Bronmarkering**
- Iedere feitelijke zin krijgt direct erachter een genummerde bronmarkering. Het bronitem toont beherende instelling, brontype, archief-, register-, akte-/documentnummer en recordnummer/identifier, plus actielinks naar Open Archieven (`https://www.openarchieven.nl/{archive_code}:{identifier}`) en, indien aanwezig in het Show-record, naar `SourceDigitalOriginal`. Elk getoond record toont `checkedAt` (tijdstip van live ophalen in déze job).

**Gecontroleerd voorbeeld (letterlijk te implementeren en testen)**
Voor 'Wie was Nicolaas Jacobus Sinnige, geboren in Heemskerk in 1878?': geen meaning-selection (voorzetsel 'in' direct vóór 'Heemskerk'). Records/Search levert exact één match op met `archive_code=nha`, `identifier=002ED0F3-F08C-4223-A5EA-BA385D04336E`. Records/Show toont geboorte op 25 juli 1878 in Heemskerk, Pieter Sinnige als Vader, Anna Geertruida Eenhuis als Moeder. Het antwoord vermeldt expliciet en zichtbaar dat deze ene geboorteakte geen volledig levensverhaal is en geen overzicht van alle gebeurtenissen in Heemskerk in 1878.

**Vervolgspoor (`followed-connection`)**
- Vanuit `supported-answer` kan de bezoeker een rol/persoon uit hetzelfde gevalideerde Show-record volgen (bv. 'Vader' Pieter Sinnige); opent een detailweergave die de oorspronkelijke vraag en het gekozen vervolgspoor zichtbaar houdt en expliciet vermeldt dat een bronrol geen volledig levensverhaal van die persoon is.
- Maximaal twee vervolgsporen per antwoord, beide binnen de gevalideerde Open Archieven-persoonsdekking (aanname: dit zijn de rollen met een gekoppelde persoonsnaam in `RelationEP` van hetzelfde Show-record, in de volgorde waarin ze in dat record voorkomen; geen extra externe aanroep nodig, want de gegevens komen al uit het reeds gevalideerde Show-record).

**Wikidata als context**
- Op `supported-answer` en `source-outage` verschijnt Wikidata-informatie uitsluitend onder een sectie die letterlijk 'Context' heet; deze sectie draagt nooit zelfstandig een geboorte-, huwelijks-, overlijdens-, doop- of bevolkingsregistratiebewering.

**Bronuitval (`source-outage`)**
- Faalt de voor een antwoord vereiste Records/Search- of Records/Show-aanroep volgens de validatieregels, dan wordt Open Archieven exact aangeduid als 'tijdelijk niet geraadpleegd'; geen enkele archiefbewering verschijnt, ook niet wanneer Wikidata wel bereikbaar was (Wikidata-inhoud dan uitsluitend onder 'Context').

**Toegankelijkheid en UX**
- `live-search`, `supported-answer`, `followed-connection` en `source-outage` zijn bedienbaar met Tab/Shift+Tab/Enter, met zichtbare focus; live-/gereed-/Context-/uitvalstatus is zonder kleur begrijpelijk.
- Voor elk van deze vier schermen bestaat precies één DESKTOP- en één MOBILE-artifact (aangeleverd via Product Factory-attachments); bij 320 CSS-pixels is `document.scrollWidth == document.clientWidth` en blijven alle inhoud en acties bereikbaar.
- De aangeleverde UX-modellen zijn richtinggevend qua hoofdstructuur, informatiehiërarchie, toestanden en flow; geen pixel-perfecte kopie vereist.

**Buiten scope**
- Statuspolling-API, hervatten na navigatie/reload/terugkeer, sessie-indicator met live aantallen, versleutelde opslag met retentie/opschoning (60 min/24 uur), CANCELLED/EXPIRED-afhandeling, en de overige vijf hoofdroute-schermen (`background-search`, `search-ready`, `meaning-selection`, `start`, `no-reliable-source` zijn al deels opgeleverd of horen bij een andere story). Dit alles volgt in de vervolgstory 'Achtergrondopdracht laten doorlopen, sessiestatus tonen, hervatten en na afloop opschonen'.
- Agent Runtime als uitvoeringsadapter is niet vereist voor deze story.

## Acceptance criteria
- Bij het indienen van een ondersteunde vraag ontstaat precies één niet-raadbare, sessiegebonden job-id met idempotente submitsemantiek; een herhaalde indiening met dezelfde idempotentiesleutel terwijl de job nog loopt start geen tweede bronraadpleging.
- De route start de live Records/Search- en benodigde Wikidata-aanroepen direct na het aanmaken van de job en wacht binnen hetzelfde webrequest maximaal twee seconden; bij een volledige, gevalideerde uitkomst binnen dat budget verschijnt het passende scherm zonder `background-search`/`search-ready` als verplichte tussenstap.
- Records/Search v1.1 wordt aangeroepen met `archive_code=nha`, `eventplace=Heemskerk`, `lang=nl`, `number_show` maximaal 100, een URL-gecodeerde `name`-waarde en `start` voor paginering; resultaten worden gededupliceerd op `archive_code` + `identifier`, met beschrijvende User-Agent, gzip en een limiet van vier requests per seconde.
- Niet-2xx-statussen, time-outs, ongeldige JSON, ontbrekende verplichte velden en een gevuld `error_code` (ook bij HTTP 200) worden altijd als mislukte bronraadpleging behandeld, nooit als geldig resultaat.
- Wanneer Records/Search `number_found > 100` teruggeeft, claimt geen enkele route een volledige uitkomst; de bezoeker krijgt een verfijningsverzoek (naam, periode of gebeurtenistype); er wordt geen Records/Show uitgevoerd.
- Voor ieder daadwerkelijk getoond kandidaatrecord wordt Records/Show v1.1 live aangeroepen; een feitelijke antwoordzin gebruikt uitsluitend `Person`-, `Event`-, `RelationEP`- en `Source`-gegevens uit dat gevalideerde Show-record.
- Voor de vraag 'Wie was Nicolaas Jacobus Sinnige, geboren in Heemskerk in 1878?' levert Search exact `archive_code=nha` met `identifier=002ED0F3-F08C-4223-A5EA-BA385D04336E`, en toont Show een geboorte op 25 juli 1878 in Heemskerk met Pieter Sinnige als Vader en Anna Geertruida Eenhuis als Moeder, zonder meaning-selection.
- Het Nicolaas-antwoord vermeldt zichtbaar dat één geboorteakte geen volledig levensverhaal en geen overzicht van alle gebeurtenissen in Heemskerk in 1878 is.
- Iedere feitelijke zin heeft direct erachter een genummerde bronmarkering met beherende instelling, brontype, archief-, register-, akte-/documentnummer en recordnummer, plus links naar Open Archieven en, indien aanwezig, `SourceDigitalOriginal`; elk record toont `checkedAt`.
- Vanuit `supported-answer` kan een rol uit hetzelfde bronrecord gevolgd worden (`followed-connection`) met behoud van oorspronkelijke vraag en spoor, met expliciete vermelding dat een bronrol geen levensverhaal is, en met hoogstens twee vervolgsporen binnen de gevalideerde dekking.
- Wikidata-informatie verschijnt uitsluitend onder een sectie die letterlijk 'Context' heet en draagt nooit zelfstandig een geboorte-, huwelijks-, overlijdens-, doop- of bevolkingsregistratiebewering.
- Bij een mislukte Records/Search- of Records/Show-aanroep voor een vereist record verschijnt `source-outage` met Open Archieven exact aangeduid als 'tijdelijk niet geraadpleegd' en zonder enige archiefbewering, ook niet wanneer Wikidata wel bereikbaar is.
- `live-search`, `supported-answer`, `followed-connection` en `source-outage` zijn bedienbaar met Tab, Shift+Tab en Enter, met zichtbare focus en kleuronafhankelijke statusweergave; voor elk van deze vier schermen bestaat exact één DESKTOP- en één MOBILE-artifact die bij 320 CSS-pixels zonder horizontaal scrollen volledig bruikbaar zijn.
- De backend introduceert een minimale, aan deze route gebonden serverside sessie voor anonieme bezoekers (geen login), uitsluitend gebruikt om jobs en idempotentiesleutels aan te binden; een andere sessie kan de job-id niet aanspreken.

## Aannames
- Er bestaat nog geen serverside sessieconcept voor anonieme bezoekers in de repo (het bestaande auth-domein is admin/Google-tokengebaseerd). Deze story introduceert een lichte, cookie-gebaseerde sessie specifiek voor deze route, zonder login en zonder de bestaande admin-authenticatie te wijzigen.
- Versleutelde persistente jobopslag met retentie (60 min inactiviteit / 24 uur hard) en opschoning bij verwijderen/annuleren/verlopen is expliciet onderdeel van de vervolgstory en wordt hier niet gebouwd; een job die het 2-seconden-budget overschrijdt mag in-process (in-memory) doorlopen, zonder dat er een contract is om de uitkomst later op te halen.
- De rate limit van 4 requests/seconde geldt procesbreed (niet per sessie), consistent met eerdere, vergelijkbare externe-API-integraties in deze repo.
- De maximaal twee vervolgsporen op `followed-connection` zijn de rollen met een gekoppelde persoonsnaam in `RelationEP` van hetzelfde reeds gevalideerde Show-record, in de volgorde waarin ze in dat record voorkomen; er wordt geen extra externe aanroep gedaan om vervolgsporen te bepalen.
- Wanneer de job na 2 seconden niet terminaal is, retourneert het webrequest zonder dat deze story een vervolgscherm (`background-search`) hoeft te tonen; de frontend van deze story hoeft alleen te weten dat er (nog) geen antwoord is, niet hoe de bezoeker dat later ophaalt.
- De acht aangeleverde PNG-artifacts (desktop + mobile voor `live-search`, `supported-answer`, `followed-connection`, `source-outage`) zijn de UX-referentie voor deze story; ze zijn inhoudelijk consistent met de storytekst gecontroleerd.

Product-Factory-Api-Version: 2
Product-Factory-Product-Id: hkh-autopilot
Product-Factory-Source-Story-Id: d2ae22ba-9629-4249-b23f-fe4194b51d18
Product-Factory-Source-Story-Version: 1
Product-Factory-Idempotency-Key: product-factory:hkh-autopilot:story:d2ae22ba-9629-4249-b23f-fe4194b51d18:v1
Product-Factory-Package-Sha256: 623a5a2c1c2650ca78c0e7a8a4d1e2e76d8744d7a19841f7556f805743570486

<!-- test-feedback:start -->
## Test-feedback
{"agent_tips_update":[{"category":"testing","key":"personsearch-openarchieven-schema-mismatch","content":"Backend personsearch module (SF-2318/SF-2320) parses Open Archieven Records/Search and Records/Show with a flat, self-invented JSON schema (top-level number_found/results; lowercase flat person/event/relationEP/source) that does NOT match the real public api.openarchieven.nl/1.1 response shape (Search wraps number_found/docs under a 'response' object; Show returns capitalized, deeply nested Person[]/Event/RelationEP[]/Source with PersonKeyRef/RelationType style relations). Verified live via curl against the real API for the story's own Nicolaas Jacobus Sinnige example: the real search correctly returns exactly one nha match with the expected identifier, but the app's DTOs would parse number_found/results (and person/event/etc.) as null and fail-closed to source-outage, since the fixture-based backend tests simulate the same wrong schema instead of the real one. When testing any future story that touches ArchivesOpenSearchClient/ArchivesOpenSearchModels, don't trust green fixture tests alone — cross-check field names against a live curl call to api.openarchieven.nl."}]}

{"phase":"test-rejected"}
<!-- test-feedback:end -->

## Eindsamenvatting

## Eindsamenvatting SF-2318 — Live persoonszoekopdracht uitvoeren en binnen twee seconden een onderbouwd antwoord tonen

**Gebouwd**
- Nieuwe backendmodule `nl.vdzon.hkh.personsearch`: lichte, aan deze route gebonden sessiecookie voor anonieme bezoekers (los van bestaande admin/Google-auth), job-creatie met cryptografisch random job-id, idempotentie op sessie-id + genormaliseerde vraag + Heemskerk-betekenis (atomair via `createIfAbsent`, race-condition op dubbele indiening opgelost na reviewbevinding), synchrone uitvoering met harde 2000ms-deadline zonder de achtergrondtaak te annuleren.
- `ArchivesOpenSearchClient` voor Open Archieven Records/Search en Records/Show met exacte queryparameters, beschrijvende User-Agent, gzip, max. 4 req/s, timeouts, begrensde back-off en fail-closed validatie op HTTP-status/JSON/verplichte velden/`error_code`; deduplicatie op `archive_code`+`identifier`; `number_found>100` → verfijningsverzoek zonder Records/Show.
- Antwoordopbouw uitsluitend uit gevalideerde Show-records (`Person`/`Event`/`RelationEP`/`Source`) met genummerde bronmarkeringen (incl. `checkedAt` en links), max. twee vervolgsporen (`followed-connection`, volledig client-side uit dezelfde payload), en een Wikidata-`Context`-sectie die nooit zelfstandig een archiefbewering draagt.
- Vier nieuwe Flutter-schermen (`live-search`, `supported-answer`, `followed-connection`, `source-outage`) met desktop- en mobile-uitwerking, en `person_query_page.dart` uitgebreid om jobs in te dienen en op resultaattype door te schakelen.
- Volledig backend- en Flutter-testpakket, inclusief het verplichte Nicolaas Jacobus Sinnige-voorbeeld end-to-end.

**Belangrijke ontwikkeling tijdens de ronde**
- Eerste testronde (SF-2320) werd **rejected**: de DTO's voor Open Archieven Records/Search en Records/Show waren gemodelleerd naar een zelfbedacht plat schema in plaats van het echte, live geverifieerde schema van `api.openarchieven.nl/1.1` (Search nest onder `response.number_found`/`response.docs`; Show gebruikt hoofdlettergevoelige, diep geneste `Person[]`/`Event`/`RelationEP[]`/`Source`). Hierdoor faalde elke productieaanroep altijd naar `source-outage`, ook het verplichte Nicolaas-voorbeeld, terwijl alle fixture-tests toch groen waren omdat ze hetzelfde onjuiste schema simuleerden.
- Developer heeft de DTO's en de mapping herschreven naar het echte schema en dit live met `curl` tegen de publieke API geverifieerd. Reviewer heeft dit onafhankelijk herverifieerd. Tweede testronde (SF-2320) is **tested/goedgekeurd**, met eigen onafhankelijke live-curl-herverificatie van zowel Search als Show tegen de echte API.

**Getest**
- Backend `mvn clean verify`: 237 tests, 0 failures. Frontend `flutter analyze`/`flutter test` (56/56, incl. concurrency-run) /`flutter build web`. Frontend-admin `flutter analyze`/`flutter test` (22/22). Live-curl-verificatie van het Nicolaas-voorbeeld tegen de echte externe API (niet alleen fixtures).

**Bewust niet gedaan** (expliciet buiten scope in de storytekst, volgt in vervolgstory): statuspolling-API, hervatten na navigatie/reload, sessie-indicator met live aantallen, versleutelde opslag met retentie/opschoning (60 min/24u), CANCELLED/EXPIRED-afhandeling, Agent Runtime als uitvoeringsadapter.

**Opmerking factory-proces**: de rolinstructies in `.task.md` vragen om af te sluiten met `{"phase":"summary-finished"}`, terwijl het opdrachtcontract in de systeemprompt `{"phase":"summarized", ...}` voorschrijft. Conform een eerdere agent-tip hierover volg ik het opdrachtcontract en meld het verschil hier expliciet.
