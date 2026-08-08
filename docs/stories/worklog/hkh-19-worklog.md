# hkh-19 — Backend recordintake-module en frontend-admin intakeformulier

## Stappenplan

- [x] Worklog aangemaakt
- [x] Backend module `recordintake`: domeinmodel + enums
- [x] Validator + validatieresultaat (verplichte velden, geïsoleerde privacyregel)
- [x] Tokenverifier (RS256/JWKS, vaste issuer/audience/scope/max-levensduur) + configuratie
- [x] Flyway-migratie + repository + service (intern_concept + optionele externe koppeling)
- [x] REST-controller + request/response-DTO's (metadata-only, geen secrets)
- [x] Backendtests (unit, integratie, Modulith-architectuur)
- [x] Frontend-admin intakeformulier (foutsamenvatting, focus, aria-live) + widgettests
- [x] Documentatie: technical-spec.md, development.md, secrets-local.md
- [x] Volledig vangnet groen

## Notities

- Nieuwe Spring Modulith-module `nl.vdzon.hkh.recordintake` (incl. `recordintake.api`) met
  `allowedDependencies = {}`, analoog aan `linkdossier` (domeinmodel/validator) en `auth`
  (tokenverificatiepatroon via `nimbus-jose-jwt`), maar met een eigen, losstaande verifier zodat er
  geen modulekoppeling met `auth` ontstaat.
- `POST /api/record-intake`: leest en verifieert eerst het `Authorization: Bearer`-token
  (RS256, vaste issuer/audience/scope `record:intake`, max. 15 minuten levensduur, JWKS via
  `HKH_RECORD_INTAKE_JWKS_URL`; fail-closed 401 zonder de configuratievariabele en fail-closed 503
  zonder JWKS-configuratie), valideert daarna het record (alle veldfouten verzameld, geen
  fail-fast) en beoordeelt de privacyregel volledig geïsoleerd: alleen `geen persoonsgegevens`
  passeert, de rest geeft `PRIVACY_CLASSIFICATION_BLOCKED` zonder opslag. Een geldig record wordt
  opgeslagen als `intern_concept` (Flyway `V4__record_intake.sql`); de optionele externe
  conceptkoppeling (`status concept`, uniek per record) wordt alleen aangemaakt als duurzame URL,
  koppelmotivering en onzekerheid (`laag`/`middel`/`hoog`) alle drie geldig zijn. Tokenwaarden,
  headers en claims worden nooit gepersisteerd, gelogd of teruggegeven.
- Frontend-admin: nieuwe sectie `RecordIntakeForm` onder het bestaande nieuwsformulier in
  `_AdminHome`, met een eigen bron `AdminRecordIntakeClient` die het bestaande gemaskeerde
  tokenmechanisme (`AdminIdentity.requestHeaders`) hergebruikt — geen los invoerveld voor
  autorisatiebewijs. Na een mislukte validatie (client- of servergevalideerd) verschijnt een
  foutsamenvatting die de toetsenbordfocus krijgt (`Focus`/`FocusNode.requestFocus()`), elke fout is
  via `FocusNode` + `errorText` aan zijn veld gekoppeld, en status gaat bewust via tekst plus
  `Semantics(liveRegion: true)` — een ander patroon dan de bestaande passieve
  `SemanticsRole.status`-conventie, omdat dit een formulierstatus na een gebruikersactie is.
- Boyscout-fix: `DatabaseIntegrationTest` verwachtte nog 3 succesvolle migraties; bijgewerkt naar 4
  na de nieuwe `V4__record_intake.sql`.
- Documentatie bijgewerkt: `technical-spec.md` (nieuwe module `recordintake`, endpoint, migratie,
  en een aparte paragraaf die het bewuste verschil met de bestaande statussemantiek toelicht),
  `development.md` (repositorystructuur) en `secrets-local.md` (`HKH_RECORD_INTAKE_JWKS_URL`),
  plus `secrets.env.example`.
- Volledig vangnet lokaal groen: backend `mvn clean verify` (102 tests, incl. Testcontainers via
  Docker-socket in deze omgeving), `frontend` analyze/test/build web, `frontend-admin`
  analyze/test.

## Review (reviewer)

- Backendmodule, validator, tokenverifier, migratie en controller gecontroleerd tegen de AC's:
  fail-closed 401 (missende claims, verkeerde issuer/audience/scope, verlopen, levensduur > 15
  min), geïsoleerde privacyregel (blokkeert `mogelijk persoonsgegevens`/`persoonsgegevens`
  onafhankelijk van overige veldfouten, `PRIVACY_CLASSIFICATION_BLOCKED` zonder opslag), verzamelde
  veldfouten zonder fail-fast, opslag uitsluitend als `intern_concept` zonder media-/
  publicatievelden in de respons, en de externe koppeling die alleen ontstaat als alle drie de
  velden geldig zijn (DB-constraints in `V4__record_intake.sql` bevestigen dit ook op tabelniveau).
  Geen tokenwaarde/claims/headers in respons, opslag of foutmeldingen aangetroffen.
- Frontend-admin `RecordIntakeForm`: foutsamenvatting met focusverplaatsing
  (`FocusNode.requestFocus()`), per-veld foutkoppeling via `errorText`, status via tekst +
  `Semantics(liveRegion: true)`, geen apart invoerveld voor autorisatiebewijs — consistent met de
  AC's en met de bewust gedocumenteerde afwijking van de bestaande `SemanticsRole.status`-conventie.
  `ModulithArchitectureTest` en `DatabaseIntegrationTest` correct bijgewerkt (module toegevoegd,
  migratieaantal 3 → 4).
- `docs/factory/technical-spec.md`, `development.md` en `secrets-local.md`/`secrets.env.example`
  zijn concreet en overeenkomstig de code bijgewerkt.
- Geen blockers gevonden; geen scope-overschrijding t.o.v. de story.
