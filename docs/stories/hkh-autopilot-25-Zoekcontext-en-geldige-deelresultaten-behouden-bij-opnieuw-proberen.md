# hkh-autopilot-25 - Zoekcontext en geldige deelresultaten behouden bij opnieuw proberen

## Story

Zoekcontext en geldige deelresultaten behouden bij opnieuw proberen

<!-- refined-by-factory -->

## Scope

Verbeter de retry-flow van de publieke historische zoekopdracht.

Een retry bewaart een momentopname van de laatst voltooide zoekpoging, inclusief alle genormaliseerde zoekparameters, de huidige pagina en de geldige genormaliseerde resultaten en bronstatussen. De bestaande zoekvelden blijven ingevuld.

De retry is beschikbaar bij volledige bronuitval en bij gedeeltelijke beschikbaarheid met minstens één uitgevallen bron. Dezelfde retry-flow geldt voor een tijdelijke transportfout of een geldige `SOURCE_FAILURE`-respons.

Tijdens een lopende retry blijven de resultaten, bronstatussen en zoekcontext van de vorige voltooide poging zichtbaar. De interface toont daarnaast duidelijk dat een nieuwe poging voor dezelfde zoekopdracht wordt uitgevoerd. De vorige uitkomst en de lopende poging krijgen tekstueel herkenbare aanduidingen.

Bij een succesvolle retry wordt de vorige uitkomst volledig vervangen door de nieuwe geldige respons. Resultaten, tellingen en bronstatussen worden niet met de vorige poging samengevoegd. Een resultaat dat niet in de nieuwe respons voorkomt, blijft dus niet als actueel resultaat zichtbaar.

Bij een mislukte retry blijven de laatst geldige deelresultaten en hun vorige bronstatussen zichtbaar. De nieuwe bronfout wordt afzonderlijk, veilig en begrijpelijk getoond, inclusief de bronnaam en de bestaande vaste foutmelding wanneer die beschikbaar is. Ruwe providerresponsen en exceptionteksten worden nooit aan de bezoeker getoond.

De bestaande toegankelijkheidsafspraken blijven gelden: één status/live-regio, een zichtbare laadstatus tijdens retry, toetsenbordbediening van de retryactie en geen automatische focusverplaatsing.

## Acceptance criteria

- Een retry verstuurt exact dezelfde genormaliseerde zoekparameters als de vorige zoekpoging: vrije tekst, plek, persoon, gebeurtenis, periode, bronkeuze, pagina-offset en paginalimiet.
- De bezoeker hoeft geen zoekveld opnieuw in te vullen; alle ingevoerde zoekwaarden blijven behouden tijdens en na de retry.
- De retryactie is zichtbaar bij zowel volledige bronuitval als gedeeltelijke beschikbaarheid met een uitgevallen bron.
- Tijdens een lopende retry blijven de laatst geldige resultaten, bronstatussen en relevante tellingen zichtbaar, met daarnaast een tekstuele aanduiding van de lopende nieuwe poging en de vorige uitkomst.
- Een succesvolle retry vervangt de vorige resultaten, tellingen en bronstatussen volledig. Er vindt geen samenvoeging met oude resultaten plaats.
- Bij een mislukte retry blijven eerder geldige deelresultaten zichtbaar en wordt de nieuwe bronfout als nieuwe poging afzonderlijk en begrijpelijk gemeld.
- Als er vóór de mislukte retry geen geldige resultaten waren, wordt de bestaande volledige-bronuitvalweergave gebruikt, inclusief de retry- en aanpasacties.
- De retry gebruikt één nieuwe aanvraag en voegt geen zoekgeschiedenis, eerdere responsen of ruwe bronpayload toe aan de aanvraag.
- Client-state bevat uitsluitend de zoekcontext en genormaliseerde gegevens die nodig zijn voor de huidige weergave en de tijdelijke vorige-uitkomstweergave; er wordt geen volledige zoekgeschiedenis of ruwe bronpayload bewaard.
- Operationele logging blijft beperkt tot de bestaande privacyveilige allowlist. Zoekwaarden, queryparameters, URLs met queryparameters, bronpayloads, identifiers, exceptiondetails en stacktraces worden niet gelogd of persistent opgeslagen.
- Geautomatiseerde frontendtests dekken minstens:
  - een gedeeltelijke eerste respons gevolgd door een gemockte tijdelijke bronfout bij retry, waarbij zoekterm en geldige deelresultaten zichtbaar blijven;
  - een succesvolle retry waarbij oude resultaten verdwijnen en nieuwe resultaten en bronstatussen volledig worden overgenomen;
  - gelijkheid van alle retry-parameters met de oorspronkelijke aanvraag;
  - het ontbreken van ruwe bronpayloads en zoekgeschiedenis in client-state en logging.
- De bestaande testconventies voor veilige bronmeldingen, één status/live-regio, toetsenbordbediening en focusbehoud blijven groen.

## Aannames

- De huidige pagina-offset en paginalimiet maken deel uit van de oorspronkelijke zoekparameters; een retry begint dus op dezelfde zichtbare pagina.
- Een geldige respons met `RESULTS`, `NO_RESULTS` of `PARTIAL_AVAILABILITY` geldt als succesvolle retry. Transportfouten en `SOURCE_FAILURE` gelden als mislukte retry.
- Eén vorige, genormaliseerde uitkomst mag tijdelijk in client-state worden bewaard om de lopende poging te tonen; oudere zoekpogingen worden niet bijgehouden.
- De bestaande backendroute, bronstatussen en vaste veilige foutmeldingen worden hergebruikt. Er is geen nieuwe API-route, database-opslag of zoekgeschiedenis nodig.

## Eindsamenvatting

Eindsamenvatting voor de PO:

- De historische zoekpagina bewaart één tijdelijke momentopname van de laatst gestarte zoekopdracht, inclusief alle genormaliseerde zoekparameters en maximaal één vorige uitkomst.
- Tijdens een retry blijven eerdere resultaten, bronstatussen, tellingen en zoekvelden zichtbaar. Dubbel starten is geblokkeerd.
- Een succesvolle retry vervangt de oude uitkomst volledig; een mislukte retry behoudt geldige deelresultaten en toont een aparte veilige foutmelding.
- Ruwe providerdata, exceptionteksten en zoekgeschiedenis worden niet bewaard of getoond.
- Getest met gerichte frontendtests (24 geslaagd) en het volledige factory-vangnet: backend, frontendanalyse/tests/webbuild en adminanalyse/tests zijn groen. `git diff --check` is schoon.
- Er zijn geen nieuwe backendroutes, databaseopslag of persistente zoekgeschiedenis toegevoegd. Preview is niet uitgevoerd omdat er geen preview-URL beschikbaar was; documentatie en deployment vallen onder latere subtaken.

<!-- deploy-summary:start -->
Bij opnieuw proberen blijven je zoekopdracht en bruikbare resultaten zichtbaar. Een geslaagde nieuwe poging toont alleen de nieuwste resultaten en een foutmelding blijft duidelijk en veilig.
<!-- deploy-summary:end -->
