# hkh-autopilot-8 - Worklog

Story-context bij eerste pickup:
GEDCOM RESN-signaal toevoegen aan privacyclassification

Voeg in nl.vdzon.hkh.privacyclassification een optioneel gedcomSource:String? veld toe aan GenealogicalRecord; implementeer een nieuwe recursieve GEDCOM RESN-parser/signaal (GedcomResnSignal: BLOCKED/NONE/NOT_APPLICABLE) die record- en feit-/gebeurtenisniveau doorzoekt op RESN CONFIDENTIAL/LOCKED/PRIVACY, met fail-closed gedrag bij syntactisch ongeldige GEDCOM-tekst; voeg een nieuwe redentekst toe in PrivacyClassificationReasons; laat PrivacyClassifier.classify dit signaal als onafhankelijke, bindende check meewegen (BLOCKED overstemt altijd de bestaande checks, NONE/NOT_APPLICABLE laat bestaande logica ongewijzigd) binnen de bestaande runCatching/fail-closed structuur. Schrijf unit tests voor de parser en voor PrivacyClassifier.classify met minimaal vier synthetische GEDCOM-fixtures (zonder RESN, RESN op recordniveau, RESN op feitniveau, geen GEDCOM-bron) plus een fail-closed test voor ongeldige syntax; bestaande PrivacyClassifierTest/LivingPersonAgeRuleTest/PrivacyPublishGuardTest moeten ongewijzigd blijven slagen. Werk docs/factory/technical-spec.md bij met de nieuwe RESN-logica, naar het patroon van de bestaande LivingPersonAgeRule-beschrijving.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `GenealogicalRecord.gedcomSource: String? = null` toegevoegd als optioneel invoerveld; geen
  bestaande velden of gedrag gewijzigd wanneer dit veld ontbreekt.
- Nieuwe `GedcomResnRule` (class, naar het patroon van `LivingPersonAgeRule`) parseert GEDCOM 7.0
  `LEVEL [@XREF@] TAG [VALUE]`-regels, bouwt de hiërarchie op via het levelgetal en doorzoekt de
  resulterende boom recursief op een RESN-tag met blokkerende waarde (`CONFIDENTIAL`, `LOCKED`,
  `PRIVACY`, ongeacht letterkast), op elk nestingniveau. Resultaat is `GedcomResnSignal`
  (`BLOCKED`/`NONE`/`NOT_APPLICABLE`). Syntactisch ongeldige, niet-lege brontekst levert
  fail-closed `BLOCKED` op (expliciete aanname uit de story, conform bestaande
  fail-closed-conventie).
- Nieuwe redentekst `PrivacyClassificationReasons.GEDCOM_RESN_BLOCKED`.
- `PrivacyClassifier.evaluate` roept `GedcomResnRule.evaluate` als eerste, onafhankelijke check
  vóór de bestaande `DeceasedStatus`-, `nextOfKin`- en leeftijdsregel-checks: bij `BLOCKED` is de
  totaaluitkomst altijd `Blocked` met de nieuwe reden, ongeacht de overige checks; bij `NONE` of
  `NOT_APPLICABLE` blijft de bestaande logica ongewijzigd leidend. Bestaande constructorparameter
  `livingPersonAgeRule` blijft ongewijzigd; nieuwe optionele parameter `gedcomResnRule` toegevoegd
  met default `GedcomResnRule()`.
- Tests: nieuw `GedcomResnRuleTest.kt` (geen bron → not applicable; geldige bron zonder RESN →
  none; RESN op recordniveau met elke blokkerende waarde, incl. andere letterkast → blocked; RESN
  genest binnen een feit → blocked; RESN met niet-blokkerende waarde → none; diverse
  syntactisch ongeldige bronnen → fail-closed blocked). `PrivacyClassifierTest.kt` uitgebreid met
  scenario's die aantonen dat een GEDCOM-blokkade de bestaande leeftijdsregel-uitkomst overstemt
  (record- en feitniveau), dat een GEDCOM-bron zonder RESN of een ontbrekende GEDCOM-bron de
  bestaande classificatie niet beïnvloedt, en dat ongeldige GEDCOM-syntax fail-closed blokkeert.
- `docs/factory/technical-spec.md` bijgewerkt met een nieuwe paragraaf over `gedcomSource` en
  `GedcomResnRule`, naar het patroon van de bestaande `LivingPersonAgeRule`-beschrijving.
