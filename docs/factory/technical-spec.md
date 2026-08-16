# Technical Spec

## Stack en componenten

- Backend: Kotlin op JDK 21, Spring Boot/Spring Modulith, Maven, PostgreSQL 16 en Flyway.
- Gebruikersfrontend: Flutter stable 3.44.7, Dart 3.12.2, Material 3, `http`; web en Android.
- Beheerfrontend: afzonderlijke Flutter-webapp.
- Deployment: containers, Kustomize/OpenShift en ArgoCD.

De backendservicecontrole combineert `GET /actuator/health` en `GET /api/version`; beide moeten
binnen tien seconden met een geldige 200-respons slagen. Nieuws komt van `GET /api/news` en heeft
dezelfde clienttimeout. `API_BASE_URL` is een compile-time Dart-define.

## Backendmodule `news`

De module `nl.vdzon.hkh.news` (inclusief de subpackage `news.api` en `news.entity`) levert het
bestaande `GET /api/news`/`POST /api/admin/news`-contract en de daarop afgeleide entiteits- en
zoekfunctionaliteit. De module staat in de moduleset van `ModulithArchitectureTest`; er is geen
nieuwe route of controller bijgekomen — `LatestNewsController` en `AdminLatestNewsController`
blijven de enige twee, gedekt door een routecontracttest op `RequestMappingHandlerMapping`.

- Entiteitsherkenning is deterministisch en gebaseerd op een statische gazetteer, geen NLP/NER:
  `nl.vdzon.hkh.news.entity.NewsGazetteer` laadt per entiteitstype (`NewsEntityType`: `PLEK`,
  `PERSOON`, `GEBEURTENIS`) een lijst `GazetteerEntry` (`canonicalLabel` + `aliases`) vanaf het
  classpath uit `backend/src/main/resources/gazetteer/{plek,persoon,gebeurtenis}.json`, via
  `NewsGazetteerLoader`/`NewsGazetteerConfiguration` (Spring-bean). `NewsEntityMatcher.match` matcht
  elke alias case-insensitive, diakrieten-genormaliseerd (`Normalizer.Form.NFD` +
  verwijdering van `\p{Mn}`-marks) en op heel woord (`\b`-regex) tegen titel + samenvatting samen,
  dedupliceert op `canonicalLabel` en sorteert op entiteitstype-ordinal, daarbinnen op eerste
  voorkomen in de tekst. Geen match levert een lege lijst op, nooit een fout.
- `LatestNewsService.search(q, entity)` combineert `LatestNewsStore.findAll()` met de matcher en
  past de optionele queryparameters `q` (case-insensitive substring op titel of samenvatting) en
  `entity` (case-insensitive vergelijking met een `canonicalLabel`) toe als AND-filter. Het
  resultaat (`NewsSearchResult`) bevat `items` (elk bericht met zijn eigen entiteitenlijst),
  `total` en de top-level `entities`: de geaggregeerde, van het filter onafhankelijke telling per
  entiteit over alle berichten in `latest_news`.
- `LatestNewsController#findAll` (nog steeds `GET /api/news`) accepteert de optionele
  `q`/`entity`-queryparameters en retourneert `LatestNewsListResponse` (`items`, `total`,
  `entities`) in plaats van een kale array. Elk item krijgt een `source`-bronvermelding in de vorm
  "Afkomstig uit gepubliceerd HKH-nieuwsbericht, gepubliceerd op `<publishedAt>`". `POST
  /api/admin/news` (aanmaken, `LatestNewsResponse`) is ongewijzigd: alleen berichten die via die
  create-flow in `latest_news` terechtkomen, worden ooit meegenomen in entiteiten of
  zoekresultaten.
- Het uitgebreide responscontract is als build-documentatie vastgelegd via een springdoc
  OpenAPI-schematest: die leest `/v3/api-docs` en bevestigt de schemavelden van
  `LatestNewsListResponse`/`LatestNewsItemResponse`/`AggregatedNewsEntityResponse` en de
  `q`/`entity`-queryparameters, zodat een vervolgstory hierop kan voortbouwen zonder handmatige
  afstemming.
- Frontend: `frontend/lib/news/latest_news.dart` heeft een nieuwe `NewsEntity`-klasse en
  `LatestNewsItem.entities` (default leeg)/`source` (optioneel) gekregen. `LatestNewsSource.
  loadLatestNews` accepteert nu optionele `q`/`entity`-parameters en retourneert het volledige
  `NewsSearchResult` (`items`, `total`, `entities: List<AggregatedNewsEntity>`) in plaats van een
  kale itemlijst — een breaking change ten opzichte van de vorige story, bewust doorgevoerd omdat
  het homepage-ontdekblok (hieronder) zowel de items als de geaggregeerde entiteiten nodig heeft.
  `frontend/lib/backend/backend_client.dart#loadLatestNews` bouwt `q`/`entity` op als
  queryparameters (weggelaten wanneer leeg/`null`) en parset de volledige
  `{items, total, entities}`-respons via `NewsSearchResult.fromJson`. `frontend-admin` roept alleen
  `POST /api/admin/news` aan en is ongewijzigd.

## Homepage-ontdekblok (gebruikersfrontend)

`frontend/lib/news/discover_section.dart` bevat `DiscoverSection`, het zoek-/ontdekblok op de
homepage (`frontend/lib/main.dart`, route `/`), naast de bestaande servicestatus- en "Laatste
nieuws"-secties (na `_LatestNewsSection` in de widgetboom, zodat de bestaande Tab-volgorde tussen
de productvisieknop en de "Opnieuw proberen"-knop van die sectie ongewijzigd blijft). Het blok is
de enige "primaire ontdekactie" op de homepage; er is geen los, tweede toegankelijkheidspad.

- Databron is uitsluitend het bestaande `GET /api/news`-contract via `LatestNewsSource.
  loadLatestNews`, zonder nieuwe backendroute of -contractwijziging. `DiscoverSection` doet bij
  `initState` een ongefilterde aanroep om de geaggregeerde `entities` te tonen als entiteitchips,
  onafhankelijk van elke latere zoekopdracht of chipselectie.
- Componenten: een gelabeld `TextField` (`Zoek in nieuwsberichten`), een rij `ActionChip`s per
  geaggregeerde entiteit (`<type>: <label>`, `PLEK`/`PERSOON`/`GEBEURTENIS`), een resultatenlijst
  (titel, samenvatting, entiteitstype-badges, bronregel) of — bij nul resultaten — een niet-lege
  lege-staat (`_EmptyState`) met suggestiechips uit diezelfde, van het filter onafhankelijke
  `entities`-lijst, en een detailweergave (`NewsDetailPage`, volledige berichttekst,
  publicatiedatum, bron) met een `AppBar`-terugknop, naar het patroon van `ProductVisionPage`
  (`Navigator.push`/`pop`, geen los "terug"-widget op dezelfde pagina).
