# hkh-autopilot-26 - Gecontroleerd verzoekbudget en caching voor Open Archieven

## Story

Gecontroleerd verzoekbudget en caching voor Open Archieven

<!-- refined-by-factory -->

## Scope

Breid de bestaande publieke historische zoekroute uit voor Open Archieven.

- Gebruik een per inkomend gebruikers-IP begrensd verzoekbudget van maximaal 60 aanvragen per minuut, met een maximale directe burst van 10 aanvragen. Het budget geldt voor Open Archieven-aanvragen en automatische retries.
- Bepaal het gebruikers-IP uitsluitend uit het door een geconfigureerde vertrouwde productieproxy aangeleverde forwarded-header. Zonder geldige vertrouwde proxycontext wordt het directe connection-IP gebruikt. Onvertrouwde forwarded-headers mogen het budget niet beïnvloeden.
- Geef bij overschrijding van het lokale budget HTTP 429 terug met uitsluitend een vaste, veilige foutmelding of foutcode. Zoektermen, persoonsnamen, IP-adressen en providerinformatie mogen niet in deze fout terugkomen.
- Voeg een begrensde, tijdelijke cache toe voor geldige genormaliseerde Open Archieven-paginantwoorden. Een cache-entry wordt bepaald door bron, de volledige genormaliseerde zoekcontext, pagina-offset, paginalimiet en de vaste taalwaarde `nl`. Er wordt geen nieuwe taalparameter aan de publieke route toegevoegd.
- Voeg single-flight-deduplicatie toe: gelijktijdige aanvragen met dezelfde cachekey delen één lopende externe aanvraag.
- Cache alleen de noodzakelijke genormaliseerde bronrespons en technische metadata. Bewaar geen ruwe providerpayload, zoekgeschiedenis of persistent cachebestand. Cachekeys bevatten geen onbewerkte zoektermen of persoonsnamen; gebruik hiervoor uitsluitend een privacyveilige technische representatie.
- Behandel verlopen, ontbrekende of onbruikbare cache-items als cachemisses. Een nieuwe aanvraag volgt dan hetzelfde verzoekbudget.
- Behandel een upstream HTTP 429 als een afzonderlijke getypeerde bronstatus `RATE_LIMITED` in het bestaande zoekantwoord. Probeer maximaal één keer opnieuw. Respecteer `Retry-After`, maar wacht nooit langer dan twee seconden; bij een langere of onbruikbare wachttijd wordt niet opnieuw geprobeerd.
- Behoud de bestaande resultaten, bronstatussen, tellingen, paginering, rechten-/privacyclassificatie en door Open Archieven geleverde permanente bronlinks wanneer een antwoord uit de cache komt.
- De afzonderlijke recordverificatie via Open Archieven, andere historische bronnen en nieuwe publieke routes vallen buiten scope.

## Acceptance criteria

