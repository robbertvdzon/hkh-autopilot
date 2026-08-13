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

Development follow-up (review blocker):
- De reviewbevinding over tegenstrijdige `number_found`- en `docs`-waarden wordt in deze run
  onderzocht en fail-closed hersteld; beide tegengestelde richtingen krijgen een regressietest.

Done / rationale:
- De story-, factory-, frontend- en backendcontractdocumentatie is gelezen; de bestaande historicalsearch-implementatie en testdekking zijn geïnventariseerd.
- De Open Archieven-adapter gebruikt voor een Heemskerk-query afzonderlijke `name`- en `archive_code=hee`-parameters, behoudt user-agent/rate limiting en maakt transportfouten onderscheidbaar van lege resultaten.
- Open Archieven-records vereisen nu fail-closed de vaste velden `source_name`, `uuid` en `original_source_url`; de genormaliseerde identiteit wordt `source_name`, `stable_identifier` (`hee:uuid`) en `original_source_url` zonder lokaal geconstrueerde URL.
- De publieke backendmapping en Flutter-resultaat-/detailweergave tonen de genormaliseerde bronnaam, volledige identifier en oorspronkelijke link; rechten blijven onafhankelijk en alleen exact `ALLOWED`/`RESTRICTED` wordt herkend.
- Contract- en frontendtests toegevoegd voor de Heemskerk-fixture, requestmapping, lege/ongeldige/transportresponsen, rights mapping en de snake_case-frontendmapping.
- Het volledige vangnet is groen: backend `mvn clean verify` (309 tests), frontend analyze, 68 frontendtests, webbuild, frontend-admin analyze en 35 frontend-admintests. De seriële frontendtest met `--concurrency=1` is ook volledig groen.

Review follow-up:
- `number_found` is nu verplicht, numeriek en niet-negatief. De adapter accepteert alleen een lege
  `docs`-pagina met totaal nul, een niet-lege pagina met een positief totaal dat minstens de
  paginagrootte bevat, en markeert ontbrekende, negatieve, verkeerd getypeerde of tegenstrijdige
  tellingen als `INVALID_RESPONSE`.
- Regressietests dekken beide tegenstrijdige richtingen (`42` met lege `docs` en `0` met een geldig
  document), plus ontbrekende, negatieve en verkeerd getypeerde `number_found`-waarden.
- Verificatie in deze follow-up: backend `mvn clean verify` (311 tests), frontend analyze, 68
  frontendtests, webbuild, frontend-admin analyze en 35 frontend-admintests; alle commando's
  eindigden met exitcode 0 en zonder failures/errors.

Review:
- [blocker] `backend/src/main/kotlin/nl/vdzon/hkh/historicalsearch/HistoricalSearchAdapters.kt:298-300` accepteert tegenstrijdige `response.number_found`-waarden zonder validatie tegen `response.docs`. Reproduceer met `{"response":{"number_found":42,"docs":[]}}`: de adapter geeft `AVAILABLE` met `total=42`; via de service/frontend wordt dan een resultaatcount zichtbaar terwijl er geen resultaat is (`RESULTS`/`42 historische resultaten geladen`). Met `{"response":{"number_found":0,"docs":[{...geldig contractrecord...}]}}` worden de records door `HistoricalSearchService` met `total=0` weggefilterd en ontstaat ten onrechte `NO_RESULTS`. Een ontbrekende/negatieve of anders ongeldig gevormde provider-telling mag evenmin stilzwijgend naar `results.size`/nul terugvallen. Behandel deze tegenstrijdige respons fail-closed als `INVALID_RESPONSE` (of normaliseer aantoonbaar zonder provider-totaal te tonen) en voeg contracttests voor beide richtingen toe.
