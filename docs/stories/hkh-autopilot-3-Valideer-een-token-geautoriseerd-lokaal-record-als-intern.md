# hkh-autopilot-3 - Valideer één token-geautoriseerd lokaal record als intern concept

## Story

Valideer één token-geautoriseerd lokaal record als intern concept

<!-- refined-by-factory -->

## Samenvatting
Een collectiebeheerder kan straks via een beveiligde intake precies één lokaal collectierecord aanleveren. De service accepteert dit alleen met een kortlevend, geldig toegangsbewijs en controleert alle verplichte gegevens, waaronder een strikte privacyregel: records met (mogelijke) persoonsgegevens worden altijd geweigerd. Een geldig record wordt opgeslagen als intern concept — nooit gepubliceerd — en kan optioneel gekoppeld worden aan een extern archiefrecord. Foutmeldingen zijn duidelijk voor toetsenbord- en schermlezergebruikers, en er wordt nooit gevoelige toegangsinformatie bewaard of getoond.

## Scope
Nieuwe backendmodule (bijv. `nl.vdzon.hkh.recordintake`) met:
- Eén REST-endpoint dat per verzoek maximaal één record verwerkt.
- JWT-verificatie (RS256) via de bestaande `nimbus-jose-jwt`-dependency, analoog aan het patroon van `GoogleIdTokenVerifier`: vaste, versieerbare configuratie voor issuer `https://hkh-autopilot.local`, audience `hkh-autopilot-record-intake`, sleutelbron `/.well-known/jwks.json`, verplichte claims (`iss`, `aud`, `sub`, `exp`, `iat`, `scope`), maximale levensduur 15 minuten en vereiste scope `record:intake`.
- Validatie van verplichte velden: lokale identifier, titel-of-beschrijving, datering, herkomst, rechtenstatus, privacyclassificatie, toegangs- of permalink.
- Fail-closed privacyregel: alleen `geen persoonsgegevens` toegestaan; overige waarden worden geweigerd met foutcode `PRIVACY_CLASSIFICATION_BLOCKED`, zonder opslag.
- Opslag van een geldig record met status `intern_concept` (nieuwe Flyway-migratie/tabel), zonder publicatie-, download-, preview- of objectmedia-velden in de respons.
- Optionele externe conceptkoppeling (status `concept`) wanneer duurzame URL, koppelmotivering en onzekerheidswaarde (`laag`/`middel`/`hoog`) alle drie geldig zijn.
- Redactie: tokenwaarden, Authorization-headers, claims en bewijsinhoud worden nooit gepersisteerd, gelogd of teruggegeven; afwijzingen bevatten alleen een technische foutcode.
- Frontendcomponent (gebruikers- of beheerfrontend, ter beoordeling van de developer) met foutsamenvatting, focusverplaatsing naar de samenvatting, programmatische veldkoppeling per fout, en status via tekst plus aria-live-gebied. Geen invoerveld voor autorisatiebewijs anders dan het bestaande gemaskeerde tokenmechanisme.
- Aanvulling van `docs/factory/technical-spec.md` en `development.md` met de nieuwe module, endpoint en migratie (analoog aan de bestaande `linkdossier`-documentatie).

Buiten scope: publicatie-workflow, objectmedia-opslag, verwerkingsgrondslag/doel/bewaartermijn-registratie, en koppeling met andere externe archieven dan het Noord-Hollands Archief-patroon.

