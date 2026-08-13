# hkh-autopilot-16 - Contextuele detailweergave voor historische zoekresultaten

## Story

Contextuele detailweergave voor historische zoekresultaten

<!-- refined-by-factory -->

## Scope

Breid de bestaande publieke historische zoekroute uit met een contextuele detailweergave per historisch zoekresultaat.

De zoekresultaten krijgen:

- een duidelijk benoemde actie `Context bekijken`;
- een genormaliseerd plaatsveld wanneer de oorspronkelijke bron dit expliciet levert;
- behoud van bestaande titel-, periode-, persoons-, gebeurtenis-, bron-, identifier-, URL-, ophaaldatum-, rechten- en privacymetadata;
- een expliciete beschikbaarheids- of onzekerheidsaanduiding voor ontbrekende, tegenstrijdige of onzekere contextmetadata.

De detailweergave toont, wanneer beschikbaar:

- titel;
- plaats;
- periode;
- persoon;
- gebeurtenis;
- bronlabel en bronhouder;
- stabiele bronidentifier;
- stabiele bron-URI;
- server-side ophaaldatum;
- technische bronstatus;
- metadatarechten, object-/mediarechten en privacystatus.

Ontbrekende contextmetadata wordt als `Niet beschikbaar` weergegeven. Onzekere of tegenstrijdige contextmetadata wordt als `Onzeker` weergegeven. Beide categorieën worden niet gebruikt om relaties te bepalen.

Verwante resultaten worden uitsluitend gezocht in de resultaten die op de huidige zichtbare zoekresultatenpagina beschikbaar zijn. Het geopende resultaat zelf wordt uitgesloten. Een relatie bestaat wanneer minstens één van deze contextvelden exact gelijk is na dezelfde deterministische normalisatie:

- plaats;
- persoon;
- gebeurtenisnaam.

Een overlappende periode mag aanvullend worden vermeld bij een al bestaande relatie, maar vormt op zichzelf geen relatie. Ontbrekende of onzekere waarden matchen nooit. Per relatie worden de gedeelde contextvelden, het bronlabel en de stabiele bronlink getoond. Er worden maximaal drie relaties getoond, in de volgorde waarin de geschikte resultaten op de huidige pagina staan.

De bestaande zoekstatussen `RESULTS`, `NO_RESULTS`, `PARTIAL_AVAILABILITY` en `SOURCE_FAILURE` blijven behouden. De detailweergave licht de relevante zoekstatus en afzonderlijke bronstatussen begrijpelijk toe. Bij gedeeltelijke bronbeschikbaarheid worden uitsluitend beschikbare resultaten en hun herleidbare metadata gebruikt; provider-totalen van ontbrekende of uitgevallen bronnen worden niet als volledig gepresenteerd.

Bronlinks blijven verwijzen naar de oorspronkelijke door de provider aangeleverde URL. Scans, foto’s, ruwe bronpayloads, zoekopdrachten en nieuwe lokale bronopslag vallen buiten scope.

## Acceptance criteria

