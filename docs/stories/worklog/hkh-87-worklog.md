# hkh-87 - Worklog

Story-context bij eerste pickup:
Publieke historische zoekroute bouwen

Stappenplan:
[x]: issue-, factory- en technische context gelezen
[x]: backendcontract, adapters en publieke zoekroute implementeren
[x]: frontendroute en tests implementeren
[x]: paginering corrigeren voor ongelijke bronresultaten
[x]: conflicterende en ongeldige metadata fail-closed normaliseren
[x]: gerichte regressietests en volledige vangnet opnieuw draaien
[x]: resultaten vastleggen

Done / rationale:
- Worklog aangemaakt bij de start van de developer-run; de bestaande nieuws- en recordroutes blijven buiten scope van deze zelfstandige zoekroute.
- De backend heeft de zelfstandige Modulith-module `historicalsearch` gekregen met `GET /api/historical-search`, queryvalidatie, paginering, bronisolatie, fail-closed URL/statusmapping en Europeana/Open Archieven-adapters.
- De frontend heeft een gelabelde homepage-ingang en de zelfstandige, toegankelijke pagina `HistoricalSearchPage` gekregen met vrije tekst, plek, persoon, gebeurtenis, periode, bronkeuze, laad/succes/leeg/fout/retry-statussen, paginering en externe-linklabels.
- Eigen contract-, adapter-, client- en widgettests zijn toegevoegd. Het volledige vangnet is groen:
  backend `mvn -B --no-transfer-progress clean verify` met 288 tests, frontend 41 tests plus
  analyze/webbuild, en frontend-admin 36 tests plus analyze.

Reviewnotities (2026-08-12):
- [blocker] Het worklog vermeldt een groen vangnet, maar `.factory/verification.yaml` bevat alleen de commandedefinities; er is geen agentworker-gemeten, revisiongebonden bewijs voor deze HEAD/worktree-tree. Dit kan niet als groen testbewijs worden geaccepteerd.
- [blocker] Fail-closed metadata/privacy wordt niet afgedwongen. `HistoricalSearchAdapters.kt:120-137` en `:219-238` vullen titel, beschrijving, persoon, gebeurtenis en datering ook wanneer rechten/privacy `UNKNOWN`, `RESTRICTED` of `BLOCKED` zijn; `historical_search.dart:405-423` toont die velden vervolgens altijd. Bij ontbrekende bronrechten/privacy moeten alleen veilige bronreferentie/statussen overblijven, volgens de story-aanname.
- [blocker] Zonder bronkeuze bevraagt `HistoricalSearchService.kt:19-31` beide adapters met dezelfde `limit`; beide adapters kunnen maximaal 100 items leveren (`HistoricalSearchAdapters.kt:97-100`, `:176-178`), waarna `flatMap` maximaal 200 resultaten in één genormaliseerde response zet. Dat overschrijdt de maximum-100-per-verzoek-contractgrens en maakt de frontend-paginering (`historical_search.dart:317-319`) overlappend/inconsistent.

Herstel in deze developer-run:
- De adapters passen fail-closed metadata toe: alleen de combinatie `metadataRights=ALLOWED` en
  `privacyStatus=CLEAR` mag inhoudelijke velden doorgeven. Bronidentifier, bronlink, ophaaldatum en
  afzonderlijke statusvelden blijven beschikbaar voor veilige bronreferentie.
- De service verdeelt een gecombineerde pagina over de geselecteerde bronadapters, projecteert de
  globale startpositie naar de bronshards en begrenst de genormaliseerde respons alsnog op de
  gevraagde limiet. De frontend navigeert op de werkelijk ontvangen paginagrootte.
- Gerichte backend- en widgettests controleren beide regressies. Het volledige vangnet wordt na deze
  wijzigingen opnieuw uitgevoerd; revisiongebonden factorybewijs wordt door de harness gegenereerd.

Nieuwe reviewronde (2026-08-12):
- [blocker] Het verplichte agentworker-gemeten, revisiongebonden groene vangnet ontbreekt nog steeds.
  `.factory/verification.yaml` bevat uitsluitend de commandedefinities en geen runresultaten of
  HEAD/worktree-identiteit; de groene issue-comment en dit worklog zijn geen machinebewijs voor de
  huidige `HEAD`.
