# hkh-autopilot-20 - Worklog

Story-context bij eerste pickup:
Historische live-status, bronuitval en toegankelijkheidsdekking

Werk frontend, backendstatusvertaling en bestaande tests bij voor één live-regio, gedeeltelijke en volledige bronuitval, retry, zoekopdracht aanpassen, focusbehoud, Heemskerk-metadata-indicatie en privacyveilige status/respons. Sluit af met zelfreview.

Stappenplan:
[ ]: read issue and target docs
[ ]: implement requested changes
[ ]: run relevant tests
[ ]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

Documentatie:
- De publieke frontend-README, de algemene ontwikkelhandleiding en de factory-documentatie zijn
  bijgewerkt met de gerealiseerde één-statusregio, het decoratief uitsluiten van de laadindicator,
  veilige meldingen bij gedeeltelijke/volledige bronuitval en de acties `Opnieuw proberen` en
  `Zoekopdracht aanpassen`.
- Vastgelegd is dat aanpassen op dezelfde route blijft, bestaande zoekwaarden behoudt en doelgericht
  het vrije-tekstveld focust; automatische statusupdates verplaatsen de focus niet. De bestaande
  API-states, bronstatussen, tellingen, rechten- en privacyregels en Heemskerk-metadata-indicatie
  blijven ongewijzigd.
