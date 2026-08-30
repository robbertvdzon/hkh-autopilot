# Technical Spec

## Stack en componenten

- Backend: Kotlin op JDK 21, Spring Boot/Spring Modulith, Maven, PostgreSQL 16 en Flyway.
- Gebruikersfrontend: Flutter stable 3.44.7, Dart 3.12.2, Material 3, `http`; web en Android.
- Beheerfrontend: afzonderlijke Flutter-webapp.
- Deployment: containers, Kustomize/OpenShift en ArgoCD.

De backendservicecontrole combineert `GET /actuator/health` en `GET /api/version`; beide moeten
binnen tien seconden met een geldige 200-respons slagen. Nieuws komt van `GET /api/news` en heeft
dezelfde clienttimeout. `API_BASE_URL` is een compile-time Dart-define.

Langlopende AI-opdrachten gaan asynchroon via de gedeelde Agent Runtime en nooit via een directe
modelaanroep in de requestthread. HKH Autopilot gebruikt een eigen `APPLICATION_WORK`-tenant,
projectprefix `HKH_AUTOPILOT` en een eigen bearercredential zonder repository-, worker- of
beheerrechten. Het normatieve aansluit- en herstelcontract staat in
[`agent-runtime.md`](agent-runtime.md).

## Frontendmodule `personquery`

De persoonsvraag-interpretatie en Heemskerk-disambiguatie zitten volledig client-side in
`frontend/lib/personquery/`, ontsloten via een nieuwe actie "Stel je vraag over Heemskerk" op de
bestaande homepage (`main.dart`, naar het patroon van de bestaande "Lees onze productvisie"-knop).

- `person_query_interpreter.dart` bevat de pure, side-effect-vrije `PersonQueryInterpreter.interpret`:
  past achtereenvolgens de vraagwoorden-, functiewoorden/lidwoorden- en vaste plaats-/
  maandnamenlijst-verwijderregel toe (drie losse `Set<String>`, hoofdletterongevoelig op
  woordgrenzen via `RegExp`), herkent daarna een naam via `_findRecognizedName` (een lopende reeks
  Unicode-bewuste hoofdletterwoorden van minstens lengte twee) en bepaalt de
  Heemskerk-disambiguatie op de ORIGINELE, niet-genormaliseerde tekst (`_heemskerkUnambiguousPattern`
  matcht `in/te/uit/van` direct vóór "Heemskerk"). "Heemskerk" zit bewust niet onvoorwaardelijk in de
  vaste verwijderlijst: het woord wordt alleen verwijderd wanneer de disambiguatie het al
  ondubbelzinnig als plaats classificeert, zodat het in het ambigue geval als achternaam-kandidaat
  kan meetellen in de naamherkenning (nodig om bijvoorbeeld "Cornelis Heemskerk" als ambigu te
  herkennen). Resultaat is het immutable `PersonQueryInterpretation`-record met naam-, jaar- en
  gebeurtenistype-kandidaten en de drie Heemskerk-vlaggen (`heemskerkMentioned`,
  `heemskerkUnambiguousPlace`, `heemskerkAmbiguous`).
- `wikidata_meaning_client.dart` bevat de vaste QID's (`WikidataMeaningIds.place` = `Q9926`,
  `WikidataMeaningIds.surname` = `Q91564725`), de injecteerbare `WikidataMeaningSource`-interface
  (zodat widgettests nooit een echte Wikidata-aanroep doen) en `WikidataMeaningClient`, die eerst
  `GET /w/api.php?action=wbsearchentities&search=Heemskerk&language=nl&type=item&format=json` en
  vervolgens `GET /wiki/Special:EntityData/<qid>.json` voor beide QID's aanroept (standaard
  `https://www.wikidata.org`, injecteerbare `http.Client`/`baseUri`, timeout 10s). Elke fout
  (netwerkfout, timeout, non-200, ontbrekend/onverwacht JSON-veld) wordt omgezet naar een
  gecontroleerde `WikidataMeaningException`; de aanroeper valt dan terug op vaste labels
  ("Q9926 · Heemskerk (plaats)" / "Q91564725 · Heemskerk (achternaam)") met een zichtbare
  storingsmelding. Resultaten van beide betekenissen worden nooit samengevoegd.
- `person_query_page.dart` bevat het instappunt `PersonQueryPage` (screenKey `start`) met interne
  state-machine tussen start-, meaning-selection- en no-reliable-source-weergave, elk met exact één
  desktop- en één mobile-uitwerking op een breakpoint van 700 logische pixels (geen horizontale
  scroll bij 320px). `meaning_selection_screen.dart` en `no_reliable_source_screen.dart` bevatten de
  bijbehorende schermwidgets; `person_query_widgets.dart` bevat gedeelde bouwstenen, waaronder
  `personQueryFocusedButtonStyle` naar het patroon van de bestaande gedeelde `ButtonStyle`-conventie
  (3px-focusrand) uit de "Flutter-webstatussemantiek"-sectie. Toetsenbordnavigatie
  (Tab/Shift+Tab/Enter, pijltjestoetsen op de radiogroep) gebruikt de standaard Flutter-widgetvolgorde
  en -activering, zonder aangepaste sort keys.
- De module zelf blijft zonder backend-, database- of infrastructuurwijziging: ze roept nooit Open
  Archieven Records/Search/Show aan, en de Wikidata-aanroep voor de meaning-selection gebeurt
  rechtstreeks vanuit de browser (CORS via `origin=*`), analoog aan de bestaande directe
  `GET /api/news`-/`GET /actuator/health`-aanroepen. Een succesvolle indiening (herkende naam, en bij
  ambiguïteit een bevestigde keuze) geeft de vraag door aan de backendmodule `personsearch`
  hieronder, die de daadwerkelijke live zoekroute uitvoert.

