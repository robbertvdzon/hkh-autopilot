# hkh-autopilot-33 - Werkende terugnavigatie vanaf Productvisie

## Story

Werkende terugnavigatie vanaf Productvisie

<!-- refined-by-factory -->

## Scope

Zorg dat de zichtbare terugactie op de publieke pagina Productvisie de bezoeker terugbrengt naar de bestaande startpagina.

Behoud de bestaande ingang “Lees onze productvisie”, de bestaande navigatiegeschiedenis en de inhoud van de Productvisiepagina. De terugactie moet zichtbaar, toetsenbordfocusbaar en semantisch herkenbaar zijn en met muis, Enter en spatie werken.

De wijziging blijft beperkt tot de publieke gebruikersfrontend. Backend, API-contracten, database, beheerfrontend, deployment en historische zoekfunctionaliteit vallen buiten scope.

## Acceptance criteria

- Een geautomatiseerde Flutter-widgettest opent de startpagina met deterministische testdata, activeert “Lees onze productvisie” en controleert dat Productvisie zichtbaar wordt.
- Een muisactivatie van de zichtbare terugactie brengt de bezoeker terug naar de bestaande startpagina; Productvisie is daarna niet langer de actieve pagina.
- Een toetsenbordtest brengt focus naar de terugactie en activeert die afzonderlijk met Enter en met spatie. Beide acties brengen de bezoeker terug naar de startpagina.
- De terugactie heeft een passende semantische rol en herkenbare naam en blijft bereikbaar in de normale Tab-volgorde.
- De bestaande navigatiestack blijft intact: Productvisie wordt als vervolgpaginageopend en terugnavigatie keert terug naar de bestaande homepage-route, zonder een tweede homepage of nieuwe deep-linkroute te creëren.
- De bestaande directe opening van Productvisie blijft werken en de Productvisie-inhoud verandert niet.
- De regressietests zijn uitvoerbaar binnen de bestaande frontend-testset en gebruiken geen echte backend, account, persoonsgegevens of externe services.
- Als productie- of acceptatieomgevingen beschikbaar zijn, kan hetzelfde gedrag daar geautomatiseerd worden gecontroleerd. Bij afwezigheid van een geconfigureerde omgeving is de geautomatiseerde Flutter-test het geldige bewijs; handmatige verificatie is niet vereist.

## Aannames

- “Directe navigatie naar Productvisie” betekent het bestaande navigatiepad binnen de app; er wordt geen nieuwe publieke URL- of deep-linkroute geïntroduceerd.
- Terugnavigatie keert terug naar de al geopende startpagina en initialiseert geen nieuwe homepage, zodat bestaande schermstatus en browsergeschiedenis niet onnodig worden vervangen.
- De repositoryconventie van Flutter widget-/semantiektests vervangt de genoemde Playwright-browser-test, omdat Playwright en een Node-toolchain niet in deze checkout aanwezig zijn.

## Eindsamenvatting

Eindsamenvatting voor de PO:

- Productvisie heeft nu een zichtbare, focusbare knop “Terug naar startpagina”.
- De bestaande navigatie en Productvisie-inhoud zijn behouden; de knop gebruikt de bestaande terugnavigatie.
- Getest met muis, Tab, Enter en spatie. Het volledige vangnet slaagde: backend 361 tests, frontend 90 tests, beheerfrontend 38 tests, analyses en webbuild zonder fouten.
- Er was geen previewomgeving beschikbaar; handmatige verificatie is daarom niet uitgevoerd.
- Het actuele opdrachtcontract is gevolgd met phase `summarized`.

<!-- deploy-summary:start -->
Vanaf Productvisie kun je nu duidelijk terug naar de startpagina. Dit werkt met muis en toetsenbord.
<!-- deploy-summary:end -->
