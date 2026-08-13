# hkh-autopilot-23 - Herleidbare foutdiagnose voor Open Archieven-responsen

## Story

Herleidbare foutdiagnose voor Open Archieven-responsen

<!-- refined-by-factory -->

## Scope

Breid het bestaande bronstatuscontract van de historische zoekroute uit met afzonderlijke, stabiele statuswaarden voor Open Archieven:

- `TIMEOUT`: de bron reageerde niet binnen de ingestelde verzoektermijn;
- `HTTP_ERROR`: de bron antwoordde met een HTTP-status buiten 2xx;
- `INVALID_JSON`: de respons kan niet als JSON worden gelezen;
- `MISSING_REQUIRED_FIELDS`: de JSON-respons mist verplichte velden, bevat onjuiste typen, lege verplichte waarden of tegenstrijdige verplichte waarden.

Behoud `AVAILABLE` voor een geldige respons met resultaten én voor een geldige respons met `number_found: 0` en een lege `docs`-lijst. Zo’n nulresultaat is geen bronfout.

Gebruik het bestaande `GET /api/historical-search`-contract en de bestaande frontendstatusweergave. Voeg geen nieuwe route, opslag of provider toe. Zorg dat beschikbare resultaten bij gedeeltelijke bronuitval zichtbaar blijven en dat volledige bronuitval geen nulresultaat of resultatentelling toont.

Gebruik uitsluitend vaste, veilige statusmeldingen. Ruwe bronresponsen, HTTP-responsinhoud, exceptionteksten, stacktraces en persoonsgegevens mogen niet in de API-respons, frontendweergave of opslag terechtkomen.

## Acceptance criteria

- Een geldige Open Archieven-respons met `response.number_found = 0` en `response.docs = []` levert `status: AVAILABLE`, een nulresultaat en geen foutcategorie op.
- Een time-outfixture levert uitsluitend `status: TIMEOUT`.
- Een fixture met een niet-2xx HTTP-respons levert uitsluitend `status: HTTP_ERROR`, ongeacht de inhoud van de HTTP-body.
- Een fixture met ongeldige JSON levert uitsluitend `status: INVALID_JSON`.
- Fixtures met ontbrekende, onjuiste, lege of tegenstrijdige verplichte responsvelden leveren uitsluitend `status: MISSING_REQUIRED_FIELDS`.
- De vier foutcategorieën zijn via `sources[].status` beschikbaar op de bestaande zoekroute en zijn reproduceerbaar in geautomatiseerde backendtests.
- De frontend toont per categorie een vaste, begrijpelijke melding:
  - time-out: Open Archieven reageerde niet op tijd;
  - HTTP-fout: Open Archieven gaf een fout bij het opvragen;
  - ongeldige JSON: Open Archieven stuurde een onleesbaar antwoord;
  - ontbrekende verplichte velden: Open Archieven stuurde een onvolledig antwoord.
- Bij gedeeltelijke beschikbaarheid blijven geldige resultaten en hun bronstatussen, tellingen, identifiers, oorspronkelijke bronlinks en rechtenstatussen behouden.
- Bij volledige bronuitval worden geen bronpayloads, HTTP-statusdetails, stacktraces of persoonsgegevens getoond.
- Bestaande geldige Open Archieven-fixtures behouden hun stabiele identifiers, door de bron geleverde oorspronkelijke links, rechtenstatussen en beschikbare resultaten.

## Aannames

- De nieuwe categorieën worden toegevoegd aan het bestaande technische bronstatusveld; er komt geen tweede foutveld.
- Andere transportproblemen die geen time-out zijn en geen HTTP-respons opleveren, behouden de bestaande tijdelijke-onbeschikbaarstatus.
- Een niet-2xx-respons is een HTTP-fout, ook wanneer de body geldige JSON bevat.
- De bestaande verplichte Open Archieven-contractvelden blijven leidend: `response`, `docs`, `number_found`, en per document `source_name`, `uuid` en `original_source_url`.
- De bestaande zoektoestand (`NO_RESULTS`, `PARTIAL_AVAILABILITY` en `SOURCE_FAILURE`) blijft ongewijzigd; alleen de bronreden wordt specifieker.
- De time-outduur is configuratie- en testbaarheidsdetail en maakt geen deel uit van het publieke contract.

## Eindsamenvatting

Eindsamenvatting voor de PO:

De bestaande historische zoekroute onderscheidt nu time-outs, HTTP-fouten, ongeldige JSON en ontbrekende verplichte velden. Geldige nulresultaten blijven `AVAILABLE`; geldige resultaten blijven zichtbaar bij gedeeltelijke bronuitval. Broninhoud, exceptiondetails en persoonsgegevens worden niet getoond. Er is geen nieuwe route, opslaglaag, provider, secret of handmatige productactie toegevoegd.

De 38 backendtests voor historische zoekopdrachten, alle frontendtests (68), frontend-analyse/build en admin-analyse/tests zijn groen. De volledige Maven-run is niet groen door 7 Testcontainers-integratiefouten omdat Docker in deze omgeving ontbreekt; de storytests zelf slagen wel. Dit blijft het voornaamste resterende verificatierisico.

De rolhandleiding noemt `summary-finished`, maar het opdrachtcontract vereist `summarized`; dat contract is gevolgd.

<!-- deploy-summary:start -->
Bij historische zoekopdrachten wordt nu duidelijk aangegeven waarom Open Archieven niet beschikbaar is, bijvoorbeeld door een time-out, fout antwoord of onleesbaar antwoord. Geldige resultaten blijven zichtbaar en nul resultaten worden niet langer als fout behandeld.
<!-- deploy-summary:end -->
