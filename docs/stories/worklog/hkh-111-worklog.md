# hkh-111 - Worklog

Story-context:
Vervolgzoekacties en dekkingstests implementeren voor de historische resultaatdetailweergave.

Stappenplan:
[x]: issue, factory-documentatie en bestaande historische flow gelezen
[x]: vervolgacties, gating en vervolgzoeknavigatie implementeren
[x]: unit/widgettests voor queries, gating, waarschuwing en navigatie toevoegen
[x]: formatten en volledig factory-vangnet uitvoeren
[x]: worklog afronden met resultaten
[x]: reviewbevindingen over fail-closed statusmapping en querydekking herstellen

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
  `flutter analyze`, `flutter test` (57 tests groen),
  `flutter build web`, admin `flutter analyze` en `flutter test`
  (35 tests groen) zijn allemaal succesvol afgerond.

Reviewherstel:
- De vorige review wees erop dat onbekende of expliciete `MISSING`-contextstatus niet naar
  `AVAILABLE` mag terugvallen en dat de exacte vervolgquery nog voor persoon, gebeurtenis en
  periode moet worden getest. De statusmapping is fail-closed gemaakt en de querydekking is
  uitgebreid met alle vier onderwerpen; de volledige verificatie is daarna opnieuw groen gedraaid.

Huidige developer-run:
- [x] actuele implementatie, tests en verificatieconfiguratie opnieuw gecontroleerd
- [x] aanvullende regressietests voor lege metadata, jaarperioden en semantiek toegevoegd
- [x] gerichte tests en het volledige factory-vangnet opnieuw uitgevoerd

Aanvulling huidige run:
- De backend-verificatie krijgt `frontend/` als relevant pad, zodat de verplichte backend-check
  bij deze frontendstory niet als `skipped` wordt geregistreerd in het factory-bewijs.

Eindresultaat huidige run:
- Gerichte follow-uptests: 6 tests groen.
- Volledig vangnet definitief groen: backend `mvn -B --no-transfer-progress clean verify` (301
  tests, 0 failures, 0 errors), frontend `flutter analyze`, `flutter test` (59 tests) en
  `flutter build web`, admin `flutter analyze` en `flutter test` (35 tests).
- Geen opslag van zoekopdrachten, bronpayloads of klikgeschiedenis toegevoegd; er zijn geen
  conflictmarkers of secrets in de wijziging aangetroffen.
