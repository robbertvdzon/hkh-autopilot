# hkh-autopilot-13 - Worklog

Story-context bij eerste pickup:
Metadata-contract en bronadapter bouwen

Implementeer het fail-closed metadata-contract, de Open Archieven/Noord-Hollands Archief-adapter, privacyredactie, rate limiting, documentatie en alle bijbehorende tests; voer ook een eigen review uit.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Factory-instructies, `development.md`, `technical-spec.md`, de bestaande externe
  verificatieclient en de relevante eerdere story gelezen. De bestaande individuele
  verificatieroute blijft bewust buiten de wijziging.
- `HistoricalMetadataContract` en `HistoricalMetadataResult` toegevoegd met stabiele identifier,
  resolvebare link, holder, titel/beschrijving, datering, bronversie/snapshot, UTC-ophaaldatum,
  gescheiden metadata-/objectmediastatus, privacy-, beschikbaarheids- en verificatiestatus plus
  machineleesbare reden. De validator retourneert bij elke ontbrekende, ongeldige of tegenstrijdige
  waarde alleen een veilige minimale uitkomst.
- `OpenArchievenMetadataAdapter` toegevoegd naast de bestaande client. De adapter leest alleen een
  allowlist uit JSON-LD, herkent lege/ongeldige/uitgevallen bronnen, controleert ETag/
  Last-Modified als actuele bronversie, gebruikt een beschrijvende user-agent en logt of bewaart
  geen payload. Persoonsvelden/persoonsgegevensmarkeringen blokkeren de publieke metadata-uitkomst.
- `FourPerSecondRateLimiter` toegevoegd met servergerichte sleutel en 251 ms minimale tussenruimte.
  Er is geen cache, zodat een volgende bronversie zichtbaar wordt en oude metadata niet stilzwijgend
  wordt hergebruikt. De Spring-configuratie registreert de adapter zonder een nieuwe route of opslag.
- Contract- en adapterdocumentatie bijgewerkt in `docs/development.md` en
  `docs/factory/technical-spec.md`. `.factory/verification.yaml` was al actueel en hoefde niet te
  wijzigen; conflictmarkers zijn niet aangetroffen.
- Nieuwe tests: 3 contracttests en 7 adaptertests voor geldige metadata, onbekende rechten,
  privacyredactie, tijdelijke uitval, lege respons, rate limiting en gewijzigde bronversie.
- Volledig vangnet groen: `(cd backend && mvn -B --no-transfer-progress clean verify)` (266 tests,
  0 failures/errors), `frontend` analyze/test/build web (35 tests, 0 failures), en
  `frontend-admin` analyze/test (36 tests, 0 failures).
- Bewust niet gedaan: publieke zoekroute, opslagmodel, frontendweergave of wijziging aan de bestaande
  individuele verificatieroute; deze vallen volgens de story buiten scope.
