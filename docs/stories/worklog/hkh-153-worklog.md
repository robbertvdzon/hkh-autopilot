# hkh-153 - Worklog

## Reviewer

- Het harness-verificatiebewijs is geldig: de actuele HEAD-tree is gelijk aan de geteste worktree-tree `c30f33354588124c013cd22fab8ce26c092b3ec5`; alle verplichte checks zijn groen.
- Gerichte controle: `frontend/test/historical_search_test.dart` geslaagd met 22 tests.
- [blocker] Tijdens `retryInProgress` blijft `historical-search-retry` een actieve knop. `_HistoricalResults` laat de knop toe wanneer `retryInProgress` waar is en `_retrySearch` start dan opnieuw een aanvraag. Herhaald activeren tijdens één lopende retry veroorzaakt dus meerdere gelijktijdige aanvragen, terwijl de story exact één nieuwe retry-aanvraag vereist en de race ertoe kan leiden dat een latere poging de eerste uitkomst overschrijft. Disableer/verberg de retryactie tijdens laden en voeg een regressietest voor dubbel activeren toe.

## Developer

- [x] Retryactie tijdens een lopende retry blokkeren.
- [x] Regressietest toevoegen die dubbele retry-aanvragen voorkomt.
- [x] Volledig vangnet uit `docs/factory/development.md` uitvoeren.

De reviewbevinding wordt opgelost door de retryknop tijdens `retryInProgress` uit de
interactie te halen, zodat één gebruikersactie maximaal één nieuwe aanvraag start. De
bestaande resultaten en statusweergave blijven tijdens het laden behouden.

Het volledige vangnet is groen uitgevoerd: backend `mvn clean verify`, frontend
`flutter analyze`, `flutter test` en `flutter build web`, plus `frontend-admin`
`flutter analyze` en `flutter test` eindigden allemaal met exitcode 0.

## Reviewer (vervolgreview)

- Het nieuwste `[FACTORY VERIFICATION EVIDENCE]` is geldig: de actuele HEAD-tree is `4d9f9fcee12c477e621c5fc23f7ef6ee495007e1`, gelijk aan de gemeten worktree-tree. De gerichte `frontend/test/historical_search_test.dart` en analyse van `historical_search.dart` zijn groen.
- [blocker] Bij een eerste of nieuw gestarte zoekopdracht die eindigt in een tijdelijke transportfout wordt geen retry-context opgeslagen. In `_runSearch` wordt `_lastCompletedSearch` bij het starten gewist (regels 554-559) en de `onError`-tak bewaart alleen de retrystatus (regels 577-583). Als de gebruiker daarna een veld wijzigt zonder opnieuw te zoeken en op `Opnieuw proberen` drukt, leest `_retrySearch` zonder snapshot de actuele controllers via `_runSearch(isRetry: true)` (regels 588-596 en 529-540). De retry verstuurt dan niet langer exact dezelfde genormaliseerde parameters als de mislukte aanvraag, in strijd met het eerste acceptance criterium en de retry-flow voor transportfouten. Bewaar de request-context al bij het starten van iedere retrybare aanvraag en voeg een regressietest toe die na een transportfout een veldwijziging vóór retry controleert.
## Developer (huidige run)

- [x] Retry-context bewaren vóór een retrybare aanvraag, ook wanneer de aanvraag in een transportfout eindigt.
- [x] Regressietest toevoegen voor wijzigen van een zoekveld vóór retry na een transportfout.
- [x] Volledig vangnet uit `docs/factory/development.md` uitvoeren.

De vervolgreview wees erop dat de retry na een transportfout geen snapshot had. De
implementatie wordt aangepast zodat de genormaliseerde request-context vóór de aanvraag
wordt bewaard en een volgende retry uitsluitend die context gebruikt; de actuele
zoekvelden blijven visueel ingevuld, maar kunnen de retry niet stilzwijgend wijzigen.

De retry-context bevat uitsluitend de genormaliseerde tekst-, filter-, bron- en
paginaparameters. De context wordt vóór de aanvraag vastgelegd, zodat ook een eerste
transportfout retrybaar blijft; controllerwijzigingen beïnvloeden de retry niet.

Verificatie: `backend/mvn -B --no-transfer-progress clean verify` (320 tests),
`frontend/flutter analyze`, `frontend/flutter test` (72 tests),
`frontend/flutter build web`, `frontend-admin/flutter analyze` en
`frontend-admin/flutter test` (35 tests) zijn allemaal geslaagd met exitcode 0,
zonder failures of errors.
