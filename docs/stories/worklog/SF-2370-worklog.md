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
