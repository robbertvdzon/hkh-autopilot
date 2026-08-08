# hkh-autopilot-2 - Worklog

Story-context bij eerste pickup:
Fail-closed validator voor koppelingsdossier in nieuwe Modulith-module

Maak in backend een nieuwe, zelfstandige Spring Modulith-module (bijv. nl.vdzon.hkh.linkdossier) met domeinmodel voor een koppelingsdossier (exact twee geordende records + één relatie) en een deterministische validator. Geen controller, repository, migratie of frontendwijziging.

Gedrag: verzamel alle overtredingen (nooit stoppen bij de eerste) en lever een resultaat met (a) dossierstatus 'publiceerbaar als metadata-link' of 'geblokkeerd', (b) een dossierbrede objectmedia-indicatie, (c) twee afzonderlijke lijsten veldpaden - metadata-blokkades en objectmedia-blokkades - elk ontdubbeld en lexicografisch gesorteerd, onafhankelijk van uitvoeringsvolgorde.

Gebruik uitsluitend de vaste veldpaden uit de story: records, records[n].sourceHolder, .permanentUrl, .identifier, .title, .description, .dating.value, .dating.uncertainty, .metadataRights, .objectRights, .privacyClassification, relation.relationType, .connectionGround, .evidenceLinks, .confirmationStatus.

Regels: aantal records != 2 -> 'records' in BEIDE lijsten; tekstwaarden gelden als ontbrekend na trimmen; alternatievenparen (permanentUrl/identifier en title/description) melden beide paden alleen als beide ontbreken; iedere opgegeven permanente URL en bewijslink moet een absolute http/https-URL zijn (malformed blokkeert het eigen pad ook als het alternatief geldig is; ongeldige bewijslinks leveren relation.evidenceLinks precies eenmaal); stabiele referentie = permanentUrl, anders identifier, gelijkheid via exacte vergelijking na trimmen blokkeert en meldt voor beide records het feitelijk gebruikte referentiepad, geen bruikbare referentie -> vergelijking vervalt; datering waarde en onzekerheid apart beoordeeld; ontbrekende of niet-herkende gecontroleerde waarden blokkeren hun eigen pad (het invoertype moet 'ontbrekend' en 'niet-herkend' kunnen representeren, zodat de validator ze afkeurt in plaats van dat constructie/deserialisatie faalt).

Fail-closed: 'publiceerbaar als metadata-link' alleen bij twee geldige verschillende records, alle verplichte velden geldig, beide metadatarechten expliciet toegestaan, beide privacyclassificaties openbaar en relatie bevestigd; 'hypothese' levert altijd geblokkeerd plus relation.confirmationStatus. Objectmedia wordt volledig apart beoordeeld: alleen recordaantal 2 en beide objectrechten expliciet toegestaan; ontbrekende/niet-toegestane/onduidelijke objectrechten komen alleen in de objectmedialijst en blokkeren nooit de metadata-link, en een om andere redenen geblokkeerd dossier mag objectmedia wel toestaan. Er ontsnapt nooit een uitzondering uit de validator.

Modulith: voeg package-info.java toe met @org.springframework.modulith.ApplicationModule(allowedDependencies = {}) zonder wildcard en neem de modulenaam op in de moduleset van backend/src/test/kotlin/nl/vdzon/hkh/ModulithArchitectureTest.kt.

Schrijf zelf de unittests (dit hoort bij deze subtaak) onder backend/src/test/kotlin/nl/vdzon/hkh/<module>/ en dek minimaal: volledig geldig dossier met bevestigde relatie dat publiceerbaar is en objectmedia toestaat; ieder verplicht recordveld parametrisch; beide alternatievenparen; hypothese; ontbrekende, niet-toegestane en onduidelijke objectrechten; verkeerd aantal records; ongeldige permanente URL en ongeldige bewijslink; twee records met dezelfde stabiele referentie; geblokkeerd-maar-objectmedia-toegestaan; meerdere gelijktijdige fouten met exacte deterministische veldpadvolgorde.

