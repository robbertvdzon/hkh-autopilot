# Development

## Vereisten

- JDK 21 en Maven 3.9 of nieuwer;
- Flutter stable 3.44.7 met Dart 3.12.2 (gelijk aan CI);
- voor lokaal backendgebruik: Docker met Compose en een `secrets.env` op basis van
  `secrets.env.example`.

## Repositorystructuur

- `backend/`: Kotlin, Spring Boot, Spring Modulith, Maven en backendtests; features zijn modules
  onder `nl.vdzon.hkh` met een eigen `package-info.java`, waaronder de interne domeinmodule
  `linkdossier` met de koppelingsdossiervalidator, de interne domeinmodule
  `privacyclassification` met de AVG-classificatie van genealogische records
  (`PrivacyClassifier`/`PrivacyPublishGuard`, aangevuld met `LivingPersonAgeRule` die per genoemde
  persoon de FamilySearch 110/95-jaarregel toepast, en met `GedcomResnRule` die het optionele
  `GenealogicalRecord.gedcomSource`-veld (ruwe GEDCOM 7.0-brontekst) recursief doorzoekt naar een
  blokkerende RESN-markering en die onafhankelijk en bindend meeweegt), de module `recordintake` met het
  `POST /api/record-intake`-endpoint (tokenverificatie, veld- en privacyvalidatie, opslag als
  intern concept plus optionele externe conceptkoppeling, Flyway-migratie `V4__record_intake.sql`),
  een nieuwe admin-only bevestigingsactie (`POST
  /api/admin/record-intake/{localIdentifier}/confirm`, hergebruikt `AdminAuthenticator`, zet
  `confirmed_by`/`confirmed_at` via `V9__record_intake_confirmation.sql`, expliciete afhankelijkheid
  op `auth`) en een nieuwe publieke, ongeauthenticeerde route (`GET
  /api/records/{localIdentifier}`, `RecordPublicStatusResolver` berekent per verzoek
  `NO_INTAKE`/`SAVED_WITHOUT_SOURCE`/`CONFIRMED` inclusief zelfherstellend gedrag bij een
  herclassificatie naar `Blocked`, altijd HTTP 200, nooit de ruwe `RecordIntakeRecord`)
  en de module `externalverification` met het `POST /api/external-verification`-endpoint
  (bevraagt archieven.nl zonder autorisatietoken, matcht naam en datumvelden tot status
  `Verified`/`Unverified`, beoordeelt per record en los daarvan de hergebruikslicentie tot
  `LICENSE_KNOWN`/`LICENSE_UNKNOWN` (`ExternalVerificationLicenseEvaluator`), `ExternalVerificationPublishGuard`
  (weigert publicatie bij `Unverified` én bij `LICENSE_UNKNOWN`), versleutelde opslag van een optioneel
  archieftoegangstoken via `ExternalVerificationTokenCipher`, Flyway-migraties
  `V5__external_verification.sql` en `V6__external_verification_license.sql`), plus het
  herbruikbare, niet-persisterende `HistoricalMetadataContract` en de `OpenArchievenMetadataAdapter`
  voor individuele historische metadata-verificatie. De adapter leest alleen een allowlist van JSON-LD-
  metadata, retourneert alleen volledig gevalideerde inhoud bij complete en consistente brondata en
  geeft anders een veilige bronverwijzing met technische status. Metadatarechten en object-/media-
  rechten zijn afzonderlijk; onbekende objectrechten blokkeren metadata niet maar geven nooit
  `mediaAllowed`. De adapter gebruikt UTC-ophaaltijd, bronversie of snapshot, een beschrijvende
  user-agent en één procesbrede limiter met minimaal 251 ms tussen verzoeken. Ruwe payloads,
  persoonsgegevens en gevoelige waarden worden niet opgeslagen, geretourneerd of gelogd. De
  zelfstandige module `historicalsearch` voegt daarnaast `GET /api/historical-search` toe met
  genormaliseerde Europeana/Open Archieven-resultaten, bronkeuze, queryvalidatie, cursorpaginering,
  een geaggregeerde state (`RESULTS`, `NO_RESULTS`, `PARTIAL_AVAILABILITY` of `SOURCE_FAILURE`),
  per-bronstatus (`AVAILABLE`, `DISABLED`, `TEMPORARILY_UNAVAILABLE` of `INVALID_RESPONSE`; voor
  Open Archieven daarnaast `TIMEOUT`, `HTTP_ERROR`, `INVALID_JSON`, `MISSING_REQUIRED_FIELDS` of
  `RATE_LIMITED`),
  fail-closed metadata/statusmapping en zonder opslag van zoekopdrachten of bronpayloads). Het
  Open Archieven-resultaat exposeert in de publieke JSON-respons de door de bron geleverde
  `source_name`, de veilige referentie `stable_identifier` (`hee:uuid`) en
  `original_source_url`; voor een Heemskerk-zoekopdracht worden `name=Heemskerk` en
  `archive_code=hee` afzonderlijk meegestuurd. De bronlink wordt nooit lokaal geconstrueerd.
  Per bron bevat de genormaliseerde response nullable `querySemantics` met uitsluitend de
  semantische providerparameters die het Open Archieven-adapterverzoek werkelijk gebruikte, zoals
  `name` en `eventplace`. Technische parameters zoals `archive_code`, paginering en rate limiting
  worden uitgesloten. Als Open Archieven niet is bevraagd of de semantiek niet betrouwbaar kan
  worden vastgesteld, blijft dit veld `null`; de frontend toont dan
  `Zoekinterpretatie: niet beschikbaar.`. Herkende waarden worden per bron tekstueel getoond met
  Nederlandse labels en de providerparameter, bijvoorbeeld `naam (name)` of `plaats (eventplace)`;
  er wordt niets afgeleid uit zoekterm, metadata, titel of URL.
  Een Open Archieven-respons is alleen geldig met een object `response`, een array `docs`, een
  niet-negatieve numerieke `number_found` en verplichte, niet-lege `source_name`, `uuid` en
  absolute HTTP(S)-`original_source_url` per document; ontbrekende, lege, onveilige of
  tegenstrijdige waarden maken de bron ongeldig. Een lege `docs`-lijst met `number_found: 0`
  blijft een beschikbaar nulresultaat. Een niet-2xx antwoord is `HTTP_ERROR`, ook wanneer de body
  geldige JSON bevat; een lege of onleesbare body is `INVALID_JSON`; ontbrekende, onjuiste, lege of
  tegenstrijdige verplichte velden zijn `MISSING_REQUIRED_FIELDS`. Andere transportproblemen
  blijven `TEMPORARILY_UNAVAILABLE`. Per daadwerkelijke Open Archieven-poging schrijft de adapter
  precies één operationeel logevent met de vaste allowlist `event=OPEN_ARCHIEVEN_SEARCH`,
  `source=OPEN_ARCHIEVEN`, technische `outcome`, niet-negatieve `durationMs`, `httpStatusClass`
  (`1xx` t/m `5xx`) wanneer een HTTP-respons beschikbaar is en `processedResultCount` voor een
  beschikbare pagina, inclusief `0` voor een geldig nulresultaat. Bij fouten zonder HTTP-respons blijft
  de statusklasse leeg en bij niet-beschikbare pagina's blijft de verwerkte-resultatentelling leeg.
  Zoekwaarden, namen, queryparameters, URL's, bronpayloads, identifiers, exceptiontekst en stacktraces
  worden niet gelogd; er wordt geen zoekgeschiedenis of persistente loggingopslag toegevoegd. Het
  Open Archieven-verkeer heeft daarnaast een proceslokaal budget van maximaal 10 directe aanvragen
  en maximaal 60 aanvragen per rollende minuut per gebruikers-IP. Een vertrouwde proxycontext mag
  `X-Forwarded-For` leveren; zonder zo'n context gebruikt de backend het directe connection-IP.
  Een budgetoverschrijding geeft HTTP 429 met alleen `RATE_LIMITED`; een upstream-429 blijft een
  veilige bronstatus en krijgt hoogstens één toegestane retry. Het
  publieke zoekresultaat mapt uitsluitend expliciete `ALLOWED` en `RESTRICTED` rechtenwaarden;
  ontbrekende, lege, niet-herkende of tegenstrijdige waarden worden `UNKNOWN` en het vrije bronveld
  `rights` bepaalt geen gecontroleerde status. Het
  resultaatcontract bevat expliciete `place`-, `person`- en `event`-velden met elk een contextstatus
  (`AVAILABLE`, `MISSING`, `UNCERTAIN` of `UNAVAILABLE`); plaats wordt nooit uit zoekterm, titel of
  URL afgeleid. Het bevat ook `relationships[]` voor complete relaties uit de expliciete providerdata,
  met `type`, `source.name`, `target.name`, een stabiele HTTP(S)-`target.uri` en een externe
  HTTP(S)-`target.link`; ontbrekende of onveilige relaties worden volledig weggelaten en nooit uit
  metadata-overlap afgeleid. De beschikbare Flutter-resultaatkaart biedt `Context bekijken`; de detailpagina
  toont de genormaliseerde bronnaam, identifier en oorspronkelijke bronlink naast context-, bron-,
  rechten- en privacymetadata, herhaalt de zoek-/bronstatus en gebruikt
  expliciet `Niet beschikbaar`/`Onzeker` voor ontbrekende of onzekere context. Verwante resultaten
  worden uitsluitend uit de huidige zichtbare `results`-lijst bepaald, maximaal drie, na exacte
  deterministische normalisatie van plaats/persoon/gebeurtenis; een periode-overlap is alleen
  aanvullende informatie en creëert geen relatie. Per geselecteerde bron rapporteert de API ook
  nullable `resultCount` en `heemskerkCount` voor de huidige zichtbare pagina. Alleen zekere,
  expliciete plaatsmetadata die na NFKC-, witruimte- en hoofdletternormalisatie exact `Heemskerk`
  is, telt mee; niet-beschikbare bronnen krijgen geen numerieke telling en de indicatie wordt niet
  als historisch bewijs gepresenteerd. Dezelfde module biedt voor beheerders ook
  `GET /api/admin/historical-search`, achter `AdminAuthenticator`, met hetzelfde genormaliseerde
  zoekcontract en verzoekbudget. `HistoricalAdminStatusContract` geeft uitsluitend veilige,
  brongeleverde en met `sourceRecordId`/`stableUrl` consistente identiteitsvelden terug en leidt
  afzonderlijke `CONFIRMED`/`UNKNOWN`/`REJECTED`/`NOT_APPLICABLE`-statussen met redenen af voor
  bronverificatie, metadatarechten, privacy, publieke vrijgave en object-/mediarechten. Ruwe
  payloads, extra persoonsgegevens, zoekgeschiedenis en afgeleide relaties of rechtenclaims blijven
  buiten de response;