## Backendmodule `personsearch`

De live persoonszoekopdracht en synchrone antwoordroute zitten in de zelfstandige Spring Modulith-
module `nl.vdzon.hkh.personsearch` (inclusief de subpackage `personsearch.api`), met
`package-info.java` en `@ApplicationModule(allowedDependencies = {})` — geen afhankelijkheid op
andere modules, ook niet op `auth` — opgenomen in de moduleset van `ModulithArchitectureTest`.

- `POST /api/person-search` (`PersonSearchController`) neemt per verzoek precies één ondersteunde
  vraag in (herkende naam, optionele tweede naam, gebeurtenistype, jaar/periode en gekozen
  Heemskerk-betekenis). De respons bevat `jobId`, `status`, de oorspronkelijke vraag en, afhankelijk
  van de uitkomst, `refinementMessage`, `answer` (zinnen met bronverwijzingen, bronnen, vervolgsporen
  en disclaimer) en/of `context` (Wikidata-label/-beschrijving).
- `PersonSearchSessionResolver` (`PersonSearchSession.kt`) geeft een route-gebonden,
  server-uitgegeven sessiecookie uit voor anonieme bezoekers: `hkh_person_search_session`, 32
  cryptografisch random bytes (`SecureRandom`, URL-safe base64), `HttpOnly`, `SameSite=Lax`, 24 uur
  levensduur. Dit is een nieuw, minimaal sessieconcept los van het bestaande admin/Google-
  authenticatiemechanisme, uitsluitend gebruikt om jobs en idempotentiesleutels aan een bezoeker te
  binden.
- `PersonSearchStatus` is een worker-onafhankelijk statuscontract — `QUEUED, RUNNING, READY,
  NO_EVIDENCE, PARTIAL, FAILED, CANCELLED, EXPIRED` — uitvoerbaar door de gewone gedeelde executor,
  zonder afhankelijkheid van Agent Runtime (`PersonSearchOutcome.toStatus()`:
  `SupportedAnswer → READY`, `NoResults → NO_EVIDENCE`, `SourceOutage → FAILED`, `Partial`
  ongewijzigd). `PersonSearchSourceConsultationStatus`
  (`NOT_STARTED/IN_PROGRESS/SUCCEEDED/FAILED`) volgt per bron (Open Archieven, Wikidata) op elke
  `PersonSearchJob`. `PERSON_SEARCH_TERMINAL_STATUSES` bevat alle statussen behalve `QUEUED`/
  `RUNNING`; `PersonSearchJob.isTerminal` leest daaruit af.
- `PersonSearchService.submit` maakt per idempotentiesleutel (sessie-id + genormaliseerde vraag +
  gekozen Heemskerk-betekenis) atomair precies één `PersonSearchJob` aan (`createIfAbsent`, dus geen
  race condition bij een dubbele gelijktijdige indiening) met een cryptografisch random,
  niet-raadbare job-id, status `QUEUED` totdat de achtergrondtaak daadwerkelijk op de executor
  start (dan `RUNNING`). De synchrone uitvoering start de Records/Search-/Records/Show-aanroepen en
  de Wikidata-contextaanroep direct na jobcreatie en wacht binnen hetzelfde HTTP-request maximaal
  2000ms op een terminale, gevalideerde uitkomst, zonder de achtergrondtaak te annuleren. Vóór elke
  uitgaande Open Archieven-/Wikidata-aanroep (ook halverwege de Show-lus, via een non-lokale
  `return` in de inline `map`-lambda) controleert `submit` `jobStore.isCancelled(jobId)`. Een
  uitkomst wordt zowel in `whenComplete` als — als vangnet wanneer die dependent stage nog niet is
  afgerond zodra `future.get()` al terugkomt — direct na een succesvolle synchrone afronding
  gepersisteerd (`persistOutcome`, idempotent op een reeds terminale job).
- `PersonSearchJobStore` (in-memory, geen aparte databasetabel) bewaart de oorspronkelijke vraag en
  de antwoordpayload uitsluitend versleuteld (`encryptedOriginalQuery`/`encryptedOutcome`, via
  `PersonSearchPayloadCipher`) en houdt per job `updatedAt`, per-bron consultatiestatus en
  `openedAt` bij. `findByIdForSession` is de sessiegebonden, fail-closed lookup die de controller
  altijd gebruikt (andere sessie ⇒ `null`, alsof de job niet bestaat); `findById` is een tweede,
  sessie-ongebonden lookup uitsluitend voor de achtergrondtaak zelf. `touchSessionActivity`
  ververst het laatste sessie-activiteitsmoment (los van de 24u-cookie-`maxAge`) bij elke
  indiening, statusaanvraag of stopactie. `cancel` zet de job op `CANCELLED` en wist direct de
  payload (idempotent op een reeds terminale job); `isCancelled` wordt vóór elke uitgaande
  bronaanroep gecontroleerd. `markOpened` markeert een `READY`-job als geopend. `sessionIndicator`
  levert aantal + job-ids van lopende en gereedstaande-niet-geopende jobs van precies één sessie op
  (job-ids nodig om na herlading te weten welke statuscontrole te hervatten; geen zichtbare
  bronlinks/analyticswaarden). `purgeExpired()` wist de payload en zet de status op `EXPIRED` zodra
  `PERSON_SEARCH_SESSION_INACTIVITY_LIMIT` (60 min) of `PERSON_SEARCH_MAX_AGE` (24 uur) verstrijkt,
  wat eerder komt.
