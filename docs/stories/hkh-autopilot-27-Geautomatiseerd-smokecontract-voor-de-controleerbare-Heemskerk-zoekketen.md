# hkh-autopilot-27 - Geautomatiseerd smokecontract voor de controleerbare Heemskerk-zoekketen

## Story

Geautomatiseerd smokecontract voor de controleerbare Heemskerk-zoekketen

<!-- refined-by-factory -->

## Scope

Voeg een reproduceerbare smoke-contractset toe voor de publieke historische zoekketen. De controle gebruikt alleen synthetische fixtures en lokale, beheersbare netwerk-mocks en wijzigt geen gebruikersfunctionaliteit, databasegegevens of externe bronnen.

De controle dekt gezamenlijk:

- de publieke zoekroute met de zoekterm `Heemskerk`;
- verwerking van een geldige Open Archieven-respons tot het bestaande genormaliseerde API-contract;
- weergave van dat resultaat in de bestaande historische zoekpagina;
- het onderscheid tussen resultaten, nulresultaat, gedeeltelijke beschikbaarheid en volledige bronuitval;
- de onafhankelijke werking van Open Archieven wanneer Europeana niet is geconfigureerd;
- cache-, single-flight- en verzoekbudgetgedrag bij gelijktijdige identieke zoekacties.

De test volgt de bestaande repositoryconventie met backend-contract-/integratietests en Flutter-widget-/clienttests. Er wordt geen browserautomatisering of nieuwe productroute toegevoegd.

## Acceptance criteria

1. De smoke-contractset draait automatisch mee met de bestaande geautomatiseerde testpipeline en vereist geen eigenaaractie, account, token, echte externe bron of handmatige configuratie.

2. De succesvolle keten start vanuit de zoekactie met `Heemskerk`, gebruikt de publieke zoekroute en toont in de bestaande zoekpagina minstens één geldig Open Archieven-resultaat.

3. Voor ieder zichtbaar Open Archieven-resultaat controleert de test met resultaatindex in de assertion:
   - brongeleverde, toegestane metadata;
   - een niet-lege bronnaam uit `source_name`;
   - een niet-lege stabiele identifier uit `stable_identifier` met de bestaande `hee:uuid`-vorm;
   - de exacte brongeleverde permanente URL uit `original_source_url`;
   - een beschikbare bronstatus.
   
   De URL wordt niet uit de identifier of een lokaal URL-patroon afgeleid.

4. Bij een synthetische geldige nulrespons (`number_found: 0`, `docs: []`) controleert de test dat:
   - Open Archieven `AVAILABLE` blijft;
   - de geaggregeerde toestand `NO_RESULTS` is;
   - de bronresultatentelling expliciet `0` is;
   - de zoekpagina geen bronuitval of tijdelijke onbeschikbaarheid toont.

5. Bij gedeeltelijke bronuitval controleert de test dat geldige resultaten van beschikbare bronnen zichtbaar blijven, dat de toestand `PARTIAL_AVAILABILITY` is en dat de falende bron een afzonderlijke veilige foutstatus krijgt. De uitkomst mag niet als `NO_RESULTS` worden gepresenteerd.

6. Bij volledige bronuitval controleert de test dat de toestand `SOURCE_FAILURE` is, dat er geen resultaten of numerieke resultatentelling als nulresultaat worden gepresenteerd en dat de zoekpagina de bestaande bronfoutweergave gebruikt.

7. Met Europeana zonder configuratie en Open Archieven met een geldige fixture controleert de test dat Europeana `DISABLED` is, Open Archieven `AVAILABLE` blijft en het Open Archieven-resultaat zichtbaar wordt.

8. Bij gelijktijdige identieke zoekacties met dezelfde genormaliseerde zoekcontext controleert de test dat:
   - de gecontroleerde Open Archieven-mock precies één gelijktijdige upstream-aanvraag ontvangt;
   - alle route-uitkomsten dezelfde genormaliseerde resultaatpagina opleveren;
   - slechts één directe Open Archieven-poging het ingestelde verzoekbudget gebruikt;
   - geen aanvraag door dit scenario onterecht `RATE_LIMITED` wordt.

9. Ontbrekende status, bronmetadata, identifier of permanente bronlink veroorzaakt telkens een specifieke, veldgerichte assertion die de betreffende resultaatindex en het ontbrekende contractonderdeel benoemt.

10. Fixtures bevatten uitsluitend minimale synthetische waarden zonder echte persoonsgegevens. Testoutput, foutmeldingen en logging bevatten geen ruwe providerpayloads, tokens, zoekgeschiedenis, identifiers buiten de gecontroleerde assertioncontext of externe wijzigingen.

## Aannames

- De bestaande contracten in de factory-documentatie zijn leidend; deze story breidt de testdekking uit en verandert geen API-, status- of gebruikerscontract.
- “Volledige keten” betekent dat de publieke backendroute en de bestaande frontendweergave beide via geautomatiseerde contracttests worden afgedekt; een aparte browser-end-to-end-oplossing is niet nodig.
- De Europeana-situatie wordt gecontroleerd via de bestaande configuratie waarbij een ontbrekende sleutel Europeana uitschakelt.
- “Herhaalde identieke zoekactie” betekent gelijktijdige requests met dezelfde zoekterm, bronkeuze, paginering en testidentiteit; de test controleert zowel single-flight als het bestaande verzoekbudget.
- De bestaande pipeline-commando’s blijven ongewijzigd en nemen de nieuwe test automatisch mee.

## Eindsamenvatting

Eindsamenvatting voor de PO:

- Een backend- en Flutter-smokecontractset toegevoegd met synthetische fixtures en lokale netwerk-mocks.
- Gedekt zijn geldige Heemskerk-resultaten, nulresultaten, gedeeltelijke/volledige bronuitval, uitgeschakelde Europeana, bronmetadata en gelijktijdige identieke zoekacties.
- Reviewpunten zijn opgelost; ontbrekende velden geven nu resultaatindex en veldnaam aan.
- Gerichte tests opnieuw groen: backend 6 tests, Flutter 3 tests. Volgens het worklog was ook het volledige factory-vangnet groen.
- Geen productiecode, gebruikersfunctionaliteit, databasegegevens of externe bronnen gewijzigd.
- De oudere roltekst noemt `summary-finished`; volgens het actuele opdrachtcontract gebruik ik `summarized`.

<!-- deploy-summary:start -->
De automatische controle voor historisch zoeken op Heemskerk is uitgebreid. Ze controleert nu ook duidelijk het verschil tussen resultaten, geen resultaten en bronproblemen, zonder echte gegevens of bronnen te gebruiken.
<!-- deploy-summary:end -->
