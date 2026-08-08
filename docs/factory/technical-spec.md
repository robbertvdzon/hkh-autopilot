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
