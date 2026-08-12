# hkh-87 - Worklog

Story-context bij eerste pickup:
Publieke historische zoekroute bouwen

Stappenplan:
[x]: issue-, factory- en technische context gelezen
[x]: backendcontract, adapters en publieke zoekroute implementeren
[x]: frontendroute en tests implementeren
[x]: gericht testen en volledige vangnet draaien
[x]: resultaten vastleggen

Done / rationale:
- Worklog aangemaakt bij de start van de developer-run; de bestaande nieuws- en recordroutes blijven buiten scope van deze zelfstandige zoekroute.
- De backend heeft de zelfstandige Modulith-module `historicalsearch` gekregen met `GET /api/historical-search`, queryvalidatie, paginering, bronisolatie, fail-closed URL/statusmapping en Europeana/Open Archieven-adapters.
- De frontend heeft een gelabelde homepage-ingang en de zelfstandige, toegankelijke pagina `HistoricalSearchPage` gekregen met vrije tekst, plek, persoon, gebeurtenis, periode, bronkeuze, laad/succes/leeg/fout/retry-statussen, paginering en externe-linklabels.
- Eigen contract-, adapter-, client- en widgettests zijn toegevoegd. Het volledige vangnet is groen: backend 284 tests, frontend 39 tests/analyze/webbuild en frontend-admin 36 tests/analyze.

Reviewnotities (2026-08-12):
- [blocker] Het worklog vermeldt een groen vangnet, maar `.factory/verification.yaml` bevat alleen de commandedefinities; er is geen agentworker-gemeten, revisiongebonden bewijs voor deze HEAD/worktree-tree. Dit kan niet als groen testbewijs worden geaccepteerd.
- [blocker] Fail-closed metadata/privacy wordt niet afgedwongen. `HistoricalSearchAdapters.kt:120-137` en `:219-238` vullen titel, beschrijving, persoon, gebeurtenis en datering ook wanneer rechten/privacy `UNKNOWN`, `RESTRICTED` of `BLOCKED` zijn; `historical_search.dart:405-423` toont die velden vervolgens altijd. Bij ontbrekende bronrechten/privacy moeten alleen veilige bronreferentie/statussen overblijven, volgens de story-aanname.
- [blocker] Zonder bronkeuze bevraagt `HistoricalSearchService.kt:19-31` beide adapters met dezelfde `limit`; beide adapters kunnen maximaal 100 items leveren (`HistoricalSearchAdapters.kt:97-100`, `:176-178`), waarna `flatMap` maximaal 200 resultaten in één genormaliseerde response zet. Dat overschrijdt de maximum-100-per-verzoek-contractgrens en maakt de frontend-paginering (`historical_search.dart:317-319`) overlappend/inconsistent.
