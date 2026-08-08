# hkh-31 - Reviewer notes

Review van de externalverification-module (backend + frontend-admin) t.o.v. main.

Bevindingen:
- [info] Module volgt exact het bestaande Modulith-patroon (`package-info.java`,
  `allowedDependencies = {}`, opgenomen in `ModulithArchitectureTest`), geen afhankelijkheden op
  andere modules.
- [info] Matchlogica (`ExternalVerificationMatcher`) is fail-closed en vereist alle drie velden
  (naam + beide datums) voor `VERIFIED`; genormaliseerd op trim+lowercase. Getest met 2
  matching-fixtures, een partial-match-fixture en een niet-gevonden/ongeldige-guid-fixture.
- [info] `ExternalVerificationPublishGuard` weigert correct bij `UNVERIFIED`, getest zowel unit
  als in de Testcontainers-integratietest.
- [info] Opslag (`V5__external_verification.sql`) bevat uitsluitend de toegestane minimale
  velden + het optionele versleutelde token; kolommenset-test (AC4) bevestigt dit expliciet via
  `information_schema.columns`.
- [info] Tokenpad: AES-256-GCM, fail-closed zonder sleutel, nooit in respons/domeinresultaat/log;
  geverifieerd met een Logback `ListAppender`-test + API-responsecheck (AC5). Geen aparte
  Flutter-invoerveld-UI gebouwd, expliciet buiten scope volgens de refined story ("... het
  daadwerkelijk bouwen van een tokenprotocol voor een endpoint dat vandaag geen autorisatie
  vereist") — consistent met de Aannames-sectie.
- [info] Frontend-admin `ExternalVerificationLinkView` heeft een `Semantics(link: true, label: ...)`
  die zowel "archieven.nl" als "nieuw tabblad" noemt; widgettest dekt dit op de semantiekboom.
  Component is (nog) niet in een scherm opgenomen — consistent met het bestaande
  `PrivacyClassificationStatusView`-patroon (ook standalone, geen bestaande workflow om op aan te
  sluiten).
- [info] Boyscout-fix `DatabaseIntegrationTest` (4→5 migraties) is correct en in scope.
- Geen blockers, bugs of regressies gevonden. Scope, AC-dekking en repo-conventies komen overeen.