- Een geautomatiseerde backendtest toont aan dat gelijktijdige identieke aanvragen maximaal één externe Open Archieven-aanvraag veroorzaken en allemaal dezelfde veilige genormaliseerde uitkomst ontvangen.
- Een geautomatiseerde backendtest toont aan dat een identieke aanvraag binnen de ingestelde cacheduur geen nieuwe externe aanvraag uitvoert.
- Een geautomatiseerde backendtest toont aan dat een verlopen, ontbrekende of onbruikbare cache-entry wel een nieuwe aanvraag uitvoert wanneer het verzoekbudget dat toestaat.
- Geautomatiseerde tests tonen aan dat de cachekey bron, alle genormaliseerde zoekvelden, pagina-offset, paginalimiet en vaste taalwaarde `nl` bevat, en dat variaties daarin niet ten onrechte dezelfde cache-entry gebruiken.
- Geautomatiseerde tests tonen aan dat de cache geen ruwe bronpayloads, vrije zoektermen, persoonsnamen of volledige zoekgeschiedenis langdurig bewaart.
- Geautomatiseerde tests tonen aan dat het lokale budget maximaal 10 directe aanvragen toestaat en daarna HTTP 429 teruggeeft, en dat het totaal binnen een minuut maximaal 60 toegestane aanvragen per gebruikers-IP bedraagt.
- Geautomatiseerde tests tonen aan dat twee gebruikers-IP’s afzonderlijke budgetten hebben en dat een vertrouwde forwarded-header wordt gebruikt terwijl een onbetrouwbare header wordt genegeerd ten gunste van het directe connection-IP.
- Een upstream HTTP 429 levert de bronstatus `RATE_LIMITED` op, veroorzaakt maximaal één retry, respecteert `Retry-After` tot maximaal twee seconden en maakt geen tweede retry.
- Wanneer de retry succesvol is, blijft de bronstatus `AVAILABLE` en wordt alleen het geldige genormaliseerde antwoord gecachet. Wanneer de retry niet mag of opnieuw met 429 faalt, blijft een afzonderlijk veilig `RATE_LIMITED`-resultaat over.
- Een budgetoverschrijding tijdens het toelaten van een nieuwe inkomende aanvraag levert HTTP 429 op; een niet-toegestane automatische upstream-retry wordt als veilige bronstatus afgehandeld en niet als ruwe providerfout teruggegeven.
- Een cache-hit levert dezelfde resultaten, bronstatussen, tellingen, metadatarechten, privacystatussen en permanente bronlinks als een rechtstreeks providerantwoord.
- Bestaande tests voor geldige resultaten, nulresultaten, bronuitval, logging, paginering, retrygedrag en andere bronnen blijven groen.
- De route accepteert geen nieuwe taalparameter en bestaande API-parameters en responsevelden blijven verder ongewijzigd.

## Aannames

- De cache en het verzoekbudget zijn proceslokaal, begrensd en tijdelijk; er wordt geen database-, zoekgeschiedenis- of externe cacheopslag toegevoegd. De huidige deployment draait één backendinstantie.
- De cacheduur is configureerbaar en wordt in tests met een injecteerbare klok gecontroleerd. Alleen geldige genormaliseerde Open Archieven-antwoorden worden gecachet; fouten en 429-antwoorden worden niet negatief gecachet.
- De zoekcontext voor de cachekey omvat ook providerrelevante vaste waarden, waaronder `archive_code=hee` wanneer die van toepassing is.
- Een automatische retry telt mee als externe aanvraag binnen hetzelfde per-IP budget.
- De statusnaam `RATE_LIMITED` is de vaste publieke naam voor een upstream-429; de bestaande geaggregeerde zoektoestanden blijven volgens hun huidige regels bepaald.
- De bestaande privacyveilige operationele logging blijft gelden voor iedere daadwerkelijke externe poging en logt geen zoekwaarden, payloads, identifiers, headers of exceptiondetails.

## Eindsamenvatting

PO-samenvatting: Open Archieven heeft nu per bezoeker een begrensd verzoekbudget, tijdelijke cache en deduplicatie voor gelijktijdige aanvragen. Upstream-429’s worden veilig afgehandeld met maximaal één korte retry en status `RATE_LIMITED`; bestaande resultaten, paginering, rechten, privacy en bronlinks blijven behouden. Proxyvertrouwen, backendtoegang, webproxies en Android-configuratie zijn aangescherpt. Gerichte backendtests: 56 geslaagd; `git diff --check` is groen. De developer rapporteert daarnaast een groen volledig vangnet met 334 backendtests, 72 frontendtests en 36 adminfrontendtests. Geen preview/E2E-test was mogelijk omdat geen preview-URL is geconfigureerd. Recordverificatie, andere historische bronnen en nieuwe publieke routes vielen bewust buiten scope. De werkboom bleef ongewijzigd.

<!-- deploy-summary:start -->
Historisch zoeken blijft stabieler wanneer veel mensen tegelijk zoeken. Herhaalde zoekopdrachten worden tijdelijk hergebruikt en problemen bij de externe bron worden veilig en duidelijk gemeld.
<!-- deploy-summary:end -->
