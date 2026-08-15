# hkh-autopilot-29 - Beheerstatus voor bronverificatie en publieke vrijgave

## Story

Beheerstatus voor bronverificatie en publieke vrijgave

<!-- refined-by-factory -->

## Scope

Voeg aan de beheerflow een statusweergave toe voor resultaten uit het bestaande genormaliseerde historische zoekresultaatcontract.

Per resultaat toont de beheerflow:

- de door de bron geleverde bronnaam;
- de stabiele identifier;
- de door de bron geleverde permanente bronlink;
- bronverificatiestatus;
- metadata-rechtenstatus;
- privacystatus;
- status van publieke vrijgave;
- een leesbare toelichting per status.

De vier statussen ondersteunen minimaal `bevestigd`, `onbekend`, `afgewezen` en `niet van toepassing`. De beoordeling wordt deterministisch afgeleid uit de bestaande bronmetadata en technische status. Bronrelaties, zoektermen, titels, URL’s of andere velden worden niet gebruikt om historische relaties of rechtenclaims af te leiden.

De statusweergave is toegankelijk: status, blokkade en toelichting zijn altijd tekstueel beschikbaar en niet uitsluitend herkenbaar via kleur of iconen.

## Acceptance criteria

- Een beheerder kan per extern historisch resultaat de bronnaam, stabiele identifier en permanente bronlink bekijken wanneer deze veilig en door de bron geleverd zijn.
- Elk resultaat toont afzonderlijke tekstuele statussen voor bronverificatie, metadatarechten, privacy en publieke vrijgave, inclusief een niet-lege toelichting.
- Geldige bronmetadata kan leiden tot `bevestigd`; ontbrekende of niet-vaststelbare informatie leidt tot `onbekend`; expliciet beperkte, geblokkeerde, ongeldige of tegenstrijdige informatie leidt tot `afgewezen`; `niet van toepassing` is representeerbaar maar geldt nooit als toestemming voor publieke vrijgave.
- Metadatarechten worden uitsluitend gebaseerd op de bestaande expliciete rechtenstatus. `ALLOWED` wordt bevestigd, `RESTRICTED` afgewezen en ontbrekende of niet-herkende informatie onbekend. Object-/mediarechten blijven een afzonderlijke bestaande beoordeling en verlenen geen extra publicatie- of mediatoestemming.
- Privacy wordt uitsluitend gebaseerd op de bestaande expliciete privacystatus. `CLEAR` wordt bevestigd, `BLOCKED` afgewezen en ontbrekende of niet-herkende informatie onbekend.
- Publieke vrijgave wordt uitsluitend als bevestigd weergegeven wanneer bronverificatie, metadatarechten en privacy alle drie bevestigd zijn en de stabiele identifier en permanente bronlink geldig zijn.
- Bij een onbekende of afgewezen bronverificatie-, rechten- of privacystatus wordt publieke vrijgave niet als bevestigd of publiek beschikbaar weergegeven.
- De statusweergave gebruikt uitsluitend gestructureerde, veilige bronmetadata. Ruwe bronpayloads, extra persoonsgegevens, zoekgeschiedenis en niet-expliciete bronrelaties worden niet opgeslagen, geretourneerd of gelogd.
- De bestaande publieke zoekroute, bronkeuze, paginering, foutstatussen, deelresultaten en retrygedrag blijven inhoudelijk ongewijzigd. Deze story voegt geen nieuwe publieke publicatieroute toe en omzeilt geen bestaande fail-closed weergaveregels.
- Geautomatiseerde fixtures dekken minimaal:
  - bevestigde bronmetadata met bevestigde rechten en privacy;
  - ontbrekende rechteninformatie;
  - ontbrekende privacyinformatie;
  - ongeldige of tegenstrijdige bronmetadata;
  - tekstuele status- en blokkeerredenen;
  - afwezigheid van ruwe payloads, extra persoonsgegevens en afgeleide relaties of rechtenclaims.
- De beheerweergave en statusmeldingen zijn met de bestaande Flutter-widget-/semantiektestconventie controleerbaar en gebruiken minimaal 4,5:1 contrast voor statuskleuren.

## Aannames

- De statussen worden per geladen resultaat serverzijdig en deterministisch afgeleid; er komt geen handmatige statusoverride of afzonderlijke beoordelingsworkflow.
- De bestaande genormaliseerde historische zoekresultaten blijven de bron van waarheid; een nieuwe persistente opslaglaag voor zoekresultaten of bronpayloads is niet nodig.
- `rechtenstatus` in deze story betekent metadatarechten. Object-/mediarechten blijven zichtbaar als afzonderlijk bestaand statusveld wanneer zij beschikbaar zijn.
- Ontbrekende bronvelden worden niet vervangen door legacyvelden, zoektermen of lokaal geconstrueerde identifiers of links.
- Publieke vrijgave is een administratieve afgeleide status en geen nieuwe publicatieactie. De bestaande publieke zoekroute blijft verantwoordelijk voor de zichtbare zoekresultaten.
- De bestaande admin-authenticatie en toegangsconventies worden hergebruikt.

## Eindsamenvatting

Eindsamenvatting voor de PO:

- De beveiligde beheerroute en Flutter-beheerweergave tonen bronnaam, stabiele identifier, permanente link, verificatie-, rechten-, privacy- en vrijgavestatus met leesbare redenen.
- Beoordeling is deterministisch en fail-closed. Tegenstrijdige bronidentiteit wordt geweigerd; publieke vrijgave vereist alle bevestigde statussen en veilige brongegevens.
- Gerichte tests: 14 backendtests en 3 admin-widgettests. Volledig vangnet groen: backend 354 tests, frontend 79 tests, adminfrontend 38 tests, plus analyses en webbuild.
- Er is geen nieuwe publieke publicatieroute of opslag voor zoekresultaten/raw payloads toegevoegd. Preview-validatie was niet mogelijk omdat geen preview-URL is geconfigureerd.
- Het rolbestand noemt `summary-finished`, maar het opdrachtcontract vereist `summarized`; daarom volgt hieronder het opdrachtcontract.

<!-- deploy-summary:start -->
Beheerders zien nu per historisch resultaat of de bron, rechten en privacy voldoende duidelijk zijn. Alleen veilige, volledig bevestigde resultaten kunnen als vrijgegeven worden getoond; twijfel blokkeert dat.
<!-- deploy-summary:end -->
