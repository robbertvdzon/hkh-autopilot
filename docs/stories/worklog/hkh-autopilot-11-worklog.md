# hkh-autopilot-11 - Worklog

Story-context bij eerste pickup:
Backend: datamodel, migratie, module-koppeling en dubbele fail-closed classificatie

Voeg deceasedStatus/nextOfKinConfirmed en vijf archiveXxx-velden toe aan RecordIntake/RecordIntakeRecord met bijbehorende migratie (alle kolommen nullable, geen wijziging aan bestaande CHECK-constraints). Geef recordintake expliciete allowedDependencies op externalverification en privacyclassification (package-info.java + ModulithArchitectureTest), zonder de bestaande route POST /api/external-verification of de matcher-/publish-guardlogica aan te raken. Voeg een nieuw, niet-persisterend preview-endpoint toe dat een durableUrl toetst tegen het patroon http://opendata.archieven.nl/id/<adtid>/<guid>, bij een match ArchivesNlClient.fetch aanroept en de kernvelden plus statuslabel (Geverifieerd/Geen match/Niet bereikbaar) teruggeeft. Breid POST /api/record-intake uit met deceasedStatus, nextOfKinConfirmed en confirmExternalArchiveData; implementeer in RecordIntakeService de fail-closed regel: lokale GenealogicalRecord (uit deceasedStatus/nextOfKinConfirmed) en, alleen bij confirmExternalArchiveData=true en geldig patroon, een servergezijdig herhaalde (dus niet door de frontend aangeleverde) ArchivesNlClient-fetch omgezet naar een tweede GenealogicalRecord; beide via PrivacyClassifier.classify(); naam/geboorte-/sterftedatum alleen opslaan wanneer beide Processable zijn, anders leeg met expliciete reden in de respons; licentie/bron-URI/ophaaldatum altijd opslaan bij geslaagde fetch. Nooit de ruwe externe respons opslaan. Inclusief unit-/integratietests voor alle AC-combinaties (Processable/Processable, Processable/Blocked, Blocked/*, onbekend/*, ongeldige/onbereikbare URI, 'sla op zonder externe brongegevens').

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## hkh-67 (developer) - backend datamodel/migratie/module-koppeling/dubbele classificatie

- Flyway-migratie `V8__record_intake_deceased_status_and_archive_data.sql`: acht nieuwe, nullable
  kolommen op `record_intake` (`deceased_status`, `next_of_kin_confirmed`, `archive_name`,
  `archive_birth_date`, `archive_death_date`, `archive_license`, `archive_source_uri`,
  `archive_fetched_at`); geen CHECK-constraints gewijzigd. `DatabaseIntegrationTest`
  migratietelling bijgewerkt naar 8.
- `recordintake/package-info.java`: expliciete, niet-wildcard `allowedDependencies =
  {"externalverification", "privacyclassification"}`; `ModulithArchitectureTest` (bestaande
  wildcard-/aanwezigheidscheck plus `ApplicationModules.verify()`) bevestigt de grenzen zonder
  aanpassing nodig te hebben.
- `RecordIntake`/`RecordIntakeRecord` uitgebreid met `deceasedStatus`, `nextOfKinConfirmed`,
  `confirmExternalArchiveData` (request) en de opgeslagen `archiveXxx`-velden.
- Nieuw, niet-persisterend endpoint `POST /api/record-intake/external-archive-preview`
  (`RecordIntakeExternalArchivePreviewController`): toetst `durableUrl` tegen
  `RecordIntakeArchiveUrlPattern` (`http://opendata.archieven.nl/id/<adtid>/<guid>`), roept bij een
  match `ArchivesNlClient.fetch` aan (geen token) en geeft kernvelden + statuslabel
  (`GEVERIFIEERD`/`GEEN_MATCH`/`NIET_BEREIKBAAR`) terug; bij een niet-matchend patroon wordt nooit
  een netwerkaanroep gedaan.
