# Worklog hkh-148

## Verificatie

- Factory-instructies, `.factory/verification.yaml`, deployment- en secretsdocumentatie gelezen.
- Preview niet beschikbaar: `SF_PREVIEW_URL` en de previewtemplate zijn leeg.
- Gerichte run: `mvn -B --no-transfer-progress -Dtest=nl.vdzon.hkh.historicalsearch.HistoricalSearchTest test`.
- Resultaat: 42 tests, 0 failures, 0 errors en 0 skips.
- De scenario’s voor beschikbaar resultaat, nulresultaat, HTTP-fout, timeout,
  ongeldige JSON en ontbrekende/tegenstrijdige verplichte velden zijn groen.
- De logasserties bevestigen één allowlisted event per aanroep met alleen
  `event`, `source`, `outcome`, `durationMs`, `httpStatusClass` en
  `processedResultCount`; querywaarden, persoonsnamen, payloadinhoud,
  identifiers en URL’s ontbreken in de loguitvoer.
- Het volledige factory-vangnet wordt na deze run door de harness uitgevoerd.