Sluit af met een eigen reviewslag op de diff en draai de volledige verplichte commandoset uit docs/factory/development.md (backend mvn clean verify plus de Flutter-commando's), allemaal met exitcode 0 en 0 failures/errors.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Nieuwe Modulith-module `nl.vdzon.hkh.linkdossier` toegevoegd met `package-info.java`
  (`@ApplicationModule(allowedDependencies = {})`, geen wildcard) en de modulenaam opgenomen in
  `ModulithArchitectureTest`. Geen controller, repository, migratie of frontendwijziging.
- Domeinmodel (`LinkDossier.kt`): `LinkDossier` (records + relatie), `LinkDossierRecord`,
  `RecordDating`, `LinkDossierRelation`. Gecontroleerde waarden zijn als ruwe `String?` gemodelleerd
  met enums (`RightsClassification`, `PrivacyClassification`, `DatingUncertainty`,
  `ConfirmationStatus`) plus `parse`. Zo kan het invoertype zowel "ontbrekend" als "niet-herkend"
  representeren zonder dat constructie of deserialisatie faalt; de validator keurt af.
- `LinkDossierValidator` verzamelt alle overtredingen, stopt nooit bij de eerste en levert
  `LinkDossierValidationResult` met dossierstatus, dossierbrede objectmedia-indicatie en twee
  afzonderlijke, ontdubbelde en lexicografisch gesorteerde veldpadlijsten (`sorted()` op een `Set`,
  dus onafhankelijk van uitvoeringsvolgorde). Vaste veldpaden staan in `LinkDossierFieldPaths`.
- Fail-closed: publiceerbaar alleen bij exact twee geldige, verschillende records, alle verplichte
  velden geldig, beide metadatarechten expliciet `toegestaan`, beide privacyclassificaties
  `openbaar` en relatie `bevestigd`. Objectmedia wordt volledig apart beoordeeld (alleen
  recordaantal 2 plus beide objectrechten `toegestaan`); objectrechten komen nooit in de
  metadatalijst, dus een om andere redenen geblokkeerd dossier mag objectmedia wel toestaan.
- `validate` is omsloten door `runCatching`; een onverwachte fout levert een geblokkeerd,
  objectmedia-verboden resultaat op in plaats van een uitzondering.
- Unittests: `LinkDossierValidatorTest` (geldig dossier, parametrische ontbrekend-veldscenario's,
  beide alternatievenparen, hypothese, ontbrekende/niet-toegestane/onduidelijke/niet-herkende
  objectrechten, verkeerd aantal records, ongeldige permanente URL en bewijslink, gelijke stabiele
  referentie via URL en via identifier, geen bruikbare referentie, geblokkeerd-maar-objectmedia-
  toegestaan en meerdere gelijktijdige fouten met exacte deterministische veldpadvolgorde).

Interpretatiekeuzes (bewust, ter controle door reviewer/PO):
- Dateringsonzekerheid `onbekend` is een geldige gecontroleerde waarde en blokkeert niet; alleen een
  ontbrekende of niet-herkende waarde blokkeert `records[n].dating.uncertainty`. Dit volgt de
  omschrijving "ontbrekende of niet-herkende gecontroleerde waarden blokkeren hun eigen pad".
- Bij een recordaantal ongelijk aan twee worden de aangeleverde records en de relatie nog steeds
  inhoudelijk beoordeeld; `records` komt daarbij in beide lijsten.

Verificatie (alle exitcode 0, 0 failures, 0 errors):
- `(cd backend && mvn -B --no-transfer-progress clean verify)` - Tests run: 64
- `(cd frontend && flutter analyze)` - No issues found
- `(cd frontend && flutter test)` - All tests passed
- `(cd frontend && flutter build web)` - Built build/web
- `(cd frontend-admin && flutter analyze)` - No issues found
- `(cd frontend-admin && flutter test)` - All tests passed