- Het zoekveld gebruikt bewust `onEditingComplete` in plaats van `onSubmitted`: Flutter's
  standaard `onSubmitted`-afhandeling unfocust het veld eerst, wat de Tab-volgorde na een
  zoekopdracht zou terugzetten naar het begin van de pagina; `onEditingComplete` roept de
  zoekactie aan zonder de focus te verliezen.
- Toegankelijkheid volgt de bestaande repo-conventie: de resultatentelling
  (`discover-result-count`) staat in `Semantics(liveRegion: true)` naar het `RecordIntakeForm`-
  patroon en de tekst wijzigt bij elke nieuwe zoekactie of chipselectie. Zoekveld, chips,
  resultaatkaarten en de terug-/wisknop zijn volledig met Tab/Enter/Spatie bedienbaar: chips en de
  resultaatkaart (`InkWell`) gebruiken Flutter's standaard `ActivateIntent`-toetsenbordafhandeling,
  de wis-/terugknoppen hergebruiken de bestaande `_focusBorderStyle`-driepixel-focusrand (naar het
  patroon van `_retryButtonStyle` in `main.dart`).
- Badge-/chipkleuren staan in `NewsEntityBadgeColors` (nieuw), naar het patroon van
  `PrivacyClassificationStatusColors`: vaste voorgrondkleuren tegen een witte achtergrond, elk
  ≥4.5:1 (PLEK 7.87:1, PERSOON 8.63:1, GEBEURTENIS 6.57:1). Elke badge is een eigen
  `Semantics(label: '<type>: <label>')`-node met `ExcludeSemantics` op de visuele `Container`,
  zodat de badge één semantiekknoop blijft.
- Alle gerenderde velden komen uitsluitend uit `LatestNewsItem`/`NewsEntity`/
  `AggregatedNewsEntity`; er wordt nooit record-intake-, privacyclassificatie- of
  externe-verificatiedata gebruikt of getoond (afgedwongen doordat `DiscoverSection` alleen
  `lib/news/latest_news.dart` importeert, gecontroleerd door een gerichte test op de brontekst).
- Getest met Flutter widget-/semantiektests (`frontend/test/discover_section_test.dart`): chips uit
  de API-`entities`, chipklik toont resultaten met niet-lege bronregel, een onbekende zoekterm
  toont de lege staat met suggestiechips, een resultaatkaart opent de detailweergave (volledige
  tekst/datum/bron) met werkende terugknop, een volledig toetsenbord-only doorloop
  (`tester.sendKeyEvent`, Enter/Spatie, geen tap) van zoekveld → chip → resultaatkaart →
  terug-/wisknop, een semantiekboomtest op labels/rollen van elk interactief element, een
  kleur-/contrasttest (WCAG-formule, ≥4.5:1) op `NewsEntityBadgeColors`, de
  `liveRegion`-telling die wijzigt per zoekactie/chipselectie, en een test die bevestigt dat alleen
  `LatestNewsItem`/`NewsEntity`/`AggregatedNewsEntity`-velden gebruikt worden.

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
en `@ApplicationModule(allowedDependencies = {"auth", "externalverification",
"privacyclassification"})` — expliciete, niet-wildcard afhankelijkheden op die drie modules (`auth`
kwam erbij voor de admin-bevestigingsroute hieronder). De module staat in de moduleset van
`ModulithArchitectureTest`, met alle drie de afhankelijkheden opgenomen in de
moduleset-verificatie.

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
- `RecordIntake.kt` heeft twee nieuwe velden gekregen: `deceasedStatus` (ruwe `String?`, fail-closed
  `ONBEKEND` zonder invoer) en `nextOfKinConfirmed` (`Boolean?`), plus `confirmExternalArchiveData`
  (`Boolean?`) dat aanvraagt dat de service de externe bron servergezijdig herbevraagt. Flyway-
  migratie `V8__record_intake_deceased_status_and_archive_data.sql` voegt aan `record_intake` de
  kolommen `deceased_status`, `next_of_kin_confirmed` en de niet-persoonsgebonden `archive_name`,
  `archive_birth_date`, `archive_death_date`, `archive_license`, `archive_source_uri` en
  `archive_fetched_at` toe.
- `RecordIntakeArchiveUrlPattern.parse` herkent of een `durableUrl` het patroon
  `http://opendata.archieven.nl/id/<adtid>/<guid>` volgt (`Regex.matchEntire`) en levert bij een
  match een `ArchiveUrlReference(adtid, guid)` op, anders `null`, zonder dat er ooit een
  netwerkaanroep gedaan wordt.
- `POST /api/record-intake/external-archive-preview` (`RecordIntakeExternalArchivePreviewController`)
  is een apart, niet-persisterend previewendpoint: het toetst `durableUrl` tegen
  `RecordIntakeArchiveUrlPattern`, bevraagt bij een match `ArchivesNlClient.fetch` (module
  `externalverification`, zonder autorisatietoken) en retourneert uitsluitend de gestructureerde
  kernvelden (naam, geboorte-/sterftedatum, licentie, bron-URI) met een statuslabel
  (`GEVERIFIEERD`/`GEEN_MATCH`/`NIET_BEREIKBAAR`); de ruwe externe respons wordt hier nooit bewaard.
- `RecordIntakeService.create` roept, wanneer `confirmExternalArchiveData == true` en
  `externalLink.durableUrl` het archieven.nl-patroon volgt, de externe bron opnieuw aan — de eerder
  via het previewendpoint aan de client getoonde data wordt nooit vertrouwd of hergebruikt. Het
  bouwt daarna, naar het patroon van `PrivacyClassificationResult`, een tijdelijk lokaal en een
  tijdelijk extern `GenealogicalRecord` (module `privacyclassification`) op en classificeert beide
  met `PrivacyClassifier.classify()`. `RecordIntakeExternalArchiveOutcome.stored` is alleen `true`
  wanneer beide classificaties `PROCESSABLE` opleveren; alleen dan slaat `RecordIntakeStore.create`
  naam/geboorte-/sterftedatum daadwerkelijk op. Licentie, bron-URI en ophaaldatum
  (`RecordIntakeExternalArchiveDataToStore`) worden bij een geslaagde bevraging altijd opgeslagen,
  ongeacht de classificatie-uitkomst; de vaste, niet-lege redenteksten staan in
  `RecordIntakeExternalArchiveReasons`. `RecordIntakeResponse.externalArchiveData` geeft de
  beheerder terug of de externe kernvelden opgeslagen zijn en waarom (niet) — nooit de opgehaalde
  naam-/datumwaarden zelf.
