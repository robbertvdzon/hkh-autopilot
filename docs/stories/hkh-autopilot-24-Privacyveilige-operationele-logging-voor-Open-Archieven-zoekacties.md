# hkh-autopilot-24 - Privacyveilige operationele logging voor Open Archieven-zoekacties

## Story

Privacyveilige operationele logging voor Open Archieven-zoekacties

<!-- refined-by-factory -->

## Scope

Voeg privacyveilige operationele logging toe aan Open Archieven-zoekacties achter de bestaande historische zoekroute.

Per aanroep wordt één allowlisted logevent vastgelegd met uitsluitend:

- bron: `OPEN_ARCHIEVEN`;
- technische uitkomstcategorie;
- duur in milliseconden;
- HTTP-statusklasse wanneer beschikbaar;
- aantal succesvol verwerkte resultaten wanneer beschikbaar.

Querywaarden, persoonsnamen, volledige queryparameters, URL’s met queryparameters, response-body’s, foutteksten, stacktraces, stabiele recordidentifiers en andere recordpayloads worden niet gelogd. Ook bij fouten en tegenstrijdige broninformatie wordt geen gevoelige fallback-inhoud toegevoegd.

## Acceptance criteria

- Een geldig resultaat levert een veilig logevent op met uitkomst `AVAILABLE`, HTTP-statusklasse en het aantal verwerkte resultaten.
- Een geldig nulresultaat levert een veilig logevent op met uitkomst `AVAILABLE` en verwerkte-resultatenaantal `0`.
- Timeout, HTTP-fout, ongeldige JSON en ontbrekende of tegenstrijdige verplichte velden leveren ieder hun bestaande technische uitkomstcategorie op.
- Bij een HTTP-fout wordt alleen de statusklasse vastgelegd; bij timeout of andere fouten zonder HTTP-respons blijft die waarde leeg.
- Een mislukte parsing of validatie logt uitsluitend de veilige categorie en nooit exceptiontekst, response-body of bronrecordgegevens.
- Geautomatiseerde tests controleren zowel de inhoud van de allowlist als de afwezigheid van zoektermen zoals `Heemskerk`, persoonsnamen, queryparameters en voorbeeldbronpayloads in applicatie- en foutloguitvoer.
- De bestaande publieke zoekrespons, statussemantiek, paginering en foutafhandeling blijven inhoudelijk ongewijzigd.
- Er wordt geen zoekgeschiedenis, gebruikersprofiel of nieuwe persistente opslag geïntroduceerd.

## Aannames

- De scope betreft de Open Archieven-zoekadapter van de historische zoekroute, niet de afzonderlijke Open Archieven-recordverificatie.
- Iedere externe Open Archieven-paginabevraging krijgt een eigen logevent.
- De bestaande technische statuscategorieën blijven leidend; een geldig nulresultaat wordt onderscheiden door `AVAILABLE` met verwerkte-resultatenaantal `0`.
- Het aantal verwerkte resultaten betreft de veilig genormaliseerde resultaten van de betreffende pagina; bij een fout is het aantal leeg.
- Het logevent gebruikt alleen de vaste bronidentificatie `OPEN_ARCHIEVEN` en nooit door de bron geleverde namen of identifiers.

## Eindsamenvatting

De story is inhoudelijk opgeleverd.

- Open Archieven schrijft per aanvraag één allowlisted logevent met alleen bron, uitkomst, duur, statusklasse en resultaatcount.
- Zoektermen, namen, URL’s, bronpayloads en foutdetails worden niet gelogd.
- Bestaande zoekrespons, statussen, paginering en foutafhandeling blijven ongewijzigd.
- Charset-afhandeling is behouden en met een regressietest gecontroleerd.
- Gerichte test: 42 geslaagd. De laatste geldige factory-verificatie meldt alle zes checks geslaagd: backend 320 tests, frontend 68 tests en adminfrontend 35 tests.
- Er is geen zoekgeschiedenis, profiel, extra opslag of logging voor recordverificatie toegevoegd. Preview-testen waren niet mogelijk omdat geen preview-URL is ingericht.
- Het rolbestand noemt `summary-finished`, maar het opdrachtcontract vereist `summarized`; daarom gebruik ik hieronder het contract.

<!-- deploy-summary:start -->
Zoekacties via Open Archieven zijn nu beter te volgen bij storingen. Daarbij worden geen namen, zoekopdrachten of brongegevens in de meldingen opgenomen. De zichtbare zoekresultaten voor gebruikers blijven hetzelfde.
<!-- deploy-summary:end -->
