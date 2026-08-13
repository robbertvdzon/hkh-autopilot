# hkh-autopilot-15 - Bronveerkrachtige historische zoekuitkomsten bij gedeeltelijke bronbeschikbaarheid

## Story

Bronveerkrachtige historische zoekuitkomsten bij gedeeltelijke bronbeschikbaarheid

<!-- refined-by-factory -->

## Scope

Verbeter de bestaande publieke historische zoekroute met Europeana en Open Archieven. De bestaande zoekfilters, bronkeuze, paginering, bronlinks en opslagloze werking blijven behouden.

De backend:

- houdt per geselecteerde bron de technische status bij: `AVAILABLE`, `DISABLED`, `TEMPORARILY_UNAVAILABLE` of `INVALID_RESPONSE`;
- levert een expliciete geaggregeerde zoektoestand voor resultaten, nul resultaten, gedeeltelijke beschikbaarheid en volledige bronuitval;
- merge’t alleen resultaten van bronnen die beschikbaar zijn;
- telt in `total` uitsluitend resultaten van beschikbare bronnen mee;
- behandelt een niet-geconfigureerde, tijdelijk onbeschikbare of ongeldige bron nooit als een succesvolle bron;
- gebruikt veilige, korte bronmeldingen zonder ruwe providerresponsen, zoektermen, secrets of persoonsgegevens;
- behoudt per resultaat de bestaande `sourceRecordId`, door de bron geleverde `stableUrl`, `retrievedAt` en afzonderlijke rechten-, object/media- en privacystatussen.

De frontend toont de geaggregeerde toestand en de afzonderlijke bronmeldingen. Bij gedeeltelijke beschikbaarheid blijven beschikbare resultaten zichtbaar. Bij volledige bronuitval toont de frontend geen resultaatcount als zoekresultaat, maar een expliciete bronprobleemtoestand met de actie `Opnieuw proberen`. Rechten- en privacygegevens blijven fail-closed weergegeven.

## Acceptance criteria

- Wanneer alle geselecteerde bronnen `AVAILABLE` zijn en resultaten leveren, toont de API en frontend de beschikbare resultaten met een `total` dat uitsluitend op die resultaten is gebaseerd.
- Wanneer alle geselecteerde bronnen `AVAILABLE` zijn maar geen resultaten leveren, retourneert en toont de route een expliciete nulresultaattoestand, onderscheiden van bronuitval.
- Wanneer minstens één bron beschikbaar is en minstens één andere bron `DISABLED`, `TEMPORARILY_UNAVAILABLE` of `INVALID_RESPONSE` is, blijven de beschikbare resultaten zichtbaar.
- Bij gedeeltelijke beschikbaarheid toont de frontend per falende bron een korte, begrijpelijke tekstuele status:
  - niet geconfigureerd;
  - tijdelijk niet beschikbaar; of
  - ongeldige bronrespons.
- Wanneer alle geselecteerde bronnen falen of niet beschikbaar zijn, retourneert de API een expliciete bronprobleemtoestand en telt geen provider-totaal mee.
- Bij volledige bronuitval toont de frontend geen misleidende nulresultaat- of provider-count, maar wel een programmatisch uitleesbare bronprobleemmelding en een toetsenbordbedienbare actie `Opnieuw proberen`.
- Een bron die tijdens paginering uitvalt, wordt als falend gemarkeerd en levert geen provider-totaalbijdrage meer; reeds veilig genormaliseerde resultaten mogen zichtbaar blijven wanneer andere resultaten beschikbaar zijn.
- De API bewaart de afzonderlijke status van elke geselecteerde bron in het bestaande responscontract of een compatibele uitbreiding daarvan.
- Elk getoond resultaat behoudt de bronidentifier, de door de bron geleverde stabiele HTTP(S)-URL en de server-side ophaaldatum.
- Scans, foto’s, ruwe bronpayloads, zoektermen en gevoelige persoonsgegevens worden niet lokaal opgeslagen, gelogd of naar de frontend doorgegeven wanneer rechten of privacy niet expliciet veilig zijn.
- Metadata wordt alleen getoond wanneer metadatarechten expliciet `ALLOWED` zijn en privacy expliciet `CLEAR` is. Onbekende of beperkte rechten en onbekende of geblokkeerde privacy blijven fail-closed.
- Laden, resultaatcount, nul resultaten, gedeeltelijke beschikbaarheid, bronfouten en opnieuw proberen worden via één programmatisch uitleesbare status/live-regio gecommuniceerd; statusinformatie wordt niet uitsluitend met kleur of pictogrammen weergegeven.
- Backendcontracttests dekken minimaal:
  - alle bronnen beschikbaar met resultaten;
  - één bron tijdelijk onbeschikbaar met gedeeltelijke resultaten;
  - nul resultaten zonder bronfout;
  - volledige bronuitval;
  - niet-geconfigureerde bron;
  - ongeldige bronrespons;
  - correcte totalen en bronstatussen.
- Frontendwidget- en toegankelijkheidstests dekken minimaal dezelfde toestanden, inclusief zichtbaarheid van gedeeltelijke resultaten, het onderscheid tussen nul resultaten en bronuitval, de retryactie, de status/live-regio en het behoud van bronmetadata.

## Aannames

- Er worden geen nieuwe historische providers toegevoegd; Europeana en Open Archieven blijven de geselecteerde bronnen.
- De bestaande queryparameters, bronkeuze, paginering en door de bronnen geleverde stabiele URLs blijven functioneel ongewijzigd.
- Een bron die in de uiteindelijke zoekuitkomst niet `AVAILABLE` is, draagt nul bij aan `total`.
- De bestaande rechten- en privacysemantiek blijft leidend en wordt niet versoepeld.
- `DISABLED` betekent dat de bron niet geconfigureerd of functioneel uitgeschakeld is; configuratiedetails en providerfouten worden niet aan bezoekers getoond.
- `Opnieuw proberen` herhaalt dezelfde zoekopdracht met dezelfde filters en bronkeuze.
- Er wordt geen nieuwe lokale opslag, cache of beheerstroom geïntroduceerd.

## Eindsamenvatting

<!-- deploy-summary:start -->
Historisch zoeken blijft bruikbaar als één bron tijdelijk niet meewerkt. Je ziet duidelijke meldingen, beschikbare resultaten en kunt eenvoudig opnieuw proberen.
<!-- deploy-summary:end -->
