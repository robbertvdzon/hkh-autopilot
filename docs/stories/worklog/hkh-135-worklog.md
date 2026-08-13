# hkh-135 - Worklog

Story-context bij eerste pickup:
Open Archieven-contract en zoekweergave ontwikkelen

Werk de bestaande historicalsearch-adapter, publieke mapping, Flutter-weergave en alle benodigde contracttests fail-closed uit, inclusief archive_code, metadatarechten, rate limiting en payloadveiligheid.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: run volledige vangnet
[x]: update story-log with results

Done / rationale:
- De story-, factory-, frontend- en backendcontractdocumentatie is gelezen; de bestaande historicalsearch-implementatie en testdekking zijn geïnventariseerd.
- De Open Archieven-adapter gebruikt voor een Heemskerk-query afzonderlijke `name`- en `archive_code=hee`-parameters, behoudt user-agent/rate limiting en maakt transportfouten onderscheidbaar van lege resultaten.
- Open Archieven-records vereisen nu fail-closed de vaste velden `source_name`, `uuid` en `original_source_url`; de genormaliseerde identiteit wordt `source_name`, `stable_identifier` (`hee:uuid`) en `original_source_url` zonder lokaal geconstrueerde URL.
- De publieke backendmapping en Flutter-resultaat-/detailweergave tonen de genormaliseerde bronnaam, volledige identifier en oorspronkelijke link; rechten blijven onafhankelijk en alleen exact `ALLOWED`/`RESTRICTED` wordt herkend.
- Contract- en frontendtests toegevoegd voor de Heemskerk-fixture, requestmapping, lege/ongeldige/transportresponsen, rights mapping en de snake_case-frontendmapping.
- Het volledige vangnet is groen: backend `mvn clean verify` (309 tests), frontend analyze, 68 frontendtests, webbuild, frontend-admin analyze en 35 frontend-admintests. De seriële frontendtest met `--concurrency=1` is ook volledig groen.
