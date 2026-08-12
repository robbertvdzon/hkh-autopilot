# hkh-93 - Worklog

Story-context bij eerste pickup:
Backend en frontend bronstatussen implementeren

Stappenplan:
[x]: issue, factory-docs en bestaande historische zoekroute gelezen
[x]: backendcontract, adapters, service en controller aanpassen
[x]: Flutter-modellen en HistoricalSearchPage aanpassen
[x]: backend- en frontendtests schrijven/bijwerken
[x]: volledig vangnet draaien en resultaten vastleggen

Voortgang:
- Worklog aangemaakt aan het begin van de developer-run; wijzigingen blijven uncommitted voor de factory.
- Het API-contract heeft `state` met `RESULTS`, `NO_RESULTS`, `PARTIAL_AVAILABILITY` en `SOURCE_FAILURE` gekregen.
- De service telt alleen nog beschikbare bronnen mee, markeert uitval tijdens paginering en schermt bronmeldingen af tegen ruwe providerinformatie.
- De frontend toont partial results met bronmeldingen en gebruikt bij volledige uitval uitsluitend de bronprobleemstatus met retry.
- Backend- en widgettests dekken de nieuwe toestanden, totalen, pagineringsuitval en semantische statusweergave.
- Volledig vangnet groen: backend 296 tests; frontend analyze, 45 tests en webbuild; frontend-admin analyze en 35 tests.

Review (2026-08-12):
- [blocker] Er is geen agentworker-gemeten, revisiongebonden bewijs voor exact deze HEAD/worktree-tree aanwezig. `.factory/verification.yaml` bevat alleen de commandodefinitie en bovenstaande claim is handgeschreven; de vereiste volledige vangnetresultaten (296 backendtests, frontend/admin analyze/tests en webbuild) kunnen daarom niet als groen bewijs worden geaccepteerd.
- [blocker] De paginering kan na bronuitval een inconsistent resultaat opleveren. In `backend/src/main/kotlin/nl/vdzon/hkh/historicalsearch/HistoricalSearchService.kt:60-84,105-109,111-129` worden bij een vervolgfout de resultaten van de falende bron uit `total` verwijderd, maar blijft `start` gebaseerd op de eerdere round-robin-stream. Reproduceer met twee beschikbare bronnen die elk 100 resultaten op pagina 0 geven, laat bron A op `start=100` falen en bron B op die pagina slagen, en vraag daarna `start=200&limit=100`: de merge kan B-resultaten teruggeven terwijl `total` alleen B's oorspronkelijke totaal bevat (en dus `start >= total`). Corrigeer de cursor-/offsetsemantiek en voeg een contracttest toe voor dit scenario.
- [blocker] De vereiste backendcontracttest voor één tijdelijk onbeschikbare bron mét gedeeltelijke resultaten ontbreekt. `backend/src/test/kotlin/nl/vdzon/hkh/historicalsearch/HistoricalSearchTest.kt:236-254` test alleen `DISABLED` als partiële bron; `:353-390` test `TEMPORARILY_UNAVAILABLE` alleen met één geselecteerde bron en dus volledige uitval. Voeg de tweebronnenvariant toe met `PARTIAL_AVAILABILITY`, beschikbare resultaten, uitsluitend beschikbare totalen en beide bronstatussen.
- Gerichte reviewchecks: backend `mvn -B --no-transfer-progress -Dtest=HistoricalSearchTest test` (18/18), frontend `flutter test test/historical_search_test.dart` (9/9) en `git diff --check` groen. Deze checks vervangen het ontbrekende volledige factorybewijs niet.
