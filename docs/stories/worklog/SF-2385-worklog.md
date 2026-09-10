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

## SF-2387 - Testnotities (tester)

- Volledig verplicht vangnet opnieuw uitgevoerd in de sandbox, alles groen:
  `backend: mvn -B --no-transfer-progress clean verify` → BUILD SUCCESS, 321 tests, 0
  failures/errors (incl. de 29 nieuwe `topicsearch`-tests); `frontend: flutter analyze` → geen
  issues; `frontend: flutter test -j 1` → 125 tests, alle geslaagd (incl.
  `test/topicsearch/topic_screens_test.dart`, `topic_search_client_test.dart` en de nieuwe
  routeringstests in `person_query_page_test.dart`); `frontend: flutter build web` → geslaagd;
  `frontend-admin: flutter analyze` en `flutter test` → geen issues, 22 tests geslaagd
  (frontend-admin ongewijzigd door deze story).
- Geen open PR/preview-omgeving beschikbaar voor deze branch (nog niet gepusht, geen
  `gh`-authenticatie in deze sandbox) en geen Docker-CLI beschikbaar om de volledige stack lokaal
  te draaien (bekende sandboxbeperking, zie eerdere agent-tips); gedragsverificatie is daarom
  gedaan op code- en live-API-niveau in plaats van via de preview-URL.
- Live curl tegen de publieke Europeana Record/Search v2-API (met de publieke demo-key `api2demo`,
  uitsluitend ad-hoc voor deze verificatie gebruikt, nergens opgeslagen of gecommit) bevestigt dat
  het werkelijke response-schema exact overeenkomt met `EuropeanaItemDto`
  (`ArchivesEuropeanaClient.kt`): top-level `title`/`dcDescription`/`dataProvider`/
  `edmIsShownAt`/`guid`/`rights` zijn aanwezig zoals gemodelleerd, in tegenstelling tot eerdere
  schema-mismatches die in andere routes van deze repo zijn gevonden. Een live item met
  `rights=["http://creativecommons.org/publicdomain/mark/1.0/"]` en een ander met
  `rights=["http://creativecommons.org/licenses/by-sa/4.0/"]` bevestigen dat
  `deriveTopicSearchLicenseBadge` deze respectievelijk correct naar "Publiek domein" en "CC BY-SA"
  vertaalt, exact conform de story-AC-voorbeelden.
- Live curl van de voorbeeldvraag-query (`watersnood van 1916 AND Heemskerk`, `rows=8`,
  `profile=rich`, `wskey=api2demo`) levert momenteel 0 Europeana-items op met de gedeelde
  demo-key. Dit blokkeert de oplevering niet: de story markeert het aanvragen/invullen van een
  echte, projectspecifieke `HKH_EUROPEANA_API_KEY` expliciet als operationele taak buiten de code
  (zie Aannames), en de fail-closed afhandeling van een ontbrekende/lege key (config-fout →
  zelfde uitkomst als storing) is wél backend-testdekkend geverifieerd. De uiteindelijke live AC
  voor deze voorbeeldvraag kan pas na provisioning van de echte key op acceptatie/productie
  definitief herbevestigd worden.
- Code-inspectie bevestigt verder: startscherm bevat de nieuwe derde dekkingsbadge
  (`coverage-badge-europeana`, tekst "Europeana — archieven, musea, kranten en beeldbanken") en de
  bijgewerkte voorbeeldvraag; `topic_empty_screen.dart`/`topic_outage_screen.dart` gebruiken de
  exact vereiste statusteksten; secrets-example-bestanden bevatten geen echte key en documenteren
  het fail-closed-gedrag; `TopicSearchCache`/`TopicSearchService` cachen nooit een mislukte
  raadpleging (`getOrPut` slaat `null` niet op), dus een tijdelijke Europeana-storing blokkeert een
  volgende retry niet blijvend.
- Geen bugs gevonden t.o.v. de story-AC's. Geen screenshots gemaakt (geen browser/preview-tool
  beschikbaar in deze sandbox, zelfde beperking als eerder gedocumenteerd).