- `RecordIntakeService`: bij `confirmExternalArchiveData=true` en een geldig patroon volgt een
  servergezijdig herhaalde `ArchivesNlClient.fetch` (nooit de eerder door de client getoonde
  preview-data). Lokale `GenealogicalRecord` (uit `deceasedStatus`/`nextOfKinConfirmed`) en externe
  `GenealogicalRecord` (uit de opgehaalde sterftedatum) gaan beide door `PrivacyClassifier.classify()`;
  naam/geboorte-/sterftedatum worden alleen opgeslagen wanneer beide `PROCESSABLE` zijn, met een
  expliciete reden in de respons (`externalArchiveData.reason`) in elk ander geval. Licentie/bron-URI/
  ophaaldatum worden altijd opgeslagen bij een geslaagde fetch, ongeacht de classificatie-uitkomst.
  Bij `confirmExternalArchiveData=false`, een niet-matchend patroon of een mislukte/niet-matchende
  fetch wordt geen enkel archiefveld opgeslagen. De ruwe externe JSON-LD-respons wordt nooit
  bewaard (alleen de gestructureerde `ArchiveRecordFields`-kernvelden stromen door).
- Nieuwe tests: `RecordIntakeArchiveUrlPatternTest` (patroonherkenning), `RecordIntakeServiceTest`
  (unit, in-memory store + instelbare fake `ArchivesNlClient`, dekt alle AC-combinaties:
  Processable/Processable, Processable/Blocked door ontbrekende externe sterftedatum, lokaal
  Blocked/onbekend, ongeldige/onbereikbare URL, opslaan zonder externe brongegevens),
  `RecordIntakeExternalArchivePreviewIntegrationTest` (fixture-archiefendpoint via
  `com.sun.net.httpserver.HttpServer`) en uitbreidingen van `RecordIntakeApiIntegrationTest`
  (volledige flow via `POST /api/record-intake`, inclusief een kolomcontrole op `record_intake` die
  bevestigt dat uitsluitend de gestructureerde velden opgeslagen worden). Bestaande
  `RecordIntakeControllerTest`/`RecordIntakeApiIntegrationTest` aangepast aan de gewijzigde
  `RecordIntakeStore`/`RecordIntakeService`-signaturen.
- Volledig vangnet gedraaid en groen: backend `mvn -B --no-transfer-progress clean verify`
  (243 tests, 0 failures/errors), `frontend`/`frontend-admin` `flutter analyze` (geen meldingen),
  `flutter test --concurrency=1 --reporter expanded` (beide apps, alle tests groen) en
  `frontend flutter build web` (succesvolle release-build). Frontend-/frontend-admin-code is niet
  gewijzigd in deze subtaak (dat is hkh-68); het vangnet is uitsluitend ter bevestiging gedraaid.
- Niet gedaan (buiten scope van hkh-67, hoort bij hkh-68): het paneel "Brongegevens (extern, ter
  controle)" in `RecordIntakeForm`, de gedebouncte frontend-aanroep naar het preview-endpoint, de
  knoppen "Bevestig brongegevens en gebruik bij record"/"Sla op zonder externe brongegevens" en de
  bijbehorende `Semantics(liveRegion: true)`/toetsenbord-/contrasttests.

## hkh-67 (reviewer) - review

- Volledige diff main...HEAD bekeken: uitsluitend `backend/` (RecordIntake-model, repository,
  service, twee controllers, package-info, V8-migratie) en tests + deze worklog. Geen wijzigingen
  in `externalverification`/`privacyclassification` (diff bevestigt 0 regels) en geen aanraking van
  de bestaande route `/api/external-verification` of matcher-/publish-guardlogica - scope conform
  hkh-67.
- Dubbele fail-closed classificatie (`RecordIntakeService.resolveExternalArchiveData`) nagelopen
  tegen alle AC-combinaties (3-6): lokaal+extern Processable geeft opslag; extern zonder
  sterftedatum ondanks lokaal bevestigd overlijden blokkeert met reden (AC5); lokaal
  Blocked/ONBEKEND blokkeert opslag; ongeldige/onbereikbare URL geeft geen opslag en geen
  netwerkaanroep bij niet-matchend patroon. Licentie/bron-URI/ophaaldatum worden altijd bij een
  geslaagde fetch bewaard, ook bij geblokkeerde opslag. Ruwe externe respons stroomt nergens door
  (`ArchiveRecordFields` is al gestructureerd) en een expliciete kolomtest bevestigt dat
  `record_intake` geen ruwe-responskolom heeft (AC7). `package-info.java`/`ModulithArchitectureTest`
  bevestigen de expliciete, niet-wildcard `allowedDependencies`.