- `RecordIntakeRecord` heeft twee nieuwe nullable velden gekregen: `confirmedBy` (`String?`) en
  `confirmedAt` (`Instant?`), gevuld via Flyway-migratie `V9__record_intake_confirmation.sql`
  (kolommen `confirmed_by` VARCHAR(320), `confirmed_at` TIMESTAMPTZ op `record_intake`).
  `RecordIntakeStore`/`RecordIntakeRepository` kregen `findByLocalIdentifier` (meest recente record
  per `localIdentifier`) en `confirm` (UPDATE ... RETURNING dat `confirmed_by`/`confirmed_at` zet,
  `null` bij een onbekende identifier).
- `RecordPublicStatus.kt` bevat de enum `RecordPublicStatus` (`NO_INTAKE`/`SAVED_WITHOUT_SOURCE`/
  `CONFIRMED`), het view-type `RecordPublicView` en `RecordPublicStatusResolver`
  (Spring-`@Component`), die per verzoek herberekent: geen record → `NO_INTAKE`; geen gevulde
  `archiveName`/`archiveSourceUri` of geen `confirmedBy`/`confirmedAt` → `SAVED_WITHOUT_SOURCE`; een
  bij dit verzoek opnieuw uitgevoerde `PrivacyClassifier.classify()` (met een tijdelijk, uit
  `deceasedStatus`/`nextOfKinConfirmed` samengesteld `GenealogicalRecord`) die niet `PROCESSABLE`
  oplevert → `SAVED_WITHOUT_SOURCE` zonder `confirmedBy`/`confirmedAt` te wissen (zelfherstellend
  gedrag); anders `CONFIRMED` met naam, jaartal-only geboorte-/sterftedatum (regex op de eerste
  4-cijferige reeks in `archiveBirthDate`/`archiveDeathDate`, nooit dag-/maandprecisie), licentie,
  bron-URI en `confirmedAt`.
- Nieuwe publieke, ongeauthenticeerde route `GET /api/records/{localIdentifier}`
  (`RecordPublicController`) levert altijd HTTP 200 op (ook zonder bestaand record), zodat "bestaat
  niet" niet via de HTTP-status te onderscheiden is van "bestaat wel, nog niet bevestigd";
  retourneert uitsluitend de velden uit `RecordPublicView` (nooit de ruwe `RecordIntakeRecord`).
- Nieuwe admin-only route `POST /api/admin/record-intake/{localIdentifier}/confirm`
  (`RecordIntakeConfirmationController`) hergebruikt `AdminAuthenticator` (zelfde
  tokenverificatie-conventie als de rest van de beheerfrontend); zet `confirmedBy`/`confirmedAt` via
  `RecordIntakeStore.confirm` en geeft HTTP 404 bij een onbekende `localIdentifier`. Hiervoor kreeg
  `recordintake`'s `package-info.java` de expliciete, niet-wildcard afhankelijkheid `auth` (zie
  boven).
- Getest met `RecordPublicStatusResolverTest` (unit, alle statusovergangen inclusief het
  zelfherstellende gedrag) en `RecordPublicApiIntegrationTest` (Testcontainers, end-to-end via de
  publieke route en de admin-bevestigingsactie, inclusief het zelfherstellende gedrag op basis van
  een live `deceased_status`-wijziging in de database).
- Frontend: `RecordIntakeForm` debounct wijzigingen aan het duurzame-URL-veld met een
  `Timer`-gebaseerde cooldown van 400 ms (standaard Flutter-mechanisme, geen nieuwe library): elke
  wijziging annuleert de vorige timer, en zowel het verlaten van het veld (focusverlies) als de
  nieuwe knop "Ophalen" triggeren direct een aanroep buiten de debounce om. Een oplopende
  requestId-teller negeert verouderde, nog lopende previewresponses. `ExternalArchivePreviewPanel`
  (`frontend-admin/lib/recordintake/external_archive_preview_panel.dart`) toont het paneel
  "Brongegevens (extern, ter controle)" als `Semantics(liveRegion: true)`-regio, met vaste
  voorgrondkleuren in `ExternalArchivePreviewStatusColors` (`verifiedForeground` 7.87:1,
  `noMatchForeground` 5.93:1, `unreachableForeground` 6.57:1 tegen een witte achtergrond, ruim boven
  de WCAG 2.1 AA-minimumwaarde van 4.5:1) en de knoppen "Bevestig brongegevens en gebruik bij
  record"/"Sla op zonder externe brongegevens", beide met Material's standaard Tab/Enter/Spatie-
  bediening.

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
- `HistoricalMetadataContract`/`HistoricalMetadataResult` is het brononafhankelijke contract voor
  één extern historisch zoekresultaat. Een volledig resultaat bevat alleen een stabiele identifier,
  resolvebare bronlink, bronhouder, titel/beschrijving, datering en bronversie of snapshot-ID,
  naast de server-side UTC-ophaaldatum, afzonderlijke metadata-/objectmediastatussen,
  privacystatus, technische beschikbaarheid en een afgeleide `VERIFIED`/`UNVERIFIED`-status met
  machineleesbare reden. `OpenArchievenMetadataAdapter` leest hiervoor uitsluitend een allowlist
  uit JSON-LD; de bestaande individuele verificatieroute blijft ongewijzigd.
- De contractvalidator is fail-closed: ontbrekende, ongeldige of tegenstrijdige velden, onbekende
  metadatarechten, geblokkeerde/onbekende privacy, lege/ongeldige bronrespons of tijdelijke
  bronuitval levert alleen de bekende veilige bronverwijzing en technische status op. Onbekende
  object-/mediarechten blokkeren metadata-verificatie niet, maar geven nooit `mediaAllowed`.
  Mogelijke persoonsgegevens of levende-personenvelden worden vóór het contractresultaat
  verwijderd door de uitkomst te blokkeren; bronpayload, gevoelige velden en payloadteksten komen
  niet in opslag, response of logging terecht.
- Uitgaande metadata-aanroepen sturen `HKH-Autopilot-HistoricalMetadata/1.0` als beschrijvende
  user-agent en gebruiken één procesbrede `FourPerSecondRateLimiter`-singleton voor de gedeelde
  backend-egress, met minimaal 251 ms tussenruimte. De doelhost en het doel-IP zijn bewust geen
  onderdeel van de bucket: verschillende bronnen kunnen de limiet voor hetzelfde server-uitgaande
  proces dus niet opsplitsen. Er is geen eindgebruikers-IP bij betrokken. Er is geen cache:
  een nieuwe bronversie of ETag/Last-Modified wordt bij iedere bevraging opnieuw zichtbaar
  vastgelegd. Dit herbruikbare metadata-contract heeft zelf geen opslagmodel of frontendweergave;
  de zelfstandige publieke zoekroute gebruikt het aparte `HistoricalSearchContract` hieronder.
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

## Backendmodule `historicalsearch`

De publieke historische zoekroute zit in de zelfstandige Spring Modulith-module
`nl.vdzon.hkh.historicalsearch` (`package-info.java`, met de API-subpackage
`historicalsearch.api`) en is opgenomen in `ModulithArchitectureTest`. De module heeft geen opslag,
migratie of afhankelijkheid op nieuws, record-intake, adminfunctionaliteit of de lokale
privacyclassificatie.

