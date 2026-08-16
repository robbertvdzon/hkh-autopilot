# hkh-autopilot-31 - Geautomatiseerde statusmatrix voor de publieke Open Archieven-flow

## Story

Geautomatiseerde statusmatrix voor de publieke Open Archieven-flow

<!-- refined-by-factory -->

## Scope

Breid de geautomatiseerde contracttestmatrix voor de publieke historische zoekflow uit over de backendroute `GET /api/historical-search` en de bestaande Flutter-zoekweergave.

Gebruik uitsluitend reproduceerbare lokale HTTP-fixtures, gecontroleerde netwerk-mocks en synthetische frontend-responses. De matrix behandelt:

- een geldige Open Archieven-respons;
- een geldig nulresultaat;
- gedeeltelijke bronbeschikbaarheid;
- ongeldige JSON en structureel onvolledige of tegenstrijdige providerdata;
- een timeout;
- een HTTP 5xx-respons;
- ontbrekende rechten- of privacymetadata.

De tests wijzigen geen externe bron, slaan geen zoekgegevens op en tonen geen ruwe providerpayloads, exceptionteksten, persoonsgegevens of secrets.

## Acceptance criteria

- De tests zijn zonder handmatige interactie uitvoerbaar en gebruiken geen echte externe bron.
- Een geldige respons resulteert in `RESULTS`, een beschikbare bron, een correct totaal en een correct per-bron gevonden aantal.
- Bij een geldige respons is minimaal één resultaatkaart zichtbaar met de door de bron geleverde bronnaam, identifier en permanente bronlink. De link wijst exact naar de door de bron geleverde URL.
- Een geldig nulresultaat resulteert in `NO_RESULTS`, totaal `0` en bronstatus `AVAILABLE` met aantal `0`. Er worden geen lege of verzonnen resultaatkaarten of links getoond.
- Bij gedeeltelijke beschikbaarheid blijven de resultaten van de beschikbare bron zichtbaar. De toestand is `PARTIAL_AVAILABILITY`, de uitgevallen bron krijgt een veilige tekstuele melding en de uitgevallen bron krijgt geen numeriek resultaatcount alsof die nul resultaten had.
- Ongeldige JSON resulteert in `INVALID_JSON`; ontbrekende, onjuiste of tegenstrijdige verplichte velden resulteren in `MISSING_REQUIRED_FIELDS`. Als geen bron beschikbaar blijft, is de geaggregeerde toestand `SOURCE_FAILURE`, met lege resultaten en zonder resultaatkaarten of links.
- Een timeout resulteert in `TIMEOUT` en een HTTP 5xx-respons in `HTTP_ERROR`. Beide leveren een expliciete veilige bronfout op, zonder onbevestigde resultaten of providerinhoud in API-respons of UI.
- Ontbrekende of niet-herkende rechten- en privacymetadata wordt als `UNKNOWN` genormaliseerd en in de frontend als `Onbekend` getoond. Inhoudelijke metadata wordt daarbij fail-closed weggelaten; alleen veilige bronidentiteit en statusinformatie mogen zichtbaar blijven.
- Elke matrixcase bevat duidelijke diagnostiek met de scenario-naam en de verwachte toestand, bronstatus, telling en kaartzichtbaarheid.
- De tests worden door de bestaande backend- en frontend-testcommando’s automatisch meegenomen en laten alle fixtures, lokale servers en mockresources na afloop netjes vrij.

## Aannames

- De scope betreft de publieke historische zoekflow en niet de adminroute, individuele metadata-verificatie of vervolgzoekacties.
- “Gedeeltelijke respons” betekent dat minstens één geselecteerde bron beschikbaar is en minstens één andere bron niet beschikbaar is.
- `number_found`/het totale aantal en `resultCount` voor de zichtbare pagina worden afzonderlijk gecontroleerd; bij bronuitval blijven tellingen `null` en worden ze niet als nulresultaat geïnterpreteerd.
- Een resultaat met geldige bronidentiteit maar onbekende rechten of privacy mag als beperkte statuskaart verschijnen; onbevestigde inhoudelijke metadata en ongeldige bronidentiteit mogen nooit worden getoond.
- De bestaande Flutter-widget- en semantiektestconventie blijft leidend; er is geen Playwright- of axe-core-test nodig.

## Eindsamenvatting

Opgeleverd:

- Een automatische contracttestmatrix voor de publieke historische zoekroute.
- Lokale HTTP-fixtures en synthetische Flutter-responses voor geldige, lege, gedeeltelijke, ongeldige, timeout- en 5xx-situaties.
- Controles op statussen, tellingen, bronidentiteit, exacte bronlinks, kaartzichtbaarheid en veilige weergave van onbekende rechten/privacy.
- Providerinhoud, persoonsgegevens en secrets worden niet publiek getoond.
- Gerichte tests: 3 backendtests en 3 Fluttertests, allemaal groen.
- Volledig vangnet: backend 359 tests, frontend 86 tests plus analyse/webbuild en frontend-admin 39 tests plus analyse, allemaal groen.
- Bewust niet gedaan: productiecode gewijzigd of een echte externe bron/previewomgeving gebruikt. Een previewtest was niet mogelijk omdat geen preview-URL is geconfigureerd.

<!-- deploy-summary:start -->
De historische zoekfunctie is uitgebreid gecontroleerd op normale, lege en foutieve zoekresultaten. Ook bij gedeeltelijke problemen blijven beschikbare resultaten betrouwbaar zichtbaar en worden onzekere gegevens veilig gemarkeerd.
<!-- deploy-summary:end -->

Contractnotitie: de lokale roltekst noemt `summary-finished`; volgens het expliciete opdrachtcontract sluit deze run af met `phase: summarized`.