- `PersonSearchPayloadCipher` versleutelt/ontsleutelt de oorspronkelijke vraag en de opgeslagen
  antwoordpayload (`PersonSearchStoredPayload`: refinementMessage/answer/context) met AES-256-GCM,
  naar het patroon van `ExternalVerificationTokenCipher`, maar als eigen `@Component` binnen de
  `personsearch`-module (die `allowedDependencies = {}` heeft). Sleutel uit
  `hkh.personsearch.payload-key`/`HKH_PERSON_SEARCH_PAYLOAD_KEY`; zonder geconfigureerde sleutel
  faalt versleuteling fail-closed. Gebruikt een eigen `JsonMapper`
  (`tools.jackson.databind.json.JsonMapper` + `kotlinModule()`) zodat er geen Spring
  `ObjectMapper`-bean-afhankelijkheid nodig is.
- `PersonSearchRetentionCleanupTask` (`@Scheduled(fixedDelay = 60_000)`, draait op de gewone Spring-
  taakscheduler) roept periodiek `jobStore.purgeExpired()` aan. `@EnableScheduling` staat op
  `HkhApplication` (nieuw in deze repo — geen bestaand `@Scheduled`-patroon om te volgen).
- `PersonSearchController` biedt naast `POST /api/person-search` ook `GET /{jobId}/status`
  (status, `createdAt`, `updatedAt`, per-bron consultatiestatus; de volledige uitkomst alleen bij
  een terminale status), `POST /{jobId}/cancel` (stopactie), `POST /{jobId}/open` (markeert een
  `READY`-job als geopend, voor de sessie-indicator) en `GET /session` (sessie-indicator: aantal +
  job-ids). Alle vier zijn sessiegebonden fail-closed via `findByIdForSession` (HTTP 404 bij
  onbekende job of een andere sessie); geen van de responses bevat een sessie-id.
- `ArchivesOpenSearchClient`/`RestClientArchivesOpenSearchClient` roept Open Archieven Records/Search
  (`GET /records/search.json` met `archive_code=nha`, `eventplace=Heemskerk`, `lang=nl`,
  `number_show=100`, URL-gecodeerde `name`, `start` voor paginering) en Records/Show
  (`GET /records/show.json` met `archive=nha`, `identifier=<id>`, `lang=nl`) aan. De basis-URI is
  overschrijfbaar via `hkh.personsearch.archives-base-url` (env
  `HKH_PERSON_SEARCH_ARCHIVES_BASE_URL`, standaard `https://api.openarchieven.nl/1.1`), uitsluitend
  zodat tests tegen een lokale fixture kunnen draaien. Elk verzoek gebruikt een beschrijvende
  User-Agent, vraagt gzip aan (`GzipRequestInterceptor`) en loopt via `PersonSearchRateLimiter`
  (maximaal 4 requests/seconde, procesbreed) met korte timeouts (connect 800ms/read 1200ms) en een
  begrensde, eindige back-off bij transiënte fouten. Validatie is altijd fail-closed: alleen HTTP
  2xx, geldige JSON, aanwezig `number_found` (resp. de Show-velden) en een leeg `error_code` gelden
  als geslaagde bronraadpleging; elke afwijking (inclusief een gevuld `error_code` bij HTTP 200) is
  een mislukte bronraadpleging. Voor Search is een uitzondering op fail-closed voor `docs`: een
  ontbrekend of `null` `docs`-veld is alleen een `Failure` als `number_found > 0` (inconsistente
  respons); bij `number_found == 0` is dit een geldig nul-resultaat
  (`ArchivesSearchOutcome.Success(numberFound = 0, results = emptyList())`), wat via
  `PersonSearchService` tot `PersonSearchOutcome.NoResults`/status `NO_EVIDENCE` leidt in plaats van
  `SourceOutage`/`FAILED`. `ArchivesOpenSearchModels.kt` modelleert
  de échte, geneste API-respons (Search: `response.number_found`/`response.docs`; Show:
  hoofdlettergevoelige, diep geneste `Person`/`Event`/`RelationEP`/`Source`) — niet een zelfbedacht
  plat schema (zie "Belangrijke ontwikkeling tijdens de ronde" in het worklog voor de eerdere,
  afgekeurde poging). Zoekresultaten worden gededupliceerd op `archive_code` + `identifier`; bij
  `number_found > 100` eindigt de job met status `PARTIAL` en een verfijningsverzoek, zonder
  Records/Show-aanroep.
