# hkh-autopilot-4 - Worklog

Story-context bij eerste pickup:
Implementeer privacyclassificatie-module en statusweergave

Bouw de nieuwe, zelfstandige Spring Modulith-backendmodule nl.vdzon.hkh.privacyclassification (package-info.java met @ApplicationModule(allowedDependencies = {}), opgenomen in ModulithArchitectureTest): domeinmodel GenealogicalRecord (overlijdensstatus als ruwe String? via parse omgezet naar Overleden/Levend/Onbekend, fail-closed naar Onbekend; benoemde velden die een levende nabestaande identificeren), PrivacyClassificationResult (status Processable/Blocked + verplichte, niet-lege leesbare reden) en PrivacyClassifier (Processable alleen bij Overleden zonder gezet nabestaande-veld; alle overige gevallen Blocked met reden, o.a. exact 'Bevat gegevens van levende nabestaande' bij een gedetecteerd nabestaande-veld; evaluatie in runCatching voor fail-closed gedrag bij onverwachte fouten). Voeg PrivacyPublishGuard toe: weigert publicatie (exception of expliciet geweigerd resultaat) bij Blocked, staat toe bij Processable, als losstaande herbruikbare functie zonder koppeling aan een bestaande publicatieworkflow. Schrijf unit tests (PrivacyClassifierTest, PrivacyPublishGuardTest) die alle acceptatiecriteria dekken: overleden zonder nabestaande-velden -> Processable; overleden met minimaal 3 verschillende fixture-varianten voor nabestaande-velden -> Blocked met exacte reden; ontbrekende/niet-herkende/onbekend/levend status -> Blocked (fail-closed default, inclusief volledig ontbrekend statusveld); niet-lege tekstuele reden voor zowel Processable als elke Blocked-variant; publish-guard weigert voor elk Blocked-record en staat toe voor elk Processable-record. Bouw in frontend-admin/lib/ een nieuwe statusweergave-widget die de classificatiestatus toont met zowel tekstlabel als icoon (nooit uitsluitend kleur), met vaste kleurwaarden die een contrastratio van minimaal 4.5:1 halen, volgens de bestaande Semantics-/statusconventies uit technical-spec.md. Schrijf een Flutter-widgettest die de semantiekboom controleert op aanwezigheid van tekstlabel en icoon voor beide statussen, plus een gerichte kleur-/contrasttest die de contrastratio van de gebruikte kleurwaarden berekent en verifieert (≥4.5:1), als vervanging van axe-core conform de bestaande repo-conventie. Werk tot slot docs/factory/technical-spec.md en development.md bij met de nieuwe module, classificatieregel, publish-guard en frontend-admin statusweergave, analoog aan de bestaande secties over linkdossier/recordintake.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Nieuwe backendmodule `nl.vdzon.hkh.privacyclassification` toegevoegd (`package-info.java` met
  `allowedDependencies = {}`), opgenomen in `ModulithArchitectureTest`. Domeinmodel
  `GenealogicalRecord`/`LivingNextOfKinFields` met fail-closed `DeceasedStatus.parse` (ontbrekend/
  niet-herkend -> `ONBEKEND`), `PrivacyClassificationResult`/`PrivacyClassificationStatus` met
  verplichte niet-lege `reason`, `PrivacyClassifier.classify` (alleen `Overleden` zonder
  nabestaande-veld -> `Processable`; alle overige gevallen `Blocked` met reden, inclusief exact
  `"Bevat gegevens van levende nabestaande"`; evaluatie in `runCatching` voor fail-closed gedrag) en
  `PrivacyPublishGuard.assertPublishable` (gooit `PrivacyPublishBlockedException` bij `Blocked`,
  staat toe bij `Processable`).
- Unit tests toegevoegd: `PrivacyClassifierTest` (overleden zonder nabestaande-velden ->
  Processable; 3 nabestaande-veldvarianten -> Blocked met exacte reden; ontbrekende/lege/onbekende/
  levende status -> Blocked; niet-herkende status -> fail-closed Blocked) en
  `PrivacyPublishGuardTest` (guard weigert voor Blocked, staat toe voor Processable).
- Frontend-admin: nieuwe widget `PrivacyClassificationStatusView`
  (`frontend-admin/lib/privacyclassification/privacy_classification_status_view.dart`) toont de
  classificatiestatus met tekstlabel én icoon (nooit uitsluitend kleur); vaste kleuren
  `PrivacyClassificationStatusColors` halen 7.87:1 (`processableForeground`) resp. 6.57:1
  (`blockedForeground`) contrast tegen wit. Widgettest
  `test/privacy_classification_status_view_test.dart` controleert de semantiekboom op aanwezigheid
  van tekstlabel én icoon voor beide statussen en berekent de contrastratio volgens de WCAG
  2.1-formule (≥4.5:1) als vervanging van axe-core.
- `docs/factory/technical-spec.md` (nieuwe sectie `Backendmodule privacyclassification`) en
  `docs/factory/development.md` (repositorystructuur) bijgewerkt met de nieuwe module,
  classificatieregel, publish-guard en frontend-admin statusweergave.
- Volledig vangnet gedraaid: `mvn -B --no-transfer-progress clean verify` (backend), `flutter
  analyze`/`flutter test`/`flutter build web` (frontend), `flutter analyze`/`flutter test`
  (frontend-admin) — alle groen (zie run hieronder).