- `historical_context_detail.dart` kan daarnaast nieuwe zoekingangen aanbieden voor expliciete,
  niet-lege en zekere plaats-, persoons- en gebeurteniswaarden en voor een geldige expliciete periode
  met twee geordende viercijferige jaren. De gate vereist technische status `AVAILABLE`,
  metadatarechten `ALLOWED` en privacy `CLEAR`; ontbrekende, onzekere, tegenstrijdige, beperkte of
  uit titel/zoekterm/URL afgeleide waarden leveren geen actie op. De bestaande
  `HistoricalSearchSource` wordt hergebruikt met de oorspronkelijke waarde, zonder bronfilter, en
  start automatisch een nieuwe zoekingang over de standaardbronnen Europeana en Open Archieven. De
  vervolgpagina toont de waarde en de waarschuwing dat dit geen bewezen relatie tussen bronnen is;
  de route-stack bewaart terugnavigatie naar detail en resultatenlijst. Er is geen lokale opslag van
  zoekopdrachten, bronpayloads, media of klikgeschiedenis;
- `frontend/`: Flutter-gebruikersapp; homepage en statusflows staan in `lib/main.dart`,
  broninterfaces onder `lib/backend/`, `lib/news/` en `lib/historical/`, widgettests onder `test/`;
  de homepage heeft naast `Laatste nieuws` de ingang `Historisch zoeken` met een zelfstandige,
  toegankelijke `HistoricalSearchPage` voor vrije tekst, plek, persoon, gebeurtenis, periode,
  bronkeuze, laad/succes/leeg/gedeeltelijke-beschikbaarheid/bronprobleem/retry-statussen,
  paginering en externe-linklabels. Bij gedeeltelijke beschikbaarheid blijven beschikbare resultaten
  zichtbaar; bij volledige bronuitval toont de pagina geen resultaatcount en biedt zij `Opnieuw
  proberen` en `Zoekopdracht aanpassen`. Aanpassen houdt de route en ingevulde zoekwaarden intact en
  focust doelgericht het bestaande vrije-tekstveld; alleen deze bewuste actie verplaatst de focus.
  Bij gedeeltelijke beschikbaarheid met een uitgevallen bron is retry eveneens beschikbaar. De
  frontend bewaart per retrybare aanvraag één genormaliseerde zoekcontext (inclusief bronkeuze,
  pagina-offset en limiet); tijdens retry blijven de vorige geldige resultaten en bronstatussen
  zichtbaar, wordt dubbele activatie geblokkeerd en vervangt een geldige retry de vorige uitkomst
  volledig. Een transportfout of `SOURCE_FAILURE` behoudt geldige deelresultaten en meldt de nieuwe
  fout afzonderlijk, zonder ruwe payloads, exceptionteksten of zoekgeschiedenis in client-state.
  De zoekweergave toont daarnaast per bron de werkelijk gebruikte Open Archieven-semantiek via
  `querySemantics`; zonder Open Archieven-aanvraag of zonder betrouwbare semantiek verschijnt de
  vaste neutrale tekst `Zoekinterpretatie: niet beschikbaar.`. Technische providerparameters worden
  niet als inhoudelijke interpretatie gepresenteerd.
  Alle zoekstatussen gebruiken één `SemanticsRole.status`-node en de zichtbare laadindicator is
  semantisch uitgesloten. `lib/historical/historical_context_detail.dart` bevat de contextdetailweergave, de aparte
  sectie `Bronvastgelegde relatie` voor providerclaims en de begrensde afgeleide relatiebepaling;
  de resultaatkaart en detailweergave tonen metadatarechten en object-/mediarechten afzonderlijk met
  de gedeelde semantische, toetsenbordbedienbare `HistoricalRightsExplanation`; deze legt beide
  richtingen van de onafhankelijke beoordeling uit en dat `UNKNOWN` geen toestemming of weigering is;
  een resultaatkaart verschijnt alleen bij een niet-lege stabiele identifier en een absolute HTTP(S)-
  bron-URL. Voor Open Archieven moeten `source_name`, `uuid` en `original_source_url` uit het
  providercontract aanwezig en onderling consistent zijn; anders worden kaart en bronlink
  weggelaten. Toegestane inhoud gebruikt de expliciete titel met primaire beschrijving als fallback,
  zonder verzonnen placeholder wanneer beide ontbreken. Onbekende rechten- en privacystatussen
  worden als `Onbekend` getoond; de externe actie kondigt zichtbaar een nieuw tabblad aan;
  Unicode-normalisatie gebruikt de directe dependency
  `unorm_dart`. `lib/records/`
  bevat de nieuwe publieke recorddetailpagina (`RecordDetailPage`) met de in-/uitklapbare sectie
  "Externe bronverificatie" (leest `GET /api/records/{localIdentifier}` via de nieuwe
  `RecordPublicSource` op `BackendClient`), inclusief een conditionele externe-linkopener
  (`external_link_launcher.dart`, nieuwe dependency `web: ^1.1.0`);
