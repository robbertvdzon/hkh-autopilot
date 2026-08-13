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
mee. Per geselecteerde bron geeft de respons daarnaast de telling op de huidige zichtbare pagina en
een als zodanig gelabelde lokale Heemskerk-indicatie op basis van zekere plaatsmetadata; die indicatie
is geen historisch bewijs. De Europeana-wskey is uitsluitend server-side configuratie; zie
[docs/factory/secrets-local.md](docs/factory/secrets-local.md) voor de configuratienamen.

Beschikbare resultaten hebben een actie `Context bekijken`. Die detailweergave behoudt de bestaande
bron-, identifier-, URL-, ophaal-, rechten- en privacymetadata en toont ook plaats, periode, persoon
en gebeurtenis. Ontbrekende of onbeschikbare context wordt als `Niet beschikbaar` en onzekere of
tegenstrijdige context als `Onzeker` getoond. Verwante resultaten komen uitsluitend uit de huidige
zichtbare resultatenpagina, worden op exact genormaliseerde plaats-, persoons- of gebeurtenisvelden
gevonden en zijn begrensd op drie; een periode-overlap maakt op zichzelf geen relatie.

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