- `HistoricalSearchContract.kt` bevat de brononafhankelijke `HistoricalSearchQuery`,
  `HistoricalSearchResult`, `HistoricalSearchPage`, bron- en statusenums (waaronder
  `RATE_LIMITED`) en
  `HistoricalSearchValidation`. `HistoricalSearchResult` bevat naast de bestaande metadata ook
  `place`, `relationships` en de drie contextstatussen `placeStatus`, `personStatus` en `eventStatus`, elk
  `AVAILABLE`, `MISSING`, `UNCERTAIN` of `UNAVAILABLE`. De normalisatie trimt lege waarden weg,
  vereist twee viercijferige jaren in een geldige volgorde, valideert `start >= 0` en begrenst
  `limit` op 1..100. De veilige tekst- en URLhelpers weigeren controletekens, te lange waarden en
  niet-HTTP(S)-URL's. Iedere relatie bevat `type`, `source.name`, `target.name`, een expliciet
  geleverde HTTP(S)-`target.uri` en een expliciet geleverde HTTP(S)-`target.link`; de lijst is
  standaard leeg.
- `HistoricalSearchController` registreert uitsluitend `GET /api/historical-search` met `q`,
  `place`, `person`, `event`, `fromYear`, `toYear`, `source`, `start` en `limit`. Een ongeldige
  query geeft HTTP 400 met `{ "error": "..." }`; een geldig verzoek geeft `{ results, total, start,
  limit, sources, state }`. `state` is `RESULTS`, `NO_RESULTS`, `PARTIAL_AVAILABILITY` of
  `SOURCE_FAILURE`; `sources` bevat voor elke geselecteerde bron de technische status
  (`AVAILABLE`, `DISABLED`, `TEMPORARILY_UNAVAILABLE` of `INVALID_RESPONSE`; voor Open Archieven
  daarnaast `TIMEOUT`, `HTTP_ERROR`, `INVALID_JSON`, `MISSING_REQUIRED_FIELDS` of `RATE_LIMITED`), een veilige
  korte melding en nullable `resultCount`/`heemskerkCount`-velden. Voor een beschikbare bron tellen deze
  telvelden alleen de veilig genormaliseerde resultaten in de huidige zichtbare `results`-pagina; voor
  een niet-beschikbare bron blijven beide `null`. Een
  Open Archieven-bronstatus bevat daarnaast nullable `querySemantics`: alleen de semantische
  providerparameters die het adapterverzoek daadwerkelijk gebruikte, zoals `name` en `eventplace`;
  technische parameters zoals `archive_code`, `number_show`, `start` en rate limiting worden niet
  opgenomen. De lijst komt rechtstreeks uit het opgebouwde adapterverzoek, is per bron en wordt
  `null` wanneer Open Archieven niet is bevraagd of de semantiek niet betrouwbaar vaststaat. Elk
  resultaat bevat de genormaliseerde metadata,
  de server-side UTC `retrievedAt`, de
  contextvelden/statussen en afzonderlijke technische, metadatarechten-, object-/mediarechten- en
  privacystatussen. De API-controller exposeert deze velden als `place`, `placeStatus`,
  `personStatus`, `eventStatus` en `relationships`, waarbij iedere relatie de velden `type`,
  `source.name`, `target.name`, `target.uri` en `target.link` behoudt. `stableUrl` blijft de link
  naar het oorspronkelijke zoekresultaat. Voor Open Archieven bevat de response daarnaast de
  expliciete snake_case-velden `source_name`, `stable_identifier` en `original_source_url`; de
  identifier heeft voor deze adapter de vorm `hee:uuid`. Een nieuw inkomend verzoek dat het lokale
  Open Archieven-budget overschrijdt geeft HTTP 429 met alleen `{ "error": "RATE_LIMITED" }`.
- `HistoricalSearchService` kiest één bron of beide bronnen, haalt providerpagina's op met een
  maximum van 100 records, en merge't beide bronstromen via cursors. De cursor telt ook provider-
  records zonder geldige URL mee, zodat volgende pagina's geen duplicaten of gaten krijgen. Een
  records zonder geldige URL mee, zodat volgende pagina's geen duplicaten of gaten krijgen. Alleen
  bronnen met de uiteindelijke status `AVAILABLE` leveren resultaten en een `total`-bijdrage. Een
  technische fout tijdens een vervolgaanvraag wordt als bronstatus doorgegeven; de bron wordt uit
  de merge gehaald en de effectieve offset wordt herberekend over de resterende beschikbare stream.
  Als geen bron beschikbaar blijft, retourneert de service `SOURCE_FAILURE` met lege resultaten en
  `total = 0`; alleen wanneer alle geselecteerde bronnen beschikbaar zijn en geen resultaten leveren,
  is de toestand `NO_RESULTS`.
- `HistoricalSearchService` berekent `heemskerkCount` uitsluitend met
  `HistoricalSearchResult.isHeemskerkPlaceIndicator()`: `placeStatus` moet `AVAILABLE` zijn en de
  expliciete plaatswaarde wordt met Unicode-NFKC, samengevoegde witruimte en hoofdletterongevoelige
  vergelijking exact genormaliseerd naar `heemskerk`. Er worden geen plaatswaarden uit zoekfilters,
  titels of URLs afgeleid. Een beschikbare lege bron krijgt tellingen `0`; een bron met een andere
  technische status krijgt geen numerieke telling. De telling blijft een plaatsmetadata-indicatie
  en wordt niet als historisch bewijs gepresenteerd.
- `HistoricalSearchAdapters.kt` bevat `EuropeanaSearchAdapter` en
  `OpenArchievenSearchAdapter`. Europeana gebruikt `GET /record/v2/search.json` met `wskey`,
  `query`, herhaalde `qf`, `rows` en `start`; Open Archieven gebruikt
  `GET /records/search.json` met `name`, optioneel `eventplace`, `number_show` en `start`. Wanneer
  de zoekopdracht expliciet op Heemskerk uitkomt, stuurt de adapter `archive_code=hee` als aparte
  parameter mee. Een gebeurtenis wordt in Open Archieven met een `~` als lage zoekzekerheid
  toegevoegd; jaren volgen de provider-syntaxis. Beide adapters lezen plaats, persoon en gebeurtenis uitsluitend uit
  expliciete, scalar bronvelden. Daarnaast lezen ze uitsluitend een expliciete provider-array
  `relationships`; complete relaties worden in bronvolgorde gemapt. Een ontbrekend type, bronnaam,
  doelnaam, stabiele doel-URI of externe doel-link laat de volledige relatie weg. Relaties worden
  nooit uit contextvelden, titels, zoekfilters, URL's of periode-overlap afgeleid. Ontbrekende velden
  worden `MISSING`; conflicterende, te lange of onveilige waarden `UNCERTAIN`. Beide adapters sturen
  `HKH-Autopilot-HistoricalSearch/1.0`. De rechtenmapping gebruikt voor beide velden alleen de
  expliciete resultaatvelden `metadataRights`/`metadataRightsStatus` en
  `objectRights`/`mediaRights`/`objectMediaRightsStatus`: exact `ALLOWED` wordt `ALLOWED`, exact
  `RESTRICTED` wordt `RESTRICTED` en alles wat ontbreekt, leeg, niet-herkend of tegenstrijdig is
  wordt `UNKNOWN`. Het vrije `rights`- of `license`-veld wordt niet voor deze mapping gebruikt.
