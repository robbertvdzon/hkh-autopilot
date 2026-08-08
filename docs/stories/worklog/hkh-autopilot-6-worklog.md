# hkh-autopilot-6 - Worklog

Story-context bij eerste pickup:
Per-record licentiecontrole voor archieven.nl-koppelingen

Voeg een per-record hergebruikslicentiecontrole toe aan het externalverification-domein. Backend: ArchivesNlClient/ArchiveJsonLdRecord/ArchiveRecordFields uitbreiden met een license-veld uit het JSON-LD-antwoord van dat specifieke record (geen caching/hergebruik tussen records). Nieuw, los licentiestatus-domeinbegrip (LICENSE_KNOWN met waarde+controledatum / LICENSE_UNKNOWN als fail-closed default), los van de bestaande VERIFIED/UNVERIFIED-enum. ExternalVerificationRecord, ExternalVerificationStore/Repository en een nieuwe Flyway-migratie (na V5__external_verification.sql) uitbreiden met de licentievelden, backward-compatible met bestaande rijen. ExternalVerificationService.verify geeft de licentievelden door. ExternalVerificationPublishGuard weigert publicatie ook bij LICENSE_UNKNOWN, ongeacht verificatiestatus, met eigen leesbare reden. Frontend-admin: nieuwe statusbadge-widget (tekstlabel + icoon, contrastratio ≥4,5:1) naar het patroon van PrivacyClassificationStatusView, getoond naast de bestaande verificatie- en privacystatusbadges. Schrijf hierbij alle benodigde unit-/widget-/contrasttests: twee JSON-LD-fixtures (met en zonder licentie), test dat License unknown publicatie blokkeert, test dat twee records uit dezelfde collectie onafhankelijke licentie-uitkomsten hebben, en een Flutter widget-/semantiek- plus contrasttest voor de badge (vervangt axe-core conform bestaande repo-conventie).

Stappenplan:
[ ]: read issue and target docs
[ ]: implement requested changes
[ ]: run relevant tests
[ ]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
