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
Bij gedeeltelijke bronuitval met minstens één uitgevallen bron is `Opnieuw proberen` eveneens
beschikbaar. De frontend bewaart voor een retry één genormaliseerde momentopname van de retrybare
zoekopdracht (vrije tekst, filters, bronkeuze, pagina-offset en limiet); tijdens de nieuwe aanvraag
blijven de vorige geldige resultaten, bronstatussen, tellingen en ingevulde velden zichtbaar. Een
lopende retry start geen tweede aanvraag. Een geldige retry vervangt de vorige uitkomst volledig;
bij een transportfout of `SOURCE_FAILURE` blijven geldige deelresultaten staan en wordt de nieuwe
veilige bronfout afzonderlijk gemeld. Er wordt geen ruwe providerrespons, exceptiontekst of
zoekgeschiedenis bewaard.
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
resultatentelling of ruwe bron- of exceptiondetails. Per daadwerkelijke Open Archieven-poging
schrijft de adapter daarnaast één operationeel logevent met uitsluitend `event=OPEN_ARCHIEVEN_SEARCH`,
`source=OPEN_ARCHIEVEN`, de technische `outcome`, niet-negatieve `durationMs`, de HTTP-statusklasse
(`1xx` t/m `5xx`) wanneer een HTTP-respons beschikbaar is, en `processedResultCount` voor een
beschikbaar resultaat, inclusief `0` voor een geldig nulresultaat. Bij fouten zonder HTTP-respons
blijft de statusklasse leeg en bij niet-beschikbare resultaten blijft de resultatentelling leeg.
Zoekwaarden, namen, queryparameters, URL's, bronpayloads, identifiers, exceptiondetails en
stacktraces komen niet in dit logevent terecht; er wordt geen zoekgeschiedenis of persistente
loggingopslag toegevoegd.

De bronstatus van een werkelijk uitgevoerde Open Archieven-aanvraag bevat daarnaast nullable
`querySemantics`: uitsluitend de semantische providerparameters die het adapterverzoek werkelijk
bevatte, zoals `name` of `eventplace`. Technische parameters zoals `archive_code`, paginering en
rate limiting worden hierin niet opgenomen. De gebruikersfrontend toont per Open Archieven-bron
deze waarden als `Zoekinterpretatie: naam (name).` of `Zoekinterpretatie: plaats (eventplace).`;
als Open Archieven niet is bevraagd of de semantiek niet betrouwbaar bekend is, toont zij
`Zoekinterpretatie: niet beschikbaar.`. De interpretatie wordt nooit afgeleid uit de zoekterm,
resultaatmetadata, titel, URL of een andere bron.

Open Archieven wordt daarnaast beschermd met een proceslokaal per-IP-verzoekbudget: maximaal 10
directe aanvragen en maximaal 60 toegestane aanvragen per rollende minuut. Alleen een expliciet
vertrouwde proxy mag `X-Forwarded-For` voor het gebruikers-IP aanleveren; anders gebruikt de backend
het directe connection-IP. Een overschrijding geeft HTTP 429 met alleen de vaste foutcode
`RATE_LIMITED`. Geldige genormaliseerde Open Archieven-pagina's worden maximaal 30 seconden in een
begrensde procescache bewaard; gelijktijdige misses delen één externe aanvraag. Een cache-hit bewaart
de bestaande resultaten, status-, rechten-, privacy- en bronlinkvelden. Een upstream HTTP 429 wordt
als `RATE_LIMITED` gemapt en krijgt hoogstens één retry met een bruikbare `Retry-After` van maximaal
twee seconden. Er wordt geen nieuwe taalparameter toegevoegd en cache, budget en single-flight zijn
niet-persistent.

De rechtenvelden van elk resultaat worden uitsluitend uit expliciete rechtenmetadata van dat
bronresultaat bepaald. Alleen `ALLOWED` en `RESTRICTED` worden herkend; ontbrekende, lege,
niet-herkende of tegenstrijdige waarden worden `UNKNOWN`. Het vrije tekstveld `rights` blijft
aanvullende broninformatie en bepaalt geen gecontroleerde status. Kaart en detailweergave tonen
metadatarechten en object-/mediarechten afzonderlijk, met een semantische, toetsenbordbedienbare
uitleg dat beide statussen onafhankelijk zijn en dat `UNKNOWN` geen toestemming of weigering betekent.

De afzonderlijke beheerfrontend biedt daarnaast een geauthenticeerde historische statusweergave via
`GET /api/admin/historical-search`. Per resultaat toont zij veilige bronidentiteit en tekstuele,
serverzijdig bepaalde statussen voor bronverificatie, metadatarechten, privacy, publieke vrijgave en
object-/mediarechten. Publieke vrijgave is alleen bevestigd als alle vereiste bron-, rechten-,
privacy- en identiteitsvoorwaarden bevestigd zijn; ruwe bronpayloads en afgeleide claims worden niet
getoond of opgeslagen.

De publieke resultaatkaart toont alleen resultaten met een geldige stabiele identifier en absolute
HTTP(S)-bron-URL. Voor Open Archieven zijn daarvoor de door de bron geleverde `source_name`, `uuid`
en `original_source_url` alle drie verplicht; ontbrekende, lege of tegenstrijdige waarden leveren
geen kaart of bronlink op. Bij toegestane metadatarechten en een duidelijke privacystatus gebruikt de
kaart de expliciete titel, anders de primaire beschrijving; ontbrekende inhoud blijft leeg en krijgt
geen verzonnen placeholder. De kaart toont de ophaaldatum en afzonderlijke onbekende statuslabels,
en de externe actie heeft het zichtbare label `Externe bron openen in nieuw tabblad`.

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

De reproduceerbare smoke-contractset voor de publieke Heemskerk-zoekketen draait automatisch mee
met deze pipeline. De gerichte backendtest is `Hkh165HistoricalSearchSmokeContractTest`; de
Flutter-tegenhanger staat in
[`frontend/test/hkh165_historical_search_smoke_contract_test.dart`](frontend/test/hkh165_historical_search_smoke_contract_test.dart).
Beide gebruiken uitsluitend synthetische fixtures en lokale mocks; gericht uitvoeren kan met:

```bash
(cd backend && mvn -B --no-transfer-progress -Dtest=Hkh165HistoricalSearchSmokeContractTest test)
(cd frontend && flutter test test/hkh165_historical_search_smoke_contract_test.dart)
```

Echte secrets, lokale overrides, buildoutput en IDE-bestanden worden niet gecommit.
