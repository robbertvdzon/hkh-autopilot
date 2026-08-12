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
