# hkh-autopilot-31 - Worklog

Story-context bij eerste pickup:
Contracttestmatrix voor publieke historische zoekstatussen

Breid backend- en Flutter-contracttests uit met reproduceerbare fixtures/mocks voor geldige, lege, partiële, ongeldige, timeout- en 5xx-scenario's. Controleer statussen, tellingen, bronidentiteit, permanente links, kaartzichtbaarheid en Onbekend-mapping; voer ook zelfreview uit.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `hkh-189` voegt een lokale backend-contractmatrix en synthetische Flutter-matrix toe voor
  geldige, lege, partiële, ongeldige, timeout-, 5xx- en fail-closed metadata-scenario's.
