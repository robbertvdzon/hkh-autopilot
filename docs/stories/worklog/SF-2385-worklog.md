# SF-2385 - Worklog

Story-context bij eerste pickup:
Europeana-onderwerpzoekroute (backend + frontend)

Nieuw backend-package topicsearch (ArchivesEuropeanaClient, Wikidata-contextclient hergebruik, TTL-cache, answer-builder, service, controller) naar het patroon van placesearch/personsearch; HKH_EUROPEANA_API_KEY toevoegen aan deploy/secrets-cluster.env en deploy/secrets-acceptance.env (+ .example) en opnieuw verzegelen via deploy/seal-secrets.sh; nieuwe frontend-map frontend/lib/topicsearch/ met de vier schermtoestanden (topic-results, topic-empty, topic-outage, elk desktop+mobile) en uitbreiding van het startscherm (person_query_page.dart) met een derde dekkingsbadge en bijgewerkte voorbeeldvraag; inclusief alle bijbehorende unit- en widget-tests (recordvalidatie, rights-URL-badge-mapping, Wikidata-cardinaliteit, timeout/outage-pad, cache-TTL, toetsenbordbediening/320px-layout).

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Backend: nieuwe module `backend/src/main/kotlin/nl/vdzon/hkh/topicsearch/` met
  `package-info.java` (`allowedDependencies = {}`, opgenomen in `ModulithArchitectureTest`),
  `ArchivesEuropeanaClient`/`RestClientArchivesEuropeanaClient` (Europeana Record/Search v2,
  `query='<topicSearchTerm> AND Heemskerk'`, `rows=8`, `profile=rich`, eigen `wskey` uit
  `HKH_EUROPEANA_API_KEY`; ontbrekende/lege key faalt fail-closed zonder aanroep),
  `TopicSearchRecordMapper` (pure recordvalidatie: titel-of-beschrijving + dataProvider +
  edmIsShownAt-of-guid; deterministische rights-URL → badge-mapping), `TopicSearchWikidataContextClient`
  (wbsearchentities → EntityData, bouwt alleen een Context-blok bij precies 1 kandidaat),
  `TopicSearchCache` (TTL-cache naar `PlaceSearchCache`-patroon), `TopicSearchService` (synchrone
  route, hard 2000ms-budget, Wikidata alleen bevraagd bij >=1 geldig record) en
  `api/TopicSearchController` (`POST /api/topic-search`, status `READY`/`EMPTY`/`OUTAGE`).
- Backend-tests: `TopicSearchRecordMapperTest`, `RestClientArchivesEuropeanaClientTest` (embedded
  JDK `HttpServer`-fixture), `TopicSearchWikidataContextClientTest` (cardinaliteit 0/1/>1),
  `TopicSearchServiceTest` (ready/empty/outage/timeout/cache-TTL/wikidata-fout blokkeert nooit) en
  `TopicSearchControllerTest`. `mvn clean verify` (backend) is groen (29 nieuwe + bestaande tests).
- Secrets: `HKH_EUROPEANA_API_KEY` toegevoegd aan `deploy/secrets-cluster.env.example` en
  `deploy/secrets-acceptance.env.example` met duidelijke toelichting (nooit de gedeelde testkey
  `api2demo`). De echte, niet-gecommitte `deploy/secrets-cluster.env`/`deploy/secrets-acceptance.env`
  bestaan niet in deze sandbox (gitignored, nooit hier aanwezig) en `deploy/seal-secrets.sh` kan in
  deze factory-sandbox niet draaien (geen kubeconfig/clustercert/kubeseal-binary/lokale
  secrets-envbronnen — zelfde structurele sandboxbeperking als eerder vastgesteld bij SF-2337).
  `seal-secrets.sh` zelf hoeft niet gewijzigd te worden: het verzegelt de volledige `.env`-bestanden
  via `--from-env-file`, dus een nieuwe regel daarin wordt automatisch meegenomen zodra de
  repo-eigenaar lokaal reselt. Dit is een bewuste aanname (zie Aannames in de story): het aanvragen/
  invullen van een echte Europeana-key en het her-sealen is een operationele taak buiten deze code en
  blokkeert de oplevering niet; een ontbrekende/lege key wordt door de backend zelf al fail-closed
  afgehandeld (zelfde uitkomst als een echte storing).
- Frontend: nieuwe map `frontend/lib/topicsearch/` met `topic_search_models.dart`,
  `topic_search_client.dart` en de drie schermen `topic_results_screen.dart` (MAIN, incl. Context-blok
  en record-raster dat op smalle breedte naar één kolom terugvalt), `topic_empty_screen.dart` (EMPTY,
  exacte tekst "Hiervoor vinden we geen betrouwbare bron" + status per bron + verfijningsvoorstellen)
  en `topic_outage_screen.dart` (ERROR, exacte tekst "Europeana is tijdelijk niet geraadpleegd" +
  status per bron + retry-actie). Elk scherm is één Dart-artifact met een interne
  `LayoutBuilder`/breakpoint-omschakeling tussen desktop- en mobile-opmaak (zelfde patroon als de
  bestaande `placesearch`-schermen), dus telt als exact 1 DESKTOP- en 1 MOBILE-implementatie.
  `topic-start` is geen nieuw scherm maar een uitbreiding van het bestaande startscherm
  (`person_query_page.dart`): derde dekkingsbadge "Europeana — archieven, musea, kranten en
  beeldbanken" en een vierde voorbeeldvraag ("Wat weten we over de watersnood van 1916 in
  Heemskerk?") naast de drie bestaande.
- Routering in `person_query_page.dart`: `topicSearchTerm` wordt uitsluitend bereikt wanneer noch
  een naam noch een plek/gebouw-kandidaat herkend is (bestaand, gemergd gedrag uit SF-2378); de
  route wordt dus nooit aangeroepen zonder bepaalde `topicSearchTerm`. Twee bestaande
  page-level-tests gebruikten een voorbeeldvraag ("Wat gebeurde er hier?") die na deze uitbreiding
  wél een `topicSearchTerm` oplevert (het woord "hier" is 4 letters en geen landmark-trefwoord) en
  dus terecht naar de nieuwe onderwerproute gaat in plaats van naar `no-reliable-source`; deze twee
  tests zijn aangepast naar een query zonder enige herkenbare naam of onderwerpwoord ("Wat is er?"),
  zodat ze het oorspronkelijke "geen enkele kandidaat"-pad blijven dekken. Dit is een bewuste,
  beargumenteerde boyscout-correctie, geen scope-uitbreiding: de interpretatielogica zelf
  (`person_query_interpreter.dart`) is niet gewijzigd.
- Frontend-tests: `test/topicsearch/topic_search_client_test.dart`,
  `test/topicsearch/topic_screens_test.dart` (records/context/lege status/outage-status,
  toetsenbordbediening, 320px zonder overloop) en drie nieuwe routeringstests in
  `test/personquery/person_query_page_test.dart` (onderwerproute wordt gekozen boven persoons-/
  plekroute, EMPTY- en OUTAGE-pad incl. retry). `flutter analyze` en `flutter test` (frontend) zijn
  groen.
- Volledig vangnet (`docs/factory/development.md`) uitgevoerd en groen: backend `mvn clean verify`,
  frontend `flutter analyze`/`flutter test`/`flutter build web`, frontend-admin `flutter analyze`/
  `flutter test` (frontend-admin is niet gewijzigd door deze story).