## Acceptance criteria
- De intake accepteert uitsluitend een kortlevend JWT-toegangstoken in de `Authorization: Bearer`-header; geen vrij tekstueel autorisatiebewijs, bestanden of bewijsvelden.
- Elk token wordt deterministisch geverifieerd tegen vaste, versieerbare configuratie (issuer, audience, RS256, JWKS-bron, verplichte claims, max. 15 minuten levensduur, vereiste scope `record:intake`); een ontbrekende, verlopen, verkeerd ondertekende of anderszins ongeldige claim geeft fail-closed HTTP 401.
- Tokenwaarden, Authorization-headers, JWT-claims en bewijsinhoud worden nooit opgeslagen, gelogd, in foutmeldingen opgenomen of in API-/UI-uitvoer teruggegeven; afwijzingen bevatten alleen een technische foutcode.
- Per verzoek wordt maximaal één record verwerkt; lokale identifier, titel-of-beschrijving, datering, herkomst, rechtenstatus, privacyclassificatie en toegangs-/permalink zijn verplicht. Elk ontbrekend/ongeldig veld geeft een machineleesbare veldfout zonder dat een conceptrecord wordt aangemaakt.
- Alleen privacyclassificatie `geen persoonsgegevens` is toegestaan; `mogelijk persoonsgegevens` en `persoonsgegevens` worden zonder opslag geweigerd met foutcode `PRIVACY_CLASSIFICATION_BLOCKED`. Er wordt geen verwerkingsgrondslag, doel, rol of bewaartermijn gevraagd, geëvalueerd of bewaard.
- Een geldige volledige inzending slaat precies één record op met status `intern_concept`; de respons bevat uitsluitend metadata, zonder publicatie-, download-, preview- of objectmedia-acties/URL's, ook bij rechtenstatus `publicatie toegestaan`.
- De externe conceptkoppeling is optioneel en wordt alleen aangemaakt (status `concept`, precies één koppeling) wanneer duurzame URL, niet-lege koppelmotivering en geldige onzekerheidswaarde (`laag`/`middel`/`hoog`) samen aanwezig en geldig zijn; anders wordt geen koppeling gemaakt terwijl het interne conceptrecord wel kan bestaan.
- De frontend toont na validatie een foutsamenvatting, verplaatst de toetsenbordfocus daarheen, koppelt elke fout programmatisch aan het veld, en communiceert succes/fout/blokkade via tekst plus een aria-live-statusgebied (niet uitsluitend kleur). Er is geen invoerveld voor autorisatiebewijs anders dan het bestaande gemaskeerde tokenmechanisme; tokeninhoud, claims of headers worden nooit getoond.
- Geautomatiseerde unit-, integratie-, contract- en toegankelijkheidstests bewijzen: tokenverificatie en secret-redactie, de enkel-recordlimiet, verplichte-veldenvalidatie, fail-closed blokkade van beide persoonsgegevensclassificaties, opslag uitsluitend als `intern_concept`, de optionele conceptkoppeling, afwezigheid van media/publicatieacties, en het beschreven focus-, foutkoppelings- en aria-live-gedrag.
- `docs/factory/technical-spec.md` en `development.md` zijn aangevuld met concrete informatie over de nieuwe module, het endpoint en de migratie.
- Het volledige vangnet uit `.factory/verification.yaml`/`development.md` (backend `mvn clean verify`, Flutter `analyze`/`test`/`build web` voor beide frontends) slaagt groen.

## Aannames
- JWT-verificatie wordt gebouwd met de reeds aanwezige `nimbus-jose-jwt`-dependency, naar analogie van `GoogleIdTokenVerifier` in de bestaande `auth`-module; er wordt geen nieuwe library toegevoegd.
- De nieuwe functionaliteit krijgt een eigen Spring Modulith-module (werktitel `recordintake`) met eigen `package-info.java` en opname in `ModulithArchitectureTest`, conform de bestaande architectuurconventies.
- Opslag van het interne conceptrecord en de optionele externe koppeling gebeurt in PostgreSQL via een nieuwe Flyway-migratie, analoog aan bestaande modules zoals `news`.
- De JWKS-sleutelbron (`/.well-known/jwks.json`) wordt als extern te bereiken endpoint beschouwd; de exacte hostconfiguratie (bijv. via `secrets.env`/omgevingsvariabele) wordt door de developer bepaald, consistent met bestaande secret-conventies in `secrets-local.md`.
- De frontendcomponent voor deze intake hoort primair bij de beheerfunctionaliteit (collectiebeheerder-rol); of dit in `frontend-admin` of `frontend` landt, bepaalt de developer op basis van bestaande rolscheiding — dit is geen blokkerende vraag omdat beide frontends dezelfde toegankelijkheidsconventies uit `technical-spec.md` volgen.
- "Duurzame URL" voor de externe koppeling wordt op dezelfde manier gevalideerd als de bestaande permalink-/URL-validatie in `linkdossier` (absolute http/https-URL via `java.net.URI`).

## Eindsamenvatting

Ik heb voldoende context. Nu de eindsamenvatting.

## Eindsamenvatting — hkh-autopilot-3: Valideer één token-geautoriseerd lokaal record als intern concept

