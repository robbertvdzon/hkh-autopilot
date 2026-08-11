# hkh-autopilot-12 - Worklog

Story-context bij eerste pickup:
Backend: statusmodel, publiek GET-by-id-leespad en beheerdersbevestigingsactie

Migratie voor confirmed_by/confirmed_at op record_intake; server-side statusresolver (NO_INTAKE/SAVED_WITHOUT_SOURCE/CONFIRMED, live PrivacyClassifier-herclassificatie met zelfherstellend gedrag zonder confirmedBy/confirmedAt te wissen); nieuwe publieke route GET /api/records/{localIdentifier} die uitsluitend afgeleide, niet-privacygevoelige velden teruggeeft (jaartal-only voor geboorte-/sterftedatum); nieuwe admin-only bevestigingsactie die confirmedBy/confirmedAt zet, als uitbreiding van het bestaande admin-pad. Inclusief unit-/integratietests voor alle statuscombinaties en het zelfherstellende gedrag.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Flyway-migratie `V9__record_intake_confirmation.sql`: nullable kolommen `confirmed_by`
  (VARCHAR(320)) en `confirmed_at` (TIMESTAMPTZ) op `record_intake`.
- `RecordIntakeRecord` uitgebreid met `confirmedBy`/`confirmedAt` (default `null`, backward
  compatible met bestaande call sites). `RecordIntakeStore`/`RecordIntakeRepository` uitgebreid
  met `findByLocalIdentifier` (meest recente record per `localIdentifier`) en `confirm` (zet
  `confirmed_by`/`confirmed_at` via UPDATE ... RETURNING, `null` bij onbekende identifier).
- Nieuw domeinbestand `RecordPublicStatus.kt` (module `recordintake`): enum `RecordPublicStatus`
  (`NO_INTAKE`/`SAVED_WITHOUT_SOURCE`/`CONFIRMED`) en `RecordPublicStatusResolver` die per verzoek
  de status server-side herberekent: geen record -> `NO_INTAKE`; geen gevulde archive-kernvelden
  (`archiveName`/`archiveSourceUri`) of geen beheerdersbevestiging -> `SAVED_WITHOUT_SOURCE`; een
  bij dit verzoek opnieuw uitgevoerde `PrivacyClassifier.classify()` die niet `PROCESSABLE`
  oplevert -> `SAVED_WITHOUT_SOURCE` zonder `confirmedBy`/`confirmedAt` te wissen (zelfherstellend
  gedrag: bij een latere `PROCESSABLE`-herclassificatie verschijnt `CONFIRMED` automatisch weer op
  basis van de bewaarde bevestiging); anders `CONFIRMED` met naam, jaartal-only geboorte-/
  sterftedatum (regex op de eerste 4-cijferige reeks, nooit dag-/maandprecisie), licentie,
  bron-URI en `confirmedAt`.
- Nieuwe publieke, ongeauthenticeerde route `GET /api/records/{localIdentifier}`
  (`RecordPublicController`) levert altijd HTTP 200 op (ook zonder bestaand record), zodat het
  onderscheid "bestaat niet" versus "bestaat wel maar niet bevestigd" niet via de HTTP-status kan
  lekken; retourneert uitsluitend de afgeleide, niet-privacygevoelige velden uit
  `RecordPublicView`, nooit de ruwe `RecordIntakeRecord` (status/`deceasedStatus`/
  `nextOfKinConfirmed` blijven onzichtbaar).
- Nieuwe admin-only route `POST /api/admin/record-intake/{localIdentifier}/confirm`
  (`RecordIntakeConfirmationController`) hergebruikt de bestaande `AdminAuthenticator`-conventie
  (zelfde tokenverificatie als de rest van de beheerfrontend); 404 bij een onbekende
  `localIdentifier`. `recordintake`'s `package-info.java` kreeg hiervoor de expliciete, niet-
  wildcard afhankelijkheid `auth` toegevoegd (naar het patroon van de module `news`).
- Tests: `RecordPublicStatusResolverTest` (unit, alle statusovergangen inclusief zelfherstellend
  gedrag) en `RecordPublicApiIntegrationTest` (Testcontainers, end-to-end via de publieke route en
  de admin-bevestigingsactie, inclusief het zelfherstellende gedrag op basis van een live
  `deceased_status`-wijziging in de database). Bestaande testfakes (`RecordIntakeServiceTest`,
  `RecordIntakeControllerTest`) uitgebreid met de twee nieuwe `RecordIntakeStore`-methoden.
  Bestaande, nu terecht rode tests hersteld volgens de boyscout-regel: verwachte Flyway-
  migratietelling in `DatabaseIntegrationTest` (8 -> 9) en de verwachte kolommenset in
  `RecordIntakeApiIntegrationTest` (twee nieuwe kolommen).
- Volledig vangnet uitgevoerd en groen: `mvn -B --no-transfer-progress clean verify` (backend,
  256+ tests, 0 failures/errors), `flutter analyze`/`flutter test`/`flutter build web` (frontend),
  `flutter analyze`/`flutter test` (frontend-admin) — alle zes commando's exitcode 0.
- Niet gedaan (buiten scope van deze subtaak `hkh-74`, volgt in latere subtaken): de publieke
  recorddetailpagina/sectie "Externe bronverificatie" in de Flutter-gebruikersfrontend (`hkh-75`),
  en updates aan `docs/factory/technical-spec.md`/`development.md` (`hkh-78`).
