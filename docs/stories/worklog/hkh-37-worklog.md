# hkh-37 — Per-record licentiecontrole voor archieven.nl-koppelingen

## Stappenplan

- [x] Backend: `ArchiveRecordFields`/`ArchiveJsonLdRecord`/`RestClientArchivesNlClient` uitbreiden met `license`.
- [x] Backend: nieuw domeinbegrip `ExternalVerificationLicenseStatus`/`ExternalVerificationLicenseResult`/
      `ExternalVerificationLicenseEvaluator` (fail-closed `LICENSE_UNKNOWN`), los van `VERIFIED`/`UNVERIFIED`.
- [x] Backend: `ExternalVerificationRecord`, `ExternalVerificationStore`/`Repository` uitbreiden met
      licentievelden + Flyway-migratie `V6__external_verification_license.sql` (backward-compatible default).
- [x] Backend: `ExternalVerificationService.verify` geeft licentievelden door.
- [x] Backend: `ExternalVerificationPublishGuard` weigert publicatie ook bij `LICENSE_UNKNOWN`.
- [x] Backend: `ExternalVerificationController`/response uitbreiden met licentievelden.
- [x] Backend-tests: evaluator-fixtures (met/zonder licentie), guard-test, service/controller/repository-
      tests, integratietest met twee records uit dezelfde collectie met onafhankelijke licentie-uitkomst.
- [x] Frontend-admin: nieuwe `LicenseStatusView` (patroon `PrivacyClassificationStatusView`) + widget-
      /semantiek- en contrasttest.
- [x] Volledig vangnet (`development.md`) draaien.

## Notities

- Licentie wordt gelezen uit het `license`-veld van het JSON-LD-antwoord van hetzelfde HTTP-verzoek dat
  al voor naam/geboortedatum/overlijdensdatum gebruikt wordt (geen extra request, geen caching/hergebruik
  tussen records).
- `LICENSE_UNKNOWN` is de fail-closed default: ontbrekende/lege/onbekende waarde of onverwachte fout
  leidt altijd tot `LICENSE_UNKNOWN`, nooit tot het overnemen van een eerdere/andere waarde.
- `ExternalVerificationPublishGuard` controleert verificatiestatus én licentiestatus onafhankelijk van
  elkaar; beide moeten in orde zijn voor publicatie.
- Migratie `V6` voegt `license_status` (NOT NULL, default `LICENSE_UNKNOWN`), `license_value` (nullable)
  en `license_checked_at` (NOT NULL, default `CURRENT_TIMESTAMP`) toe; bestaande rijen krijgen automatisch
  de default, dus backward-compatible zonder handmatige backfill.
