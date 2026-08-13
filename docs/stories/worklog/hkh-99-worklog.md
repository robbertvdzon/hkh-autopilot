# hkh-99 - Worklog

Story-context bij eerste pickup:
Historische contextdetailweergave en deterministische relaties

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- `.task.md`, de developer-instructies, development-/technical-spec en bestaande historische zoekcode zijn gelezen.
- De bestaande historische zoekroute blijft de bron voor de zichtbare resultatenpagina; contextrelaties worden daarop begrensd.
- Het historische resultaatcontract bevat nu expliciete plaatsmetadata en per contextveld de status `AVAILABLE`, `MISSING`, `UNCERTAIN` of `UNAVAILABLE`; adapters herkennen ontbrekende en tegenstrijdige bronwaarden en behouden de bestaande fail-closed rechten/privacyregels.
- `HistoricalSearchRelations` vergelijkt uitsluitend zekere plaats-, persoons- en gebeurtenisvelden na trimmen, Unicode-normalisatie, witruimte-normalisatie en hoofdletterongevoelige vergelijking. Het geopende resultaat wordt uitgesloten, periode-overlap is alleen aanvullende informatie en de uitkomst is begrensd op drie resultaten in zichtbare volgorde.
- De Flutter-resultaatkaart heeft de actie `Context bekijken`; de detailpagina toont context-, bron-, status-, rechten- en privacymetadata met expliciete `Niet beschikbaar`-/`Onzeker`-labels en links naar uitsluitend de aangeleverde stabiele bron-URI.
- Eigen backendcontracttests en Flutter-widget-/relationtests dekken de contextstatussen, exacte relaties, periode-overlap, maximum drie, detailactie, bronstatussen en toetsenbord-/semantiekgedrag.
- Volledig vangnet groen: `mvn -B --no-transfer-progress clean verify` (300 tests), frontend analyze/tests/webbuild (51 tests) en frontend-admin analyze/tests (35 tests), alle exitcode 0.
