# hkh-195 - Worklog

Story-context bij eerste pickup:
Canonieke Open Archieven-configuratie en contractdekking

Stappenplan:
[x]: issue, factory-instructies en technische documentatie gelezen
[x]: canonieke configuratie en overlay-pariteit implementeren
[x]: pariteits-, integratie-, smoke- en regressietests schrijven
[x]: gericht testen en volledig vangnet draaien
[x]: story-log bijwerken met resultaten

Done / rationale:
- Story-log aangemaakt aan het begin van de developer-run, zodat plan en verificatie onderdeel zijn van de PR.
- Eén gedeelde `deploy/base` ConfigMap bevat het productie- en acceptancecontract; beide overlays erven deze zonder lokale override.
- De backend leest endpoint-, pad-, parameter-, timeout-, cache-, rate-limit- en budgetwaarden uit de gedeelde ConfigMap.
- `Hkh195OpenArchievenConfigurationContractTest` valideert afzonderlijk endpoint/pad/parameters, feature-instellingen, secretsafety en overlay-erfenis.
- Bestaande lokale fixturetests `Hkh165HistoricalSearchSmokeContractTest` en `Hkh189HistoricalSearchContractTest` dekken integratie, Heemskerk-mapping en de statusmatrix.
- Verificatie: Maven 360 tests groen; frontend analyse/test/build groen (86 tests); frontend-admin analyse/test groen (38 tests).

Review-notities (eerste ronde):
- [blocker] `OpenArchievenSearchAdapter` bouwt met de canonieke `searchPath` `/records/search.json` een URI met een leidende slash. In combinatie met base-URL `https://api.openarchieven.nl/1.1` resolveert Spring dit naar `https://api.openarchieven.nl/records/search.json` en valt het `/1.1`-endpoint weg; een gerichte request-path-test ontbreekt.
- [blocker] `Hkh195OpenArchievenConfigurationContractTest` leest alleen de base-ConfigMap en controleert tekstueel dat de twee overlaybestanden naar `../../base` verwijzen. De test rendert of inspecteert geen effectieve overlay-output en kan dus een overlay-patch/resource via een apart bestand missen; daarmee is de vereiste effectieve pariteitscontrole niet afgedekt.
