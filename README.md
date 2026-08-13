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
mee. De gebruikersroute kondigt deze overgangen aan via één statusregio. Bij volledige bronuitval
zijn `Opnieuw proberen` en `Zoekopdracht aanpassen` beschikbaar; aanpassen behoudt de ingevulde
waarden en brengt de focus naar het bestaande zoekveld. Per geselecteerde bron geeft de respons
daarnaast de telling op de huidige zichtbare pagina en een als zodanig gelabelde lokale
Heemskerk-indicatie op basis van zekere plaatsmetadata; die indicatie is geen historisch bewijs.
De Europeana-wskey is uitsluitend server-side configuratie; zie
[docs/factory/secrets-local.md](docs/factory/secrets-local.md) voor de configuratienamen.

Voor een Heemskerk-zoekopdracht bevraagt de Open Archieven-adapter de bron met afzonderlijke
parameters `name=Heemskerk` en `archive_code=hee`. Een geldig Open Archieven-resultaat exposeert
de door de bron geleverde `source_name`, `stable_identifier` in de vorm `hee:uuid` en
`original_source_url`; die bronlink wordt nooit lokaal samengesteld. Ontbrekende, lege,
ongeldige of tegenstrijdige verplichte responsvelden maken de bronrespons ongeldig. Een lege
`docs`-lijst met `number_found: 0` blijft daarentegen een beschikbaar nulresultaat. De adapter
behoudt de bestaande User-Agent, limiet, paginering en procesbrede rate limit. De bronstatus maakt
voor Open Archieven afzonderlijk onderscheid tussen `TIMEOUT`, `HTTP_ERROR`, `INVALID_JSON` en
`MISSING_REQUIRED_FIELDS`; de frontend toont daarvoor vaste, veilige meldingen. Andere
transportproblemen blijven `TEMPORARILY_UNAVAILABLE`. Geen van deze foutstatussen toont een
resultatentelling of ruwe bron- of exceptiondetails.

De rechtenvelden van elk resultaat worden uitsluitend uit expliciete rechtenmetadata van dat
bronresultaat bepaald. Alleen `ALLOWED` en `RESTRICTED` worden herkend; ontbrekende, lege,
niet-herkende of tegenstrijdige waarden worden `UNKNOWN`. Het vrije tekstveld `rights` blijft
aanvullende broninformatie en bepaalt geen gecontroleerde status. Kaart en detailweergave tonen
metadatarechten en object-/mediarechten afzonderlijk, met een semantische, toetsenbordbedienbare
uitleg dat beide statussen onafhankelijk zijn en dat `UNKNOWN` geen toestemming of weigering betekent.

Beschikbare resultaten hebben een actie `Context bekijken`. Die detailweergave behoudt de bestaande
bron-, identifier-, URL-, ophaal-, rechten- en privacymetadata en toont ook plaats, periode, persoon
en gebeurtenis. Ontbrekende of onbeschikbare context wordt als `Niet beschikbaar` en onzekere of
tegenstrijdige context als `Onzeker` getoond. Verwante resultaten komen uitsluitend uit de huidige
zichtbare resultatenpagina, worden op exact genormaliseerde plaats-, persoons- of gebeurtenisvelden
gevonden en zijn begrensd op drie; een periode-overlap maakt op zichzelf geen relatie.
Wanneer de externe bron expliciet relaties met doelrecords levert, toont de detailweergave daarnaast
een afzonderlijke sectie `Bronvastgelegde relatie`. Iedere geldige bronrelatie bevat het relatietype,
de bronnaam, de doelrecordnaam, een expliciete stabiele doel-URI en een externe doelrecordlink. De
weergave labelt dit als bronclaim en vermeldt dat HKH de relatie niet heeft afgeleid; onvolledige of
onveilige relaties worden niet opgenomen. De oorspronkelijke bronlink van het geopende resultaat,
de metadata-overlap en de vervolgzoekacties blijven hiervan onafhankelijk.
Vanuit de detailweergave zijn daarnaast nieuwe zoekingangen beschikbaar voor expliciet aanwezige,
zekere plaats-, persoons- en gebeurtenismetadata en voor een geldige expliciete periode. Deze acties
zijn fail-closed begrensd tot beschikbare resultaten met toegestane metadatarechten en een duidelijke
privacystatus, gebruiken exact de oorspronkelijke bronwaarde en starten de bestaande zoekroute zonder
bronfilter, zodat Europeana en Open Archieven volgens de standaardkeuze worden bevraagd. De nieuwe
zoekweergave toont de gebruikte vervolgwaarde en de waarschuwing dat dit geen bewezen relatie tussen
bronnen is; de detailweergave en beide terugnavigatiestappen blijven behouden. Er wordt geen zoekopdracht,
bronpayload of klikgeschiedenis lokaal opgeslagen.

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
