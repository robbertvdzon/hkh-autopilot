# hkh-autopilot-5 - Extern verificatie-anker via archieven.nl open data zonder autorisatietoken

## Story

Extern verificatie-anker via archieven.nl open data zonder autorisatietoken

<!-- refined-by-factory -->

## Samenvatting
We willen dat het systeem zelf kan controleren of een lokaal genealogisch record (zoals een bidprentje) overeenkomt met het publieke, vrij toegankelijke archief van archieven.nl/Noord-Hollands Archief. Er is geen inlogtoken nodig om deze bron te bevragen. Het systeem vergelijkt naam en geboorte-/overlijdensdatum van het lokale record met wat het archief teruggeeft en zet daarmee automatisch een label 'Verified' of 'Unverified'. Een niet-geverifieerd record wordt automatisch tegengehouden bij publicatie. Er wordt bewust geen zwaar inlogmechanisme gebouwd, omdat dat nu niet nodig is; alleen als het archief ooit zelf om een toegangscode vraagt, komt er precies één invoerveld daarvoor, en die code wordt dan versleuteld bewaard en nooit zichtbaar getoond of gelogd. Op het scherm waar de archiefbron getoond wordt, is voor schermlezers duidelijk dat de link naar een externe bron in een nieuw tabblad opent.

## Scope
- Nieuwe, zelfstandige Spring Modulith-backendmodule `nl.vdzon.hkh.externalverification` (`package-info.java` met `@ApplicationModule(allowedDependencies = {})`, opgenomen in de moduleset van `ModulithArchitectureTest`), naar het patroon van `recordintake`: wél een eigen repository/migratie omdat deze module resultaten persisteert, geen afhankelijkheid op andere modules.
- Domeininvoer (ruwe, op zichzelf staande velden naar het patroon van `RecordIntake`/`LinkDossier`, niet gekoppeld aan een bestaand persistent record): lokale identifier, naam, geboortedatum, overlijdensdatum en de archieven.nl-identifier (`adtid` + `guid`) waarmee de resolvebare URI `http://opendata.archieven.nl/id/<adtid>/<guid>` wordt opgebouwd.
- HTTP-cliënt die deze URI bevraagt met header `Accept: application/ld+json`, zonder autorisatietoken tenzij het endpoint expliciet één vereist (zie hieronder). Content-negotiation en JSON-LD-parsing gebeuren serverside; er wordt geen volledige externe brondata opgeslagen.
- Matchlogica die naam en geboorte-/overlijdensdatum van het lokale record vergelijkt met de opgehaalde JSON-LD-kernvelden en een status `Verified` (bij match) of `Unverified` (geen match, inclusief een niet-bestaande/ongeldige guid) oplevert, naar het patroon van `PrivacyClassificationResult` (verplichte, niet-lege leesbare toelichting).
- Opslag (nieuwe Flyway-migratie) van uitsluitend de minimale verificatievelden: externe URI, welke velden gematcht zijn, controletijdstip en de resulterende status — nooit de volledige externe JSON-LD-payload.
- Publish-guard binnen dezelfde module (naar het patroon van `PrivacyPublishGuard`) die publicatie weigert voor `Unverified`-records en toestaat voor `Verified`-records; er is nog geen bestaande publicatieworkflow in de repo om op aan te sluiten, dus dit is een op zichzelf staande, herbruikbare guard.
- Optioneel, uitsluitend-bij-noodzaak tokenmechanisme: één invoerveld dat alleen getoond wordt wanneer het archiefendpoint zelf expliciet een toegangstoken eist (bijv. herkenbaar aan een specifieke autorisatie-foutrespons); het token wordt versleuteld opgeslagen (nieuw, want er bestaat in deze repo nog geen precedent voor het versleuteld bewaren van uitgaande tokens — de bestaande Nimbus/JWKS-code verifieert uitsluitend inkomende tokens) en nooit in leesbare vorm getoond, gelogd of in een respons opgenomen.
- Zichtbaarheid in `frontend-admin`: een link naar de externe archiefbron met een programmatisch gekoppeld `aria-label`/semantisch label dat aankondigt dat de link een externe bron in een nieuw tabblad opent, naar de bestaande toegankelijkheidsconventies van `frontend-admin`.
- Toegankelijkheidsverificatie via een Flutter-widgettest op de semantiekboom (bestaande repo-conventie in plaats van axe-core, dat niet aanwezig is in deze Kotlin/Flutter-repo) die het aria-label van de externe link controleert.
- Buiten scope: de daadwerkelijke publicatieworkflow zelf, koppeling met andere externe archieven dan het archieven.nl/Noord-Hollands Archief-patroon, en het daadwerkelijk bouwen van een tokenprotocol voor een endpoint dat vandaag geen autorisatie vereist (alleen het invoerveld en de versleutelde opslag ervoor worden voorbereid).

