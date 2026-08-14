# hkh-165 — Smokecontract voor de Heemskerk-zoekketen

## Stappenplan

- [x] Bestaande backend- en frontendzoekcontracten en testconventies inventariseren.
- [x] Synthetische smoke-contracttests en lokale netwerkfixtures toevoegen.
- [x] Gerichte tests, analyse en build uitvoeren; bevindingen oplossen.
- [x] Volledig factory-vangnet uitvoeren en zelfreview afronden.

## Uitvoering

Developer-run gestart. De bestaande contracten worden eerst gelezen voordat de smoke-fixtures
worden toegevoegd, zodat de test geen productfunctionaliteit of externe bron aanspreekt.

De backend-smoke gebruikt de publieke `GET /api/historical-search`-controller met een lokale
`HttpServer` voor Open Archieven en dekt geldige resultaten, nulresultaat, gedeeltelijke en
volledige bronuitval, uitgeschakelde Europeana, veldgerichte contractassertions en gelijktijdige
single-flight/verzoekbudget-aanvragen. De Flutter-smoke voert dezelfde synthetische route-respons
door `BackendClient` en `HistoricalSearchPage` en controleert de zichtbare onderscheidingen.
Gerichte backend- en Fluttertests zijn groen.

Het volledige vangnet is groen uitgevoerd: backend Maven verify (339 tests), gebruikersfrontend
analyse, tests (75 tests) en webbuild, plus beheerfrontend analyse en tests (35 tests). Er zijn
geen productbestanden gewijzigd, geen echte externe bronnen aangesproken en geen secrets of
persoonsgegevens aan fixtures of output toegevoegd.
