# hkh-autopilot-23 - Worklog

Story-context bij eerste pickup:
Open Archieven-statuscontract en veilige zoekweergave

Breid het backendstatuscontract en de Open Archieven-adapter uit met TIMEOUT, HTTP_ERROR, INVALID_JSON en MISSING_REQUIRED_FIELDS. Behoud AVAILABLE voor geldig nulresultaat, veilige bronmeldingen, geldige metadata, gedeeltelijke resultaten en fail-closed payloadbescherming. Werk de bestaande Flutter-statusmapping bij en schrijf alle deterministische backend- en frontendtests; voer ook zelfreview uit.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- De documentatie is bijgewerkt in de root- en frontend-README's, de Engelse
  ontwikkelhandleiding, de factory functional/technical specs en de dubbele factory
  development-handleiding.
- De huidige documentatie beschrijft nu de Open Archieven-statussen `TIMEOUT`, `HTTP_ERROR`,
  `INVALID_JSON` en `MISSING_REQUIRED_FIELDS`, de vaste veilige frontendmeldingen, het geldige
  nulresultaat (`AVAILABLE`) en het niet tonen van bron- of exceptiondetails bij bronuitval.