- `OpenArchievenSearchAdapter` schrijft in een `finally`-pad precies één operationeel logevent per
  daadwerkelijke providerpoging. De logregel heeft uitsluitend de velden `event=OPEN_ARCHIEVEN_SEARCH`,
  `source=OPEN_ARCHIEVEN`, `outcome`, `durationMs`, `httpStatusClass` en `processedResultCount`.
  `outcome` gebruikt dezelfde technische status als de zoekpagina (`AVAILABLE`, `TIMEOUT`,
  `HTTP_ERROR`, `INVALID_JSON`, `MISSING_REQUIRED_FIELDS`, `RATE_LIMITED`, `DISABLED` of een andere veilige
  transportstatus). `durationMs` is niet-negatief; `httpStatusClass` is `1xx` t/m `5xx` zodra
  een HTTP-respons beschikbaar is en blijft anders leeg. `processedResultCount` bevat alleen het aantal
  veilig genormaliseerde resultaten van een beschikbare pagina en is dus `0` voor een geldig
  nulresultaat; bij iedere fout of niet-beschikbare pagina blijft het leeg. Querywaarden, namen,
  volledige queryparameters, URLs, response-body's, bronrecordgegevens, identifiers, exceptiontekst
  en stacktraces worden niet als loggerargument of fallbackinhoud gebruikt. De logging is uitsluitend
  operationeel en voegt geen zoekgeschiedenis, gebruikersprofiel of persistente opslag toe.
- De Europeana-wskey komt uit `hkh.historical.europeana-wskey` / `HKH_EUROPEANA_WSKEY`. Een lege
  waarde markeert alleen Europeana als `DISABLED`; Open Archieven blijft onafhankelijk beschikbaar.
  De providerbasis-URL's zijn overschrijfbaar via `HKH_HISTORICAL_EUROPEANA_BASE_URL` en
  `HKH_HISTORICAL_OPEN_ARCHIEVEN_BASE_URL`, zodat tests fixtures/mockservers kunnen gebruiken.
- Voor productie en acceptatie is de Open Archieven-configuratie gecentraliseerd in de niet-geheime
  ConfigMap `deploy/base/open-archieven-config.yaml`. De OpenShift-overlay en acceptatie-overlay
  erven deze ConfigMap zonder lokale patch. Het canonieke contract bevat endpoint
  `https://api.openarchieven.nl/1.1`, zoekpad `/records/search.json`, de providerparameter-namen
  `name`, `eventplace`, `number_show`, `start` en `archive_code`, Heemskerk-code `hee`, timeout
  `10s`, cacheduur `30s`, rate-limitinterval `251ms`, budget `60` per rollende minuut, burst `10`
  en refill `1.0` per seconde. De backenddeployment importeert deze waarden via `envFrom`; lokale
  fixture- en mock-overrides blijven uitsluitend voor lokaal/testgebruik toegestaan.
- `OpenArchievenSearchAdapter` gebruikt bij een actieve aanvraag een proceslokaal per-IP-budget van
  maximaal 10 directe pogingen en 60 pogingen per rollende minuut. `HKH_HISTORICAL_TRUSTED_PROXY_ADDRESSES`
  configureert de directe proxy-peers waarvoor `X-Forwarded-For` als client-IP mag gelden; buiten die
  context is het directe connection-IP leidend. De cacheduur is configureerbaar via
  `HKH_HISTORICAL_OPEN_ARCHIEVEN_CACHE_DURATION` (standaard `30s`); de cache is proceslokaal en
  begrensd op 1.024 entries. Cachekeys bevatten bron, een SHA-256-digest van de volledige
  genormaliseerde providercontext, offset, limiet en de vaste taalwaarde `nl`, zonder vrije
  zoekwaarden. Alleen geldige `AVAILABLE`-pagina's worden gecachet en gelijktijdige identieke
  cachemisses delen één in-flight aanvraag. Een cache-hit verbruikt geen nieuw providerbudget.
- `FourPerSecondHistoricalRateLimiter` is één Spring-bean die alle Open Archieven-aanvragen deelt en
  minimaal 251 ms tussen permits afdwingt. De limiet is procesbreed, niet per eindgebruikers-IP of
  per host. Er is geen persistente opslag van zoektermen, responses, media of persoonsgegevens.
- De story-brede smoke-contracttest
  `Hkh165HistoricalSearchSmokeContractTest` gebruikt een lokale `HttpServer` met minimale
  synthetische fixtures en bouwt de publieke route met dezelfde adapters, cache en request-budget-
  componenten op. Zij controleert de geldige Heemskerk-mapping, nulresultaat, gedeeltelijke en
  volledige bronuitval, uitgeschakelde Europeana, veldgerichte foutdiagnose en één upstream-aanvraag
  voor identieke gelijktijdige zoekcontexten. De test draait automatisch onder Maven `verify`.
- De uitgebreide publieke statusmatrix
  `Hkh189HistoricalSearchContractTest` gebruikt eveneens uitsluitend een lokale `HttpServer`.
  Zij controleert naast geldige en lege responses ook timeout, HTTP 5xx, ongeldig JSON, ontbrekende
  of tegenstrijdige verplichte providerdata, gedeeltelijke bronbeschikbaarheid en ontbrekende
  rechten-/privacymetadata. Per scenario worden de geaggregeerde state, bronstatus, totale en
  nullable per-bron telling, kaartzichtbaarheid, door de bron geleverde identiteit en exacte URL
  gecontroleerd; providerinhoud wordt uit de publieke respons geweerd. De Flutter-tegenhanger
  `hkh189_historical_search_contract_test.dart` voert synthetische responses door de bestaande
  `HistoricalSearchPage` en controleert dezelfde contractwaarden, veilige frontendmeldingen,
  `Onbekend`-mapping en kaartzichtbaarheid. Beide tests draaien automatisch onder de bestaande
  Maven-/Flutter-testcommando's.
- `Hkh195OpenArchievenConfigurationContractTest` rendert de effectieve OpenShift- en
  acceptatie-overlays met Kustomize en vergelijkt de endpoint-, pad-, parameter- en
  featureconfiguratie afzonderlijk met het canonieke contract. De test controleert ook dat de
  ConfigMap geen secrets of zoekpayloads bevat en dat de providerbasis-URL syntactisch geldig is.
