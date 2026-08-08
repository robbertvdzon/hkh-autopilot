# hkh-38 — Story-brede test (worklog)

## Uitgevoerde verificatie

- `backend`: `mvn -B --no-transfer-progress clean verify` → BUILD SUCCESS, Tests run: 160, Failures: 0, Errors: 0.
  - Nieuwe/aangepaste tests bevestigd groen: `ExternalVerificationLicenseEvaluatorTest` (8), `ExternalVerificationPublishGuardTest` (3),
    `RestClientArchivesNlClientTest` (6), `ExternalVerificationControllerTest` (5), `ExternalVerificationApiIntegrationTest` (9),
    `DatabaseIntegrationTest` (migratieaantal 6, incl. `V6__external_verification_license.sql`).
- `frontend-admin`: `flutter analyze` → No issues found.
- `frontend-admin`: `flutter test` (default concurrency) → 22/22 groen, exitcode 0. Bekend
  weergave-artefact (`frontend-admin-flutter-test-concurrency-artifact`) trad opnieuw op voor
  `license_status_view_test.dart` (progressteller toont niet alle 4 subtests los in de default run).
  Bevestigd als omgevingsartefact, geen echte regressie, via geïsoleerde run
  (`flutter test test/license_status_view_test.dart` → 4/4 groen) én `flutter test -j 1`
  (volledige suite, 22/22 groen, alle bestanden inclusief `license_status_view_test.dart` individueel zichtbaar).

## Gedragscontrole tegen acceptatiecriteria

- Per-record licentie wordt gelezen uit het JSON-LD-antwoord van dát record; geen collectiebrede
  afleiding — bevestigd in `ExternalVerificationLicenseEvaluatorTest` en met name
  `ExternalVerificationApiIntegrationTest` (meerdere records uit dezelfde collectie met
  onafhankelijke `LICENSE_KNOWN`/`LICENSE_UNKNOWN`-uitkomsten, zie testlog).
- Ontbrekende licentie ⇒ `LICENSE_UNKNOWN`, publicatie geweigerd ondanks `VERIFIED`-verificatiestatus
  — bevestigd in `ExternalVerificationPublishGuardTest`.
- Aanwezige licentie ⇒ waarde + controledatum opgeslagen — bevestigd via repository/response-velden
  en API-integratietest.
- Frontend-badge (`LicenseStatusView`) toont tekstlabel + icoon, apart van bestaande status-badges;
  contrast ≥4,5:1 geautomatiseerd getest (7,87:1 / 6,57:1) — bevestigd groen.

Geen bugs gevonden. Geen preview-URL beschikbaar (leeg in `deployment.md`); verificatie is uitgevoerd
via het volledige voorgeschreven vangnet (backend maven verify + frontend-admin flutter
analyze/test) conform tester-instructies.
