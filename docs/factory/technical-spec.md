# Technical Spec

## Stack en componenten

- Backend: Kotlin op JDK 21, Spring Boot/Spring Modulith, Maven, PostgreSQL 16 en Flyway.
- Gebruikersfrontend: Flutter stable 3.44.7, Dart 3.12.2, Material 3, `http`; web en Android.
- Beheerfrontend: afzonderlijke Flutter-webapp.
- Deployment: containers, Kustomize/OpenShift en ArgoCD.

De backendservicecontrole combineert `GET /actuator/health` en `GET /api/version`; beide moeten
binnen tien seconden met een geldige 200-respons slagen. Nieuws komt van `GET /api/news` en heeft
dezelfde clienttimeout. `API_BASE_URL` is een compile-time Dart-define.

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
  de kernvelden gaan naar de matcher.
- `ExternalVerificationMatcher.match` vergelijkt naam en geboorte-/overlijdensdatum van het lokale
  record met de opgehaalde JSON-LD-kernvelden en levert een `ExternalVerificationMatchResult` op
  met status `VERIFIED` (alle velden komen overeen) of `UNVERIFIED` (geen match, inclusief een
  niet-bestaande/ongeldige guid), een lijst met uitsluitend de namen van gematchte velden
  (`ExternalVerificationMatchableFields`: `name`, `birthDate`, `deathDate` — nooit de opgehaalde
  waarden zelf) en een verplichte, niet-lege leesbare `reason`
  (`ExternalVerificationReasons`), naar het patroon van `PrivacyClassificationResult`.
- `ExternalVerificationService.verify` orkestreert client, matcher en opslag en levert een
  `ExternalVerificationOutcome` (opgeslagen record plus reden). `ExternalVerificationRepository`
  (Flyway-migratie `V5__external_verification.sql`, tabel `external_verification`) slaat
  uitsluitend de minimale verificatievelden op: externe URI, gematchte velden, controletijdstip,
  status en — indien aanwezig — het versleutelde toegangstoken; nooit de volledige externe
  JSON-LD-payload.
- `ExternalVerificationPublishGuard.assertPublishable` is een losstaande, herbruikbare guard (naar
  het patroon van `PrivacyPublishGuard`) die publicatie weigert met
  `ExternalVerificationPublishBlockedException` wanneer de status niet `VERIFIED` is en niets doet
  bij `VERIFIED`. Er is nog geen bestaande publicatieworkflow om op aan te sluiten.
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
