# SF-2370 - Worklog

Story-context bij eerste pickup:
Plek/gebouw-route: interpreter, backend-module placesearch, en frontend-schermen

Breid frontend/lib/personquery/person_query_interpreter.dart uit met landmark-trefwoordherkenning (kasteel, kerk, molen, toren, gemaal, station, brug, huis, hof, plein, sluis, kapel, klooster) die, direct naast minstens één hoofdletterwoord, voorrang krijgt op de bestaande persoonsherkenning en het losstaande woord 'Heemskerk' verwijdert voor deze tak; bestaande persoonsregels blijven ongewijzigd zonder landmark-trefwoord. Voeg een nieuwe, zelfstandige Spring Modulith-backendmodule nl.vdzon.hkh.placesearch toe (package-info.java, @ApplicationModule(allowedDependencies = {}), toegevoegd aan ModulithArchitectureTest) met synchrone POST /api/place-search binnen een harde 2000ms-totaalbudget: wbsearchentities (language=nl, type=item, limit=5) → per QID Special:EntityData ophalen → filter op P131 (Q9926, evt. één niveau doorverwezen) of P625 binnen een vaste, in code gedocumenteerde bounding-box/puntlijst voor Heemskerk (eigen aanname, geen SPARQL). Hergebruik het fail-closed RestClient-patroon van PersonSearchWikidataContextClient.kt en het beanpatroon van PersonSearchClientConfiguration.kt (eigen base-URL-bean voor commons.wikimedia.org). Bij precies 1 match: bouw antwoordzinnen uit label/description/P571/P149/P84/P1435 met genummerde bronverwijzing (QID, wikidata.org/wiki/{QID}, checkedAt), naar het patroon van PersonSearchAnswer.kt/PersonSearchAnswerBuilder.kt. Bij 0 of >1 match: geen antwoord, bij >1 match kandidaatlabels als verfijningsvoorstel zonder samenvoeging. Voor het gevonden item: P373 (Commons-categorie) of anders P18 gebruiken om live de Commons Action-API te bevragen (generator=categorymembers, gcmtype=file, gcmlimit=12, prop=imageinfo, iiprop=url|extmetadata), gededupliceerd op bestandsnaam tot max. 6 afbeeldingen (url, licentie, bestandspaginalink). Fail-closed foutafhandeling: Wikidata-fout/timeout/ongeldige JSON/ontbrekend verplicht veld ⇒ 'Wikidata is tijdelijk niet geraadpleegd' zonder claims; alleen Commons faalt ⇒ Wikidata-antwoord blijft staan met 'Wikimedia Commons · niet uitgevoerd · afhankelijk van Wikidata'. Kortstondige in-memory TTL-cache per Wikidata-record en Commons-imageinfo-respons met zichtbare checkedAt. Werk het startscherm bij (nieuwe voorbeeldvraag, extra dekkingsbadge) en voeg drie nieuwe schermtoestanden toe (place-answer met bronbox/beeldgalerij/licentiebadges/Context-blok, place-empty met verfijningsvoorstellen, place-outage met retry-actie), elk met desktop- en 320px-mobilevariant, Tab/Shift+Tab/Enter-bedienbaar met zichtbare focusrand en kleuronafhankelijke statusbadges, hergebruik makend van person_query_widgets.dart. Schrijf hierbij ook alle tests: interpreter-unittests (incl. 'Wat is Kasteel Assumburg?' en negatieve gevallen), backend-tests met gemockte Wikidata/Commons-responses (0/1/>1 matches, P131-doorverwijzing, P625-filter, ontbrekende P373-fallback naar P18, 2s-timeout/foutpaden), en widget-/e2e-tests voor alle vier schermtoestanden inclusief toetsenbordbediening en 320px-layout.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Frontend-interpreter (`person_query_interpreter.dart`): landmark-trefwoordherkenning toegevoegd
  (`_landmarkWords`, `_findPlaceCandidate`) op een apart genormaliseerde tekst waarin "Heemskerk"
  onvoorwaardelijk verwijderd wordt (in tegenstelling tot de bestaande naam-normalisatie). Nieuw veld
  `placeCandidate`/`hasPlaceCandidate` op `PersonQueryInterpretation`; krijgt in `person_query_page.dart`
  voorrang op de persoonsroute. Bestaande persoonsregels/tests ongewijzigd. Nieuwe unittests in
  `person_query_interpreter_test.dart` (incl. "Wat is Kasteel Assumburg?", voorrang, negatieve gevallen).