- `frontend-admin/`: afzonderlijke Flutter-webbeheerapp en widgettests; naast nieuws- en
  recordintakebeheer bevat deze `lib/historical/admin_historical_search.dart` en
  `admin_historical_search_view.dart` voor de auth-afgeschermde historische zoekstatusweergave.
  De view toont bronidentiteit en alle status-/redenvelden tekstueel, met minimaal 4,5:1 contrast en
  zonder ruwe bronpayloads;
- `deploy/`: OpenShift-, Kustomize- en ArgoCD-manifests;
- `.factory/verification.yaml`: machine-leesbaar, revisiongebonden verificatievangnet.

De productie-overlay `deploy/overlays/openshift` en de acceptatie-overlay
`deploy/overlays/acceptance` gebruiken dezelfde canonieke, niet-geheime ConfigMap
`deploy/base/open-archieven-config.yaml`. Deze bevat endpoint `https://api.openarchieven.nl/1.1`,
zoekpad `/records/search.json`, `name`, `eventplace`, `number_show`, `start`, de Heemskerk-
`archive_code=hee`-mapping, timeout `10s`, cache `30s`, rate-limitinterval `251ms` en het budget
`60` per rollende minuut met burst `10` en refill `1.0` per seconde. Overlay-specifieke afwijkingen
zijn niet toegestaan; fixture- en mock-overschrijvingen zijn alleen lokaal of in tests toegestaan.
De pariteit en syntactische geldigheid worden gecontroleerd door
`Hkh195OpenArchievenConfigurationContractTest`, die de effectieve Kustomize-output van beide
overlays tegen één contract vergelijkt.

