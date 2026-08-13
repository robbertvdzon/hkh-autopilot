# hkh-111 - Worklog

Story-context:
Vervolgzoekacties en dekkingstests implementeren voor de historische resultaatdetailweergave.

Stappenplan:
[x]: issue, factory-documentatie en bestaande historische flow gelezen
[x]: vervolgacties, gating en vervolgzoeknavigatie implementeren
[x]: unit/widgettests voor queries, gating, waarschuwing en navigatie toevoegen
[x]: formatten en volledig factory-vangnet uitvoeren
[x]: worklog afronden met resultaten

Done / rationale:
- Worklog aangemaakt aan het begin van de developer-run, zoals voorgeschreven door de factory.
- `historicalFollowUpActions` biedt alleen zekere, expliciete en rechten-/privacy-toegestane
  plaats-, persoons-, gebeurtenis- en geldige periodeacties; titel, zoekterm en URL worden niet
  als metadata voor acties gebruikt.
- De detailweergave toont semantische vervolgknoppen en de vaste waarschuwing. Iedere actie opent
  de bestaande `HistoricalSearchPage` zonder bronfilter, met de vervolgwaarde zichtbaar en de
  zoekopdracht automatisch via de geïnjecteerde bestaande source. De route-stack bewaart beide
  terugnavigatiestappen.
- `historical_follow_up_test.dart` dekt exacte actievelden, fail-closed uitzonderingen, semantiek,
  waarschuwing, bronhergebruik en terugnavigatie. Er is geen opslagmechanisme toegevoegd.
- Verificatie: backend `mvn -B --no-transfer-progress clean verify` (301 tests groen), frontend
  `flutter analyze`, `flutter test --concurrency=1 --reporter expanded` (56 tests groen),
  `flutter build web`, admin `flutter analyze` en `flutter test --concurrency=1 --reporter expanded`
  (35 tests groen) zijn allemaal succesvol afgerond.