**Gebouwd:**
- Nieuwe backendmodule `nl.vdzon.hkh.recordintake` (Spring Modulith, eigen `package-info.java`, opgenomen in `ModulithArchitectureTest`) met endpoint `POST /api/record-intake`.
- Tokenverificatie via een eigen RS256/JWKS-verifier (naar het patroon van de bestaande `auth`-module, maar zonder modulekoppeling): vaste issuer/audience/scope `record:intake`, max. 15 minuten levensduur, fail-closed 401 bij ontbrekende configuratie, fail-closed 503 zonder JWKS-configuratie. JWKS-bron configureerbaar via `HKH_RECORD_INTAKE_JWKS_URL`.
- Validator die alle veldfouten verzamelt (geen fail-fast) voor verplichte velden (lokale identifier, titel-of-beschrijving, datering, herkomst, rechtenstatus, privacyclassificatie, toegangs-/permalink).
- Geïsoleerde, fail-closed privacyregel: alleen `geen persoonsgegevens` wordt geaccepteerd; overige waarden geven `PRIVACY_CLASSIFICATION_BLOCKED` zonder opslag.
- Opslag als `intern_concept` (nieuwe migratie `V4__record_intake.sql`), respons bevat uitsluitend metadata — geen publicatie-/download-/preview-/objectmediavelden, ook niet bij rechtenstatus "publicatie toegestaan".
- Optionele externe conceptkoppeling (status `concept`), alleen aangemaakt als duurzame URL, koppelmotivering én onzekerheidswaarde alle drie geldig zijn (ook afgedwongen via DB-constraints).
- Redactie: tokenwaarden, headers en claims worden nooit gepersisteerd, gelogd of teruggegeven.
- Frontend-admin component `RecordIntakeForm`: foutsamenvatting met focusverplaatsing, per-veld foutkoppeling, status via tekst + aria-live-gebied, hergebruik van het bestaande gemaskeerde tokenmechanisme (geen los invoerveld voor autorisatiebewijs).
- Documentatie bijgewerkt: `technical-spec.md`, `development.md`, `secrets-local.md`, `secrets.env.example`.

**Keuzes:**
- Eigen, losstaande tokenverifier in plaats van hergebruik van de bestaande `auth`-module, om modulekoppeling te vermijden.
- Statuscommunicatie in het formulier via tekst + `Semantics(liveRegion: true)`, bewust afwijkend van de bestaande passieve `SemanticsRole.status`-conventie, omdat dit een formulierstatus na gebruikersactie betreft (gedocumenteerd in technical-spec.md).
- Boyscout-fix: `DatabaseIntegrationTest` bijgewerkt van 3 naar 4 verwachte migraties.

**Getest:**
- Volledig vangnet groen: backend `mvn clean verify` (102 tests, 0 failures/errors, incl. Testcontainers/Postgres), frontend `analyze`/`test`/`build web`, frontend-admin `analyze`/`test` (12 tests, incl. 5 nieuwe voor `RecordIntakeForm`).
- Aparte tests voor tokenverificatie/redactie, enkel-recordlimiet, verplichte velden, fail-closed privacyblokkade, opslag als `intern_concept`, optionele koppeling, afwezigheid van media/publicatievelden, en focus-/aria-live-gedrag in de frontend.
- Geen preview-omgeving beschikbaar (geen preview-URL-template in `deployment.md`); getest lokaal.
- Tester meldde een omgevingsgebonden flake in `frontend-admin flutter test` (parallelle testrunner in sandbox toont soms een onvolledige voortgangsweergave terwijl alle tests wel slagen) — geen functionele regressie, agent-tip vastgelegd.

**Bewust niet gedaan:** publicatie-workflow, objectmedia-opslag, verwerkingsgrondslag/doel/bewaartermijn-registratie, koppeling met andere externe archieven dan het Noord-Hollands Archief-patroon — conform de afgebakende scope.

**Opmerking:** de phase-JSON in de rolinstructies van `.task.md` (`summary-finished`) wijkt af van het opdrachtcontract (`summarized`); dit is een bekend, eerder gedocumenteerd verschil — ik volg het opdrachtcontract.

Geen blockers gevonden door reviewer of tester; geen bugs.

<!-- deploy-summary:start -->
Beheerders kunnen nu via een beveiligd formulier één collectierecord aanmelden als intern concept. Records met (mogelijke) persoonsgegevens worden automatisch geweigerd, en er kan optioneel een link naar een extern archief worden meegegeven. Het formulier is ook goed te gebruiken met toetsenbord en schermlezer.
<!-- deploy-summary:end -->
