# hkh-183 - Worklog

Story-context:
Querysemantiek doorgeven en zichtbaar maken

Stappenplan:
[x]: issue, factory-documentatie en technische specificatie gelezen
[x]: backendcontract en Open Archieven-adapter uitbreiden
[x]: route-mapping en Flutterweergave uitbreiden
[x]: naam-, plaats- en onbepaalde interpretaties testen
[x]: relevant tests en volledig vangnet draaien
[x]: zelfreview uitvoeren en resultaten vastleggen
[x]: reviewbevinding voor expliciete Europeana-bron herstellen en regressietest toevoegen

Done / rationale:
- Developer-run gestart met het issuecontract, de factory-regels en de bestaande historische zoekimplementatie als uitgangspunt.
- `OpenArchievenSearchAdapter` bouwt de semantie naast het echte verzoek op en geeft alleen `name` en een daadwerkelijk gebruikte `eventplace` door; `archive_code`, paginering en rate limiting blijven technische parameters.
- `querySemantics` is nullable per bron en loopt via backendpagina, service, publieke/adminbronstatus en Flutter-parser naar de zoekweergave. De UI gebruikt vaste Nederlandse labels met de providerparameter en toont anders `Zoekinterpretatie: niet beschikbaar.`.
- Tests dekken naam-, plaats- en neutrale interpretatie en controleren zowel uitgaande filters als zichtbare tekst.
- Vangnet groen: backend `mvn -B --no-transfer-progress clean verify` (356 tests), frontend `flutter analyze`, `flutter test` (82 tests), `flutter build web`, frontend-admin `flutter analyze` en `flutter test` (38 tests).

Huidige developer-run:
- De reviewbevinding wordt hersteld door ook zonder Open Archieven-bron een vaste neutrale
  interpretatiestatus te tonen; daarna wordt het Europeana-pad gericht getest en volgt opnieuw het
  volledige vangnet.
- De bronlijst bepaalt nu centraal de interpretatietekst: een expliciete Europeana-respons of een
  respons zonder Open Archieven-verzoek toont altijd `Zoekinterpretatie: niet beschikbaar.`.
- Een nieuwe widgettest selecteert Europeana, controleert de uitgaande bronkeuze en bewaakt dat geen
  plaatsinterpretatie uit de zoekterm wordt afgeleid. Gerichte en volledige frontendtests zijn groen.
- Zelfreview afgerond: alleen Open Archieven-semantieken worden als providerinterpretatie benoemd;
  bij afwezigheid van die bron blijft de UI neutraal.

Review:
- [blocker] Bij een expliciete Europeana-bron bevat de API alleen de Europeana-status. De frontend bouwt de interpretatie uitsluitend uit `response.sources` met `source == 'OPEN_ARCHIEVEN'` (`frontend/lib/historical/historical_search.dart:973-976` en `:1250-1253`). Daardoor wordt bij een zoekopdracht waarvoor geen Open Archieven-verzoek is uitgevoerd geen `Zoekinterpretatie: niet beschikbaar.` getoond, terwijl de story dat expliciet vereist. Reproductie: kies `Europeana`, voer een geldige zoekopdracht uit, en controleer dat er geen neutrale interpretatiemelding verschijnt; toon ook in dit pad de vaste neutrale melding zonder naam-/plaatsclaim.
- Gerichte checks: backend `HistoricalSearchTest` (46 tests) en frontend `historical_search_test.dart` (26 tests) groen.
