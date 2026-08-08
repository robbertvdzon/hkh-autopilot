# hkh-20 — Story-brede test

## Vangnet (volledig, lokaal gedraaid)

- `(cd backend && mvn -B --no-transfer-progress clean verify)`: BUILD SUCCESS, Tests run: 102,
  Failures: 0, Errors: 0, Skipped: 0. Testcontainers/PostgreSQL 16 draaide correct (docker.sock
  beschikbaar). Inclusief `RecordIntakeValidatorTest` (15), `RecordIntakeTokenVerifierTest` (9),
  `RecordIntakeApiIntegrationTest` (6, echte Postgres), `RecordIntakeControllerTest` (8),
  `ModulithArchitectureTest` (2, nieuwe module correct opgenomen).
- `(cd frontend && flutter analyze)`: No issues found.
- `(cd frontend && flutter test)`: All tests passed (11 tests, `-j 1` herhaald ter controle).
- `(cd frontend && flutter build web)`: Built build/web succesvol.
- `(cd frontend-admin && flutter analyze)`: No issues found.
- `(cd frontend-admin && flutter test)`: All tests passed. Zie flake-notitie hieronder.

## Flake-notitie (agent-tip, geen blokkade)

In deze sandbox toont `flutter test` (default concurrency) in `frontend-admin` intermitterend een
onvolledige/misleidende voortgangsweergave: het testbestand `admin_record_intake_test.dart` lijkt
niet te worden geladen terwijl de totale teller toch op het juiste aantal (+12) uitkomt en de tool
"All tests passed"/exitcode 0 meldt. Met `flutter test -j 1` en met elk testbestand losser gedraaid
(`flutter test test/admin_record_intake_test.dart`) draaien alle 12 unieke tests (incl. de 5 tests
in `admin_record_intake_test.dart` en beide tests in `widget_test.dart`) consistent en groen. Dit is
een omgevingsartefact van de parallelle testrunner in deze sandbox (waarschijnlijk beperkte
CPU-cores), geen functionele regressie: alle onderliggende testcases zijn geverifieerd te slagen.
Zie agent-tip hieronder voor toekomstige testers.

## Gedragsverificatie (codereview + geautomatiseerde tests)

- `RecordIntakeController`: retourneert uitsluitend `id`, `status`, `createdAt`,
  `externalLink{id,status}` — geen publicatie-/download-/preview-/objectmediavelden, ook niet bij
  `rightsStatus = "publicatie toegestaan"` (bevestigd via
  `RecordIntakeApiIntegrationTest`).
- Tokenverificatie: ontbrekende/foutieve Bearer-token geeft fail-closed HTTP 401 vóór validatie
  (test "rejects requests without a valid bearer token before touching validation").
- Redactie: respons bevat nooit de tokenwaarde of "Bearer" (test "never echoes the authorization
  header or token value back to the caller").
- Privacyregel: alleen `geen persoonsgegevens` toegestaan; `mogelijk persoonsgegevens` geeft HTTP
  422 met `errorCode: PRIVACY_CLASSIFICATION_BLOCKED`, zonder opslag
  (`RecordIntakeValidator.evaluate` beoordeelt de privacyregel onafhankelijk van overige
  veldfouten; fail-closed `runCatching`/`failClosed()` bij onverwachte fouten).
- Verplichte velden + machineleesbare veldfouten: `RecordIntakeValidator` verzamelt alle fouten
  (stopt niet bij de eerste), inclusief het titel-of-beschrijving-alternatievenpaar.
- Optionele externe conceptkoppeling: alleen aangemaakt wanneer duurzame URL (absolute http/https
  via `URI`), niet-lege koppelmotivering én geldige onzekerheid (`laag`/`middel`/`hoog`) alle drie
  aanwezig zijn (`RecordIntakeValidator.isValidExternalLink`, bevestigd via integratietest "a
  submission with a fully valid external link also creates the concept link").
- Frontend (`RecordIntakeForm`): foutsamenvatting met `Semantics(label: 'Foutsamenvatting')`,
  focusverplaatsing via `_summaryFocus.requestFocus()` na `addPostFrameCallback`, elke fout
  programmatisch gekoppeld aan het veld via `errorText` op `TextFormField`/`DropdownButtonFormField`
  met matchende `FocusNode`, status via tekst plus `Semantics(liveRegion: true)`. Geen invoerveld
  voor autorisatiebewijs anders dan het bestaande gemaskeerde tokenmechanisme
  (`widget.identity`/`AdminIdentity`).
- `docs/factory/technical-spec.md` en `development.md` zijn aangevuld met de nieuwe module, het
  endpoint en de Flyway-migratie `V4__record_intake.sql`.

## Preview-omgeving

`deployment.md` bevat geen preview-URL-template voor deze repo (velden leeg); getest is daarom
uitsluitend lokaal (backend + Flutter web builds), zoals hierboven beschreven.

## Conclusie

Volledig vangnet groen (0 failures, 0 errors) en gedrag komt overeen met de acceptatiecriteria van
`hkh-autopilot-3`. Geen bugs gevonden.
