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

Reviewronde (2026-08-12):
- [blocker] `.factory/verification.yaml` bevat nog steeds alleen commandedefinities. Er is geen agentworker-gemeten, revisiongebonden bewijs voor HEAD `444a560` en de huidige worktree-tree; issue-comment en handgeschreven worklogtekst tellen niet als groen vangnetbewijs.
- [bug] `HistoricalSearchAdapters.kt:217-220` behandelt iedere parsebare JSON-respons zonder `response.docs` als `AVAILABLE` met nul resultaten. Open Archieven retourneert ook JSON-foutobjecten; zo wordt een bronfout ten onrechte als een lege successtatus aan de UI doorgegeven in plaats van als fout/ongeldige respons. Reproduceer met een fixture-respons `{"error_code":21,"error_description":"Missing required name"}` en controleer de bronstatus van `OpenArchievenSearchAdapter.search(...)`.
- Gerichte reviewchecks waren groen: `HistoricalSearchTest` 10/10 en `frontend/test/historical_search_test.dart` 5/5. Dit heft het ontbrekende volledige factorybewijs niet op.
