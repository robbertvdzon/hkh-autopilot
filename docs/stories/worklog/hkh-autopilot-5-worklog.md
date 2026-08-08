# hkh-autopilot-5 - Worklog

Story-context bij eerste pickup:
External verification module: archieven.nl-koppeling, matching en publish-guard

Bouw de zelfstandige Spring Modulith-module nl.vdzon.hkh.externalverification (package-info.java, allowedDependencies={}, opgenomen in ModulithArchitectureTest) naar het patroon van recordintake/privacyclassification: domeinmodel voor lokale naam/geboortedatum/overlijdensdatum en archieven.nl adtid/guid (ruwe String?-velden, parse-conversie); een HTTP-cliënt die http://opendata.archieven.nl/id/<adtid>/<guid> bevraagt met Accept: application/ld+json zonder autorisatietoken (optioneel invoerveld + AES-versleutelde opslag alleen wanneer het endpoint expliciet een token eist, nooit leesbaar in respons/logging); matchlogica die naam- en datumvelden vergelijkt en Verified/Unverified oplevert (fail-closed bij fout of ongeldige guid) met verplichte leesbare toelichting; een Flyway-migratie V5__external_verification.sql die uitsluitend externe URI, gematchte velden, controletijdstip en status opslaat (geen volledige externe payload); een ExternalVerificationPublishGuard naar het patroon van PrivacyPublishGuard die publicatie weigert bij Unverified; en in frontend-admin/lib/externalverification/ een statusview met een externe link met programmatisch gekoppeld aria-label dat aankondigt dat de link een externe bron in een nieuw tabblad opent. Schrijf hierbij alle automatische tests die de acceptatiecriteria dekken: een integratietest tegen een fixture/mock-endpoint zonder autorisatietoken, minimaal 2 matching-fixtures (Verified), 1 fixture met ongeldige guid (Unverified + publish-guard weigert), een test op de opgeslagen veldenset (geen volledige payload), een test die logoutput en API-respons controleert op afwezigheid van de tokenwaarde, en een Flutter-widgettest op de semantiekboom die het aria-label van de externe link controleert.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Nieuwe zelfstandige Spring Modulith-module `nl.vdzon.hkh.externalverification`
  (`allowedDependencies = {}`, opgenomen in `ModulithArchitectureTest`), naar het patroon van
  `recordintake`/`privacyclassification`:
  - `ExternalVerificationRequest`: ruwe `String?`-invoervelden (`localIdentifier`, `name`,
    `birthDate`, `deathDate`, `adtid`, `guid`, optioneel `accessToken`), los van bestaande
    persistente records.
  - `ExternalVerificationValidator`: verzamelt alle veldfouten (nooit fail-fast), fail-closed op
    onverwachte fouten.
  - `ArchivesNlClient`/`RestClientArchivesNlClient`: bevraagt
    `http://opendata.archieven.nl/id/<adtid>/<guid>` met header `Accept: application/ld+json`
    zonder autorisatietoken; een token wordt alleen meegestuurd als het verzoek er expliciet één
    bevat. 401/403 wordt herkend als `AuthenticationRequired`, 404/overige fouten als `NotFound`
    (fail-closed). Basis-URI is overschrijfbaar via
    `hkh.externalverification.archives-base-url` (env
    `HKH_EXTERNAL_VERIFICATION_ARCHIVES_BASE_URL`), uitsluitend voor tests tegen een lokale
    fixture.
  - `ExternalVerificationMatcher`: `VERIFIED` alleen wanneer naam, geboortedatum én
    overlijdensdatum (genormaliseerd: trim + lowercase) alle drie overeenkomen; in alle andere
    gevallen (geen/gedeeltelijke match, ontbrekend archiefrecord, token vereist, onverwachte fout)
    `UNVERIFIED` met verplichte leesbare `reason`.
  - `ExternalVerificationTokenCipher`: AES-256-GCM-versleuteling van het optionele
    archiefendpoint-toegangstoken (sleutel via `hkh.externalverification.token-key`, env
    `HKH_EXTERNAL_VERIFICATION_TOKEN_KEY`); faalt fail-closed zonder geconfigureerde sleutel. Het
    token wordt nooit in leesbare vorm gelogd, opgeslagen of teruggegeven; alleen relevant zodra
    het archiefendpoint (vandaag niet het geval) expliciet een token eist.
  - `ExternalVerificationRepository` + Flyway-migratie `V5__external_verification.sql`: slaat
    uitsluitend externe URI, gematchte velden, controletijdstip, status en het (optionele)
    versleutelde token op - nooit de volledige externe JSON-LD-payload.
  - `ExternalVerificationPublishGuard`: losstaande, herbruikbare guard naar het patroon van
    `PrivacyPublishGuard`, weigert publicatie bij `UNVERIFIED` met
    `ExternalVerificationPublishBlockedException`.
  - `POST /api/external-verification` (`ExternalVerificationController`): valideert eerst, roept
    daarna de service aan; respons bevat uitsluitend metadata (nooit een tokenwaarde of de
    volledige externe payload).