De gebruikersfrontend krijgt de backendbasis tijdens compilatie via
`--dart-define=API_BASE_URL=https://...`; zonder define gebruikt hij `http://localhost:8080`.

## Lokaal draaien

Start PostgreSQL en de backend vanuit de repositoryroot:

```bash
cp secrets.env.example secrets.env
docker compose -f docker-compose.dev.yml up -d
mvn -f backend/pom.xml spring-boot:run
```

Start de webfrontend in een tweede shell:

```bash
cd frontend
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080
```

## Volledig verplicht vangnet

Voer vóór afronding alle onderstaande commando's uit, ook wanneer slechts één component gewijzigd
is. Dit is dezelfde commandoset als `.factory/verification.yaml`; elk commando moet eindigen met
exitcode 0, 0 failures en 0 errors.

```bash
(cd backend && mvn -B --no-transfer-progress clean verify)
(cd frontend && flutter analyze)
(cd frontend && flutter test)
(cd frontend && flutter build web)
(cd frontend-admin && flutter analyze)
(cd frontend-admin && flutter test)
```

De webbuild staat daarna in `frontend/build/web/`. Gebruik voor een handmatige schermlezertest een
build met een expliciete testbackend, bijvoorbeeld
`flutter build web --dart-define=API_BASE_URL=https://test.example`.

