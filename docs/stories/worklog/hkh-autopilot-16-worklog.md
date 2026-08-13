# hkh-autopilot-16 - Worklog

Story-context bij eerste pickup:
Historische contextdetailweergave en deterministische relaties

Breid backendcontract, bronadapters, historische zoekservice en Flutter-zoekweergave uit met expliciete contextmetadata, veilige onzekerheidsstatussen, een detailweergave en maximaal drie deterministisch gematchte relaties uit uitsluitend de zichtbare resultatenpagina. Schrijf binnen deze development-subtaak alle backend- en frontendtests en voer een zelfreview uit op bronherkomst, fail-closed rechten/privacy, statussemantiek en het ontbreken van lokale bronopslag.

Stappenplan:
[ ]: read issue and target docs
[ ]: implement requested changes
[ ]: run relevant tests
[ ]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

Documenter 2026-08-13:
- De algemene README's, `docs/development.md`, `frontend/README.md` en de factory-
  development-, functional-spec- en technical-spec-documentatie bijgewerkt met het uitgebreide
  historische zoekcontract.
- De documentatie beschrijft nu plaatsmetadata en de statussen `AVAILABLE`, `MISSING`, `UNCERTAIN`
  en `UNAVAILABLE`, de actie `Context bekijken`, de detailvelden en de expliciete
  `Niet beschikbaar`/`Onzeker`-weergave.
- De relatiegrenzen zijn vastgelegd: alleen de huidige zichtbare resultatenpagina, uitsluiting van
  het geopende resultaat, exacte deterministische contextmatching, maximaal drie relaties en
  periode-overlap uitsluitend als aanvullende informatie.
- Deployment- en secret-documentatie hoefde niet te wijzigen; deze story voegt geen nieuwe runtime-
  configuratie of secret toe.