- Backend: nieuwe zelfstandige Spring Modulith-module `nl.vdzon.hkh.placesearch`
  (`allowedDependencies = {}`, toegevoegd aan `ModulithArchitectureTest`) met synchrone
  `POST /api/place-search`, harde 2000ms-deadline via een eigen 2-threads-executor +
  `Future.get(timeout)` (zelfde patroon als `PersonSearchService`, maar zonder jobstore/sessie).
  `PlaceSearchWikidataClient` doet `wbsearchentities` (limit 5) + per QID `Special:EntityData`, filtert
  op P131 (Q9926, één niveau doorverwezen) of P625 binnen een zelf gedocumenteerde bounding box
  (code-commentaar in het bestand, eigen aanname, geen SPARQL). Bij precies 1 match bouwt
  `PlaceSearchAnswerBuilder` genummerde antwoordzinnen uit label/description/P571/P149/P84/P1435, elk
  met een eigen bronverwijzing naar hetzelfde QID; P131-gemeentekoppeling komt als apart
  `contextSentence` terug. Bij 0/>1 match: `NoMatch` met kandidaatlabels (alleen bij >1) als
  verfijningsvoorstel, geen samenvoeging. `PlaceSearchCommonsClient` haalt afbeeldingen op via P373
  (categorie) of anders P18 (los bestand), dedupliceert op bestandsnaam, max. 6. Alles fail-closed:
  elke fout/timeout in de Wikidata-keten of het overschrijden van het budget levert `WikidataOutage`
  op; een losstaande Commons-fout laat het Wikidata-antwoord staan met `commonsOutage=true`.
  `PlaceSearchCache` is een kleine in-memory TTL-cache (5 min) voor zowel entity- als image-lookups.
  Eigen kopie van het gzip-interceptorpatroon (`PlaceSearchGzipSupport.kt`) omdat de module niet op
  `personsearch` mag steunen. `PlaceSearchClientConfiguration` volgt het beanpatroon van
  `PersonSearchClientConfiguration` met een eigen base-URL-bean voor `commons.wikimedia.org`.
- Belangrijke ontwikkeling tijdens de ronde: het toevoegen van een tweede `ExecutorService`-bean
  (`placeSearchExecutor`) naast de bestaande `personSearchExecutor` veroorzaakte een
  `NoUniqueBeanDefinitionException` bij het opstarten van de volledige Spring-context (beide
  constructor-parameters heten `executor`, dus geen automatische naamsdisambiguatie). Opgelost door
  op beide services een expliciete `@Qualifier` op de `executor`-parameter te zetten
  (`PersonSearchService.kt` kreeg er één bij, ook al is dat buiten de `placesearch`-module zelf).
  Verder: RestClient in deze repo gebruikt Jackson 3 (`tools.jackson.*`) voor HTTP-(de)serialisatie,
  niet Jackson 2 (`com.fasterxml.jackson.databind.*`); een `JsonNode`-veld in een claim-DTO moet dus
  `tools.jackson.databind.JsonNode` zijn (de `com.fasterxml.jackson.annotation.*`-annotaties blijven
  wel gewoon werken, dat is een apart, ongewijzigd module).
- Frontend: nieuwe module `frontend/lib/placesearch/` (`place_search_models.dart`,
  `place_search_client.dart` met `PlaceSearchSource`/`PlaceSearchClient`, en de drie schermen
  `place_answer_screen.dart`, `place_empty_screen.dart`, `place_outage_screen.dart`), hergebruikt
  `person_query_widgets.dart` (`PersonQueryStatusMessage`, `personQueryFocusedButtonStyle`,
  breakpoint) voor consistente focusrand/statussemantiek/responsive layout. `person_query_page.dart`
  routeert een herkend `placeCandidate` synchroon naar deze route (geen polling), met een
  `_LazyPlaceSearchClient` naar het bestaande lazy-client-patroon. Startscherm kreeg een derde
  voorbeeldvraag ("Wat is Kasteel Assumburg?") en een tweede dekkingsbadge
  (`_CoverageBadge`-widget, icoon + tekst, nooit kleur-only) naast de bestaande Open
  Archieven-badge; de badge-`Row` moest de labeltekst in een `Flexible` wrappen om
  RenderFlex-overflow op smalle breedtes te voorkomen.
