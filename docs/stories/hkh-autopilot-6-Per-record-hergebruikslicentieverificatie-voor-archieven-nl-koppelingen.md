# hkh-autopilot-6 - Per-record hergebruikslicentieverificatie voor archieven.nl-koppelingen

## Story

Per-record hergebruikslicentieverificatie voor archieven.nl-koppelingen

<!-- refined-by-factory -->

## Samenvatting
Bij het extern verifiëren van een archieven.nl-record controleert het systeem voortaan ook of dat specifieke record een hergebruikslicentie vermeldt (bijvoorbeeld CC0). Heeft een record geen zichtbare licentie, dan krijgt het de status 'License unknown' en wordt het automatisch niet gepubliceerd — ook als andere records uit dezelfde archiefcollectie wél een licentie hebben. Zo voorkomen we dat we ten onrechte aannemen dat een hele collectie dezelfde licentie deelt. De licentiestatus wordt apart getoond, naast de bestaande verificatie- en privacystatus, met een tekstlabel en icoon.

## Scope
- Uitbreiding van de externe verificatieflow (module `nl.vdzon.hkh.externalverification`) met een per-record licentiecontrole, uitgevoerd als onderdeel van dezelfde bevraging van archieven.nl die nu al plaatsvindt voor naam/geboortedatum/overlijdensdatum (`ArchivesNlClient`/`RestClientArchivesNlClient`).
- De licentie-informatie wordt gelezen uit het JSON-LD/RDF-antwoord van het specifieke record (bijv. een `license`-veld); er wordt nooit een eerder resultaat van een ander record binnen dezelfde collectie hergebruikt of gecachet als aanname.
- Nieuw, apart licentiestatusveld (los van de bestaande verificatiestatus `VERIFIED`/`UNVERIFIED`) met minimaal de waarden: licentie bekend (met de vastgestelde licentiewaarde en controledatum) en `License unknown`.
- Uitbreiding van de publicatieguard (`ExternalVerificationPublishGuard`) zodat publicatie ook geweigerd wordt wanneer de licentiestatus `License unknown` is, ongeacht de verificatiestatus.
- Frontend-admin: nieuwe statusbadge-view voor de licentiestatus, naast de bestaande status-weergaven (patroon: tekstlabel + icoon, kleurcontrast ≥4,5:1, zoals `PrivacyClassificationStatusView`).
- Buiten scope: wijzigingen aan de bestaande naam-/datumverificatielogica (`ExternalVerificationMatcher`), aan het toegangstoken-mechanisme, en een algemene publicatieworkflow (die bestaat nog niet; alleen de guard wordt uitgebreid).

## Acceptance criteria
- Voor elk extern geverifieerd record wordt de hergebruikslicentie opgehaald uit het JSON-LD/RDF-antwoord van dát specifieke record (niet uit een collectiebrede of gecachete waarde). Geautomatiseerd getest met twee fixtures: één archiefrecord met zichtbare licentie, één zonder.
- Ontbreekt de licentie-informatie in het externe record, dan krijgt het record licentiestatus 'License unknown' en wordt het automatisch uitgesloten van publicatie (verificatiestatus alleen is dan niet voldoende voor publicatie). Geautomatiseerd getest.
- Is een licentie aanwezig (bijv. CC0), dan wordt deze samen met de controledatum bij dat specifieke record opgeslagen. Geautomatiseerd getest op de aanwezigheid en waarden van deze velden.
- Twee records uit dezelfde archiefcollectie kunnen elk hun eigen, onafhankelijke licentie-uitkomst hebben (één met licentie, één zonder); geen van beide wordt afgeleid van de ander. Geautomatiseerd getest met twee records uit dezelfde collectie en verschillende licentie-uitkomsten.
- De licentiestatus wordt getoond als apart statusbadge (tekstlabel + icoon) naast de bestaande verificatie- en privacystatusbadges. Toegankelijkheid wordt geautomatiseerd getest via een Flutter widget-/semantiektest op de aanwezigheid van tekstlabel + icoon, plus een gerichte contrasttest (WCAG 2.1-formule, ≥4,5:1) — dit vervangt axe-core conform de bestaande repo-conventie (er is geen axe-core-tooling in deze Kotlin/Flutter-repo; zie eerdere stories in dit domein).