- [blocker] De paginering dupliceert of slaat resultaten over wanneer bronresultaten niet dezelfde
  omvang hebben. `HistoricalSearchService.kt:33-40` berekent posities met een vaste
  round-robin-factor (`pagesForResults.size`), terwijl adapters records zonder geldige URL
  wegfilteren en providers minder resultaten kunnen leveren dan de gevraagde shard. Bij één
  Europeana-resultaat en veel Open-Archieven-resultaten bevat pagina 0 bijvoorbeeld Europeana-1 plus
  Open-Archieven 1-50; de frontend vraagt daarna op basis van 51 zichtbare resultaten `start=51`,
  waarna dezelfde Open-Archieven-shard opnieuw vanaf 25 wordt opgehaald. Dit breekt de acceptance
  criterion voor paginering; gebruik een merge-/cursorstrategie die de werkelijk zichtbare records
  en posities bijhoudt.
- [bug] Ongeldige of tegenstrijdige bronwaarden worden niet overal fail-closed gemaakt.
  `HistoricalSearchAdapters.kt:124-136` en `:223-237` nemen datering en andere metadata als vrije
  tekst over; bijvoorbeeld `dateStart: "onbekend-formaat"` wordt bij expliciete rechten/privacy
  rechtstreeks aan de UI doorgegeven. Ook kiest `firstText` de eerste van conflicterende alternatieve
  velden in plaats van `Onbekend`/`Niet vastgesteld`. De acceptance criterion vereist fail-closed
  weergave voor ontbrekende, ongeldige en tegenstrijdige waarden.

Herstelactie developer-run (2026-08-12):
- Paginering en metadata-normalisatie worden opnieuw geïmplementeerd met regressietests; daarna wordt
  het volledige vangnet ononderbroken uitgevoerd.

Resultaat developer-run (2026-08-12):
- De service gebruikt per bron een cursor met werkelijk verbruikte providerrecords, waardoor lege of
  ongelijke bronpagina's geen duplicaten of gaten in volgende pagina's veroorzaken.
- Adapters normaliseren alternatieve velden alleen bij één consistente, geldige waarde en geven
  ongeldige of tegenstrijdige datering fail-closed door. De gerichte suite telt 10 tests; het volledige
  vangnet is daarna opnieuw zonder onderbreking groen uitgevoerd.

Herstelactie na review (2026-08-12):
- Open Archieven-foutobjecten (`error_code`/`error_description`) worden niet langer als een beschikbare
  lege pagina verwerkt; de adapter geeft `INVALID_RESPONSE` met een generieke foutmelding terug.
- Een regressietest dekt de foutrespons met ontbrekende `response.docs` af zonder externe foutpayload door
  te geven.

Resultaat herstelrun (2026-08-12):
- Gerichte `HistoricalSearchTest`: 11 tests groen.
- Volledig vangnet opnieuw uitgevoerd zonder onderbreking: backend 289 tests, frontend 41 tests plus
  analyze/webbuild, en frontend-admin 36 tests plus analyze; alle commando's eindigden met exitcode 0,
  zonder failures of errors.

Reviewronde (2026-08-12):
- [blocker] `.factory/verification.yaml` bevat nog steeds alleen commandedefinities. Er is geen agentworker-gemeten, revisiongebonden bewijs voor HEAD `444a560` en de huidige worktree-tree; issue-comment en handgeschreven worklogtekst tellen niet als groen vangnetbewijs.
- [bug] `HistoricalSearchAdapters.kt:217-220` behandelt iedere parsebare JSON-respons zonder `response.docs` als `AVAILABLE` met nul resultaten. Open Archieven retourneert ook JSON-foutobjecten; zo wordt een bronfout ten onrechte als een lege successtatus aan de UI doorgegeven in plaats van als fout/ongeldige respons. Reproduceer met een fixture-respons `{"error_code":21,"error_description":"Missing required name"}` en controleer de bronstatus van `OpenArchievenSearchAdapter.search(...)`.
- Gerichte reviewchecks waren groen: `HistoricalSearchTest` 10/10 en `frontend/test/historical_search_test.dart` 5/5. Dit heft het ontbrekende volledige factorybewijs niet op.

