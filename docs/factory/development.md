# Development

## Vereisten

- JDK 21 en Maven 3.9 of nieuwer;
- Flutter stable 3.44.7 met Dart 3.12.2 (gelijk aan CI);
- voor lokaal backendgebruik: Docker met Compose en een `secrets.env` op basis van
  `secrets.env.example`.

## Repositorystructuur

- `backend/`: Kotlin, Spring Boot, Spring Modulith, Maven en backendtests; features zijn modules
  onder `nl.vdzon.hkh` met een eigen `package-info.java`, waaronder de interne domeinmodule
  `linkdossier` met de koppelingsdossiervalidator en de module `recordintake` met het
  `POST /api/record-intake`-endpoint (tokenverificatie, veld- en privacyvalidatie, opslag als
  intern concept plus optionele externe conceptkoppeling, Flyway-migratie `V4__record_intake.sql`);
- `frontend/`: Flutter-gebruikersapp; homepage en statusflows staan in `lib/main.dart`,
  broninterfaces onder `lib/backend/` en `lib/news/`, widgettests onder `test/`;
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
