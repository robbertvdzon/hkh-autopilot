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