- De adapters vereisen een expliciete resultaatarray (`items` voor Europeana, `docs` voor Open
  Archieven). Voor Open Archieven zijn bovendien een object `response`, een niet-negatieve
  numerieke `number_found` en per document de verplichte velden `source_name`, veilige `uuid` en
  absolute HTTP(S)-`original_source_url` met geldige host vereist. `number_found` moet met een
  lege/niet-lege `docs`-pagina overeenkomen en minstens de paginagrootte omvatten. Een niet-2xx
  providerantwoord wordt `HTTP_ERROR`, ongeacht de body. Een lege of niet als JSON leesbare respons
  wordt `INVALID_JSON`; foutobjecten, ontbrekende, lege, onjuiste of tegenstrijdige verplichte
  velden worden `MISSING_REQUIRED_FIELDS`. Een ongeldige individuele Open Archieven-documentrespons
  maakt dus de bronrespons ongeldig. Een lege `docs`-lijst met `number_found: 0` blijft een geldige
  lege bronrespons. De bronlink wordt uitsluitend uit het bronantwoord overgenomen en nooit
  geconstrueerd.
- `failClosedMetadata()` behoudt de veilige bronidentifier, bronlink, ophaaltijd en afzonderlijke
  statusvelden, maar wist inhoudelijke metadata tenzij `metadataRights == ALLOWED` én
  `privacyStatus == CLEAR`. Bij zo'n blokkade worden de drie contextstatussen `UNAVAILABLE` en
  worden contextwaarden en `relationships` gewist. Onbekende object-/mediarechten blokkeren de
  metadataweergave niet op zichzelf, maar geven geen toestemming om media te tonen.
- `HistoricalSearchRelations.kt` bepaalt de afzonderlijke, afgeleide sectie `Verwante resultaten`
  zonder nieuwe bronaanvraag. Het geopende resultaat
  wordt uitgesloten en alleen beschikbare plaats-, persoons- en gebeurtenisvelden worden vergeleken
  na trimmen, Unicode-NFKC-normalisatie, witruimte-normalisatie en hoofdletterongevoelige
  vergelijking. Eén gedeeld veld volstaat; de uitkomst is maximaal drie kandidaten in zichtbare
  volgorde. Periode-overlap is uitsluitend een annotatie op een bestaande relatie. Deze uitkomst
  staat los van de providerrelaties in `HistoricalSearchResult.relationships`.

## Beveiligde historische beheerroute

`AdminHistoricalSearchController` registreert in dezelfde `historicalsearch`-module de
authenticatie-afgeschermde route `GET /api/admin/historical-search`. De route accepteert `q`,
`place`, `person`, `event`, `fromYear`, `toYear`, `source`, `start` en `limit`, gebruikt dezelfde
queryvalidatie, `HistoricalSearchService`, client-IP-/verzoekbudgetgrens en veilige bronstatussen
als de publieke route, en hergebruikt `AdminAuthenticator` (inclusief de bestaande previewheader).
Een ongeldige query geeft HTTP 400; een overschreden Open Archieven-budget geeft HTTP 429 met alleen
`RATE_LIMITED`.

`HistoricalAdminStatusContract` evalueert ieder genormaliseerd resultaat serverzijdig en zonder
opslag. Het contract geeft alleen veilige, door de bron geleverde `source_name`,
`stable_identifier` en `original_source_url` terug wanneer deze syntactisch geldig zijn; de
identifier en bronlink moeten bovendien gelijk zijn aan de genormaliseerde `sourceRecordId`/
`stableUrl`. De response bevat daarnaast `technicalStatus` en
voor bronverificatie, metadatarechten, privacy, publieke vrijgave en object-/mediarechten telkens
een statusveld en een niet-lege reden, in het envelope `results`, `total`, `start`, `limit`,
`sources` en `state`. Statuswaarden zijn `CONFIRMED`, `UNKNOWN`, `REJECTED` en `NOT_APPLICABLE`.

De bronstatusmapping is fail-closed: ongeldige, onleesbare, ontbrekende of tegenstrijdige
identiteitsmetadata wordt afgewezen of onbekend volgens de technische situatie. Rechten en privacy
worden uitsluitend uit de bestaande expliciete statusvelden afgeleid; `rights`, titels, URL's,
zoektermen en relaties worden niet gebruikt om claims te construeren. Publieke vrijgave wordt alleen
`CONFIRMED` wanneer bronverificatie, metadatarechten, privacy en beide veilige identiteitsvelden
bevestigd zijn. Er worden geen ruwe bronpayloads, extra persoonsgegevens, zoekgeschiedenis of
afgeleide relaties opgeslagen, gelogd of geretourneerd. De module declareert hiervoor de expliciete
`auth`-afhankelijkheid in `package-info.java`.

## Publieke historische zoekfrontend

`frontend/lib/historical/historical_search.dart` bevat `HistoricalSearchPage`,
`HistoricalSearchSource` en de response/result/source-statusmodellen. `BackendClient` implementeert
dit contract naast de bestaande status-, nieuws- en recordbronnen en vertaalt lege filters naar
afwezige queryparameters. De client gebruikt dezelfde 10-seconden-timeout als de andere backendcalls;
HTTP 400 wordt een `HistoricalSearchValidationException` met de servermelding, andere non-200's
blijven retrybare technische fouten.

`HomePage` toont na een succesvolle servicecheck de gelabelde knop `Historisch zoeken` naast de
bestaande nieuwsflow en opent de zelfstandige pagina. Het formulier bevat vrije tekst, plek, persoon,
gebeurtenis, vanafjaar, eindjaar en bronkeuze. De pagina ondersteunt distincte laad-, succes-,
lege-, gedeeltelijke-beschikbaarheids-, volledige-bronuitval-, validatie- en retrybare foutstatussen,
paginering en volledig toetsenbordbedienbare retry- en paginaknoppen. `HistoricalSearchResponse`
leest de expliciete API-state en kan die voor compatibiliteit afleiden uit resultaten, totalen en
bronstatussen wanneer de state ontbreekt. Gedeeltelijke beschikbaarheid toont beschikbare resultaten
en per falende bron een veilige tekstuele melding. Bij beschikbare bronnen toont de status/live-regio
per bron de resultatentelling en de tekst `Lokale Heemskerk-indicatie op basis van plaatsmetadata:
<aantal> (geen historisch bewijs)`. Volledige bronuitval toont geen resultaatcount of andere
numerieke brondekking, maar wel de acties `Opnieuw proberen` en `Zoekopdracht aanpassen`. De laatste
actie focust het bestaande vrije-tekstveld zonder de ingevoerde zoekwaarden te wissen. Retry gaat
opnieuw via de laadstatus. Statussen gebruiken één `SemanticsRole.status`-node; de zichtbare tekst
en laadindicatoren voegen geen extra statusknoop toe en de spinner is semantisch uitgesloten;
automatische statusupdates verplaatsen de focus niet. De bewuste aanpasactie is de enige focusverplaatsing;
de vier Open Archieven-diagnosestatussen worden gemapt naar de vaste meldingen `Open Archieven
reageerde niet op tijd`, `Open Archieven gaf een fout bij het opvragen`, `Open Archieven stuurde een
onleesbaar antwoord` en `Open Archieven stuurde een onvolledig antwoord`. Ruwe provider- of
exceptioninhoud wordt nooit gerenderd;
naast de bronstatus toont de pagina per Open Archieven-bron de herkende `querySemantics` als
`Zoekinterpretatie: naam (name).` of `Zoekinterpretatie: plaats (eventplace).`. Bij een andere
bron, een niet-uitgevoerde Open Archieven-aanvraag of onbekende semantiek toont zij
`Zoekinterpretatie: niet beschikbaar.`. Deze tekst wordt niet afgeleid uit zoekterm,
resultaatmetadata, titel of URL.
externe bronknoppen
hebben een tekstueel label dat het openen van een externe bron in een nieuw tabblad aankondigt.

