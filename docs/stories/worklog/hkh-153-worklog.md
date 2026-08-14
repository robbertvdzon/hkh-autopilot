# hkh-153 - Worklog

## Reviewer

- Het harness-verificatiebewijs is geldig: de actuele HEAD-tree is gelijk aan de geteste worktree-tree `c30f33354588124c013cd22fab8ce26c092b3ec5`; alle verplichte checks zijn groen.
- Gerichte controle: `frontend/test/historical_search_test.dart` geslaagd met 22 tests.
- [blocker] Tijdens `retryInProgress` blijft `historical-search-retry` een actieve knop. `_HistoricalResults` laat de knop toe wanneer `retryInProgress` waar is en `_retrySearch` start dan opnieuw een aanvraag. Herhaald activeren tijdens één lopende retry veroorzaakt dus meerdere gelijktijdige aanvragen, terwijl de story exact één nieuwe retry-aanvraag vereist en de race ertoe kan leiden dat een latere poging de eerste uitkomst overschrijft. Disableer/verberg de retryactie tijdens laden en voeg een regressietest voor dubbel activeren toe.
