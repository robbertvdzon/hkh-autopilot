# hkh-autopilot-9 - Worklog

Story-context bij eerste pickup:
Entiteiten en zoekfilter op het bestaande /api/news-contract

Voeg een statische gazetteer (config-resource per entiteitstype plek/persoon/gebeurtenis met canonicalLabel+aliases) en een deterministische, case-insensitive whole-word matcher toe in nl.vdzon.hkh.news. Wijzig GET /api/news (LatestNewsController, geen nieuwe route/controller) naar een responsobject {items, total, entities} met optionele queryparameters q (vrije tekst op titel/samenvatting) en entity (filter op canonicalLabel), AND-combineerbaar; items bevatten per bericht hun gededupliceerde, gesorteerde entiteitenlijst, top-level entities is de geaggregeerde telling over alle berichten in latest_news. Geen statuskolom, geen wijziging aan POST /api/admin/news, geen aanraking van recordintake/privacyclassificatie/externalverification. Werk frontend/lib/news/latest_news.dart en frontend/lib/backend/backend_client.dart bij op het nieuwe contract. Schrijf hierbij ook alle tests: gazetteer-unit tests (dedup/sortering/geen-match), een contracttest die bevestigt dat het aantal routes/controllers in nl.vdzon.hkh.news.api ongewijzigd blijft plus een OpenAPI-schema-snapshotvergelijking (springdoc, al in pom.xml) als build-documentatie, integratietests voor het q-/entity-filtergedrag inclusief lege-resultaat-AC (HTTP 200, total=0) en voor uitsluiting van een niet via de admin-API aangemaakt (niet-'gepubliceerd') teststorebericht, een domeinisolatiecheck dat geen code uit de uitgesloten modules wordt aangeraakt, en een Flutter-test voor het bijgewerkte responscontract.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Statische gazetteer toegevoegd in `nl.vdzon.hkh.news.entity`: `NewsEntityType` (PLEK, PERSOON,
  GEBEURTENIS), `NewsGazetteer`/`GazetteerEntry`/`NewsGazetteerLoader` (leest
  `gazetteer/plek.json`, `gazetteer/persoon.json`, `gazetteer/gebeurtenis.json` van het classpath,
  5-6 entries per type, canonicalLabel + aliases, opgebouwd rond bestaande seed-data zoals
  "Kerkweg" en "Slot Assumburg" uit `PreviewDataSeeder`). `NewsEntityMatcher` matcht deterministisch,
  hoofdletterongevoelig, diakrieten-genormaliseerd en op heel woord, met dedup op canonicalLabel en
  sortering op type dan eerste voorkomen in tekst — geen NLP/NER-fallback.
- `LatestNewsService.search(q, entity)` combineert store, matcher en filters (AND) en levert
  `NewsSearchResult` (items + entities per item, total, top-level `entities` = geaggregeerde telling
  over alle berichten in `latest_news`, ongeacht filter). `LatestNewsController` (`GET /api/news`,
  zelfde route/controller) accepteert optionele `q`/`entity`-queryparameters en retourneert
  `{items, total, entities}`; elk item krijgt een `source`-bronvermelding
  ("Afkomstig uit gepubliceerd HKH-nieuwsbericht, gepubliceerd op ..."). `POST /api/admin/news`
  (create-flow, response-DTO) is ongewijzigd gelaten, geen statuskolom toegevoegd.
- Belangrijke Jackson-valkuil: deze repo gebruikt Jackson 3 (`tools.jackson.*`, via
  `tools.jackson.module:jackson-module-kotlin` in pom.xml), niet `com.fasterxml.jackson.databind`.
  `com.fasterxml.jackson.databind.ObjectMapper` autowiren geeft `NoSuchBeanDefinitionException` in
  een `@SpringBootTest`; gebruik `tools.jackson.databind.ObjectMapper`. Verder heeft
  `tools.jackson.databind.JsonNode` een eigen member `<R> R map(Function<JsonNode,R>)` die Kotlins
  `Iterable<JsonNode>.map { }`-extensie verbergt (silent fallback resulteert in `Set<Char>` i.p.v.
  `Set<String>`); gebruik `node.asIterable().map { ... }` om de Kotlin-extensie te forceren.
  `fieldNames()` bestaat niet meer op `JsonNode`, gebruik `propertyNames(): Collection<String>`.