- Testdekking: backend `PlaceSearchServiceTest` (embedded HttpServer-fixtures voor zowel Wikidata als
  Commons: 0/1/>1 matches, P131-doorverwijzing, P625-filter binnen/buiten bounding box, P373- en
  P18-pad, Commons-fout, Wikidata-fout/ongeldige JSON, 2000ms-budgetoverschrijding),
  `PlaceSearchAnswerBuilderTest`, `PlaceSearchCacheTest`, `PlaceSearchControllerTest`. Frontend:
  interpreter-unittests, `place_search_client_test.dart`, `place_screens_test.dart` (per scherm:
  inhoud, Tab/Enter-bediening, 320px zonder overloop) en drie nieuwe routeringstests in
  `person_query_page_test.dart` (landmark-voorrang, place-empty met verfijning, place-outage met
  retry). Volledig vangnet (`backend mvn clean verify`, `frontend`/`frontend-admin` analyze/test/build
  web) draait groen: backend 292/292, frontend 104/104, frontend-admin 22/22.
- Niet gedaan / bewust buiten scope: geen wijziging aan `.factory/verification.yaml` nodig (bestaande
  path-prefix-regels dekken de nieuwe bestanden al af); geen wijziging aan `frontend-admin` (story
  raakt uitsluitend de gebruikersfrontend en de backend); geen SPARQL/Query Service-aanroep conform
  de story-restrictie.

## [REVIEWER] SF-2371 (subtaak van deze story)

Volledige story-diff (`main...HEAD`, commit `b7a5d52`) tegen de story/AC's beoordeeld. Tree-sha van de
factory-verificatie (`212055a1...`) komt overeen met de huidige HEAD-tree; het meegeleverde
`[FACTORY VERIFICATION EVIDENCE]`-blok toont alle vier commando's `passed` (backend 292/292, frontend
104/104, build web groen); `admin-flutter-*` terecht `skipped` (geen wijziging in `frontend-admin`).

Geverifieerd, geen blockers gevonden:
- Interpreter: landmark-herkenning + voorrangsregel + onvoorwaardelijke verwijdering van "Heemskerk"
  precies zoals gespecificeerd; goede unittests inclusief het canonieke voorbeeld, prioriteitsgeval en
  negatieve gevallen (`person_query_interpreter_test.dart`).
- Backend `placesearch`-module: eigen `package-info.java`/`allowedDependencies = {}`, opgenomen in
  `ModulithArchitectureTest`; wbsearchentities → Special:EntityData → P131 (incl. één niveau
  doorverwijzing) / P625-bounding-box-filter, geen SPARQL; fail-closed op elke fout/timeout/budget via
  `Future.get(2000ms)`; P373→P18-fallback voor Commons, dedup op bestandsnaam, max. 6. Uitstekende
  testdekking met embedded `HttpServer`-fixtures (geen echte netwerkaanroepen in de testsuite):
  0/1/>1-matches, P131-doorverwijzing, P625 binnen/buiten box, Commons-fout, Wikidata-fout, timeout.
- `PersonSearchService.kt`-wijziging (alleen de `@Qualifier`-toevoeging) is een noodzakelijke, minimale
  fix voor de door de developer beschreven bean-ambiguïteit; geen ongerelateerde wijzigingen.
- Frontend-schermen (`place-answer`/`place-empty`/`place-outage`) volgen de UX-artifacts qua
  structuur, hergebruiken `person_query_widgets.dart` voor focusrand/statussemantiek, en zijn getest
  op 320px en toetsenbordbediening.
- `.factory/verification.yaml` inderdaad ongewijzigd correct: bestaande `backend/`- en
  `frontend/`-path-prefixes dekken alle nieuwe bestanden.

Aandachtspunten (niet blokkerend, ter info voor eventuele vervolgronde):
- [info] Bij het indienen van een plek/gebouw-vraag (`person_query_page.dart:_submit`) blijft het
  scherm op `start` staan totdat de (max. 2s) synchrone aanroep terugkomt — er is geen tussentijds
  laad-/wachtscherm zoals bij de persoonsroute (`liveSearch`). Niet door de AC's vereist (de route is
  bewust synchroon), maar UX-technisch kan dit tot 2 seconden zonder zichtbare feedback aanvoelen.
