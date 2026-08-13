# hkh-autopilot-22 - Open Archieven als controleerbare externe bronadapter

## Story

Open Archieven als controleerbare externe bronadapter

<!-- refined-by-factory -->

## Scope

Breid de bestaande Open Archieven-adapter van `GET /api/historical-search` uit zonder een nieuwe publieke route of opslaglaag toe te voegen.

Voor de Heemskerk-zoekopdracht stuurt de adapter naast `name=Heemskerk` ook `archive_code=hee` mee. De bestaande limiet, paginering, user-agent en procesbrede rate limit blijven van kracht.

Het genormaliseerde Open Archieven-resultaat gebruikt exact deze veldnamen:

- `source_name`: de door de bron geleverde bronnaam;
- `stable_identifier`: de volledige bronreferentie in de vorm `hee:uuid`;
- `original_source_url`: de door de bron geleverde, resolveerbare Open Archieven-URL;
- de expliciet geleverde gebeurtenisplaats, indien aanwezig.

`archive_code` is geen verplicht responseveld. De adapter mag de code afleiden uit de stabiele bronidentifier, maar mag geen identifier of bronlink verzinnen. De bestaande technische, rechten-, privacy- en statusinformatie blijft behouden voor zover die expliciet en veilig uit de bron kan worden genormaliseerd.

## Acceptance criteria

- Een deterministische fixture voor `name=Heemskerk` en `archive_code=hee` levert minimaal één geldig resultaat met `source_name`, `stable_identifier` in de vorm `hee:uuid`, `original_source_url` en, wanneer aanwezig, de expliciete gebeurtenisplaats.
- De requestmapping controleert aantoonbaar dat `name=Heemskerk` en `archive_code=hee` als afzonderlijke parameters worden verstuurd.
- Alleen het vastgelegde Open Archieven-responsecontract wordt geaccepteerd. Ontbrekende, lege, tegenstrijdige of ongeldig gevormde verplichte velden leiden tot `INVALID_RESPONSE`; het betreffende resultaat wordt nooit als geldig gepresenteerd.
- `original_source_url` wordt uitsluitend overgenomen uit de bronrespons en moet een absolute HTTP(S)-URL met geldige host zijn. De adapter construeert geen URL en voert geen aanvullende netwerkcontrole uit.
- Een geldige response met een expliciete lege resultaatlijst leidt tot een beschikbare bron en `NO_RESULTS`, met nul zichtbare resultaten en zonder verzonnen resultaat of zichtbaar provider-totaal.
- Een time-out of andere transportfout blijft onderscheiden van een geldig nulresultaat en leidt tot de bestaande tijdelijke bronfoutstatus.
- Alleen expliciete rechtenwaarden `ALLOWED` en `RESTRICTED` worden herkend. Ontbrekende, niet-herkende of tegenstrijdige rechteninformatie wordt `UNKNOWN` en wordt als `Onbekend` weergegeven; er ontstaat nooit een hergebruik-, media- of downloadclaim.
- De adapter bewaart, retourneert of logt geen scans, digitale objectbestanden of ruwe API-payloads.
- De publieke zoekrespons en gebruikersweergave tonen de genormaliseerde bronnaam, identifier en oorspronkelijke externe link, naast alleen toegestane metadata.
- Deterministische contracttests dekken minimaal: geldig Heemskerk-resultaat, lege response, time-out, ongeldige response, ontbrekende identifier, ongeldige link, requestparameters, rechtenmapping, user-agent en rate limiting.
- De implementatie vereist geen account, eigenaaractie, autorisatietoken of nieuwe secretconfiguratie.

## Aannames

- `archive_code=hee` is voor deze story een vaste providerparameter voor de Heemskerk-zoekopdracht en wordt niet als verplicht veld door de bezoeker ingevoerd.
- `uuid` is de door Open Archieven geleverde recordcomponent waaruit `hee:uuid` wordt gevormd; bij ontbrekende of onveilige waarde wordt de bronrespons afgewezen.
- “Resolveerbare Open Archieven-URL” betekent een door de bron geleverde absolute HTTP(S)-URL met geldige host; er wordt geen live URL-check aan de adapter toegevoegd.
- De bestaande Europeana-adapter en overige historische zoekfunctionaliteit blijven inhoudelijk ongewijzigd, behalve waar de gedeelde responsmapping deze drie expliciete veldnamen moet doorgeven.

## Eindsamenvatting

PO-samenvatting:

- Open Archieven zoekt voor Heemskerk met afzonderlijke parameters `name=Heemskerk` en `archive_code=hee`.
- Geldige resultaten tonen bronnaam, identifier `hee:uuid`, oorspronkelijke bronlink en expliciete plaatsgegevens.
- Ontbrekende, onveilige of tegenstrijdige brondata wordt fail-closed afgewezen; lege resultaten en transportfouten blijven afzonderlijk herkenbaar.
- Rechten worden alleen bij expliciete waarden toegestaan of beperkt verklaard; overige waarden blijven onbekend.
- De bestaande zoekweergave en detailpagina tonen de genormaliseerde brongegevens. Er zijn geen nieuwe routes, opslaglagen, accounts of secrets toegevoegd.
- Een reviewprobleem rond tegenstrijdige `number_found`-tellingen is opgelost met regressietests.
- Het volledige vangnet is groen: backend 311 tests, frontend 68 tests plus analyse en webbuild, en frontend-admin 35 tests plus analyse. De previewomgeving was niet beschikbaar.

<!-- deploy-summary:start -->
Historische zoekresultaten laten nu duidelijk zien uit welke bron ze komen en openen de oorspronkelijke bron. Foute of onvolledige broninformatie wordt niet als betrouwbaar resultaat getoond.
<!-- deploy-summary:end -->
