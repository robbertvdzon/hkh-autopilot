# hkh-autopilot-32 - Deployment-pariteit voor Open Archieven-configuratie

## Story

Deployment-pariteit voor Open Archieven-configuratie

<!-- refined-by-factory -->

## Scope

Leg één versie van de actuele Open Archieven-configuratie vast voor productie en acceptatie. Deze configuratie bevat het gevalideerde endpoint, het zoekpad, de bestaande parameter-mapping en de bestaande Open Archieven-feature-instellingen.

De configuratie volgt het bestaande zoekcontract:

- endpoint `https://api.openarchieven.nl/1.1`;
- zoekpad `/records/search.json`;
- `name`, optioneel `eventplace`, `number_show` en `start`;
- `archive_code=hee` wanneer de zoekopdracht expliciet op Heemskerk uitkomt;
- Open Archieven blijft onafhankelijk beschikbaar wanneer Europeana niet is geconfigureerd;
- bestaande timeout-, cache-, rate-limit-, budget- en privacyveilige loggingregels blijven van kracht.

Productie en acceptatie gebruiken dezelfde niet-geheime, versioneerde configuratie. Lokale fixture- en mock-overschrijvingen blijven uitsluitend voor tests en lokaal gebruik beschikbaar.

## Acceptance criteria

1. De productie-overlay en acceptatie-overlay gebruiken aantoonbaar dezelfde gevalideerde Open Archieven-endpoint-, pad-, parameter- en featureconfiguratie. De configuratie bevat geen secrets of zoekpayloads.

2. Een geautomatiseerde pariteitstest controleert de effectieve configuratie van beide overlays tegen één canoniek contract en faalt bij een afwijkende, ontbrekende of syntactisch ongeldige waarde. De test controleert afzonderlijk endpoint, zoekpad, parameters en feature-instellingen.

3. Een geautomatiseerde integratietest gebruikt een lokale geldige Open Archieven-fixture via de publieke zoekroute. De respons wordt geclassificeerd als `AVAILABLE` en levert minstens één zichtbaar resultaat met bronnaam, `hee:uuid`-identifier en de exact door de bron geleverde permanente HTTP(S)-link. De respons wordt niet geclassificeerd als bronfout, ongeldige bronrespons of ongeldig JSON.

4. Een geautomatiseerde smoke-test voor `q=Heemskerk` controleert dat de publieke zoekroute de Open Archieven-adapter bereikt en dat het verzoek de bestaande Heemskerk-mapping gebruikt, inclusief `archive_code=hee`.

5. De bestaande statusmatrix blijft ongewijzigd: geldige nulresultaten blijven beschikbaar met nul resultaten, gedeeltelijke beschikbaarheid behoudt geldige resultaten, en fouten zoals timeout, HTTP-fout, ongeldig JSON en ontbrekende verplichte velden blijven afzonderlijke foutstatussen.

6. Er wordt geen nieuwe externe bron, publieke route, kaart-, tijd- of vervolgzoekfunctionaliteit toegevoegd. Tokens, volledige zoekpayloads, bronpayloads, identifiers en zoekwaarden worden niet gelogd; bestaande allowlisted operationele logging blijft leidend.

## Aannames

- Productie betekent de OpenShift-overlay en acceptatie betekent de bestaande acceptatie-overlay.
- De publieke Open Archieven-endpoint is de gedeelde productie- en acceptatiebron; fixtures en mock-endpoints zijn alleen voor geautomatiseerde tests.
- De bestaande adapter en `GET /api/historical-search` blijven het contractuele uitgangspunt.
- De bestaande parametersemantiek, statuswaarden, caching, rate limiting, verzoekbudgetten en veilige logging worden niet inhoudelijk gewijzigd.
- Een configuratieoverschrijving mag lokaal of in tests blijven bestaan, maar mag de gedeelde productie- en acceptatieconfiguratie niet verschillend maken.

## Vastlegging

De gedeelde configuratie staat in `deploy/base/open-archieven-config.yaml` en wordt zonder override
geërfd door `deploy/overlays/openshift` (productie) en `deploy/overlays/acceptance` (acceptatie).
De backenddeployment importeert de ConfigMap via `envFrom`. De pariteitstest
`Hkh195OpenArchievenConfigurationContractTest` rendert beide effectieve overlays en vergelijkt
endpoint, zoekpad, parameter-mapping, feature-instellingen, syntactische geldigheid en
secretsafety met één canoniek contract. Fixture- en mock-overschrijvingen blijven beperkt tot lokaal
gebruik en tests.

## Eindsamenvatting

Opgeleverd voor `hkh-autopilot-32`:

- Eén gedeelde, niet-geheime Open Archieven-configuratie voor productie en acceptatie.
- Pariteitstest die de effectief gerenderde overlays vergelijkt met het canonieke contract.
- Correcte versiepad-opbouw (`/1.1/records/search.json`) en Heemskerk-mapping met `archive_code=hee`.
- Lokale fixturetests voor geldige resultaten, nulresultaten, gedeeltelijke beschikbaarheid, time-outs, HTTP-fouten, ongeldige JSON en ontbrekende velden.
- Volledig vangnet groen: backend 361 tests, frontend 86 tests plus analyse/webbuild, frontend-admin 38 tests plus analyse. De 11 direct relevante backendtests zijn opnieuw groen uitgevoerd.
- Geen nieuwe bron, route, kaart-, tijd- of vervolgzoekfunctionaliteit toegevoegd; er is geen echte externe bron bevraagd en geen productie-deploy uitgevoerd.

<!-- deploy-summary:start -->
Productie en acceptatie gebruiken nu dezelfde gecontroleerde Open Archieven-instellingen. Hierdoor blijven zoekopdrachten en foutmeldingen in beide omgevingen betrouwbaar en gelijk. Dit is uitgebreid getest met lokale voorbeelden.
<!-- deploy-summary:end -->
