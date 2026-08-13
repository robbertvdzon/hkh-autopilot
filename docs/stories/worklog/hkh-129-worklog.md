# Worklog hkh-129

## Stappenplan

- [x] Rechtencontract en bestaande historische zoekweergave inspecteren
- [x] Backendmapping en frontendweergave aanpassen
- [x] Unit-, contract-, widget- en semantiektests schrijven en uitvoeren
- [x] Volledig factory-vangnet uitvoeren
- [x] Zelfreview afronden

## Uitvoering

Developer-run afgerond.

- De historische adapters mappen uitsluitend expliciete `ALLOWED` en `RESTRICTED`
  waarden; ontbrekende, lege, onbekende en tegenstrijdige waarden blijven
  `UNKNOWN`. Het vrije bronveld `rights` wordt niet als status gebruikt.
- De resultaatkaart en detailweergave tonen metadatarechten en object-/mediarechten
  afzonderlijk en bevatten dezelfde uitklapbare, semantisch interactieve uitleg.
  De uitleg benoemt de onafhankelijke beoordeling en de betekenis van `Onbekend`.
- Backendtests dekken toegestane, beperkte, ontbrekende, niet-herkende en
  tegenstrijdige rechten voor beide historische adapters. Frontendtests controleren
  labels, semantische knopstatus, kaart/detailweergave en Enter/spatie-bediening.
- Verificatie groen: `mvn -B --no-transfer-progress clean verify` (305 tests),
  frontend analyze, 66 frontendtests, frontend webbuild, admin analyze en 36
  admintests; alle met exitcode 0 en zonder failures/errors.