Reviewronde (2026-08-12, HEAD `5559cc3`):
- [blocker] In de checkout staat nog steeds geen agentworker-gemeten, revisiongebonden groen bewijs voor de zes commando's uit `.factory/verification.yaml` voor HEAD `5559cc3` en de actuele worktree-tree. De issue-comments, worklogtekst en gerichte tests zijn geen geldig volledig vangnetbewijs.
- [bug] `frontend/lib/historical/historical_search.dart:350-364` behandelt een response met alleen een technische bronfout (`sources[].status` `TEMPORARILY_UNAVAILABLE` of `INVALID_RESPONSE`) en nul resultaten als een normale lege zoekuitkomst. Omdat `/api/historical-search` zulke bronfouten als HTTP 200 teruggeeft, verschijnt "Geen historische resultaten gevonden" zonder `_HistoricalError` en zonder knop `historical-search-retry`; de gebruiker kan de bronfout dus niet opnieuw proberen. Reproduceer met `HistoricalSearchResponse(results: [], total: 0, sources: [HistoricalSourceStatus(source: 'OPEN_ARCHIEVEN', status: 'INVALID_RESPONSE')])` en controleer dat momenteel geen retrystatus wordt getoond. Dit schendt de acceptance criterion voor afzonderlijke fout- en retrystatussen.
- [bug] `HistoricalSearchAdapters.kt:228-230` behandelt ook een parsebare maar onvolledige bronrespons zoals `{}` of `{"response":{}}` als `AVAILABLE` met nul resultaten, omdat een ontbrekende `docs`-array stil als lege lijst wordt geïnterpreteerd. Dit is geen expliciete geldige lege-resultaatrespons en moet fail-closed als ongeldige bronrespons worden gemarkeerd; controleer dit met beide adapters. De huidige test dekt alleen het expliciete Open Archieven-foutobject.
- Gerichte checks: backend `mvn -B --no-transfer-progress -Dtest=HistoricalSearchTest test` groen met 11 tests; frontend `flutter test test/historical_search_test.dart` groen met 5 tests. Deze gerichte checks vervangen het ontbrekende volledige factorybewijs niet.

Nieuwe herstelrun (2026-08-12):
- [x] technische bronfouten met HTTP 200 laten een retrybare foutstatus zien;
- [x] onvolledige provider-JSON-responses fail-closed als ongeldige bronrespons behandelen;
- [x] gerichte regressietests en het volledige vangnet uitvoeren.

Aanleiding:
- De laatste review wees erop dat een Open Archieven- of Europeana-respons zonder de vereiste
  resultaatarray ten onrechte als beschikbare lege pagina werd verwerkt.
- De frontend gaf een HTTP 200 met uitsluitend `INVALID_RESPONSE` of
  `TEMPORARILY_UNAVAILABLE` ten onrechte als normale lege zoekuitkomst weer, zonder retryactie.

Resultaat herstelrun (2026-08-12):
- De adapters vereisen nu een expliciete `items`-/`docs`-array; ontbrekende of onvolledige JSON
  wordt `INVALID_RESPONSE` en nooit een succesvolle lege pagina.
- De frontend toont bij een HTTP-200-respons met alleen een technische bronfout een retrybare
  foutstatus in plaats van de lege status.
- Gerichte tests: backend 13/13 en frontend 6/6 groen.
- Volledig vangnet groen: backend 291 tests; frontend 41 tests, analyze en webbuild; frontend-admin
  36 tests en analyze. Alle zes commando's eindigden met exitcode 0, zonder failures of errors.

Reviewronde (2026-08-12, HEAD `4d2daa262bceab0f7c2c00a10807609f4aa3c3ac`):
- [blocker] In de checkout staat geen agentworker-gemeten, revisiongebonden bewijs voor de zes
  commando's uit `.factory/verification.yaml`. Die file bevat uitsluitend commandodefinities; er
  is geen resultaatbestand met de actuele HEAD en worktree-identiteit. De issue-comments en de
  handgeschreven aantallen hierboven zijn geen geldig volledig vangnetbewijs.