- `PersonSearchAnswerBuilder` bouwt de antwoordzinnen uitsluitend uit gevalideerde Show-velden
  (`Person`/`Event`/`RelationEP`/`Source`); elke zin krijgt een genummerde bronmarkering
  (`PersonSearchSourceCitation`: beherende instelling, brontype, archief-/register-/akte-/
  documentnummer, recordnummer/identifier, link naar
  `https://www.openarchieven.nl/{archive_code}:{identifier}`, optioneel `SourceDigitalOriginal`-link
  en `checkedAt`). Vervolgsporen (`followed-connection`) zijn de rollen met een gekoppelde
  persoonsnaam in `RelationEP` van hetzelfde Show-record, in recordvolgorde, begrensd tot twee, en
  vragen geen extra externe aanroep. `PersonSearchService.handleSearchSuccess` filtert de
  Show-uitkomsten op `ArchivesShowOutcome.Success`: zijn er bij meerdere kandidaatrecords geen
  geslaagde Show-records (alle mislukken, of de vereiste Records/Search-aanroep faalt), dan levert
  de job status `FAILED` op — Open Archieven wordt exact aangeduid als "tijdelijk niet geraadpleegd"
  en er verschijnt geen enkele archiefbewering. Is er ten minste één geslaagd Show-record, dan wordt
  de job `READY` op basis van uitsluitend die deelverzameling; mislukte kandidaten leveren geen
  feitelijke zin of bronmarkering en blokkeren de overige, wel gevalideerde records niet. Zijn er
  wel-maar-niet-alle kandidaten onverifieerbaar, dan breidt `PersonSearchAnswerBuilder.buildDisclaimer`
  de bewijsbegrenzingstekst uit met een aantal-gebaseerde vermelding (bijv. "1 van de 4 gevonden
  kandidaten kon niet worden geverifieerd en is buiten beschouwing gelaten.").
- `PersonSearchWikidataContextClient`/`WikidataPersonSearchContextClient` haalt optionele
  Wikidata-contextinformatie op (basis-URI overschrijfbaar via
  `hkh.personsearch.wikidata-base-url`/`HKH_PERSON_SEARCH_WIKIDATA_BASE_URL`, standaard
  `https://www.wikidata.org`); een mislukte contextaanroep blokkeert nooit de archiefuitkomst en
  levert alleen een ontbrekende `context` op. Contextinhoud draagt nooit zelfstandig een geboorte-,
  huwelijks-, overlijdens-, doop- of bevolkingsregistratiebewering.
- Frontend: zes Flutter-schermen onder `frontend/lib/personsearch/` — `live_search_screen.dart`
  (`live-search`), `supported_answer_screen.dart` (`supported-answer`, incl. bronmarkeringen, Context-
  sectie en vervolgsporen), `followed_connection_screen.dart` (`followed-connection`),
  `source_outage_screen.dart` (`source-outage`), `background_search_screen.dart`
  (`background-search`: oorspronkelijke vraag, starttijdstip, status, per-bronvoortgang,
  "andere vraag stellen zonder de lopende job te onderbreken" en een stopactie) en
  `search_ready_screen.dart` (`search-ready`: voltooiingstijdstip, daadwerkelijk geraadpleegde
  bronnen, precies één actie die het antwoord opent) — elk met exact één desktop- en één
  mobile-uitwerking op hetzelfde breakpoint als `personquery` (geen aparte desktop-/mobile-
  widgetklassen, enkel de containerbreedte verschilt). `session_indicator_badge.dart` bevat
  `SessionIndicatorBadge`, een zelfverversend widget (eigen `Timer.periodic`, standaard elke 5s) dat
  `GET /api/person-search/session` bevraagt en in de `AppBar` van `PersonQueryPage` staat, dus op
  alle schermen van de route zichtbaar is.
  `person_search_client.dart`/`person_search_models.dart` ontsluiten naast `POST /api/person-search`
  ook `pollStatus`/`cancel`/`open`/`sessionIndicator`; `PersonSearchStatusException`/
  `PersonSearchJobUnavailableException` (laatste specifiek voor een 404) laten de UI een tijdelijke
  netwerkfout onderscheiden van een niet meer beschikbare job. `person_query_page.dart` dient de
  vraag in en schakelt op basis van de respons door naar het passende scherm; bij `QUEUED`/`RUNNING`
  wordt eerst één keer de volledige status opgehaald (voor starttijd/per-bron-status) voordat naar
  `background-search` geschakeld wordt, waarna een eigen `Timer` (3s, geen acceptatiecriterium op
  het exacte interval) de volgende statuscontrole plant zolang de job niet terminaal is. "Stel
  intussen een andere vraag" stopt alleen de voorgrondpolling van dat scherm (via een
  generation-teller) en navigeert naar `start`; de achtergrondtaak blijft server-side doorlopen.
  `initState` roept `sessionIndicator()` aan om na herlading de eerste lopende of
  gereedstaande-niet-geopende job van de sessie in de voorgrond te hervatten (bij meerdere
  gelijktijdige jobs alleen de eerste; de sessie-indicator toont wel het juiste totaal). Na een
  verwijderde/verlopen job (404 op de statusaanvraag) toont `_JobUnavailableScreen` een duidelijke
  niet-meer-beschikbaar-melding met een aanbod om de vraag opnieuw in te dienen, in plaats van een
  oud antwoord als actuele uitkomst.

## Backendmodule `linkdossier`

De koppelingsdossiervalidatie zit in de zelfstandige Spring Modulith-module
`nl.vdzon.hkh.linkdossier`. De module heeft `package-info.java` met
`@ApplicationModule(allowedDependencies = {})` — geen wildcard, dus geen afhankelijkheden naar andere
modules — en staat in de moduleset van `ModulithArchitectureTest`. Er is bewust geen controller,
repository of migratie: de module is puur intern domein.

- `LinkDossier.kt` bevat `LinkDossier`, `LinkDossierRecord`, `RecordDating` en `LinkDossierRelation`
  plus de enums `RightsClassification`, `PrivacyClassification`, `DatingUncertainty` en
  `ConfirmationStatus`.
- Gecontroleerde waarden staan in het domeinmodel als ruwe `String?` en worden pas door `parse`
  omgezet. Zo kan een dossier zowel een ontbrekende als een niet-herkende waarde bevatten zonder dat
  constructie of deserialisatie faalt; de validator keurt af in plaats van te klappen. `parse` trimt
  de invoer en vergelijkt hoofdletterongevoelig.
- `LinkDossierValidator.validate` verzamelt alle overtredingen in twee `MutableSet<String>` en stopt
  nooit bij de eerste. Ontdubbeling volgt uit de set en de volgorde uit `sorted()`, dus het resultaat
  is onafhankelijk van de uitvoeringsvolgorde. De hele evaluatie zit in `runCatching`: een onverwachte
  fout levert een geblokkeerd, objectmedia-verboden resultaat op in plaats van een uitzondering.
- `LinkDossierValidationResult.kt` bevat het resultaattype, `DossierStatus` en de vaste veldpaden in
  `LinkDossierFieldPaths` (`records`, `records[n].<veld>`, `relation.<veld>`). Recordpaden gebruiken
  de invoerindex.
- URL-validatie gebruikt `java.net.URI`: absoluut, schema `http` of `https` en een niet-lege host. Er
  wordt geen netwerkverkeer gegenereerd.

## Backendmodule `recordintake`

