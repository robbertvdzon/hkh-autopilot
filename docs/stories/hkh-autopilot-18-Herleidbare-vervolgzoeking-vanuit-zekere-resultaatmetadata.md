# hkh-autopilot-18 - Herleidbare vervolgzoeking vanuit zekere resultaatmetadata

## Story

Herleidbare vervolgzoeking vanuit zekere resultaatmetadata

<!-- refined-by-factory -->

## Scope

Breid de bestaande historische resultaatdetailweergave uit met vervolgzoekacties voor expliciete, zekere metadatawaarden:

- plaats;
- persoon;
- gebeurtenis;
- periode.

Een vervolgactie opent de bestaande historische zoekroute met de gekozen bronwaarde als nieuw zoekcriterium. De actie maakt duidelijk dat dit een nieuwe zoekingang is en geen bewezen relatie tussen bronnen.

De bestaande bronmetadata blijft behouden: bronidentifier, stabiele bron-URI, ophaaldatum, rechtenstatus, privacystatus en externe bronlink. De uitbreiding gebruikt uitsluitend de bestaande Europeana- en Open Archieven-connectors en introduceert geen opslag van zoekopdrachten, bronpayloads, media of klikgeschiedenis.

## Acceptance criteria

- De resultaatdetailweergave biedt alleen vervolgacties voor metadata die expliciet aanwezig, niet-leeg en zeker beschikbaar is.
- Vervolgacties zijn beschikbaar voor plaats, persoon, gebeurtenis en een expliciet weergegeven periode.
- Waarden die ontbreken, leeg zijn, onzeker, tegenstrijdig, privacygevoelig, rechtenbeperkt of uit titel, zoekterm of URL afgeleid zijn, leveren geen vervolgactie op.
- Een plaatsactie gebruikt exact de oorspronkelijke plaatswaarde als zoekwaarde.
- Een persoonsactie gebruikt exact de oorspronkelijke persoonswaarde als zoekwaarde.
- Een gebeurtenisactie gebruikt exact de oorspronkelijke gebeurteniswaarde als zoekwaarde.
- Een periodeactie gebruikt de expliciete bronwaarden voor begin- en eindjaar als vervolgzoekperiode; alleen een periode die door het bestaande zoekcontract als geldige jaarperiode kan worden doorgegeven krijgt een actie.
- Elke vervolgactie start een nieuwe zoekopdracht via de bestaande historische zoekroute en gebruikt geen nieuwe connector of opslagmechanisme.
- De vervolgzoeking gebruikt de standaard bestaande bronselectie waarbij Europeana en Open Archieven worden bevraagd.
- De nieuwe zoekweergave toont duidelijk de gebruikte vervolgwaarde en de tekst: “Dit is een nieuwe zoekingang en bewijst geen relatie tussen bronnen.”
- De oorspronkelijke bronwaarde blijft in de detailweergave ongewijzigd zichtbaar naast de vervolgactie.
- Terugnavigatie vanuit de vervolgzoeking brengt de bezoeker terug naar het oorspronkelijke resultaatdetail; terugnavigatie vanuit daar blijft naar de oorspronkelijke resultatenlijst werken.
- De bestaande bronidentifier, stabiele bron-URI, ophaaldatum, rechtenstatus, privacystatus en externe bronlink blijven beschikbaar.
- Iedere vervolgactie is een semantische button of link met een duidelijk toegankelijk label waarin het metadataonderwerp herkenbaar is.
- Focus op vervolgacties is zichtbaar en de waarschuwing over het ontbreken van een bewezen relatie is programmatisch beschikbaar.
- Tests controleren exacte querywaarden voor alle ondersteunde metadataonderwerpen, het ontbreken van acties voor onzekere of afgeleide waarden, de waarschuwingstekst en beide terugnavigatiestappen.
- Tests controleren dat geen zoekopdracht, bronpayload of klikgeschiedenis lokaal wordt opgeslagen.

## Aannames

- De bestaande fail-closed-regels blijven leidend: inhoudelijke metadata is alleen vervolgzoekbaar wanneer metadatarechten `ALLOWED` en de privacystatus `CLEAR` zijn.
- De bestaande contextstatus `AVAILABLE` bepaalt of plaats, persoon en gebeurtenis als zeker gelden.
- Een periode bestaat voor deze story uit expliciete begin- en eindjaarwaarden die rechtstreeks aan het bestaande `fromYear`- en `toYear`-contract kunnen worden doorgegeven; bij ontbrekende of niet-valide jaarwaarden is er geen periodeactie.
- De vervolgzoeking laat de bronfilter weg en gebruikt daarmee de bestaande standaard waarbij beide historische bronnen worden bevraagd.
- De bestaande zoek-, status-, rechten-, privacy-, paginerings- en externe-linksemantiek blijft verder ongewijzigd.

## Eindsamenvatting

PO-samenvatting:

- Vanuit de resultaatdetailweergave zijn vervolgzoekacties toegevoegd voor plaats, persoon, gebeurtenis en geldige periodes.
- Acties verschijnen alleen bij expliciete, zekere metadata met toegestane rechten en duidelijke privacystatus.
- De bestaande zoekroute en bronkeuze blijven gebruikt; de bronfilter wordt bewust niet overgenomen.
- De waarschuwing dat dit geen bewezen relatie tussen bronnen is, wordt zichtbaar en toegankelijk aangeboden.
- Terugnavigatie naar detail en daarna naar de resultatenlijst blijft behouden.
- Er is geen opslag toegevoegd voor zoekopdrachten, brongegevens of klikgeschiedenis.
- Gerichte tests: 6 groen. Volledig vangnet: backend 301 tests, frontend 59 tests, admin 35 tests; analyse en webbuild zijn geslaagd.
- De algemene README, frontend-handleiding, reguliere en factory-developmentdocs en de functionele
  en technische factory-specs beschrijven nu de vervolgacties, fail-closed gating, queryhergebruik,
  waarschuwing en terugnavigatie. Merge en deploy vallen buiten deze documentatietaak.

<!-- deploy-summary:start -->
Je kunt nu vanuit een historisch zoekresultaat direct verder zoeken op de betrouwbare plaats, persoon, gebeurtenis of periode die daar staat. De nieuwe zoekingang maakt duidelijk dat dit geen bewijs is dat twee bronnen bij elkaar horen.
<!-- deploy-summary:end -->