## Acceptance criteria
- Gegeven een lokaal record met een gekoppelde archieven.nl-identifier (`adtid`/`guid`), bevraagt het systeem de resolvebare URI met header `Accept: application/ld+json` zonder autorisatietoken; geverifieerd via een geautomatiseerde integratietest tegen een fixture-/mock-endpoint.
- Wanneer naam- en datumvelden van het lokale record overeenkomen met de opgehaalde externe JSON-LD-kernvelden, krijgt het record status `Verified`; getest met minimaal 2 verschillende matching-fixtures.
- Wanneer geen match wordt gevonden (bijvoorbeeld bij een niet-bestaande of ongeldige guid), krijgt het record status `Unverified` en weigert de publish-guard publicatie voor dat record; getest met een fixture met een ongeldige guid en een geautomatiseerde test op de guard.
- Het systeem slaat uitsluitend de minimale verificatievelden op (externe URI, gematchte velden, controletijdstip, status) en dupliceert geen volledige externe brondata; gecontroleerd via een geautomatiseerde test op de opgeslagen veldenset (bijv. reflectie/schema-assertie op de migratie/entiteit).
- Alleen wanneer het archiefendpoint in de mock-fixture expliciet een toegangstoken vereist, toont het systeem één invoerveld hiervoor; het token wordt versleuteld opgeslagen en verschijnt nooit in leesbare vorm in UI-respons of logoutput; geverifieerd met een geautomatiseerde test die logoutput en API-responses controleert op afwezigheid van de tokenwaarde.
- Een geautomatiseerde Flutter-widgettest op de semantiekboom van `frontend-admin` bevestigt dat de externe link naar archieven.nl een aria-label/semantisch label heeft dat aankondigt dat een externe bron in een nieuw tabblad opent.

## Aannames
- De velden naam, geboortedatum en overlijdensdatum bestaan nog niet in een persistent domeinmodel in deze repo; deze story introduceert ze als eigen, op zichzelf staande invoervelden in de nieuwe module `externalverification`, naar het patroon van `RecordIntake`/`LinkDossier` (ruwe input, geen koppeling aan `recordintake` of `privacyclassification`).
- De archieven.nl-koppeling (`adtid`/`guid`) wordt als los invoerveld bij deze module gemodelleerd en niet automatisch afgeleid uit het bestaande `durableUrl`-veld van `record_intake_external_link`, omdat dat veld generiek is en geen `adtid`/`guid`-structuur garandeert.
- De externe link wordt getoond in `frontend-admin` (beheerfrontend), niet in de publieke gebruikersfrontend, naar analogie van `PrivacyClassificationStatusView` en `RecordIntakeForm`.
- Versleutelde tokenopslag wordt met een eenvoudig, app-beheerd symmetrisch mechanisme (bijv. AES met een sleutel uit configuratie/secrets, naar het patroon van de bestaande `secrets.env`-aanpak) gerealiseerd; er is geen bestaand precedent in de repo voor het versleuteld bewaren van uitgaande tokens, dus dit is nieuw, maar blijft ongebruikt zolang het archiefendpoint (zoals nu het geval is) geen token vereist.
- De toegankelijkheidsaudit wordt uitgevoerd met de bestaande repo-conventie (Flutter widget-/semantiektest) in plaats van axe-core, conform eerdere stories in deze repo.
- De publish-guard is een op zichzelf staande functie (nog niet gekoppeld aan een bestaande publicatieworkflow), omdat die workflow nog niet bestaat in de repo — zelfde patroon als `PrivacyPublishGuard`.
- De genoemde mogelijke overlap met kandidaat #15 is een niet-blokkerende waarschuwing volgens de Product Factory-beoordeling; deze wordt tijdens planning/implementatie gecontroleerd, maar blokkeert deze refinement niet.

## Eindsamenvatting

## Eindsamenvatting — hkh-33 (Story hkh-autopilot-5: Extern verificatie-anker via archieven.nl open data)

**Wat is gebouwd**

Een nieuwe, zelfstandige Spring Modulith-backendmodule `nl.vdzon.hkh.externalverification` (geen afhankelijkheden op andere modules, opgenomen in `ModulithArchitectureTest`), naar het patroon van `recordintake`/`privacyclassification`:

- **Domeininvoer**: `ExternalVerificationRequest` met ruwe invoervelden (lokale identifier, naam, geboorte-/overlijdensdatum, `adtid`/`guid`, optioneel toegangstoken), los van bestaande persistente records, met een validator die alle veldfouten verzamelt (fail-closed).
- **HTTP-cliënt** (`RestClientArchivesNlClient`) die `http://opendata.archieven.nl/id/<adtid>/<guid>` bevraagt met header `Accept: application/ld+json`, zonder autorisatietoken tenzij een verzoek er expliciet één meestuurt; 401/403 wordt herkend als "token vereist", 404/overig als "niet gevonden" (fail-closed).
- **Matchlogica** (`ExternalVerificationMatcher`): status `VERIFIED` alleen als naam, geboortedatum én overlijdensdatum alle drie overeenkomen (genormaliseerd); anders `UNVERIFIED` met verplichte leesbare toelichting.
- **AES-256-GCM tokenversleuteling** (`ExternalVerificationTokenCipher`), fail-closed zonder geconfigureerde sleutel; token wordt nooit leesbaar gelogd, opgeslagen of teruggegeven.
- **Opslag** (nieuwe migratie `V5__external_verification.sql`): uitsluitend externe URI, gematchte velden, controletijdstip, status en het (optionele) versleutelde token — nooit de volledige externe JSON-LD-payload.
- **Publish-guard** (`ExternalVerificationPublishGuard`): weigert publicatie bij `UNVERIFIED`, naar het patroon van `PrivacyPublishGuard`.
- **API**: `POST /api/external-verification`, respons bevat uitsluitend metadata, nooit een tokenwaarde of volledige externe payload.
- **Frontend-admin**: `ExternalVerificationLinkView` met een link naar de archieven.nl-bron, voorzien van een programmatisch gekoppeld `Semantics`-label dat aankondigt dat de link een externe bron in een nieuw tabblad opent.

**Keuzes**

- Basis-URI van de archiefkoppeling is overschrijfbaar via configuratie/env, uitsluitend om lokaal tegen een fixture-server te kunnen testen.
- Het toegankelijkheidslabel is geverifieerd via een Flutter-widgettest op de semantiekboom (bestaande repo-conventie) in plaats van axe-core, dat niet in deze stack past.
- Boyscout-fix: `DatabaseIntegrationTest` bijgewerkt van 4 naar 5 verwachte migraties na de nieuwe Flyway-migratie.

**Getest**

- Backend: `mvn clean verify` — 145 tests, 0 failures/errors, inclusief Testcontainers-Postgres. Alle acceptatiecriteria gedekt: geen autorisatietoken standaard, 2 matching-fixtures → `VERIFIED`, ongeldige guid → `UNVERIFIED` + guard weigert publicatie, opgeslagen kolommenset bevat uitsluitend de minimale velden, logoutput/API-respons bevatten nooit de tokenwaarde.
- Frontend-admin: `flutter analyze` schoon, `flutter test` 18/18 groen, inclusief de widgettest op het aria-label.
- Tester heeft de code steekproefsgewijs herlezen en bevestigt dat het gedrag overeenkomt met de acceptatiecriteria; geen bugs gevonden, `git status` bleef clean.

**Bewust niet gedaan**

Geen aparte Flutter invoerveld-UI voor het archiefendpoint-toegangstoken gebouwd — conform de scope-afbakening ("buiten scope: het daadwerkelijk bouwen van een tokenprotocol voor een endpoint dat vandaag geen autorisatie vereist"). Alleen het backend-invoerveld en de versleutelde opslag zijn voorbereid; het archiefendpoint eist vandaag geen token.

**Noot over procescontract**: de rolinstructies in `.task.md` schrijven als afsluitende JSON `{"phase":"summary-finished"}` voor, terwijl het opdrachtcontract `{"phase":"summarized"}` vraagt. Conform eerdere agent-tip volg ik het opdrachtcontract; dit verschil wordt hier expliciet gemeld.

<!-- deploy-summary:start -->
Het systeem kan nu automatisch controleren of gegevens uit een familiegeschiedenis-record (zoals naam en geboorte-/overlijdensdatum) overeenkomen met wat het openbare archief archieven.nl daarover vermeldt. Komt dit overeen, dan krijgt het record het label "geverifieerd"; komt het niet overeen, dan wordt publicatie van dat record automatisch tegengehouden totdat dit is opgelost. Op het beheerscherm is duidelijk aangegeven dat de link naar het archief in een nieuw tabblad opent, ook voor gebruikers van een schermlezer.
<!-- deploy-summary:end -->