- Volledig vangnet uit `development.md` gedraaid: `mvn -B --no-transfer-progress clean verify`
  (backend, 201 tests, 0 failures/errors), `flutter analyze`/`flutter test`/`flutter build web`
  (frontend), `flutter analyze`/`flutter test` (frontend-admin) — allemaal exitcode 0, geen
  failures/errors. Bestaande `PrivacyClassifierTest`, `LivingPersonAgeRuleTest` en
  `PrivacyPublishGuardTest` slagen ongewijzigd.
- `.factory/verification.yaml` behoefde geen wijziging: bestaande commandoset/paden dekken de
  gewijzigde bestanden al (alleen binnen `backend/` en `docs/factory/`, geen nieuwe module of
  endpoint).

Niet gedaan / bewust buiten scope:
- Geen echte GEDCOM-ophaalintegratie (conform Aannames in de story); `gedcomSource` wordt alleen
  in tests met synthetische fixtures gevuld.
- Geen wijziging aan `PrivacyPublishGuard`, moduleafhankelijkheden (`allowedDependencies` blijft
  leeg), REST-endpoints of frontend.

## Review (hkh-49)
- Diff tegen `main` beoordeeld: `GedcomResnRule.kt` (nieuw), `GenealogicalRecord.gedcomSource`,
  `PrivacyClassificationReasons.GEDCOM_RESN_BLOCKED`, `PrivacyClassifier.evaluate` (bindende
  RESN-check vóór bestaande logica), tests en `technical-spec.md`.
- Parser/hiërarchie-opbouw (`parseLine`/`buildTree`) nagelopen op edge cases: recordregel met
  pointer (`0 @I1@ INDI`), regel zonder pointer, ontbrekend levelgetal, negatief levelgetal en
  niveausprongen — allemaal correct fail-closed of correct genest voor recursieve RESN-zoektocht.
  Alle acceptatiecriteria (record-/feitniveau-blokkade overstemt leeftijdsregel, geen RESN blijft
  bestaande uitkomst leidend, geen bron is not-applicable, fail-closed op ongeldige syntax) hebben
  een dekkende test.
- Scope is strak gehouden: geen wijziging aan moduleafhankelijkheden, `PrivacyPublishGuard`,
  REST-endpoints of frontend; alleen `nl.vdzon.hkh.privacyclassification` en `technical-spec.md`
  geraakt.
- Gerichte testrun uitgevoerd (geen volledig vangnet opnieuw, alleen de geraakte/relevante
  klassen): `mvn -B --no-transfer-progress -Dtest=GedcomResnRuleTest,PrivacyClassifierTest,
  LivingPersonAgeRuleTest,PrivacyPublishGuardTest test` → groen, 0 failures/errors
  (GedcomResnRuleTest 16, PrivacyClassifierTest 17, LivingPersonAgeRuleTest 17,
  PrivacyPublishGuardTest 2 tests). Bestaande tests ongewijzigd geslaagd.
- Geen blockers gevonden. Akkoord.

## Test (hkh-50)
- Code beoordeeld: `GedcomResnRule.kt`, `GenealogicalRecord.gedcomSource`,
  `PrivacyClassifier.evaluate` (bindende RESN-check vóór bestaande logica) — komt overeen met alle
  acceptatiecriteria uit de story (record-/feitniveau-blokkade overstemt leeftijdsregel, geen RESN
  laat bestaande uitkomst leidend, geen bron is not-applicable, fail-closed op ongeldige syntax).
  `GedcomResnRuleTest` en `PrivacyClassifierTest` dekken alle vier vereiste fixtures plus extra
  edge cases (letterkast, geneste niveaus, ongeldige syntax-varianten).
- Volledig voorgeschreven vangnet gedraaid (niets overgeslagen):
  - `(cd backend && mvn -B --no-transfer-progress clean verify)` → BUILD SUCCESS, Tests run: 201,
    Failures: 0, Errors: 0, Skipped: 0.
  - `(cd frontend && flutter analyze)` → No issues found.
  - `(cd frontend && flutter test)` → 11/11 groen.
  - `(cd frontend && flutter build web)` → Built build/web.
  - `(cd frontend-admin && flutter analyze)` → No issues found.
  - `(cd frontend-admin && flutter test)` → 22/22 groen.
  Geen rode/flaky tests waargenomen; geen herdraai nodig.
- Feature is pure backend-domeinlogica zonder REST-endpoint of frontendwijziging (conform scope);
  geen preview-URL beschikbaar (lege previewvelden in `deployment.md`) en niet van toepassing hier.
- Geen bugs gevonden. Akkoord voor doorgang naar summary/documentation/merge.
