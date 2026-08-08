# hkh-autopilot-7 - Worklog

Story-context bij eerste pickup:
Persoonsniveau leeftijdsregel (FamilySearch 110/95) toevoegen aan privacyclassification

Breid backendmodule nl.vdzon.hkh.privacyclassification uit: nieuw NamedPerson-datatype (optionele String?-velden birthDate, marriageDate, childBirthDate, deathDate, burialDate) en veld namedPersons op GenealogicalRecord; nieuw, zelfstandig testbaar mechanisme dat per persoon LIKELY_LIVING/DECEASED/UNKNOWN_FAILCLOSED bepaalt volgens de FamilySearch 110/95-jaarregel (ISO-8601 met yyyy-only fallback, inclusieve grenzen, injecteerbare tijdsbron voor determinisme, fail-closed bij ontbrekende/onparsbare datums); integreer dit additief in PrivacyClassifier.evaluate zodat het record BLOCKED blijft zodra minstens één genoemde persoon LIKELY_LIVING of UNKNOWN_FAILCLOSED is, met nieuwe reasonconstante(n) in PrivacyClassificationReasons; behoud bestaande DeceasedStatus/nextOfKin-gedrag en runCatching-faalveiligheid ongewijzigd. Schrijf unit tests voor het nieuwe mechanisme (grenzen 109/111 jaar geboorte, 94/96 jaar huwelijk/kind, overlijdensdatum aanwezig, ontbrekend/onleesbaar veld) en voor PrivacyClassifier (meerdere genoemde personen met precies één LIKELY_LIVING → BLOCKED; alle personen DECEASED en geen ander blokkerend signaal → PROCESSABLE). Documenteer de externe, niet-wettelijke herkomst van de 110/95-regel en de bekende beperking rond ontbrekende event-granulariteit in KDoc en in docs/factory/technical-spec.md en functional-spec.md.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `hkh-43` geïmplementeerd: `GenealogicalRecord.namedPersons: List<NamedPerson>` toegevoegd
  (nieuw `NamedPerson`-datatype met optionele `String?`-velden `birthDate`, `marriageDate`,
  `childBirthDate`, `deathDate`, `burialDate`, naar het patroon van
  `ExternalVerificationRequest.birthDate`).
- Nieuwe klasse `LivingPersonAgeRule` (met enum `PersonAgeStatus`:
  `LIKELY_LIVING`/`DECEASED`/`UNKNOWN_FAILCLOSED`) implementeert de FamilySearch 110/95-jaarregel:
  geldige overlijdens-/begrafenisdatum → `DECEASED`; anders geboortedatum ≤110 jaar geleden of
  huwelijks-/kindgeboortedatum ≤95 jaar geleden (grenzen inclusief) → `LIKELY_LIVING`; geboortedatum
  >110 jaar geleden zonder recent huwelijks-/kindsignaal → `DECEASED`; ontbrekend of onparsbaar
  datumveld → fail-closed `UNKNOWN_FAILCLOSED`. Datumparsing ondersteunt ISO-8601 (`yyyy-MM-dd`) met
  een `yyyy`-only fallback (1 januari als impliciete dag); tijdsbron is een injecteerbare
  `java.time.Clock` (standaard systeemklok) voor deterministische tests.
- `PrivacyClassifier.evaluate` uitgebreid: aanvullend op de bestaande `DeceasedStatus`/`nextOfKin`-
  controles blijft het record `BLOCKED` zodra minstens één `namedPersons`-item `LIKELY_LIVING` of
  `UNKNOWN_FAILCLOSED` oplevert, met nieuwe redenconstantes
  `PrivacyClassificationReasons.NAMED_PERSON_LIKELY_LIVING` en `NAMED_PERSON_AGE_UNKNOWN_FAILCLOSED`.
  Bestaand gedrag (records zonder `namedPersons`) en `runCatching`-faalveiligheid blijven ongewijzigd.
- Nieuwe testsuite `LivingPersonAgeRuleTest` (grenzen 109/111 jaar geboorte, 94/96 jaar
  huwelijk/kind, overlijdens-/begrafenisdatum aanwezig, ontbrekend/onleesbaar veld, yyyy-only
  fallback) en uitbreiding van `PrivacyClassifierTest` (meerdere genoemde personen met precies één
  `LIKELY_LIVING` → `BLOCKED`; onleesbaar datumveld → `BLOCKED`; alle personen `DECEASED` zonder
  ander blokkerend signaal → `PROCESSABLE`).
- Documentatie bijgewerkt: `docs/factory/technical-spec.md` (nieuwe paragrafen bij
  `Backendmodule privacyclassification`) en `docs/factory/functional-spec.md` (nieuwe alinea bij
  `Privacyclassificatie`) documenteren expliciet de externe, niet-wettelijke herkomst van de
  FamilySearch 110/95-regel en de bekende beperking rond ontbrekende event-granulariteit
  (yyyy-only datums).
- Volledig vangnet gedraaid en groen: `mvn -B --no-transfer-progress clean verify` (180 tests, 0
  failures/errors), `frontend`: `flutter analyze`/`flutter test`/`flutter build web`,
  `frontend-admin`: `flutter analyze`/`flutter test`. Geen bestaande rode tests aangetroffen.

Niet gedaan / aangepast:
- Geen wijzigingen aan bestaande `DeceasedStatus`/`nextOfKin`-velden of -logica, geen
  opslag/REST-endpoint voor de nieuwe persoonsgegevens, geen UI-weergave van de per-persoon
  uitkomst — conform expliciete scope-afbakening in de story.