De intake van precies één lokaal collectierecord zit in de zelfstandige Spring Modulith-module
`nl.vdzon.hkh.recordintake` (inclusief de subpackage `recordintake.api`), met `package-info.java`
en `@ApplicationModule(allowedDependencies = {})` — geen afhankelijkheid op andere modules, ook niet
op `auth`. De module staat in de moduleset van `ModulithArchitectureTest`.

- `POST /api/record-intake` (`RecordIntakeController`, patroon van `LatestNewsController`) leest de
  `Authorization: Bearer`-header, verifieert eerst het token en valideert daarna pas het record.
  Bij een geldige inzending wordt precies één record opgeslagen; de respons bevat uitsluitend
  metadata (`id`, `status`, `createdAt`, optioneel `externalLink`) en nooit een tokenwaarde, header
  of claim.
- Tokenverificatie (`RecordIntakeTokenVerifier`/`NimbusRecordIntakeTokenVerifier`) volgt het patroon
  van `NimbusGoogleIdTokenVerifier`, maar met een eigen, vaste, versieerbare configuratie: RS256,
  issuer `https://hkh-autopilot.local`, audience `hkh-autopilot-record-intake`, JWKS-bron via
  `hkh.recordintake.jwks-url` (env `HKH_RECORD_INTAKE_JWKS_URL`), verplichte claims `iss`, `aud`,
  `sub`, `exp`, `iat` en `scope`, een maximale levensduur van vijftien minuten en de vereiste scope
  `record:intake`. Zonder geconfigureerde JWKS-bron is de intake fail-closed uitgeschakeld (HTTP
  503); elke overige afwijking geeft fail-closed HTTP 401 zonder tokenwaarde of claims in de respons
  of logging.
- `RecordIntake.kt` modelleert het verzoek naar het patroon van `LinkDossier`: ruwe `String?`-velden
  (`localIdentifier`, `title`, `description`, `dating`, `provenance`, `rightsStatus`,
  `privacyClassification`, `accessUrl`, optioneel `externalLink`), pas via `parse` omgezet naar de
  enums `PrivacyClassification` en `LinkUncertainty`.
- `RecordIntakeValidator.validate` verzamelt alle veldfouten in `RecordIntakeValidationResult.
  fieldErrorPaths` (nooit fail-fast) en beoordeelt de privacyregel volledig geïsoleerd in
  `privacyBlocked`: alleen `geen persoonsgegevens` passeert, `mogelijk persoonsgegevens` en
  `persoonsgegevens` blokkeren opslag onafhankelijk van de overige veldfouten met de technische
  foutcode `PRIVACY_CLASSIFICATION_BLOCKED`. Veldfouten geven HTTP 400 met `fieldErrors`
  (machineleesbare veldpaden uit `RecordIntakeFieldPaths`); een privacyblokkade geeft HTTP 422 met
  `errorCode`. Er ontsnapt nooit een uitzondering; een onverwachte fout blokkeert alles.
- `RecordIntakeService.create` slaat het gevalideerde record op met status `intern_concept`
  (Flyway-migratie `V4__record_intake.sql`, tabel `record_intake`, geen media- of
  publicatievelden) en maakt de optionele externe conceptkoppeling (tabel
  `record_intake_external_link`, status `concept`, uniek per record) alleen aan wanneer duurzame
  URL, koppelmotivering en onzekerheidswaarde (`laag`/`middel`/`hoog`) alle drie geldig zijn;
  anders blijft het interne conceptrecord bestaan zonder koppeling.
- Frontend: `frontend-admin/lib/recordintake/` bevat `RecordIntakeForm` (foutsamenvatting met
  toetsenbordfocus na een mislukte validatie, per fout programmatisch aan het veld gekoppeld via
  `FocusNode` en `errorText`, status via tekst plus `Semantics(liveRegion: true)`) en
  `AdminRecordIntakeClient`, die het bestaande gemaskeerde tokenmechanisme (`AdminIdentity.
  requestHeaders`) hergebruikt: er is geen apart invoerveld voor autorisatiebewijs.

## Backendmodule `privacyclassification`

De AVG-classificatie van genealogische records (bijvoorbeeld bidprentjes) zit in de zelfstandige
Spring Modulith-module `nl.vdzon.hkh.privacyclassification`, met `package-info.java` en
`@ApplicationModule(allowedDependencies = {})` — geen afhankelijkheid op andere modules — opgenomen
in de moduleset van `ModulithArchitectureTest`. Er is bewust geen controller, repository of
migratie: net als `linkdossier` is de module puur intern domein.

- `GenealogicalRecord.kt` modelleert `GenealogicalRecord` (overlijdensstatus als ruwe `String?`) en
  `LivingNextOfKinFields` (benoemde velden `contactName`, `contactAddress` en
  `contactPhoneNumber` die, indien gezet, een nog levende nabestaande identificeren). De enum
  `DeceasedStatus.parse` is naar het patroon van `RightsClassification.parse`, maar levert
  fail-closed altijd een waarde op: een ontbrekende of niet-herkende ruwe waarde wordt `ONBEKEND`
  in plaats van `null`.
- `PrivacyClassificationResult.kt` bevat `PrivacyClassificationStatus` (`PROCESSABLE`/`BLOCKED`) en
  `PrivacyClassificationResult`, met een verplichte, niet-lege leesbare `reason` — ook bij
  `PROCESSABLE` — naar het patroon van `LinkDossierValidationResult`. Vaste redenteksten staan in
  `PrivacyClassificationReasons`.
