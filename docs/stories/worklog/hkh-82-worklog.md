# hkh-82 — Story-brede test

## Testnotities

- Previewomgeving niet beschikbaar: `deployment.md` bevat geen preview-URL of namespace.
- Gerichte backendrun: `HistoricalMetadataContractTest` en `OpenArchievenMetadataAdapterTest`, samen
  22 tests, groen; 0 failures/errors.
- Volledig factory-vangnet groen:
  - `(cd backend && mvn -B --no-transfer-progress clean verify)`: BUILD SUCCESS, 278 tests,
    0 failures, 0 errors.
  - `(cd frontend && flutter analyze)`: geen issues.
  - `(cd frontend && flutter test)`: 35 tests, alle geslaagd.
  - `(cd frontend && flutter build web)`: succesvol.
  - `(cd frontend-admin && flutter analyze)`: geen issues.
  - `(cd frontend-admin && flutter test)`: 36 tests, alle geslaagd.
- De bestaande adaptertests verifiëren geldig metadataresultaat, onbekende metadata-/objectrechten,
  privacyblokkade, tijdelijke bronuitval, lege respons, gewijzigde bronversie, tegenstrijdige
  JSON-LD-waarden, user-agent en server-egress-rate-limiting.
- Geen previewbrowserrun of screenshots uitgevoerd omdat geen previewcontext beschikbaar is.
- Worktree bleef verder ongewijzigd; geen code, tests of infrastructuur aangepast.

## Conclusie

Alle acceptance-criteria die lokaal verifieerbaar zijn, zijn met groene tests afgedekt. Besluit:
`tested`.
