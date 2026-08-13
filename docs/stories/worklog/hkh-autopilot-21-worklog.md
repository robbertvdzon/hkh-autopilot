# hkh-autopilot-21 - Worklog

Story-context bij eerste pickup:
Development - Rechtenstatus en toegankelijke uitleg implementeren

Werk backendmapping, resultaatkaart en detailweergave bij; schrijf alle benodigde unit-, contract-, widget- en semantiektests en voer zelfreview uit.

Stappenplan:
[ ]: read issue and target docs
[ ]: implement requested changes
[ ]: run relevant tests
[ ]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## Documenter-run

- [x]: relevante README's en ontwikkelhandleidingen bijgewerkt
- [x]: functionele en technische factory-specificaties bijgewerkt
- [x]: rechtenmapping en toegankelijke uitleg gedocumenteerd

De documentatie beschrijft nu dat de publieke historische zoekroute uitsluitend expliciete
`ALLOWED`/`RESTRICTED`-rechtenwaarden per resultaat herkent, overige gevallen als `UNKNOWN` toont,
het vrije `rights`-veld niet vertaalt en beide onafhankelijke rechtenstatussen met een gedeelde,
toetsenbordbedienbare uitleg in kaart en detailweergave toont.
