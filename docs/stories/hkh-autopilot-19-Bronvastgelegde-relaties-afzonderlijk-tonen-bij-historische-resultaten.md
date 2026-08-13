# hkh-autopilot-19 - Bronvastgelegde relaties afzonderlijk tonen bij historische resultaten

## Story

Bronvastgelegde relaties afzonderlijk tonen bij historische resultaten

<!-- refined-by-factory -->

## Scope

Breid historische zoekresultaten uit met `relationships[]` voor relaties die expliciet door de externe bron zijn geleverd.

Een relatie bevat:

- `type`: relatietype;
- `source.name`: naam van de externe bron;
- `target.name`: naam van het doelrecord;
- `target.uri`: door de bron geleverde stabiele URI van het doelrecord;
- `target.link`: externe link naar het oorspronkelijke doelrecord.

Relaties worden uitsluitend uit expliciete brondata overgenomen. Ze worden niet afgeleid uit gedeelde plaats-, persoons- of gebeurtenismetadata, periode-overlap, zoekfilters, titels, beschrijvingen of aannames.

Toon geldige bronrelaties in een afzonderlijke sectie `Bronvastgelegde relatie` op de bestaande historische resultaatdetailpagina. Houd de bestaande sectie voor metadata-overlap (`Verwante resultaten`) en de vervolgzoekacties afzonderlijk.

## Acceptance criteria

- Een resultaat bevat alleen `relationships[]` wanneer de externe bron expliciete relaties met doelrecords levert.
- Een getoonde relatie bevat het relatietype, de naam van de externe bron, de naam van het doelrecord, een stabiele `target.uri` en een geldige `target.link`.
- Een relatie zonder stabiele doel-URI, zonder voldoende bron- of doelidentificatie, of zonder herleidbare externe link wordt volledig weggelaten.
- De detailweergave toont de sectie `Bronvastgelegde relatie` alleen wanneer minstens één geldige bronrelatie aanwezig is.
- De interface labelt iedere relatie duidelijk als een bronclaim en vermeldt dat de relatie niet door HKH is afgeleid.
- De externe link verwijst naar `target.link`, opent het oorspronkelijke doelrecord en wordt tekstueel aangekondigd als externe link.
- De bestaande bronvermelding en bronlink van het geopende resultaat blijven naar het oorspronkelijke bronrecord wijzen.
- Metadata-overlap en vervolgzoekingen blijven zichtbaar en functioneren onafhankelijk van bronrelaties.
- Geautomatiseerde backendtests tonen een expliciete bronrelatie en verwerpen relaties die afgeleid, onvolledig of niet stabiel herleidbaar zijn.
- Geautomatiseerde frontendtests controleren de afzonderlijke sectie, de bronclaimtekst, de externe-linkaankondiging en het verborgen blijven van ongeldige relaties.
- De functionaliteit slaat geen media, ruwe bronpayloads, persoonsgegevens, zoekgeschiedenis of klikgeschiedenis op.

## Aannames

- `relationships[]` wordt toegevoegd aan het bestaande genormaliseerde historische resultaatcontract.
- De bronadapters mappen alleen providerdata die expliciet als relatie en doelrecord herkenbaar is; ontbrekende of niet-herkenbare bronvelden leveren geen relatie op.
- De volgorde van geldige relaties volgt de volgorde waarin de bron ze aanlevert.
- Een stabiele URI is expliciet door de bron geleverd, syntactisch een geldige HTTP(S)-URI en wordt niet lokaal geconstrueerd.
- `target.link` is de door de bron geleverde link naar het doelrecord; de bestaande `stableUrl` blijft de link naar het oorspronkelijke zoekresultaat.

## Eindsamenvatting

PO-samenvatting: expliciete bronrelaties zijn toegevoegd aan backendcontract, REST-respons en Flutter-detailweergave. Alleen complete, veilige relaties met bron-URI en externe link worden in bronvolgorde getoond; afgeleide of onvolledige relaties worden weggefilterd. Metadata-overlap en vervolgzoekingen blijven onafhankelijk. De volledige verificatie en gerichte hertests zijn groen. Er was geen preview beschikbaar; opslag van ruwe brondata of relaties is niet toegevoegd. Het opdrachtcontract gebruikt `summarized` als fasewaarde; dat volgt deze samenvatting.

<!-- deploy-summary:start -->
Historische zoekresultaten tonen nu afzonderlijk de relaties die een externe bron zelf vermeldt. Je kunt het genoemde doelrecord rechtstreeks openen. Onvolledige of onveilige relaties worden niet getoond.
<!-- deploy-summary:end -->
