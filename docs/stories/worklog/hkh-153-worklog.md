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
