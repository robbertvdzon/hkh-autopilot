# Development

## Vereisten

- JDK 21 en Maven 3.9 of nieuwer;
- Flutter stable 3.44.7 met Dart 3.12.2 (gelijk aan CI);
- voor lokaal backendgebruik: Docker met Compose en een `secrets.env` op basis van
  `secrets.env.example`.

Wanneer een feature Agent Runtime gebruikt, voer na het maken van `secrets.env` eenmaal
`./deploy/configure-agent-runtime-secrets.sh` uit. De vaste API-flow en testgrenzen staan in
[`agent-runtime.md`](agent-runtime.md); vraag de Stakeholder nooit om een bestaand Factory-token.

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
  intern concept plus optionele externe conceptkoppeling, Flyway-migratie `V4__record_intake.sql`)
  en de module `externalverification` met het `POST /api/external-verification`-endpoint
  (bevraagt archieven.nl zonder autorisatietoken, matcht naam en datumvelden tot status
  `Verified`/`Unverified`, beoordeelt per record en los daarvan de hergebruikslicentie tot
  `LICENSE_KNOWN`/`LICENSE_UNKNOWN` (`ExternalVerificationLicenseEvaluator`), `ExternalVerificationPublishGuard`
  (weigert publicatie bij `Unverified` én bij `LICENSE_UNKNOWN`), versleutelde opslag van een optioneel
  archieftoegangstoken via `ExternalVerificationTokenCipher`, Flyway-migraties
  `V5__external_verification.sql` en `V6__external_verification_license.sql`) en de module
  `personsearch` met het `POST /api/person-search`-endpoint (route-gebonden anonieme sessiecookie
  `hkh_person_search_session`, atomaire idempotente jobcreatie, synchrone uitvoering met een harde
  2000ms-deadline, live Open Archieven Records/Search-/Records/Show-aanroepen via
  `ArchivesOpenSearchClient` met `PersonSearchRateLimiter` (4 req/s) en fail-closed validatie, en een
  optionele Wikidata-contextaanroep via `PersonSearchWikidataContextClient`). Het jobstatuscontract
  (`QUEUED, RUNNING, READY, NO_EVIDENCE, PARTIAL, FAILED, CANCELLED, EXPIRED`) is
  worker-onafhankelijk (gewone gedeelde executor, geen Agent Runtime); `GET /{jobId}/status`,
  `POST /{jobId}/cancel`, `POST /{jobId}/open` en `GET /session` zijn sessiegebonden fail-closed
  endpoints. Jobs leven in-memory zonder aparte databasetabel; oorspronkelijke vraag en
  antwoordpayload worden versleuteld bewaard (`PersonSearchPayloadCipher`, AES-256-GCM,
  `HKH_PERSON_SEARCH_PAYLOAD_KEY`, fail-closed zonder sleutel) en door
  `PersonSearchRetentionCleanupTask` (`@Scheduled`) verwijderd na 60 min sessie-inactiviteit of 24
  uur, wat eerder komt; en de module `placesearch` met het `POST /api/place-search`-endpoint voor de
  plek/gebouw-route (Wikidata + Wikimedia Commons): geen sessiegebonden achtergrondjob, maar één
  synchrone aanroep binnen een harde 2000ms-deadline (`PlaceSearchService`, eigen
  `placeSearchExecutor`-bean). `PlaceSearchWikidataClient` doet `wbsearchentities` gevolgd door
  `Special:EntityData` per QID en filtert op P131 (Q9926, evt. één niveau doorverwezen) of P625
  binnen een vaste, code-gedocumenteerde bounding box (geen SPARQL); bij precies 1 match bouwt
  `PlaceSearchAnswerBuilder` genummerde antwoordzinnen (label/description/P571/P149/P84/P1435) met
  bronverwijzing per QID, en haalt `PlaceSearchCommonsClient` via P373 (categorie) of P18-fallback
  maximaal 6 gededupliceerde Commons-afbeeldingen op. Alles kortstondig in-memory TTL-gecachet
  (`PlaceSearchCache`), fail-closed op elke fout/timeout/budgetoverschrijding (`OUTAGE`);
- `frontend/`: Flutter-gebruikersapp; homepage en statusflows staan in `lib/main.dart`,
  broninterfaces onder `lib/backend/` en `lib/news/`, widgettests onder `test/`; de volledig
  client-side persoonsvraag-/Heemskerk-disambiguatiemodule (start-, meaning-selection- en
  no-reliable-source-scherm, `PersonQueryInterpreter`, `WikidataMeaningClient`) staat onder
  `lib/personquery/` — `PersonQueryInterpreter` herkent hier ook een landmark-gebaseerd
  plek/gebouw-kandidaat die voorrang krijgt op de persoonsroute; de zes schermen voor de live
  zoek-/antwoordroute (`live-search`, `supported-answer`, `followed-connection`, `source-outage`,
  `background-search`, `search-ready`), de sessie-indicator (`SessionIndicatorBadge`) en de
  bijbehorende client (`PersonSearchClient`) staan onder `lib/personsearch/`, widget- en
  unittests onder `test/personsearch/`; de drie schermen voor de synchrone plek/gebouw-route
  (`place-answer`, `place-empty`, `place-outage`) en de bijbehorende client (`PlaceSearchClient`)
  staan onder `lib/placesearch/`, widget- en unittests onder `test/placesearch/`;
- `frontend-admin/`: afzonderlijke Flutter-webbeheerapp en widgettests;
- `deploy/`: OpenShift-, Kustomize- en ArgoCD-manifests;
- `.factory/verification.yaml`: machine-leesbaar, revisiongebonden verificatievangnet.

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
  gebruiken deterministische fakes en `Completer`s voor laden, fout, retry, leeg en succes.
- Widgettests inspecteren de daadwerkelijke Flutter-semantiekboom en toetsenbordfocus. Nieuwe
  zichtbare statuskopieën mogen geen tweede `SemanticsRole.status` opleveren.
- Kotlin-tests draaien via Maven `verify`; componentgrenzen volgen de bestaande Spring
  Modulith-structuur. Een nieuwe module krijgt een `package-info.java` met expliciete
  `allowedDependencies` (geen wildcard) en wordt opgenomen in de moduleset van
  `ModulithArchitectureTest`.
- Geen echte secrets, persoonsgegevens, buildoutput of lokale overrides versioneren.
