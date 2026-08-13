# Historische Kring Heemskerk App — Autopilot

Dit is de autonoom productgestuurde variant van de HKH-app. De repository begint met dezelfde
technische baseline als `hkh`; na baseline-tag `comparison-baseline-v1` mogen de productfeatures
uiteen gaan lopen. Product Factory en Software Factory ontwikkelen deze variant verder.

## Componenten

- `backend` — Kotlin, Spring Boot en Spring Modulith;
- `frontend` — Flutter-gebruikersapp voor web en Android; de homepage controleert de backend en
  toont daarna het laatste nieuws en de ingang `Historisch zoeken`;
- `frontend-admin` — afzonderlijke Flutter-webapp voor beheerders;
- `deploy` — OpenShift/Kustomize/ArgoCD-configuratie;
- `.factory` — revisiongebonden verificatie voor Software Factory.

De backendbasis volgt de architectuurconventies van Personal News Feed. De exacte referentie en
bewuste afwijkingen staan in [docs/architecture/reference-baseline.md](docs/architecture/reference-baseline.md).
Repositoryspecifieke build-, test- en toegankelijkheidsafspraken staan in
[docs/factory/](docs/factory/README.md).

De publieke zoekroute `GET /api/historical-search` zoekt, zonder lokale opslag van zoekopdrachten
of bronpayloads, in Europeana en Open Archieven. De respons onderscheidt resultaten, nul resultaten,
gedeeltelijke bronbeschikbaarheid en volledige bronuitval; `total` telt alleen beschikbare bronnen
mee. De Europeana-wskey is uitsluitend server-side configuratie; zie
[docs/factory/secrets-local.md](docs/factory/secrets-local.md) voor de configuratienamen.

## Backend lokaal starten

Vereisten: JDK 21 en Maven 3.9 of nieuwer.

```bash
cp secrets.env.example secrets.env
docker compose -f docker-compose.dev.yml up -d
mvn -f backend/pom.xml spring-boot:run
```

De applicatie leest `secrets.env` uit de repositoryroot. Proces-environmentvariabelen hebben
voorrang. Controleer na het starten:

```text
GET http://localhost:8080/actuator/health
GET http://localhost:8080/api/version
GET http://localhost:8080/swagger-ui.html
```

## Verificatie

Het volledige repositoryvangnet, gelijk aan `.factory/verification.yaml`, is:

```bash
(cd backend && mvn -B --no-transfer-progress clean verify)
(cd frontend && flutter analyze)
(cd frontend && flutter test)
(cd frontend && flutter build web)
(cd frontend-admin && flutter analyze)
(cd frontend-admin && flutter test)
```

Echte secrets, lokale overrides, buildoutput en IDE-bestanden worden niet gecommit.