Een resultaatkaart toont alleen publiek geldige resultaten en alleen toegestane inhoudelijke metadata,
plus altijd de genormaliseerde bronnaam, veilige bronidentifier, oorspronkelijke bron-URL, ophaaldatum
en de afzonderlijke technische, metadatarechten-, object-/mediarechten- en privacystatussen. De
frontend filtert resultaten zonder niet-lege stabiele identifier of absolute HTTP(S)-URL weg. Voor
Open Archieven is `openArchievenContractValid` alleen waar wanneer `source_name`, `stable_identifier`
en `original_source_url` niet-leeg, veilig en consistent met de legacy-identiteitsvelden zijn; een
ongeldig resultaat krijgt geen kaart of externe link. De kaart gebruikt bij `ALLOWED` plus `CLEAR` de
expliciete niet-lege titel, anders de primaire beschrijving, en toont niets inhoudelijks wanneer beide
ontbreken. Onbekende rechten- of privacystatussen mappen naar `Onbekend`. Het zichtbare label
`Externe bron openen in nieuw tabblad` is tegelijk de semantische linknaam en blijft toetsenbordbedienbaar.
Een beschikbare kaart bevat de actie `Context bekijken`, die opent
`historical_context_detail.dart`. De detailweergave toont de context- en bronvelden, herhaalt de
zoek- en bronstatus, gebruikt `Niet beschikbaar`/`Onzeker` volgens de contextstatus en toont
maximaal drie relaties uit de huidige responsepagina. Elke relatielink gebruikt uitsluitend de door
de backend geleverde stabiele URL. Providerrelaties worden daarnaast alleen bij een niet-lege,
geldige lijst getoond in `Bronvastgelegde relatie`, met expliciete bronclaimtekst, target-URI en
een tekstueel aangekondigde externe link naar `target.link`. De frontend gebruikt `unorm_dart`
voor dezelfde NFKC-normalisatie als de backend.

De resultaatkaart en detailweergave gebruiken de gedeelde widget
`historical_rights_explanation.dart`/`HistoricalRightsExplanation`. Deze rendert een semantische
Material `TextButton`-bediening die via Tab, Enter en spatie bereikbaar is. Na activering toont de
widget de uitleg dat metadatarechten en object-/mediarechten onafhankelijk worden beoordeeld in beide
richtingen en dat `UNKNOWN` alleen betekent dat de bron geen expliciete, verifieerbare status levert.

De detailweergave bevat ook `historicalFollowUpActions`, die alleen acties teruggeeft wanneer het
resultaat technisch `AVAILABLE` is, metadatarechten `ALLOWED` zijn en privacy `CLEAR` is. Voor plaats,
persoon en gebeurtenis zijn de contextstatus `AVAILABLE` en een niet-lege expliciete bronwaarde vereist;
de fail-closed JSON-mapping promoveert een ontbrekende of onbekende status nooit op basis van aanwezige
tekst. Een periodeactie vereist beide expliciete datumwaarden als viercijferige jaren in oplopende
volgorde. `HistoricalFollowUpAction` bewaart de oorspronkelijke waarde voor precies één filterveld.

Elke actie is een semantische Material `TextButton` met een onderwerp in het label. De actie pusht een
nieuwe `HistoricalSearchPage` met dezelfde `HistoricalSearchSource` en een `followUp`-actie; de pagina
vult de betreffende controllers, laat de bronkeuze leeg en roept daarna automatisch
`loadHistoricalSearch` aan. Lege filters worden als afwezige queryparameters doorgegeven, zodat de
standaardselectie beide bronnen bevraagt. De vervolgpagina toont de gekozen waarde en de vaste
`historicalFollowUpWarning`-tekst in een programmatisch beschikbare regio. De Navigator-stack bewaart
de oorspronkelijke detailpagina en resultatenlijst. Er is geen opslagmechanisme toegevoegd.

De retry-state in `historical_search.dart` bewaart maximaal één `_CompletedHistoricalSearch` met een
`_HistoricalSearchContext` en de genormaliseerde response, plus één `_lastRequestContext` voor een
aanvraag die nog geen response heeft opgeleverd. De context bevat alleen tekstfilters, bronkeuze,
pagina-offset en limiet; er wordt geen volledige zoekgeschiedenis, ruwe providerrespons of
exceptiontekst bewaard. `_runSearch` legt de context vóór het starten van de Future vast en gebruikt
bij een retry uitsluitend die context, ook wanneer de gebruiker daarna een veld wijzigt. Een retry
kan worden gestart bij gedeeltelijke beschikbaarheid of een bronfout; tijdens het laden toont de
pagina de vorige uitkomst, bronstatussen en tellingen met een tekstuele melding over de lopende
nieuwe poging. `_retryInProgress` verhindert een tweede aanvraag. Een respons met
`RESULTS`, `NO_RESULTS` of `PARTIAL_AVAILABILITY` vervangt de vorige snapshot volledig. Bij
`SOURCE_FAILURE` of een transportfout blijft de vorige geldige uitkomst beschikbaar en worden de
nieuwe bronstatus of vaste transportfout afzonderlijk weergegeven. Zonder eerdere resultaten valt
de weergave terug op de bestaande volledige-bronuitvalstaat met retry- en aanpasacties.

De aanvullende Flutter-smoke-contracttest
`frontend/test/hkh165_historical_search_smoke_contract_test.dart` voert een synthetische respons
door `BackendClient` en `HistoricalSearchPage`. Zij controleert de zichtbaarheid van een geldig
Open Archieven-resultaat en het onderscheid tussen nulresultaat, gedeeltelijke beschikbaarheid en
volledige bronuitval; `flutter test` neemt de test automatisch mee.

## Historische beheerfrontend

