# hkh-autopilot-17 - Transparante bron- en Heemskerk-dekkingssamenvatting bij historische zoekresultaten

## Story

Transparante bron- en Heemskerk-dekkingssamenvatting bij historische zoekresultaten

<!-- refined-by-factory -->

## Scope

Breid de bestaande publieke historische zoekroute uit met een compacte dekkingssamenvatting voor de geselecteerde bronnen Europeana en Open Archieven.

De samenvatting toont:

- per bevraagde bron de bestaande status `AVAILABLE`, `DISABLED`, `TEMPORARILY_UNAVAILABLE` of `INVALID_RESPONSE`;
- per bron het aantal veilig genormaliseerde resultaten dat op de huidige resultatenpagina beschikbaar is;
- de bestaande geaggregeerde zoektoestand `RESULTS`, `NO_RESULTS`, `PARTIAL_AVAILABILITY` of `SOURCE_FAILURE`;
- het aantal resultaten met een zekere, expliciete plaatswaarde die na deterministische normalisatie exact overeenkomt met `Heemskerk`.

De telling voor Heemskerk is een indicatie op basis van plaatsmetadata en wordt niet gepresenteerd als historisch bewijs. De oorspronkelijke plaatswaarde en de bestaande titel-, periode-, bron-, identifier-, stabiele URI-, ophaal-, rechten- en privacymetadata blijven per resultaat beschikbaar volgens de bestaande fail-closed-regels.

Er worden geen nieuwe bronnen, externe zoekroutes, lokale opslag, scans, foto’s of volledige externe bronpayloads toegevoegd. Bronlinks blijven uitsluitend verwijzen naar de door de bron aangeleverde stabiele URI.

## Acceptance criteria

- De publieke historische zoekroute toont voor elke daadwerkelijk geselecteerde bron een compacte status- en resultatensamenvatting.
- Een beschikbaar resultaat telt voor precies één bron mee op basis van de bron die het resultaat heeft geleverd.
- Een bron met nul beschikbare resultaten wordt onderscheiden van een bron die niet geconfigureerd, tijdelijk niet beschikbaar of ongeldig is.
- Bij gedeeltelijke beschikbaarheid blijven resultaten van beschikbare bronnen zichtbaar en wordt per niet-beschikbare bron een begrijpelijke tekstuele waarschuwing getoond.
- Bij volledige bronuitval wordt geen misleidende nulresultaat- of brontelling getoond; de bestaande bronprobleemstatus en retryactie blijven zichtbaar.
- De zoekopdracht wordt bij gedeeltelijke bronuitval niet als algemene zoekfout gepresenteerd.
- Een resultaat telt mee als lokale Heemskerk-indicatie uitsluitend wanneer `placeStatus` `AVAILABLE` is en de expliciete plaatswaarde na trimmen, Unicode-NFKC-normalisatie, samenvoegen van witruimte en hoofdletterongevoelige vergelijking exact gelijk is aan `Heemskerk`.
- Ontbrekende, onzekere, tegenstrijdige of door rechten- of privacyregels afgeschermde plaatsmetadata telt niet mee.
- De lokale telling wordt zichtbaar gelabeld als indicatie op basis van plaatsmetadata en niet als historisch bewijs.
- De oorspronkelijke plaatswaarde blijft bij elk resultaat zichtbaar wanneer die volgens de bestaande metadata- en privacyregels beschikbaar is; de samenvatting vervangt deze waarde niet.
- Bestaande bronmetadata, stabiele bron-URI, ophaaldatum, rechtenstatus en privacystatus blijven per resultaat behouden.
- Externe links blijven verwijzen naar de oorspronkelijke door de bron geleverde URI.
- Scans, foto’s, zoekopdrachten en volledige externe bronpayloads worden niet opgeslagen of weergegeven.
- Backendcontracttests en frontendtests dekken minimaal volledige beschikbaarheid, nul resultaten, gedeeltelijke beschikbaarheid, bronfout en volledige bronuitval.
- Tests dekken daarnaast lokale tellingen voor een zekere match, hoofdletter- en witruimtevarianten, ontbrekende metadata, onzekere metadata en andere plaatsen.

## Aannames

- De bron- en lokale tellingen hebben betrekking op de huidige zichtbare resultatenpagina; de bestaande geaggregeerde `total` blijft ongewijzigd.
- Niet-geselecteerde bronnen verschijnen niet in de samenvatting.
- Bij een falende bron wordt geen numerieke nul gebruikt als dat de afwezigheid van resultaten niet betrouwbaar bewijst.
- De bestaande bronstatussen, zoektoestanden, rechtenregels, privacyregels, paginering en externe-linkwerking blijven leidend.
- Er worden geen extra providerpagina’s opgehaald uitsluitend om de lokale telling over de volledige zoekresultatenset te berekenen.

## Eindsamenvatting

PO-samenvatting:

- Per geselecteerde bron zijn status, zichtbare resultatentelling en Heemskerk-indicatie toegevoegd.
- Alleen zekere plaatsmetadata telt mee; normalisatie gebeurt hoofdletterongevoelig met Unicode- en witruimte-normalisatie.
- Bij bronuitval blijven tellingen leeg; gedeeltelijke resultaten en waarschuwingen blijven zichtbaar.
- Bestaande metadata, bron-URI’s, privacy- en rechtenregels zijn behouden. Er zijn geen nieuwe bronnen, opslag of volledige bronpayloads toegevoegd.
- Getest: gerichte backendtests 23/23 en frontendtests 15/15 groen. Volgens het worklog was ook het volledige vangnet groen: 301 backendtests, 53 frontendtests, frontend analyze/build en admin analyze/35 tests.
- De instructie noemt `summary-finished`, maar het opdrachtcontract vereist `summarized`; het opdrachtcontract is gevolgd.

<!-- deploy-summary:start -->
Historische zoekresultaten tonen nu per bron hoeveel resultaten beschikbaar zijn en hoeveel daarvan een Heemskerk-verwijzing bevatten. Deze plaatsverwijzing wordt duidelijk als indicatie weergegeven en niet als historisch bewijs.
<!-- deploy-summary:end -->