- Eigen gerichte testrun (niet het volledige vangnet): `mvn test
  -Dtest='nl.vdzon.hkh.recordintake.**,nl.vdzon.hkh.ModulithArchitectureTest,nl.vdzon.hkh.DatabaseIntegrationTest'`
  op HEAD (b6d01df) -> BUILD SUCCESS, 68 tests, 0 failures/errors. Surefire-reports in `target/`
  bevestigen dezelfde groene uitkomst voor alle nieuwe/gewijzigde testklassen.
- Geen blockers gevonden. Akkoord.

## hkh-68 (developer) - frontend paneel, debounce en a11y in RecordIntakeForm

Stappenplan:
[x]: lees issue/parent-story-context, backend-contract (`RecordIntakeExternalArchivePreviewController`,
     `RecordIntakeService`, `RecordIntakeController`) en bestaande frontend-admin-conventies
     (`PrivacyClassificationStatusView`, `LicenseStatusView`, `technical-spec.md`)
[x]: implementeer frontend-wijzigingen
[x]: schrijf/actualiseer widget-/semantiek-/contrasttests
[x]: draai het volledige vangnet (backend + beide Flutter-apps)
[x]: worklog bijwerken

Gedaan:
- `frontend-admin/lib/recordintake/external_archive_preview_panel.dart` (nieuw):
  `ExternalArchivePreviewPanel`, naar het patroon van `PrivacyClassificationStatusView`/
  `LicenseStatusView` maar met `Semantics(liveRegion: true)` (formulierstatus na een actie, niet de
  passieve `SemanticsRole.status`-conventie, conform de bestaande afwijking van `RecordIntakeForm`).
  Toont statuslabel (`Geverifieerd`/`Geen match`/`Niet bereikbaar`) met zowel tekst als icoon (nooit
  uitsluitend kleur), en bij `Geverifieerd` naam/geboortedatum/sterftedatum/licentie/bron-URI.
  `ExternalArchivePreviewStatusColors`: groen (7.87:1, hergebruikt) voor Geverifieerd, een nieuwe
  amberkleur `0xFF8A5A00` (5.93:1) voor Geen match en rood (6.57:1, hergebruikt) voor Niet
  bereikbaar - alle drie ≥4.5:1 WCAG 2.1 AA. Twee knoppen ("Bevestig brongegevens en gebruik bij
  record"/"Sla op zonder externe brongegevens") met stabiele `Key`s voor toetsenbordtests.
- `frontend-admin/lib/recordintake/admin_record_intake.dart`: `RecordIntakeInput` uitgebreid met
  `deceasedStatus`/`nextOfKinConfirmed`/`confirmExternalArchiveData` (naar `toJson`, alleen gezet
  wanneer niet-`null`). `RecordIntakeCreationResult` uitgebreid met `externalArchiveDataStored`/
  `externalArchiveDataReason` (uit `RecordIntakeResponse.externalArchiveData`), zodat het formulier
  een eventuele weigeringsreden kan tonen. Nieuw: `RecordIntakeExternalArchivePreviewResult` +
  `RecordIntakeExternalArchivePreviewStatus`-wire-constanten en
  `AdminRecordIntakeSource.previewExternalArchiveData`/`AdminRecordIntakeClient`-implementatie
  (`POST /api/record-intake/external-archive-preview`, zonder autorisatieheader - de backend
  vereist die voor dit endpoint niet). Een clientside netwerkfout valt terug op `NIET_BEREIKBAAR`.
- `frontend-admin/lib/recordintake/record_intake_form.dart`:
  - Timer-gebaseerde debounce (400 ms, `_archivePreviewDebounce`) op wijzigingen van het bestaande
    `durableUrl`-veld, plus een nieuwe knop "Ophalen" ernaast (`force`-fetch, negeert de debounce/
    dedupe) en een focus-listener die bij het verlaten van het veld direct (niet gedebiast)
    bevraagt. Een oplopende `_archivePreviewRequestId` negeert trage, ingehaalde responses; een
    `_lastPreviewedDurableUrl`-dedupe voorkomt een overbodige tweede aanroep wanneer de debounce al
    exact dezelfde waarde heeft opgehaald vóórdat het veld verlaten wordt - samen garanderen ze
    precies één netwerkaanroep per stabiele waarde (AC1).
  - `ExternalArchivePreviewPanel` verschijnt direct na de duurzame-URL-/Ophalen-rij (vóór
    koppelmotivering/onzekerheid), zodat de Tab-volgorde logisch is: URL-veld -> Ophalen -> Bevestig
    -> Sla op zonder externe brongegevens -> koppelmotivering (AC8). Beide paneelknoppen zetten
    lokale `_confirmExternalArchiveData`-state (`true`/`false`/`null` zolang onbeslist); een nieuwe
    preview-fetch reset deze keuze.
  - Nieuwe velden "Overlijdensstatus" (`DropdownButtonFormField`, default `onbekend`) en "Bevat
    gegevens van een nog levende nabestaande" (`CheckboxListTile`, default `false`), naar het
    patroon van de bestaande privacyclassificatie-dropdown. Beide plus
    `confirmExternalArchiveData` gaan mee in `_buildInput()`.
  - Succesbericht na opslaan bevat, wanneer `confirmExternalArchiveData=true` was, ook
    `externalArchiveDataReason` uit de respons (bv. de expliciete AC4/AC5-weigeringsreden), via het
    bestaande `Semantics(liveRegion: true)`-statuspatroon.
  - `_clearForm()` reset ook de nieuwe velden en annuleert een eventuele lopende debounce-timer.
- Tests: `frontend-admin/test/external_archive_preview_panel_test.dart` (nieuw, naar het patroon
  van `privacy_classification_status_view_test.dart`): semantiekboom per status (label + apart
  icoon-`semanticLabel`), kernvelden alleen bij Geverifieerd, beide actieknoppen roepen hun
  callback aan, en een WCAG 2.1-contrasttest (≥4.5:1) voor alle drie de statuskleuren.
  `frontend-admin/test/record_intake_form_test.dart` uitgebreid: debounce-aanroeptelling bij snel
  opeenvolgende wijzigingen (exact 1 aanroep), directe aanroep bij het verlaten van het veld,
  opslaan-zonder-externe-gegevens voor zowel een niet-matchend patroon als "Geen match" (AC2),
  bevestigen stuurt `deceasedStatus`/`nextOfKinConfirmed`/`confirmExternalArchiveData=true` mee, en
  een toetsenbord-only doorloop (`tester.sendKeyEvent`, Tab/Enter/Spatie, geen tap) van
  Ophalen-knop -> Bevestig-knop -> Sla op zonder-knop. Bestaande `_RecordingSource` (dit bestand) en
  de fakes in `test/widget_test.dart` uitgebreid met de nieuwe interfacemethode
  `previewExternalArchiveData`.
- Backend is in deze subtaak niet gewijzigd (dat was hkh-67); `RecordIntakeService`/
  `RecordIntakeExternalArchivePreviewController` zijn hergebruikt zoals opgeleverd.
- Bewuste keuze: de bestaande knoplabel "Intake indienen" is niet hernoemd naar "Opslaan record".
  De AC's/beschrijving noemen "Opslaan record" beschrijvend ("laat 'Opslaan record' beschikbaar")
  zonder een letterlijke hernoeming te vragen; een rename zou een niet-gevraagde breaking change
  op een reeds bestaande, goedgekeurde knop zijn geweest.
- Volledig vangnet gedraaid en groen: backend `mvn -B --no-transfer-progress clean verify`,
  `frontend`/`frontend-admin` `flutter analyze` (geen meldingen), `flutter test --concurrency=1
  --reporter expanded` (beide apps, alle tests groen) en `frontend flutter build web`
  (succesvolle release-build). `dart format` toegepast op gewijzigde/nieuwe bestanden.
- Niet gedaan / buiten scope: geen wijziging aan de backend-contracten, aan `externalverification`/
  `privacyclassification`, of aan `frontend/` (gebruikersapp) - deze story raakt uitsluitend
  `frontend-admin`.