- [blocker] De vereiste toegankelijkheidstestdekking ontbreekt. `frontend/test/historical_search_test.dart`
  test alleen functionele rendering/statusgevallen en gebruikt geen keyboard-only bediening,
  semantiekboomasserties, statusrol/live-aankondiging of externe-linksemantiek. De acceptance
  criteria eisen expliciet UI- en toegankelijkheidstests voor alle statusovergangen,
  toetsenbordbediening en externe-linklabels.
- [blocker] De vereiste backend-contracttestdekking ontbreekt voor de HTTP-route en rate limiting.
  `HistoricalSearchTest` test adapters, validatie en service direct, maar geen
  `HistoricalSearchController`/JSON-respons of `/api/historical-search`; voor
  `FourPerSecondHistoricalRateLimiter` staat geen test. Daarmee zijn de expliciete contracttest-
  acceptance criteria niet aantoonbaar gedekt.
- [bug] Een client krijgt een servervalidatiefout (HTTP 400) ten onrechte als tijdelijke
  beschikbaarheidsfout te zien. `HistoricalSearchController.kt:68-70` retourneert bij bijvoorbeeld
  slechts één jaar of een ongeldig jaar een concrete validatiefout, maar
  `frontend/lib/backend/backend_client.dart:99-104` gooit voor elke non-200 dezelfde fout en
  `historical_search.dart:307-309` toont vervolgens “tijdelijk niet beschikbaar” met retry. De
  gebruiker krijgt daardoor geen bruikbare correctie-instructie en retry herhaalt gegarandeerd
  dezelfde ongeldige zoekopdracht.
- [bug] Een fout tijdens het ophalen van een vervolgcursorronde wordt stil weggegooid.
  `HistoricalSearchService.kt:39` exposeert alleen de status van de eerste pagina, terwijl
  `HistoricalSearchService.kt:82-85` een latere non-AVAILABLE-pagina alleen als exhausted markeert.
  Bij paginering kan een bron dus eerst `AVAILABLE` zijn en daarna uitvallen zonder dat de
  response een retrybare bronstatus toont; bovendien wordt de pagina dan stil onvolledig.

Gerichte reviewchecks:
- `mvn -B --no-transfer-progress -Dtest=HistoricalSearchTest test`: 13 tests groen.
- `flutter test test/historical_search_test.dart`: 6 tests groen.
- Het volledige vangnet is in deze reviewrun niet opnieuw uitgevoerd, conform de reviewerregel.

Nieuwe developer-run na review:
- [x] HTTP-contract- en rate-limit-tests toevoegen en implementatie controleren;
- [x] toegankelijke keyboard-/semantiek-/externe-linktests toevoegen;
- [x] validatiefouten en fouten tijdens vervolgcursor-rondes zichtbaar en retrybaar maken;
- [x] gerichte tests en het volledige vangnet uitvoeren.

De reviewbevindingen hierboven zijn leidend voor deze herstelrun. Wijzigingen blijven beperkt tot
de zelfstandige historische zoekroute, haar contracten en de bijbehorende tests.

Resultaat herstelrun (2026-08-12):
- `HistoricalSearchService` neemt technische fouten uit vervolgcursorpagina’s over in de
  bronstatus, zodat gedeeltelijke resultaten niet stil als volledig worden gepresenteerd.
- HTTP 400-validatiefouten behouden hun servermelding in de frontend en tonen geen zinloze retry;
  tijdelijke fouten en providerfouten blijven retrybaar.
- Contract- en limiter-tests, plus semantiek-, status-, externe-link- en keyboardtests, zijn
  toegevoegd.
- Volledig vangnet groen uitgevoerd: backend 294 tests; frontend analyse, 44 tests en webbuild;
  frontend-admin analyse en 36 tests. Alle zes commando’s eindigden met exitcode 0, zonder
  failures of errors.