- `PrivacyClassifier.classify` levert alleen `PROCESSABLE` op bij `DeceasedStatus.OVERLEDEN` zonder
  een gezet nabestaande-veld. In alle overige gevallen (onbekende status, `LEVEND`, of wel een
  gedetecteerd nabestaande-veld) is de uitkomst `BLOCKED` met reden, waaronder exact
  `"Bevat gegevens van levende nabestaande"` bij een gedetecteerd nabestaande-veld. De hele
  evaluatie zit in `runCatching`: een onverwachte fout levert fail-closed een geblokkeerd resultaat
  op.
- `PrivacyPublishGuard.assertPublishable` is een losstaande, herbruikbare guard die publicatie
  weigert met `PrivacyPublishBlockedException` wanneer de classificatie `BLOCKED` is en niets doet
  bij `PROCESSABLE`. Er is nog geen bestaande publicatieworkflow om op aan te sluiten; een latere
  publicatiefeature kan deze guard hergebruiken.
- `GenealogicalRecord.namedPersons` (`List<NamedPerson>`, standaard leeg) modelleert de in het
  record genoemde personen (hoofdpersoon en familieleden). `NamedPerson` heeft vijf optionele
  `String?`-datumvelden (`birthDate`, `marriageDate`, `childBirthDate`, `deathDate`, `burialDate`),
  naar het patroon van `ExternalVerificationRequest.birthDate` — ruwe tekst, geen datumtype, zodat
  constructie nooit faalt op een onleesbare waarde.
- `LivingPersonAgeRule.evaluate` bepaalt per `NamedPerson` een `PersonAgeStatus`
  (`LIKELY_LIVING`/`DECEASED`/`UNKNOWN_FAILCLOSED`) volgens de FamilySearch 110/95-jaarregel: een
  extern gedocumenteerde, niet-wettelijke vuistregel uit de genealogiepraktijk (geen AVG- of andere
  wettelijke norm). Een geldige overlijdens- of begrafenisdatum levert `DECEASED` op; zonder die
  datum levert een geboortedatum ≤110 jaar geleden, of een huwelijks-/kindgeboortedatum ≤95 jaar
  geleden, `LIKELY_LIVING` op (grenzen inclusief); een geboortedatum >110 jaar geleden zonder recent
  huwelijks-/kindsignaal levert `DECEASED` op; ontbreken van elk bruikbaar datumveld, of een gezet
  maar onparsbaar datumveld, levert fail-closed `UNKNOWN_FAILCLOSED` op. Datums worden verwacht in
  ISO-8601 (`yyyy-MM-dd`); een `yyyy`-only waarde wordt ook geparsed met 1 januari als impliciete
  dag — een bekende beperking bij ontbrekende event-granulariteit (bijv. alleen jaartal). De
  tijdsbron is een injecteerbare `java.time.Clock` (standaard de systeemklok), zodat de regel
  deterministisch getest kan worden.
- `PrivacyClassifier.evaluate` blijft aanvullend `BLOCKED` opleveren zodra minstens één
  `namedPersons`-item `LIKELY_LIVING` of `UNKNOWN_FAILCLOSED` oplevert (reasons
  `PrivacyClassificationReasons.NAMED_PERSON_LIKELY_LIVING` respectievelijk
  `NAMED_PERSON_AGE_UNKNOWN_FAILCLOSED`), ná de bestaande `DeceasedStatus`- en `nextOfKin`-checks;
  die bestaande checks en het `runCatching`-faalveiligheidsgedrag blijven ongewijzigd.
- `GenealogicalRecord.gedcomSource` is een optioneel, ruw GEDCOM 7.0-brontekstveld (`String?`,
  standaard `null`); er is geen echte GEDCOM-producerende bron aangesloten (de bestaande
  `ArchivesNlClient` levert JSON-LD, geen GEDCOM) - het veld wordt in tests met synthetische
  fixtures gevuld. `GedcomResnRule.evaluate` parseert de brontekst regel-voor-regel
  (`LEVEL [@XREF@] TAG [VALUE]`), bouwt de hiërarchie op via het levelgetal en doorzoekt de
  resulterende boom recursief naar een RESN-tag met een blokkerende waarde (`CONFIDENTIAL`,
  `LOCKED` of `PRIVACY`, ongeacht letterkast), op elk nestingniveau - zowel op recordniveau als
  binnen een geneste gebeurtenis/feit. Dit levert een `GedcomResnSignal` op:
  `NOT_APPLICABLE` wanneer `gedcomSource` ontbreekt (`null`/leeg), `BLOCKED` zodra een blokkerende
  RESN-markering gevonden wordt óf de brontekst niet-leeg maar syntactisch ongeldig is
  (fail-closed, naar het patroon van de overige fail-closed-conventies in deze module), en anders
  `NONE`. `PrivacyClassifier.evaluate` weegt dit signaal onafhankelijk en bindend mee vóórdat de
  bestaande `DeceasedStatus`-, `nextOfKin`- en leeftijdsregel-checks lopen: bij `BLOCKED` is de
  totaaluitkomst altijd `PrivacyClassificationStatus.BLOCKED` met reason
  `PrivacyClassificationReasons.GEDCOM_RESN_BLOCKED`, ongeacht de uitkomst van die overige checks;
  bij `NONE` of `NOT_APPLICABLE` blijft de bestaande classificatielogica ongewijzigd bepalend.
  Bekende beperking: alleen GEDCOM 7.0 RESN-syntax wordt ondersteund.
