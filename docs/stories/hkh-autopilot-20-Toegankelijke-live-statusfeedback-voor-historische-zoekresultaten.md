# hkh-autopilot-20 - Toegankelijke live-statusfeedback voor historische zoekresultaten

## Story

Toegankelijke live-statusfeedback voor historische zoekresultaten

<!-- refined-by-factory -->

## Scope

Verbeter de bestaande publieke historische zoekroute met één dynamische, semantisch herkenbare live-regio voor:

- het laden van historische zoekresultaten;
- beschikbare resultaten;
- nul resultaten;
- gedeeltelijke bronbeschikbaarheid;
- volledige bronuitval;
- opnieuw proberen en het aanpassen van de zoekopdracht.

Bij gedeeltelijke beschikbaarheid blijft de bestaande resultatenlijst zichtbaar. De status benoemt de beschikbare bron, de uitgevallen bron, de veilige foutreden en het aantal beschikbare resultaten op de huidige zichtbare resultatenpagina.

Bij volledige bronuitval toont de route geen misleidende nulresultaat- of resultatentelling. De interface meldt dat geen bronnen konden worden geraadpleegd, benoemt per bron de veilige foutreden en biedt de acties `Opnieuw proberen` en `Zoekopdracht aanpassen`.

De bestaande bronstatussen, tellingen, paginering, rechtenregels, privacyregels en fail-closed resultaatcontracten blijven leidend. Er worden geen nieuwe bronnen, routes, opslag, zoekgeschiedenis of volledige bronpayloads toegevoegd.

Heemskerk-tellingen worden zichtbaar aangeduid als metadata-indicatie op basis van plaatsmetadata en expliciet als geen bewijs van historische waarheid.

## Acceptance criteria

- De historische zoekroute gebruikt precies één semantisch herkenbare live/status-regio voor de relevante statusovergangen; visuele tekst en laadindicatoren creëren geen extra statusnode.
- De live-regio kondigt laadstatus, nul resultaten, volledige beschikbaarheid, gedeeltelijke beschikbaarheid en volledige bronuitval programmatisch aan met zowel een begrijpelijke zichtbare tekst als een toegankelijkheidslabel.
- Bij gedeeltelijke bronuitval bevat de live-status minimaal de naam van een beschikbare bron, de naam van iedere uitgevallen bron, de veilige foutreden per uitgevallen bron en de resultatentelling van de beschikbare bron op de huidige zichtbare pagina. Beschikbare resultaten blijven zichtbaar.
- Bij volledige bronuitval meldt de interface tekstueel dat geen bronnen konden worden geraadpleegd, toont zij per bron de veilige foutreden en biedt zij de toetsenbordbedienbare acties `Opnieuw proberen` en `Zoekopdracht aanpassen`.
- `Opnieuw proberen` start opnieuw de passende laadstatus en daarna de nieuwe uitkomst. `Zoekopdracht aanpassen` houdt de gebruiker op dezelfde route, maakt de bestaande zoekvelden bereikbaar en laat de bestaande zoekwaarden ongewijzigd totdat de gebruiker ze aanpast.
- Statusupdates verplaatsen de toetsenbordfocus niet onverwacht. Alle zoek-, retry- en aanpasacties zijn via Tab, Enter en spatie bedienbaar en in de toegankelijkheidsboom herkenbaar.
- Elke getoonde Heemskerk-indicatie bevat expliciet dat het om een metadata-indicatie gaat en niet om bewijs van historische waarheid.
- Geautomatiseerde widget-, semantiek- en focus-tests dekken nul falende bronnen, één falende bron en alle falende bronnen, inclusief statusnaam, bronnaam, foutreden, resultatentelling, zichtbaarheid van beschikbare resultaten en vervolgstappen.
- Geautomatiseerde backend- en frontendtests bevestigen dat zoektermen, ruwe bronpayloads en niet-noodzakelijke persoonsgegevens niet door de statusinformatie of door nieuw toegevoegde responsevelden worden teruggegeven. Bestaande expliciet toegestane metadata blijft onder de huidige rechten- en privacyregels vallen.
- De bestaande functionaliteit voor zoeken, bronkeuze, paginering, externe bronlinks en context bekijken blijft werken.

## Aannames

- Resultatentellingen betreffen de huidige zichtbare resultatenpagina, overeenkomstig het bestaande contract.
- Alleen veilige, vooraf bepaalde foutteksten worden aan gebruikers getoond; providerpayloads en technische details worden niet rechtstreeks weergegeven.
- Een bewuste actie op `Zoekopdracht aanpassen` mag de focus doelgericht naar het zoekformulier brengen; automatische statusupdates mogen de focus niet wijzigen.
- De bestaande statuswaarden `RESULTS`, `NO_RESULTS`, `PARTIAL_AVAILABILITY` en `SOURCE_FAILURE` en bronstatussen blijven ongewijzigd.
- De bestaande Flutter-widget-, semantiek- en backend-contracttestconventies zijn voldoende; er is geen Playwright- of axe-core-teststraat nodig.

## Eindsamenvatting

Eindsamenvatting voor de PO:

- De historische zoekroute gebruikt één toegankelijke statusregio voor laden, resultaten, nulresultaten, gedeeltelijke beschikbaarheid en volledige bronuitval.
- Bij gedeeltelijke uitval blijven beschikbare resultaten zichtbaar met bronnamen, veilige foutmeldingen en aantallen.
- Bij volledige uitval worden geen misleidende resultaatcijfers getoond; de gebruiker krijgt per bron een veilige melding plus `Opnieuw proberen` en `Zoekopdracht aanpassen`.
- Aanpassen brengt focus naar het bestaande zoekveld en behoudt ingevulde waarden. De bestaande bronkeuze, paginering, rechten-, privacy- en Heemskerk-metadataregels blijven ongewijzigd.
- Getest: gerichte Flutter-test 16/16; volledig vangnet groen: backend 303 tests, frontend 64 tests en webbuild, frontend-admin 35 tests. Ook `git diff --check` is groen.
- Bewust niet gedaan: nieuwe bronnen, backendroutes, opslag, zoekgeschiedenis of ruwe bronpayloads. Documentatie-update volgt in subtaak `hkh-126`; er is geen previewomgeving geconfigureerd.
- De rolinstructie noemt `summary-finished`, maar het opdrachtcontract vereist `summarized`; daarom volgt hieronder de contractvorm.

<!-- deploy-summary:start -->
Historisch zoeken laat nu duidelijk weten wat er met je zoekopdracht gebeurt. Als een bron uitvalt, blijven beschikbare resultaten zichtbaar of krijg je duidelijke keuzes om opnieuw te proberen of je zoekopdracht aan te passen, zonder ingevulde waarden kwijt te raken. De meldingen en acties zijn ook goed bruikbaar met toetsenbord en schermlezer.
<!-- deploy-summary:end -->
