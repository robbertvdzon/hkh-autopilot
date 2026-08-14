# hkh-autopilot-26 - Worklog

Story-context bij eerste pickup:
Open Archieven-budget, cache en deduplicatie implementeren

Implementeer budget, cache, single-flight, retry/statuscontract en alle ontwikkeltests; voer review uit.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `.task.md`, de developer-instructies, `development.md`, `technical-spec.md` en de bestaande
  historicalsearch-code/tests gelezen; er zijn geen conflictmarkers of andere lokale wijzigingen.
- De Open Archieven-route gebruikt nu een proceslokaal per-IP budget met 10 burst en maximaal 60
  aanvragen per rolling minuut. Het IP komt alleen uit `X-Forwarded-For` bij een geconfigureerde
  vertrouwde directe proxy; anders blijft het directe connection-IP leidend.
- Geldige genormaliseerde Open Archieven-pagina's worden tijdelijk en begrensd gecachet op een
  SHA-256-cachekey met bron, volledige genormaliseerde context, offset, limiet en vaste taal `nl`.
  Gelijktijdige misses delen één externe aanvraag via single-flight. Cachekeys bewaren geen vrije
  zoekwaarden.
- Upstream HTTP 429 is toegevoegd als veilige bronstatus `RATE_LIMITED`; maximaal één retry gebruikt
  alleen een bruikbare `Retry-After` van ten hoogste twee seconden. Iedere daadwerkelijke poging houdt
  de bestaande allowlisted logging aan; een lokale budgetoverschrijding geeft vaste HTTP 429 terug.
- Nieuwe `OpenArchievenProtectionTest`-tests dekken concurrency, TTL/negatieve cache, keyvariaties,
  budget/IP-isolatie, proxyvertrouwen, retry en veilige statusmapping.
- Verificatie groen: `mvn -B --no-transfer-progress clean verify` (327 tests), `flutter analyze`,
  `flutter test` (72 tests), `flutter build web`, `frontend-admin/flutter analyze` en
  `frontend-admin/flutter test` (36 tests); alle commando's exitcode 0 zonder failures/errors.