## Conventies en teststrategie

- Dart-code wordt met `dart format` geformatteerd en moet `flutter analyze` zonder meldingen halen.
- Statusbronnen worden achter `BackendStatusSource` en `LatestNewsSource` geïnjecteerd; widgettests
  gebruiken deterministische fakes en `Completer`s voor laden, fout, retry, leeg en succes. De
  historische retry-tests controleren contextgelijkheid, behoud tijdens een lopende/mislukte retry,
  volledige vervanging na succes en het voorkomen van dubbele aanvragen.
- Widgettests inspecteren de daadwerkelijke Flutter-semantiekboom en toetsenbordfocus. Nieuwe
  zichtbare statuskopieën mogen geen tweede `SemanticsRole.status` opleveren.
- Kotlin-tests draaien via Maven `verify`; componentgrenzen volgen de bestaande Spring
  Modulith-structuur. Een nieuwe module krijgt een `package-info.java` met expliciete
  `allowedDependencies` (geen wildcard) en wordt opgenomen in de moduleset van
  `ModulithArchitectureTest`.
- De reproduceerbare smoke-contractset voor de publieke Heemskerk-zoekketen staat in
  `backend/src/test/kotlin/nl/vdzon/hkh/historicalsearch/Hkh165HistoricalSearchSmokeContractTest.kt`
  en `frontend/test/hkh165_historical_search_smoke_contract_test.dart`. De backendtest gebruikt
  een lokale `HttpServer` en de Fluttertest gebruikt een synthetische route-respons; beide spreken
  geen echte externe bron aan. De set controleert geldige Open Archieven-metadata en bronlink,
  nulresultaat, gedeeltelijke/volledige bronuitval, uitgeschakelde Europeana, veldgerichte
  fail-closed assertions en single-flight/verzoekbudget bij identieke gelijktijdige zoekacties.
  De bestaande `verify`-/`flutter test`-commando's nemen deze bestanden automatisch mee. Gericht
  uitvoeren kan met `mvn -B --no-transfer-progress -f backend/pom.xml
  -Dtest=Hkh165HistoricalSearchSmokeContractTest test` en `flutter test
  test/hkh165_historical_search_smoke_contract_test.dart`.
- De aanvullende statusmatrix voor de publieke Open Archieven-flow staat in
  `backend/src/test/kotlin/nl/vdzon/hkh/historicalsearch/Hkh189HistoricalSearchContractTest.kt`
  en `frontend/test/hkh189_historical_search_contract_test.dart`. Deze controleert met lokale of
  synthetische fixtures geldige en lege responses, gedeeltelijke beschikbaarheid, timeout, HTTP
  5xx, ongeldig JSON, ontbrekende of tegenstrijdige verplichte velden en ontbrekende rechten/privacy-
  metadata. Per scenario worden state, bronstatus, totaal/per-bron tellingen, bronidentiteit,
  exacte bronlink, veilige fout-/metadataweergave en kaartzichtbaarheid gecontroleerd. Gericht
  uitvoeren kan met `mvn -B --no-transfer-progress -f backend/pom.xml
  -Dtest=Hkh189HistoricalSearchContractTest test` en `flutter test
  test/hkh189_historical_search_contract_test.dart`; beide tests spreken geen echte externe bron
  aan.
- Geen echte secrets, persoonsgegevens, buildoutput of lokale overrides versioneren.