`frontend-admin/lib/historical/admin_historical_search.dart` bevat het geïnjecteerde
`AdminHistoricalSearchSource`, de HTTP-client en de JSON-modellen voor de adminrespons.
`AdminHistoricalSearchView` in `admin_historical_search_view.dart` rendert een zoekveld, laad-/fout- en
lege staten, een live resultaatcount en per resultaat de veilige identiteit plus tekstuele status- en
redenregels. De vier statuswaarden worden naar de Nederlandse labels `Bevestigd`, `Onbekend`,
`Afgewezen` en `Niet van toepassing` gemapt; object-/mediarechten blijven zichtbaar als afzonderlijk
bestaand veld. `Semantics`-containers maken elke status en reden tekstueel beschikbaar, terwijl de
vaste statuskleuren op een witte achtergrond minimaal 4,5:1 contrast bieden.

De view wordt na authenticatie in `frontend-admin/lib/main.dart` aan de bestaande beheerhome
toegevoegd en gebruikt dezelfde `AdminIdentity.requestHeaders`; er is geen apart tokenveld. De
gerichte tests staan in `backend/src/test/kotlin/nl/vdzon/hkh/historicalsearch/` en
`frontend-admin/test/admin_historical_search_test.dart`. Ze dekken auth, statusmapping, veilige en
tegenstrijdige bronidentiteit, tekstuele redenen, blokkering van publieke vrijgave, semantiek en
contrast.

## Publieke recorddetailpagina (gebruikersfrontend)

`frontend/lib/records/` bevat de publieke recorddetailpagina en de sectie "Externe
bronverificatie", als nieuwe module naast `frontend/lib/news/`.

- `record_public_view.dart` bevat `RecordPublicStatus` (`noIntake`/`savedWithoutSource`/
  `confirmed`, geparset uit de backend-`status`-string, met `noIntake` als fail-closed default bij
  een onbekende waarde), `RecordPublicView.fromJson` (`localIdentifier`, `status`, optioneel `name`/
  `birthYear`/`deathYear`/`license`/`sourceUri`/`confirmedAt`) en het abstracte
  `RecordPublicSource`-contract (`loadRecord`, levert altijd een resultaat, nooit een 404 op
  clientniveau).
- `BackendClient` (`frontend/lib/backend/backend_client.dart`) implementeert `RecordPublicSource`
  naast de bestaande `BackendStatusSource`/`LatestNewsSource`: `loadRecord` roept
  `GET /api/records/{localIdentifier}` aan met dezelfde 10-secondentimeout-conventie als
  `loadLatestNews` en parset de respons via `RecordPublicView.fromJson`.
- `record_detail_page.dart` bevat `RecordDetailPage` (laadt via `RecordPublicSource.loadRecord` in
  `initState`, met een expliciete "Opnieuw proberen"-knop bij een fout) en
  `ExternalSourceVerificationSection`: een in-/uitklapbare sectie (standaard uitgeklapt) met een h2
  "Externe bronverificatie" (`Semantics(header: true, headingLevel: 2)`) en een toggle-knop met
  `Semantics(button: true, expanded: ..., controlsNodes: {...})` — het Flutter-equivalent van
  `aria-expanded`/`aria-controls` — gekoppeld aan de sectie-inhoud via een gedeelde
  `Semantics(identifier: ...)`-id.
  - Bij `RecordPublicStatus.confirmed`: statuslabel "Extern geverifieerd" met tekst én
    `Icons.verified` (nooit uitsluitend kleur), naam, "Geboortejaar: …"/"Sterftejaar: …" (alleen
    getoond wanneer aanwezig), licentie, een `_ExternalSourceLink` ("Bekijk bron", met
    `Semantics(link: true)` en een label dat programmatisch aankondigt dat de link een externe bron
    in een nieuw tabblad opent) en "Bevestigd door beheerder op dd-mm-jjjj" (`confirmedAt.toLocal()`).
  - In alle andere gevallen: exact dezelfde neutrale tekst
    (`neutralExternalVerificationMessage`), bewust identiek voor `savedWithoutSource`, `noIntake` en
    een gedegradeerd `confirmed`-record, om geen metadata over een eventuele eerdere publicatie te
    lekken.
  - `ExternalSourceVerificationColors`: vaste voorgrondkleuren tegen een witte achtergrond,
    `confirmedForeground` 7.87:1 en `neutralForeground` 10.05:1, beide ruim boven de WCAG 2.1
    AA-minimumwaarde van 4.5:1 (naar het patroon van `PrivacyClassificationStatusColors`).
- `_ExternalSourceLink.openLink` roept `openExternalLink` aan uit `external_link_launcher.dart`, een
  conditionele export (`external_link_launcher_web.dart` op `dart.library.html`, anders
  `external_link_launcher_stub.dart` — nodig omdat `flutter test` standaard op de Dart VM draait,
  zonder `dart:html`). De webvariant gebruikt `package:web`s `window.open(uri, '_blank',
  'noopener')`, het equivalent van `rel="noopener"`, zodat de nieuwe pagina geen `window.opener`
  krijgt. `frontend/pubspec.yaml` kreeg hiervoor de nieuwe dependency `web: ^1.1.0`.
- Getest met Flutter widget-/semantiektests (`frontend/test/record_detail_page_test.dart`): de
  volledige semantiekboom bij `confirmed` (h2, statuslabel, naam, jaartallen, licentie, linktekst —
  met een expliciete assertie dat de datumtekst geen dag-/maandgetal bevat), de neutrale melding
  zonder velden/link voor zowel `savedWithoutSource` als `noIntake` apart, het zelfherstellende
  gedrag over twee opeenvolgende `loadRecord`-aanroepen met een statuswijziging ertussenin, volledige
  toetsenbordbereikbaarheid/-activering van de bronlink (`tester.sendKeyEvent`, geen tap/muis), een
  gerichte WCAG 2.1-contrasttest op `ExternalSourceVerificationColors`, een semantiekboomsnapshot van
  `expanded`/`controlsNodes` vóór en na de toggle, en een regressietest dat de bestaande
  recordvelden, paginanavigatie en het homepage-ontdekblok ongewijzigd blijven. Een aanvullende test
  in `backend_client_test.dart` dekt `loadRecord`/`RecordPublicView.fromJson`.

## Flutter-webstatussemantiek

Statussen gebruiken een eigen `Semantics`-container met `SemanticsRole.status` en exact één
betekenisvol label. Op Flutter web wordt dit de ARIA-statusrol. Die rol is volgens WAI-ARIA een
beleefde, atomische live region en hoort bij een statuswijziging geen focus te krijgen. Voor passieve
statusupdates wordt daarom geen aanvullende `liveRegion`-vlag, focuscallback of programmatische focus
gebruikt. De historische zoekroute heeft één bewuste uitzondering buiten de statusnode: de actie
`Zoekopdracht aanpassen` focust na volledige bronuitval het bestaande zoekveld; automatische
statusupdates behouden ook daar de focus.

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