- [info] `PlaceSearchWikidataClient.evaluateHeemskerkMatch` haalt de "één-niveau-doorverwezen"
  P131-entiteit op via een rechtstreekse `fetchEntity`-aanroep buiten `PlaceSearchService`s
  `entityCache` om; dat ene opgehaalde record wordt dus niet TTL-gecachet (in tegenstelling tot de
  overige opgehaalde Wikidata-records). Beperkte impact: uitsluitend relevant bij herhaalde identieke
  aanvragen binnen de 5-minuten-TTL.
- [info] De architectuurstijl/architect/erfgoedstatus-labels (P149/P84/P1435) vereisen elk een eigen
  sequentiële Wikidata-round-trip bovenop de kandidaat-opzoeking; dit is al door de developer zelf
  gedocumenteerd als operationeel aandachtspunt (agent-tip `live-external-api-round-trip-budget`) en
  wordt hier bevestigd, geen actie in deze ronde.
- [info] Geen widget-test rendert de `_ImageGallery`/`_ImageTile` met een niet-lege `images`-lijst
  (alle screen-tests in `place_screens_test.dart` gebruiken de default lege lijst); de backend-kant
  van de beeld-pijplijn (dedup/max 6/P373→P18) is wel grondig getest. Niet AC-blokkerend, wel een
  kleine hiaat in de frontend-testdekking van het specifiek genoemde "beeldgalerij met per-afbeelding
  licentiebadge"-onderdeel.

Oordeel: akkoord, geen blockers.

## [TESTER] SF-2372

HEAD (`a2f02d6`) is de door de reviewer beoordeelde commit; de reviewercommit zelf wijzigt
uitsluitend deze worklog (geen codewijziging), dus de eerder groen geverifieerde tree
(`mvn clean verify` backend 292/292, frontend 104/104, `flutter build web` groen,
`frontend-admin` 22/22, `skipped` want ongewijzigd) blijft geldig voor deze HEAD. Het volledige
vangnet wordt hierna nogmaals revisiegebonden door de harness gedraaid; dat zelf herhalen is
dubbel werk, dus deze ronde is gericht op live gedragsverificatie tegen de preview
(`https://hkh-autopilot-pr-59.vdzonsoftware.nl`), rechtstreeks tegen `POST /api/place-search`
(geen browser-/screenshot-tool beschikbaar in deze sandbox, zelfde bekende beperking als eerdere
testrondes op dit project).

Live-verificatie tegen `POST /api/place-search`:
- Canoniek voorbeeld "Kasteel Assumburg" → `status=READY`, `qid=Q1967073`, precies de verwachte
  antwoordzinnen (label/description, P149 architectuurstijl "classicisme", P1435 erfgoedstatus
  "Rijksmonument"), apart `contextSentence` voor P131 ("Assumburg ligt in de gemeente Heemskerk."),
  elk met eigen genummerde bron (QID + wikidata.org-link + checkedAt), en 6 gededupliceerde
  Commons-afbeeldingen met license + bestandspaginalink. Komt exact overeen met de story-AC voor dit
  voorbeeld. Herhaalde aanroep gaf identiek, stabiel resultaat.
- Leeg `candidateTerm` → HTTP 400 met `{"fieldErrors":["candidateTerm"]}` (input-validatie werkt).
- Niet-matchende termen ("Kasteel Nergensland", "Huis Marquette") → `status=NO_MATCH`,
  `answer=null`, fail-closed zoals vereist.
- Generieke, brede termen ("Kerk", "Molen" los) leverden bij herhaling wisselend `NO_MATCH` of
  `OUTAGE` op — verwacht gedrag: dit soort brede termen triggert meerdere live Wikidata
  round-trips (P149/P84/P1435 elk een eigen call, zie reviewer-aandachtspunt) binnen het harde
  2000ms-budget, en overschrijding daarvan valt terecht fail-closed terug op `OUTAGE` zonder
  verzonnen antwoord. Geen bug, analoog aan het bekende personsearch->2s-gedrag bij generieke
  vragen.

Backend-/frontend-testdekking (interpreter-unittests, gemockte 0/1/>1-match/P131-doorverwijzing/
P625/P373→P18/timeout-paden, widget-/e2e-tests voor de vier schermtoestanden) is inhoudelijk
beoordeeld in de reviewerronde hierboven en niet opnieuw herhaald in deze ronde (geen code-/
testwijziging sindsdien). Geen bugs gevonden; live gedrag komt overeen met de story-AC's.

Oordeel: tested, geen blockers.