- Frontend-admin: `frontend-admin/lib/externalverification/external_verification_link_view.dart`
  met `ExternalVerificationLinkView` - een link naar de archieven.nl-bron met een programmatisch
  gekoppeld `Semantics(link: true, label: ...)` dat expliciet aankondigt dat de link een externe
  bron in een nieuw tabblad opent (`linkSemanticLabel`), naar de bestaande
  toegankelijkheidsconventies (vergelijkbaar met `PrivacyClassificationStatusView`).
- Boyscout-fix: `DatabaseIntegrationTest` verwachtte nog 4 succesvolle migraties; bijgewerkt naar 5
  na de nieuwe `V5__external_verification.sql`.
- Tests (allemaal nieuw, alle AC's gedekt):
  - Backend: `ExternalVerificationValidatorTest`, `ExternalVerificationMatcherTest` (2 matching-
    fixtures + gedeeltelijke match + niet-gevonden guid + token-vereist), `RestClientArchivesNlClientTest`
    (embedded `HttpServer`-fixture: `Accept: application/ld+json`, geen tokenheader standaard,
    401/404-afhandeling, token alleen bij expliciete opgave), `ExternalVerificationTokenCipherTest`
    (round-trip, niet-deterministische ciphertext, fail-closed zonder sleutel),
    `ExternalVerificationPublishGuardTest`, `ExternalVerificationControllerTest` (unit, met fakes),
    en `ExternalVerificationApiIntegrationTest` (Testcontainers Postgres + embedded
    fixture-archiefserver): AC1 (geen autorisatietoken), AC2 (2 matching-fixtures -> `VERIFIED`),
    AC3 (ongeldige guid -> `UNVERIFIED` + `ExternalVerificationPublishGuard` weigert), AC4
    (opgeslagen kolommenset via `information_schema.columns`, uitsluitend de minimale velden),
    AC5 (Logback `ListAppender` + API-respons gecontroleerd op afwezigheid van de tokenwaarde).
  - Frontend-admin: `external_verification_link_view_test.dart` (widgettest op de semantiekboom;
    controleert aria-label op aanwezigheid van zowel "archieven.nl" als "nieuw tabblad").
- Configuratie: `hkh.externalverification.archives-base-url` en `hkh.externalverification.token-key`
  toegevoegd aan `application.properties`; `HKH_EXTERNAL_VERIFICATION_TOKEN_KEY` toegevoegd aan
  `secrets.env.example` (optioneel, blijft leeg zolang het archiefendpoint geen token eist).
- Volledig vangnet lokaal groen: backend `mvn clean verify` (145 tests, incl. Testcontainers via
  de Docker-socket in deze omgeving), `frontend-admin` analyze/test, `frontend`
  analyze/test/build web. `.factory/verification.yaml` behoefde geen wijziging (bestaande
  `backend/`-pathPrefix dekt de nieuwe module al).
- Niet gedaan: geen aparte Flutter-invoerveld-UI voor het archiefendpoint-toegangstoken gebouwd -
  volgens de refined story-scope ("Buiten scope: ... het daadwerkelijk bouwen van een
  tokenprotocol voor een endpoint dat vandaag geen autorisatie vereist") is alleen het
  backend-invoerveld + de versleutelde opslag voorbereid; het archiefendpoint eist vandaag geen
  token.

## Testnotities (hkh-32, tester)

- `backend`: `mvn -B --no-transfer-progress clean verify` — BUILD SUCCESS, 145 tests, 0 failures, 0
  errors (Testcontainers Postgres via de Docker-socket werkte in deze omgeving). Externalverification-
  tests dekken alle AC's: geen autorisatietoken standaard, 2 matching-fixtures -> VERIFIED, ongeldige
  guid -> UNVERIFIED + publish-guard weigert, opgeslagen kolommenset (uitsluitend minimale velden),
  en logoutput/API-respons zonder tokenwaarde.
- `frontend-admin`: `flutter analyze` — geen issues. `flutter test` — 18/18 groen (`All tests
  passed!`), inclusief `external_verification_link_view_test.dart` (aria-label bevat zowel
  "archieven.nl" als "nieuw tabblad"). Bekend weergave-artefact (zie agent-tip
  frontend-admin-flutter-test-concurrency-artifact) trad hier niet storend op; run was volledig
  groen zonder herhaling nodig.
- `frontend`: geen wijzigingen in dit issue, niet opnieuw gedraaid.
- Code steekproefsgewijs gelezen (`ExternalVerificationMatcher`, `ExternalVerificationPublishGuard`,
  `ArchivesNlClient`, `ExternalVerificationLinkView`): gedrag komt overeen met de acceptatiecriteria.
- Geen bugs gevonden. `git status` was clean voor en na het testen; geen testdata/cleanup nodig.
