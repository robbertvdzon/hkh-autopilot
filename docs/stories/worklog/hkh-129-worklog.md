# Worklog hkh-129

## Herstelrun na review

- [x] Reviewblocker lokaliseren in de gedeelde rechtenuitleg
- [x] Omgekeerde onafhankelijkheid van object-/mediarechten expliciet maken
- [x] Gerichte frontendtest uitvoeren
- [x] Volledig factory-vangnet uitvoeren
- [x] Zelfreview afronden

De gedeelde rechtenuitleg benoemt nu beide richtingen van de onafhankelijke
beoordeling. De semantiektest controleert de nieuwe tekst in de geopende uitleg.
Gerichte test en het volledige factory-vangnet zijn groen uitgevoerd.

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

## Reviewnotities

- Volledige story-diff ten opzichte van `main` gecontroleerd; het harness-bewijs is
  geldig (`tested worktree tree` is gelijk aan `HEAD^{tree}`) en de gerichte backend-
  (27 tests) en frontendtests (24 tests) zijn groen.
- [blocker] `frontend/lib/historical/historical_rights_explanation.dart:3-9`
  benoemt wel dat toegestane metadatarechten niet automatisch object-/mediarechten
  geven, maar niet de omgekeerde richting. De acceptance criteria verlangen dat de
  uitleg tekstueel maakt dat de ene status de andere niet impliceert; voeg ook toe
  dat toegestane object-/mediarechten niet automatisch metadatarechten betekenen,
  zowel op de resultaatkaart als in de detailweergave (die deze component delen).

## Vervolgreview

- De eerdere blocker is opgelost: de gedeelde uitleg bevat nu ook expliciet de
  omgekeerde onafhankelijkheid van object-/mediarechten en metadatarechten.
- De uitleg blijft op zowel resultaatkaart als detailweergave een semantische,
  toetsenbordbedienbare `TextButton` en de nieuwe tekst wordt door de semantiektest
  gecontroleerd.
- Geen nieuwe regressies gevonden in de volledige `main...HEAD`-diff. Gerichte
  checks zijn groen: `HistoricalSearchTest` (27/27) en de relevante Flutter-tests
  (25/25). Het factory-verificatieblok is geldig voor de actuele tree.
