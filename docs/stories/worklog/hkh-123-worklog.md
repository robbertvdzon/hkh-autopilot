# hkh-123 - Worklog

## Stappenplan

- [x] Huidige historische zoekflow en factory-contracten gecontroleerd
- [x] Live-status, bronuitval, retry en zoekopdracht-aanpassen geïmplementeerd
- [x] Unit-/widget-/contracttests toegevoegd en gedraaid
- [x] Volledig factory-vangnet gedraaid en resultaten vastgelegd

## Uitvoering

- De bestaande historische pagina gebruikt één `SemanticsRole.status`-regio voor de
  laad-, resultaat-, lege-, gedeeltelijke en volledige bronuitvalstatus. De spinner is
  decoratief uitgesloten zodat er geen tweede statusknoop ontstaat.
- Volledige bronuitval meldt dat geen bronnen konden worden geraadpleegd, toont alleen
  vaste veilige foutredenen per bron en biedt `Opnieuw proberen` en
  `Zoekopdracht aanpassen`. De laatste actie focust het bestaande vrije-tekstveld en
  laat alle ingevoerde zoekwaarden staan.
- Gedeeltelijke beschikbaarheid houdt de resultaten zichtbaar en neemt de beschikbare
  bron met de paginatelling en iedere uitgevallen bron op in de statuslabeltekst.
- Widgettests dekken veilige volledige uitval, gedeeltelijke bronstatus, één statusknoop,
  focusbehoud, keyboard-herkenbare acties en retry via loading naar een nieuwe lege uitkomst.
- Gerichte controle: `flutter test --concurrency=1 --reporter expanded test/historical_search_test.dart`
  is groen (16 tests).

## Volledig vangnet

- `cd backend && mvn -B --no-transfer-progress clean verify`: groen, 303 tests, 0 failures/errors.
- `cd frontend && flutter analyze`: groen.
- `cd frontend && flutter test`: groen, 64 tests, 0 failures/errors.
- `cd frontend && flutter build web`: groen.
- `cd frontend-admin && flutter analyze`: groen.
- `cd frontend-admin && flutter test`: groen, 35 tests, 0 failures/errors.

## Review

- Volledige diff `main...HEAD` beoordeeld; geen concrete blocker, bug, scope-regressie of ontbrekende testdekking gevonden.
- Revisiongebonden factory-bewijs gecontroleerd: alle zes verplichte checks zijn groen en `testedTreeSha` komt overeen met `HEAD^{tree}`.
- Gerichte controle `flutter test --concurrency=1 --reporter expanded test/historical_search_test.dart`: groen, 16 tests.