- Frontend: `frontend-admin/lib/privacyclassification/privacy_classification_status_view.dart`
  bevat `PrivacyClassificationStatusView`, die de classificatiestatus in de beheerfrontend toont met
  zowel een tekstlabel (`Verwerkbaar`/`Geblokkeerd`) als een icoon (`Icons.check_circle`/
  `Icons.block`), nooit uitsluitend via kleur. De vaste voorgrondkleuren in
  `PrivacyClassificationStatusColors` halen tegen de witte achtergrond een contrastratio van
  7.87:1 (`processableForeground`) respectievelijk 6.57:1 (`blockedForeground`), ruim boven de
  WCAG 2.1 AA-minimumwaarde van 4.5:1. Het icoon krijgt een eigen `semanticLabel` (`Icoon
  <label>`), zodat zowel het tekstlabel als het icoon een eigen node in de semantiekboom hebben; een
  widgettest controleert die semantiekboom op aanwezigheid van beide, en een aparte test berekent de
  contrastratio van de gebruikte kleurwaarden volgens de WCAG 2.1-formule, als vervanging van
  axe-core conform de bestaande repo-conventie.

## Backendmodule `externalverification`

De externe verificatie tegen archieven.nl/Noord-Hollands Archief zit in de zelfstandige Spring
Modulith-module `nl.vdzon.hkh.externalverification` (inclusief de subpackage
`externalverification.api`), met `package-info.java` en `@ApplicationModule(allowedDependencies =
{})` — geen afhankelijkheid op andere modules — opgenomen in de moduleset van
`ModulithArchitectureTest`. Anders dan `linkdossier` en `privacyclassification` heeft deze module wél
een eigen repository en migratie, naar het patroon van `recordintake`, omdat ze resultaten
persisteert.

- `POST /api/external-verification` (`ExternalVerificationController`) neemt per verzoek precies
  één verificatie in. `ExternalVerificationRequest` modelleert de invoer als ruwe, op zichzelf
  staande velden (`localIdentifier`, `name`, `birthDate`, `deathDate`, `adtid`, `guid`, optioneel
  `accessToken`), naar het patroon van `RecordIntake`/`LinkDossier` en niet gekoppeld aan een
  bestaand persistent record. `archivesNlUri(adtid, guid)` bouwt de resolvebare URI
  `http://opendata.archieven.nl/id/<adtid>/<guid>` op.
- `ExternalVerificationValidator.validate` verzamelt alle veldfouten in
  `ExternalVerificationValidationResult.fieldErrorPaths` (nooit fail-fast; ontdubbeld en
  lexicografisch gesorteerd), naar het patroon van `RecordIntakeValidator`; een onverwachte fout
  levert fail-closed alle verplichte velden als ontbrekend op. Veldfouten geven HTTP 400 met
  `fieldErrors` (machineleesbare paden uit `ExternalVerificationFieldPaths`).
- `ArchivesNlClient`/`RestClientArchivesNlClient` bevraagt de resolvebare URI met header
  `Accept: application/ld+json`, zonder autorisatietoken tenzij een geconfigureerd
  `accessToken` aanwezig is. De basis-URI is overschrijfbaar via
  `hkh.externalverification.archives-base-url` (env
  `HKH_EXTERNAL_VERIFICATION_ARCHIVES_BASE_URL`), uitsluitend zodat tests tegen een lokale
  fixture/mock-endpoint kunnen draaien. Er wordt geen volledige externe brondata opgeslagen; alleen
  de kernvelden (inclusief `license`) gaan naar de matcher/evaluator. `ArchiveRecordFields.license`
  wordt gelezen uit hetzelfde JSON-LD-antwoord als naam/geboortedatum/overlijdensdatum — geen extra
  HTTP-verzoek.
- `ExternalVerificationMatcher.match` vergelijkt naam en geboorte-/overlijdensdatum van het lokale
  record met de opgehaalde JSON-LD-kernvelden en levert een `ExternalVerificationMatchResult` op
  met status `VERIFIED` (alle velden komen overeen) of `UNVERIFIED` (geen match, inclusief een
  niet-bestaande/ongeldige guid), een lijst met uitsluitend de namen van gematchte velden
  (`ExternalVerificationMatchableFields`: `name`, `birthDate`, `deathDate` — nooit de opgehaalde
  waarden zelf) en een verplichte, niet-lege leesbare `reason`
  (`ExternalVerificationReasons`), naar het patroon van `PrivacyClassificationResult`.
- `ExternalVerificationLicense.kt` bevat een nieuw, los domeinbegrip voor de per-record
  hergebruikslicentie, gescheiden van `ExternalVerificationStatus`: `ExternalVerificationLicenseStatus`
  (`LICENSE_KNOWN`/`LICENSE_UNKNOWN`), `ExternalVerificationLicenseResult` (bewaakt in `init` dat
  `licenseValue` uitsluitend gezet is bij `LICENSE_KNOWN`) en `ExternalVerificationLicenseEvaluator`.
  `ExternalVerificationLicenseEvaluator.evaluate` leest uitsluitend het `license`-veld van het
  antwoord van dát ene record (nooit een waarde van een ander record binnen dezelfde archiefcollectie
  hergebruikt of gecachet); de hele evaluatie zit in `runCatching` en levert bij ontbrekende, lege of
  onverwachte waarden fail-closed `LICENSE_UNKNOWN` op (`ExternalVerificationLicenseReasons`).
- `ExternalVerificationService.verify` orkestreert client, matcher, licentie-evaluator en opslag en
  levert een `ExternalVerificationOutcome` (opgeslagen record plus reden). `ExternalVerificationRepository`
  (Flyway-migratie `V5__external_verification.sql` gevolgd door `V6__external_verification_license.sql`,
  tabel `external_verification`) slaat uitsluitend de minimale verificatievelden op: externe URI,
  gematchte velden, controletijdstip, status, licentiestatus, licentiewaarde (indien bekend),
  licentiecontroletijdstip en — indien aanwezig — het versleutelde toegangstoken; nooit de volledige
  externe JSON-LD-payload. `V6` voegt `license_status` (NOT NULL, default `LICENSE_UNKNOWN`),
  `license_value` (nullable) en `license_checked_at` (NOT NULL, default `CURRENT_TIMESTAMP`) toe, met
  een consistency-check dat `license_value` alleen gezet is bij `LICENSE_KNOWN`; bestaande rijen
  krijgen automatisch de fail-closed default, dus backward-compatible zonder handmatige backfill.