- Tests (allen in `backend/src/test/kotlin/nl/vdzon/hkh/news/`):
  `entity/NewsEntityMatcherTest.kt` (unit, dekt geen-match, hoofdletter/diakrieten, heel-woord,
  dedup, sortering per type/eerste-voorkomen, title+message samen). `api/LatestNewsApiIntegrationTest.kt`
  uitgebreid met: vrije-tekstzoekterm + niet-lege `source`, entiteitsfilter met correct type-label,
  leeg resultaat (HTTP 200, total=0) voor zowel `q` als `entity`, een test die bevestigt dat de
  response nooit recordintake/privacyclassificatie/externalverification-achtige velden bevat
  (domeinisolatiecheck op responsniveau), een test die een bericht rechtstreeks in een aparte
  JDBC-tabel (niet `latest_news`) plaatst en bewijst dat dit nooit in entiteiten/zoekresultaten
  verschijnt, een routecontracttest (`RequestMappingHandlerMapping`) die bevestigt dat de
  news-module nog steeds precies 2 controllers/2 routes registreert, en een OpenAPI-schema-test
  (`/v3/api-docs` via springdoc) die de gegenereerde schemavelden van
  `LatestNewsListResponse`/`LatestNewsItemResponse`/`AggregatedNewsEntityResponse` en de
  queryparameters vastlegt als build-documentatie. Bestaande tests in dat bestand aangepast naar het
  nieuwe `{items, total, ...}`-responsformaat; de volgorde-test gebruikt nu een unieke zoekmarker
  omdat de testcontainer-DB tussen testmethodes binnen deze klasse niet wordt leeggemaakt.
- Frontend: `frontend/lib/news/latest_news.dart` kreeg `NewsEntity` en `LatestNewsItem.entities`
  (default leeg) + `source` (optioneel), zodat bestaande UI-code ongewijzigd blijft.
  `frontend/lib/backend/backend_client.dart#loadLatestNews` parset nu `{items: [...]}` i.p.v. een
  kale array. `frontend/test/backend_client_test.dart` bijgewerkt naar het nieuwe contract.
  `frontend-admin` raakt `GET /api/news` niet aan (alleen `POST /api/admin/news` via
  `AdminLatestNewsClient`), dus daar was geen wijziging nodig.
- Vangnet: `mvn -f backend/pom.xml clean verify`, `flutter analyze`/`flutter test`/`flutter build web`
  (frontend), `flutter analyze`/`flutter test` (frontend-admin) — alle groen, zie testresultaten
  hieronder.

## Review-notities (hkh-55)

- Diff (`main...HEAD`, commit `abf6b54`) beoordeeld tegen de story-AC's, `technical-spec.md` en
  `ModulithArchitectureTest`. Alle AC's zijn concreet gedekt door tests: routecontracttest (2
  controllers/2 routes ongewijzigd), OpenAPI-schema-test (`LatestNewsListResponse`/
  `LatestNewsItemResponse`/`AggregatedNewsEntityResponse` + `q`/`entity`-parameters), gazetteer-unit
  tests (geen-match, hoofdletter/diakrieten, heel-woord, dedup, sortering per type/eerste-voorkomen),
  integratietests voor vrije tekst + bronvermelding, entiteitsfilter met typelabel, leeg resultaat
  (HTTP 200, total=0) voor zowel `q` als `entity`, domeinisolatie op responsniveau
  (recordintake/privacyclassificatie/externalverification-achtige velden afwezig) en de
  niet-via-`POST /api/admin/news`-fixture (aparte JDBC-tabel) die nooit in entiteiten/zoekresultaten
  verschijnt. `nl.vdzon.hkh.news.entity` volgt hetzelfde subpackage-patroon als het bestaande
  `nl.vdzon.hkh.news.api` (geen eigen `package-info.java`, blijft binnen de `news`-module), dus
  `ModulithArchitectureTest` blijft van toepassing zonder wijziging. Frontend (`latest_news.dart`,
  `backend_client.dart`) en de bijbehorende test zijn correct bijgewerkt op `{items, total,
  entities}`; `frontend-admin` terecht ongewijzigd (roept alleen `POST /api/admin/news` aan). De
  bestaande `PreviewDataApiIntegrationTest` is terecht als boyscout-fix aangepast aan het nieuwe
  responsformaat.
- [suggestie] `LatestNewsService.findAll()` (`LatestNewsService.kt:21`) wordt sinds deze wijziging
  nergens meer aangeroepen (de controller gebruikt nu uitsluitend `search`); dode code, geen
  blocker.
- Geen bugs, regressies of scope-overschrijding gevonden. Geoordeeld: akkoord.
