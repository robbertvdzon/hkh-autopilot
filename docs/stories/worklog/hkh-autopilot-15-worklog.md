# hkh-autopilot-15 - Worklog

Story-context bij eerste pickup:
Backend en frontend bronstatussen implementeren

Pas backendcontract, service, adapters en controller aan voor geaggregeerde toestanden, beschikbare-resultaten-merging, correcte totalen en bronstatussen. Pas de Flutter-modellen en HistoricalSearchPage aan voor gedeeltelijke resultaten, lege resultaten, volledige bronuitval, retry en één live-regio. Werk binnen deze development-subtaak ook de backendcontracttests en frontendwidget-/semantiektests bij en sluit af met een zelfreview op privacy-, rechten-, metadata- en pagineringsgedrag.

Stappenplan:
[ ]: read issue and target docs
[ ]: implement requested changes
[ ]: run relevant tests
[ ]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

Documentatie-update (2026-08-13):
- De publieke historische zoekroute is bijgewerkt beschreven in de root-README, de frontend-README,
  `docs/development.md` en de factory development-, functionele en technische specs.
- De documentatie legt nu het API-veld `state`, de vier geaggregeerde toestanden, de vier technische
  bronstatussen, beschikbare-resultaten-merging, correcte `total`-bijdragen, veilige bronmeldingen,
  pagineringsherbasering en het onderscheid tussen gedeeltelijke beschikbaarheid en volledige
  bronuitval vast.
- Alleen documentatie is gewijzigd; productiecode en tests zijn ongemoeid gelaten. Er is geen aparte
  changelog of handgeschreven API-documentatie in deze checkout die aanvullend bijgewerkt moest worden.