- Elk beschikbaar historisch zoekresultaat bevat een duidelijk benoemde actie om de contextuele detailweergave te openen.
- Het zoekresultaatcontract en de frontend kunnen plaatsmetadata verwerken zonder bestaande bronstatussen, rechtenstatussen, privacystatussen, identifiers, stabiele URLs of ophaaldatums te verliezen.
- De detailweergave toont alle beschikbare contextvelden en toont ontbrekende of onzekere velden expliciet als `Niet beschikbaar` of `Onzeker`.
- De detailweergave toont bronlabel, bronhouder wanneer beschikbaar, stabiele bronidentifier, stabiele bron-URI, ophaaldatum, technische bronstatus, rechtenstatussen en privacystatus.
- Relaties worden uitsluitend bepaald uit de resultaten van de huidige zichtbare zoekresultatenpagina.
- Plaats, persoon en gebeurtenis worden deterministisch genormaliseerd en daarna uitsluitend op exacte gelijkheid vergeleken.
- Eén exact gedeeld plaats-, persoons- of gebeurteniskenmerk is voldoende voor een relatie.
- Een overlappende periode vormt nooit zelfstandig een relatie; ontbrekende, onzekere of tegenstrijdige metadata matcht nooit.
- De detailweergave toont maximaal drie relaties en vermeldt per relatie minimaal het gedeelde contextkenmerk, de bron en de stabiele bronlink.
- Er worden geen relaties afgeleid uit alleen vrije tekst, titelbeschrijving, zoekfilters, aannames over personen of aannames over gebeurtenissen.
- `RESULTS`, `NO_RESULTS`, `PARTIAL_AVAILABILITY` en `SOURCE_FAILURE` blijven zichtbaar en worden ook in de relevante detailcontext begrijpelijk toegelicht.
- Bij gedeeltelijke bronbeschikbaarheid blijven alleen beschikbare resultaten en hun herleidbare metadata bruikbaar voor de detailweergave en relaties.
- Geen enkele provider-totalenbijdrage van een ontbrekende of uitgevallen bron wordt als volledig zoekresultaat gepresenteerd.
- Externe links verwijzen uitsluitend naar de oorspronkelijke bron-URL; scans, foto’s en ruwe externe bronpayloads worden niet lokaal opgeslagen.
- Backendcontracttests dekken minimaal plaatsnormalisatie, ontbrekende en onzekere metadata, exacte relaties, periode-overlap zonder relatie, maximaal drie relaties, uitsluiting van het geopende resultaat en gedeeltelijke bronbeschikbaarheid.
- Frontendtests dekken de contextactie, detailweergave, expliciete onzekerheidsmeldingen, bronstatussen, maximaal drie relaties, externe-linklabels en toetsenbordbediening.

## Aannames

- De “huidige zichtbare zoekresultatenpagina” is exact de `results`-lijst van het huidige zoekantwoord; er worden geen extra providerpagina’s opgehaald om relaties te vinden.
- Plaats wordt alleen overgenomen uit expliciete bronmetadata en nooit afgeleid uit de zoekopdracht, titel, beschrijving of bron-URL.
- De normalisatie bestaat uit trimmen, Unicode-normalisatie, samenvoegen van opeenvolgende witruimte en hoofdletterongevoelige vergelijking; er wordt geen fuzzy matching toegepast.
- Bij meerdere gedeelde kenmerken worden alle aantoonbaar gedeelde kenmerken vermeld, maar één exact gedeeld kenmerk is al voldoende.
- De bestaande fail-closed-regels voor metadatarechten en privacy blijven leidend: inhoudelijke metadata wordt niet alsnog zichtbaar wanneer rechten of privacy niet expliciet veilig zijn.
- De bestaande paginering, bronkeuze, bronadapters en externe-linkwerking blijven verder ongewijzigd.

## Eindsamenvatting

Opgeleverd:

- Historische resultaten bevatten nu expliciete plaatsinformatie en onzekerheidsstatussen.
- Via `Context bekijken` is alle beschikbare context-, bron-, rechten- en privacymetadata zichtbaar.
- Verwante resultaten worden alleen bepaald op basis van exacte, genormaliseerde plaats-, persoons- of gebeurtenisgegevens uit de huidige resultatenpagina. Periode-overlap is slechts aanvullende informatie.
- Ontbrekende of onzekere gegevens worden duidelijk als `Niet beschikbaar` of `Onzeker` getoond.
- Externe bronlinks blijven verwijzen naar de oorspronkelijke bron; er worden geen scans, foto’s, zoekopdrachten of ruwe brongegevens lokaal opgeslagen.

Getest:

- Volledig vangnet groen: backend 300 tests, frontend 51 tests, frontend-admin 35 tests, analyses en webbuild zonder fouten.
- Gerichte contexttests: 22 backendtests en 15 Flutter-tests.
- Geen previewtest uitgevoerd omdat er geen preview-URL is geconfigureerd.

De rol-instructie noemt `summary-finished`, maar het opdrachtcontract vereist `summarized`; daarom gebruik ik hieronder de contractvorm.

<!-- deploy-summary:start -->
Historische zoekresultaten hebben nu een knop ‘Context bekijken’. Je ziet daar de beschikbare plaats, periode, persoon, gebeurtenis en broninformatie; ontbrekende of onzekere informatie wordt duidelijk aangegeven. Verwante resultaten worden alleen getoond wanneer ze echt een gedeeld kenmerk hebben.
<!-- deploy-summary:end -->