## Aannames
- De exacte plaats van de licentie-informatie in het JSON-LD/RDF-antwoord (bijv. veldnaam `license` of een vergelijkbaar RDF-predicaat) wordt door de developer bepaald aan de hand van de twee testfixtures; dit is een implementatiedetail en geen blokkerende vraag.
- De licentiestatus is een nieuw, los veld/domeinbegrip en wordt niet samengevoegd met de bestaande verificatiestatus-enum (`VERIFIED`/`UNVERIFIED`), conform "apart statusbadge" in de acceptatiecriteria.
- Toegankelijkheidsverificatie gebeurt met de bestaande repo-conventie (Flutter widget-/semantiektest + gerichte contrasttest) in plaats van axe-core, zoals eerder toegepast in dit domein (o.a. bij privacyclassificatie en externe verificatie).
- Er bestaat nog geen algemene publicatieworkflow; deze story breidt alleen de bestaande `ExternalVerificationPublishGuard` uit met de licentiecheck, zodat een latere publicatiefeature deze kan hergebruiken.

## Eindsamenvatting

I have everything needed. This is a phase-JSON conflict noted in the agent tips (task says "summary-finished", contract says "summarized") — I'll follow the contract per instructions and flag it.

## Eindsamenvatting — hkh-39 (Eindsamenvatting story hkh-autopilot-6)

**Gebouwd:** Per-record hergebruikslicentiecontrole voor archieven.nl-koppelingen, toegevoegd aan het `externalverification`-domein.

- **Backend:** `ArchivesNlClient`/`RestClientArchivesNlClient` leest nu ook het `license`-veld uit het JSON-LD-antwoord van hetzelfde HTTP-verzoek dat al voor naam/geboortedatum/overlijdensdatum gebruikt werd (geen extra request). Nieuw, los domeinbegrip `ExternalVerificationLicenseStatus`/`-Result`/`-Evaluator`, gescheiden van de bestaande `VERIFIED`/`UNVERIFIED`-status. `ExternalVerificationRecord`, de store/repository en de API-response zijn uitgebreid met licentievelden, ondersteund door Flyway-migratie `V6__external_verification_license.sql` (backward-compatible, bestaande rijen krijgen automatisch de fail-closed default). `ExternalVerificationPublishGuard` weigert publicatie nu ook bij `LICENSE_UNKNOWN`, onafhankelijk van de verificatiestatus.
- **Frontend-admin:** nieuwe `LicenseStatusView`-badge (tekstlabel + icoon) naar het patroon van de bestaande `PrivacyClassificationStatusView`, met widget-/semantiek- en contrasttest (WCAG ≥4,5:1, gemeten 7,87:1 / 6,57:1).

**Belangrijke keuze:** licentiestatus is fail-closed — ontbrekende, lege of onverwachte waarden resulteren altijd in `LICENSE_UNKNOWN`, nooit in het overnemen van een licentiewaarde van een ander record uit dezelfde collectie. Dit is expliciet getest met twee records uit dezelfde collectie die elk hun eigen, onafhankelijke licentie-uitkomst behouden.

**Getest:** Backend `mvn clean verify` — 160 tests, 0 failures. Frontend-admin `flutter analyze` schoon, `flutter test` 22/22 groen (met bekend, bevestigd omgevingsartefact rond testconcurrency, geen echte regressie — bevestigd via geïsoleerde run en `-j 1`). Alle acceptatiecriteria uit de story zijn expliciet in de tests teruggevonden (per-record afleiding, fail-closed blokkade van publicatie, opslag van waarde+controledatum, badge-toegankelijkheid).

**Bewust niet gedaan:** geen wijzigingen aan `ExternalVerificationMatcher` (naam-/datumverificatie), het toegangstoken-mechanisme, of een algemene publicatieworkflow (bestaat nog niet) — conform de vastgelegde scope-afbakening. De `LicenseStatusView` is, net als het bestaande `PrivacyClassificationStatusView`-patroon, nog niet in een scherm verankerd; dit is bestaande repo-conventie en geen regressie.

**Proceskanttekening:** de phase-JSON in de rolinstructies van `.task.md` (`summary-finished`) wijkt af van het opdrachtcontract (`summarized`). Conform eerdere agent-tip is het opdrachtcontract gevolgd.

<!-- deploy-summary:start -->
Bij het controleren van archiefkoppelingen wordt nu ook automatisch gecheckt of een record een hergebruikslicentie heeft. Ontbreekt die licentie, dan wordt dat record niet gepubliceerd, ook al zijn andere gegevens van het record al goedgekeurd. Dit is per record afzonderlijk zichtbaar met een eigen statuslabel, los van de bestaande verificatiestatus.
<!-- deploy-summary:end -->
