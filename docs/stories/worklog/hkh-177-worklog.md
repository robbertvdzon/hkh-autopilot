# hkh-177 - Worklog

Story-context bij eerste pickup:
Serverzijdig statuscontract, beheerflow en geautomatiseerde dekking

Werk backendstatuscontract, auth-afgeschermde adminflow, frontend-admin-weergave, tests en zelfreview uit.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Factory-instructies, taakcontext en technische ontwikkelrichtlijnen gelezen.
- Dit worklog aangemaakt voor issue hkh-177; het bestaande parent-worklog is niet aangepast.
- Het serverzijdige admin-statuscontract, de auth-afgeschermde historische adminroute en de
  Flutter-beheerweergave zijn toegevoegd. Alleen brongeleverde, veilige identiteit wordt teruggegeven;
  statussen en leesbare redenen worden deterministisch uit het bestaande contract afgeleid.
- Gerichte backendtests (10 tests) en Flutter-admin tests (3 tests) zijn groen uitgevoerd.
- Het volledige factory-vangnet is groen: backend `mvn -B --no-transfer-progress clean verify`
  (350 tests), frontend analyze/test (79 tests)/webbuild en frontend-admin analyze/test (38 tests),
  allemaal met exitcode 0 en zonder failures/errors.
- Zelfreview tegen de acceptance criteria uitgevoerd: ontbrekende waarden blijven UNKNOWN, expliciet
  beperkte of ongeldige waarden worden REJECTED, NOT_APPLICABLE is representeerbaar, object-/media-
  rechten blijven gescheiden en publieke vrijgave vereist uitsluitend de expliciete bevestigingen en
  veilige bronidentiteit.

## Review

- [blocker] `HistoricalAdminStatusContract` controleert `stableIdentifier` en
  `originalSourceUrl` alleen afzonderlijk op syntactische veiligheid. De bestaande
  genormaliseerde Open Archieven-contractcontrole vereist daarnaast dat deze velden gelijk zijn aan
  respectievelijk `sourceRecordId` en `stableUrl`. Bij bijvoorbeeld `stableIdentifier = hee:record-2`
  naast `sourceRecordId = hee:record-1` (of een afwijkende permanente URL) worden de bron- en
  vrijgavestatus toch `CONFIRMED` en worden de tegenstrijdige waarden geretourneerd. Dit schendt de
  fail-closed acceptance voor tegenstrijdige bronmetadata; voeg de consistentiecontrole serverzijdig
  toe en dek beide mismatch-richtingen met controller/statuscontracttests.
- Gerichte backendcontrole uitgevoerd: `AdminHistoricalSearchControllerTest` en
  `HistoricalAdminStatusContractTest` samen 10 tests groen; het verplichte factory-vangnet staat in
  de task-context als revisiongebonden groen bewijs.
