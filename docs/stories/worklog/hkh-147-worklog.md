# Worklog hkh-147

## Stappenplan

- [x] Factory-instructies, development- en technical-spec gelezen
- [x] Bestaande Open Archieven-zoekadapter en statusmapping inspecteren
- [x] Privacyveilige allowlisted logging implementeren
- [x] Gerichte unittests schrijven en uitvoeren
- [x] Volledig factory-vangnet uitvoeren

## Uitvoering

- Developer-run gestart; storycontext en factory-instructies gecontroleerd.
- `OpenArchievenSearchAdapter` logt per aanroep één vaste allowlist met bron,
  technische uitkomst, duur, HTTP-statusklasse en veilig verwerkte resultaatcount.
  Querywaarden, bronpayloads en exceptiondetails worden niet aan de logger
  doorgegeven. HTTP-responsen worden hiervoor gelezen met behoud van de statuscode;
  de publieke zoekrespons en bestaande statusmapping blijven ongewijzigd.
- Tests toegevoegd voor geldige resultaten, nulresultaten, timeout, HTTP-fout,
  ongeldige JSON en ontbrekende verplichte velden, inclusief allowlist- en
  privacyasserties. De gerichte `HistoricalSearchTest`-run is groen (41 tests).
- Volledig vangnet groen: backend `mvn -B --no-transfer-progress clean verify`
  (319 tests, 0 failures/errors), frontend analyze/test/webbuild en
  frontend-admin analyze/test allemaal exitcode 0.