- `ExternalVerificationPublishGuard.assertPublishable` is een losstaande, herbruikbare guard (naar
  het patroon van `PrivacyPublishGuard`) die publicatie weigert met
  `ExternalVerificationPublishBlockedException` wanneer de status niet `VERIFIED` is, én wanneer de
  licentiestatus niet `LICENSE_KNOWN` is — beide checks zijn onafhankelijk van elkaar, dus een
  `VERIFIED`-record met `LICENSE_UNKNOWN` wordt alsnog geweigerd. Er is nog geen bestaande
  publicatieworkflow om op aan te sluiten.
- `ExternalVerificationTokenCipher` versleutelt/ontsleutelt een optioneel archiefendpoint-token met
  AES-256-GCM. De sleutel komt uit `hkh.externalverification.token-key` (env
  `HKH_EXTERNAL_VERIFICATION_TOKEN_KEY`), naar het patroon van de bestaande `secrets.env`-aanpak;
  zonder geconfigureerde sleutel faalt versleuteling fail-closed. Het token wordt nooit in leesbare
  vorm getoond, gelogd of in een API-respons opgenomen — dit is nieuw, want er bestond nog geen
  precedent in de repo voor het versleuteld bewaren van uitgaande tokens (de bestaande
  Nimbus/JWKS-code verifieert uitsluitend inkomende tokens).
- Frontend: `frontend-admin/lib/externalverification/external_verification_link_view.dart` bevat
  `ExternalVerificationLinkView`, die de link naar het externe archiefrecord toont met een
  `Semantics`-node (`link: true`) waarvan het `label` programmatisch aankondigt dat de link een
  externe bron in een nieuw tabblad opent (`linkSemanticLabel`), naar de bestaande
  toegankelijkheidsconventies van `frontend-admin`.
- Frontend: `frontend-admin/lib/externalverification/license_status_view.dart` bevat
  `LicenseStatusView`, naar het patroon van `PrivacyClassificationStatusView`: tekstlabel
  (`Licentie bekend`/`License unknown`) en icoon (`Icons.verified`/`Icons.help_outline`), nooit
  uitsluitend via kleur. De vaste voorgrondkleuren in `LicenseStatusColors` halen tegen de witte
  achtergrond een contrastratio van 7.87:1 (`knownForeground`) respectievelijk 6.57:1
  (`unknownForeground`), ruim boven de WCAG 2.1 AA-minimumwaarde van 4.5:1. Het icoon krijgt een
  eigen `semanticLabel` (`Icoon <label>`), zodat tekstlabel en icoon elk een eigen node in de
  semantiekboom hebben.

## Flutter-webstatussemantiek

Statussen gebruiken een eigen `Semantics`-container met `SemanticsRole.status` en exact één
betekenisvol label. Op Flutter web wordt dit de ARIA-statusrol. Die rol is volgens WAI-ARIA een
beleefde, atomische live region en hoort bij een statuswijziging geen focus te krijgen. Daarom wordt
geen aanvullende `liveRegion`-vlag, focuscallback of programmatische focus gebruikt.

Bij een zichtbare kopie vervangt `excludeSemantics: true` alleen de semantiek van die kopie; gewone
uitleg, versiegegevens, nieuwsinhoud en retryknoppen blijven afzonderlijk leesbaar. De geladen
nieuwsgroep gebruikt `explicitChildNodes: true`, zodat het statuslabel één node blijft terwijl de
berichten toegankelijk blijven. Decoratieve statusiconen zijn uitgesloten.

Bronnen voor deze keuze:

- [Flutter `Semantics`](https://api.flutter.dev/flutter/widgets/Semantics-class.html): een container
  introduceert een eigen node en `excludeSemantics` vervangt kindsemantiek;
- [WAI-ARIA 1.2 `status`](https://www.w3.org/TR/wai-aria-1.2/#status): status is impliciet
  `aria-live="polite"`, atomisch en mag door een wijziging geen focus ontvangen.

De Material-knoppen behouden de standaard Enter- en spatieactivering. Een gedeelde `ButtonStyle`
voegt voor `WidgetState.focused` een contrasterende rand van drie pixels toe: `onPrimary` op de
gevulde knop en `primary` op de omlijnde knop. De natuurlijke widgetvolgorde bepaalt lees- en
focusvolgorde; er worden geen aangepaste sort keys gebruikt.

`RecordIntakeForm` (frontend-admin) wijkt hier bewust van af: het is een formulierstatus na een
gebruikersactie, geen passieve achtergrondstatus, dus wordt `Semantics(liveRegion: true)` gebruikt
in plaats van `SemanticsRole.status`, en verplaatst een mislukte validatie de toetsenbordfocus
programmatisch naar de foutsamenvatting (`Focus` + `FocusNode.requestFocus()`), met
`explicitChildNodes: true` zodat de samenvatting één label blijft terwijl de losse foutregels
afzonderlijk aan hun veld gekoppeld en focusbaar blijven.

## Verificatieconfig

`.factory/verification.yaml` gebruikt schema 1. Iedere opdracht heeft een stabiele id, een directe
`argv` zonder shell, een bestaande relatieve working directory en een begrensde timeout. Het vangnet
bestaat uit Maven `clean verify`, analyze en tests voor beide Flutter-apps en een release-webbuild
van de gebruikersfrontend. De factory voert dit na de agentrun opnieuw uit en koppelt resultaten aan
HEAD plus de worktree-tree.

Bekende valkuil: een expressiecallback als `setState(() => future = load())` retourneert de toegewezen
`Future` in debugmodus. Retrycallbacks gebruiken daarom een block-body die synchroon `void` blijft.
