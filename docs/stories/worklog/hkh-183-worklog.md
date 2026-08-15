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

Done / rationale:
- Developer-run gestart met het issuecontract, de factory-regels en de bestaande historische zoekimplementatie als uitgangspunt.
- `OpenArchievenSearchAdapter` bouwt de semantie naast het echte verzoek op en geeft alleen `name` en een daadwerkelijk gebruikte `eventplace` door; `archive_code`, paginering en rate limiting blijven technische parameters.
- `querySemantics` is nullable per bron en loopt via backendpagina, service, publieke/adminbronstatus en Flutter-parser naar de zoekweergave. De UI gebruikt vaste Nederlandse labels met de providerparameter en toont anders `Zoekinterpretatie: niet beschikbaar.`.
- Tests dekken naam-, plaats- en neutrale interpretatie en controleren zowel uitgaande filters als zichtbare tekst.
- Vangnet groen: backend `mvn -B --no-transfer-progress clean verify` (356 tests), frontend `flutter analyze`, `flutter test` (82 tests), `flutter build web`, frontend-admin `flutter analyze` en `flutter test` (38 tests).
