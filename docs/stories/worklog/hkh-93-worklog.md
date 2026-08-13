# hkh-93 - Worklog

Story-context bij eerste pickup:
Backend en frontend bronstatussen implementeren

Stappenplan:
[x]: issue, factory-docs en bestaande historische zoekroute gelezen
[x]: backendcontract, adapters, service en controller aanpassen
[x]: Flutter-modellen en HistoricalSearchPage aanpassen
[x]: backend- en frontendtests schrijven/bijwerken
[x]: volledig vangnet draaien en resultaten vastleggen

Nieuwe developer-run (2026-08-12):
- De reviewbevinding over terugpaginering bij korte pagina's is bevestigd in de bestaande
  widgettest: een antwoord op `start=100` met één resultaat vroeg offset `99` op.
- Deze run corrigeert de terugpaginering naar de servergeleverde `limit` en breidt de testnaam/
  verwachting aan zodat de regressie expliciet wordt vastgelegd.

Resultaat huidige run:
- Gerichte frontendregressie: `flutter test --concurrency=1 --reporter expanded
  test/historical_search_test.dart` — 10 tests, 0 failures, 0 errors.
- Volledig vangnet: backend `mvn -B --no-transfer-progress clean verify` — 298 tests; frontend
  analyze, 46 tests en webbuild; frontend-admin analyze en 35 tests. Alle zes commando's
  eindigden met exitcode 0, zonder failures of errors.
- `.factory/verification.yaml` is niet gewijzigd: de bestaande stabiele commandoset dekt deze
  wijziging en revision-/tree-gebonden bewijs wordt door de factory-harness gegenereerd.

Nieuwe developer-run (2026-08-12):
- De actuele branch en de leidende review-/factory-instructies opnieuw gecontroleerd.
- De bestaande implementatie van backend, frontend en tests blijft de scope van deze run;
  ik valideer de actuele paginerings- en bronstatussemantiek opnieuw en voeg alleen een
  regressie toe wanneer de code/testdekking daar aanleiding toe geeft.

Resultaat huidige run:
- Gerichte regressies: backend `HistoricalSearchTest` 20/20 en frontend
  `historical_search_test.dart` 10/10.
- Volledig vangnet: backend `mvn -B --no-transfer-progress clean verify` met 298 tests,
  frontend analyze, test (46 tests) en webbuild, frontend-admin analyze en test (36 tests);
  alle commando's eindigden met exitcode 0, zonder failures of errors.
- De factory-configuratie is ongewijzigd en bevat de stabiele zes vangnetcommando's; het
  revisiongebonden agentworker-bewijs wordt door de factory-harness na deze run gegenereerd.

Voortgang:
- Nieuwe developer-run gestart voor het resterende reviewblocker: paginering wordt bij
  vervolguitval opnieuw gemerged over alleen de nog beschikbare bronstromen; de regressietest
  controleert dat de tweede beschikbare bronpagina bereikbaar blijft. Het volledige vangnet wordt
  na deze wijziging opnieuw uitgevoerd.
- De merge geeft bij vervolguitval de effectieve beschikbare `start` terug en rebased over de
  genormaliseerde resultaten van de uitgevallen bron. Daardoor blijven beschikbare vervolgpagina's
  bereikbaar en blijven `start`, `total` en de geretourneerde resultaten consistent.
- De backendregressietest controleert dat de tweede Open Archieven-pagina na Europeana-uitval
  zichtbaar blijft met `start=100`, `total=200` en de juiste bronstatussen. De frontendtest
  controleert dat terugpagineren de effectieve server-start gebruikt.
- Het volledige vangnet is voor deze run groen uitgevoerd: backend `clean verify` (298 tests),
  frontend analyze, 46 tests en webbuild, frontend-admin analyze en 35 tests; alle commando's
  eindigden met exitcode 0 zonder failures of errors.
- Worklog aangemaakt aan het begin van de developer-run; wijzigingen blijven uncommitted voor de factory.
- Reviewherstel afgerond: de merge-uitkomst wordt na vervolguitval begrensd door het definitieve beschikbare `total`, zodat `start >= total` geen resultaten buiten het contract kan tonen.
- De reviewregressies zijn toegevoegd: een tweebronnen-test voor tijdelijke uitval met partiële resultaten en een paginerings-test voor `start >= total`.
- Het API-contract heeft `state` met `RESULTS`, `NO_RESULTS`, `PARTIAL_AVAILABILITY` en `SOURCE_FAILURE` gekregen.
- De service telt alleen nog beschikbare bronnen mee, markeert uitval tijdens paginering en schermt bronmeldingen af tegen ruwe providerinformatie.
- De frontend toont partial results met bronmeldingen en gebruikt bij volledige uitval uitsluitend de bronprobleemstatus met retry.
- Backend- en widgettests dekken de nieuwe toestanden, totalen, pagineringsuitval en semantische statusweergave.
- Volledig vangnet groen: backend 298 tests; frontend analyze, 46 tests en webbuild; frontend-admin analyze en 36 tests.

Review (2026-08-12):
- [blocker] Er is geen agentworker-gemeten, revisiongebonden bewijs voor exact deze HEAD/worktree-tree aanwezig. `.factory/verification.yaml` bevat alleen de commandodefinitie en bovenstaande claim is handgeschreven; de vereiste volledige vangnetresultaten (296 backendtests, frontend/admin analyze/tests en webbuild) kunnen daarom niet als groen bewijs worden geaccepteerd.
- [blocker] De paginering kan na bronuitval een inconsistent resultaat opleveren. In `backend/src/main/kotlin/nl/vdzon/hkh/historicalsearch/HistoricalSearchService.kt:60-84,105-109,111-129` worden bij een vervolgfout de resultaten van de falende bron uit `total` verwijderd, maar blijft `start` gebaseerd op de eerdere round-robin-stream. Reproduceer met twee beschikbare bronnen die elk 100 resultaten op pagina 0 geven, laat bron A op `start=100` falen en bron B op die pagina slagen, en vraag daarna `start=200&limit=100`: de merge kan B-resultaten teruggeven terwijl `total` alleen B's oorspronkelijke totaal bevat (en dus `start >= total`). Corrigeer de cursor-/offsetsemantiek en voeg een contracttest toe voor dit scenario.
- [blocker] De vereiste backendcontracttest voor één tijdelijk onbeschikbare bron mét gedeeltelijke resultaten ontbreekt. `backend/src/test/kotlin/nl/vdzon/hkh/historicalsearch/HistoricalSearchTest.kt:236-254` test alleen `DISABLED` als partiële bron; `:353-390` test `TEMPORARILY_UNAVAILABLE` alleen met één geselecteerde bron en dus volledige uitval. Voeg de tweebronnenvariant toe met `PARTIAL_AVAILABILITY`, beschikbare resultaten, uitsluitend beschikbare totalen en beide bronstatussen.
- Gerichte reviewchecks: backend `mvn -B --no-transfer-progress -Dtest=HistoricalSearchTest test` (18/18), frontend `flutter test test/historical_search_test.dart` (9/9) en `git diff --check` groen. Deze checks vervangen het ontbrekende volledige factorybewijs niet.

Reviewherstel (2026-08-12):
- De vervolguitval-regressie is opgelost door de geretourneerde pagina na de merge te begrenzen op het definitieve beschikbare `total`; bij `start >= total` worden geen resultaten buiten het contract teruggegeven.
- De verplichte tweebronnenvariant voor `TEMPORARILY_UNAVAILABLE` met `PARTIAL_AVAILABILITY`, beschikbare resultaten, beschikbare totalen en beide bronstatussen is toegevoegd.
- Het volledige vangnet is opnieuw uitgevoerd op deze worktree: backend 298 tests, frontend analyze/46 tests/webbuild en frontend-admin analyze/36 tests, allemaal groen.

Eindreview (2026-08-12):
- [blocker] `.factory/verification.yaml` bevat alleen de commandodefinitie; er is geen agentworker-gemeten, revisiongebonden resultaat voor de exacte `HEAD`/worktree-tree. De handgeschreven claim dat backend, frontend en frontend-admin groen zijn, is volgens de factory-regels geen geldig volledig vangnetbewijs.
- [blocker] De pagineringsfix voorkomt alleen output buiten het definitieve `total`, maar kan beschikbare resultaten onbereikbaar maken. Reproduceer met twee bronnen die op `start=0` elk 100 resultaten en `total=200` leveren, laat Europeana falen op `start=100` en Open Archieven slagen met de tweede pagina, en vraag `start=200&limit=100`: de merge leest de tweede Open-Archievenpagina, verlaagt `total` naar 200 doordat Europeana geen bijdrage meer levert, en `take((total-start).coerceAtLeast(0))` op `HistoricalSearchService.kt:45-49` gooit de zojuist opgehaalde beschikbare resultaten weg. De client heeft daarna op basis van dat `total` geen volgende offset meer om die resultaten op te vragen. Corrigeer de cursor-/offsetsemantiek zodat een bronuitval tijdens paginering geen gaten in de beschikbare bronstroom veroorzaakt, en dek dit af met een contracttest die de beschikbare tweede pagina controleert.
- Gerichte reviewchecks: `mvn -B --no-transfer-progress -Dtest=HistoricalSearchTest test` (20/20), `flutter test test/historical_search_test.dart` (9/9) en `git diff --check` zijn groen; dit vervangt het ontbrekende volledige factorybewijs niet.

Review huidige HEAD (2026-08-12):
- [blocker] `.factory/verification.yaml` bevat nog steeds alleen de zes commandodefinities en geen agentworker-gemeten, revisiongebonden evidence voor exact deze HEAD en worktree-tree. De handgeschreven claims in de issue-comments/worklog zijn daarom geen geldig groen bewijs. Voeg voor elk geverifieerd commando evidence toe met revision/tree-binding; tot die tijd kan deze review niet akkoord zijn.
- Gerichte checks: backend `HistoricalSearchTest` 20/20, frontend `historical_search_test.dart` 11/11 en `git diff --check` groen. Het volledige vangnet is in deze review niet opnieuw uitgevoerd, conform de reviewer-instructie, en deze checks vervangen het ontbrekende factorybewijs niet.

Nieuwe developer-run (2026-08-12):
- De reviewcontext en de actuele checkout opnieuw gecontroleerd; er zijn geen conflictmarkers en
  geen onbedoelde wijzigingen in de working tree.
- De bestaande implementatie en regressietests worden opnieuw inhoudelijk gevalideerd. Eventuele
  aanvullende correcties worden samen met gerichte tests uitgevoerd; daarna draait het volledige
  vangnet uit `docs/factory/development.md` volledig uit.

Resultaat nieuwe developer-run:
- Gerichte regressies: backend `HistoricalSearchTest` 20/20 en frontend
  `historical_search_test.dart` 10/10, beide zonder failures of errors.
- Volledig vangnet: backend `mvn -B --no-transfer-progress clean verify` met 298 tests,
  frontend analyze, 46 tests en webbuild, frontend-admin analyze en 35 tests; alle zes
  commando's eindigden met exitcode 0, zonder failures of errors.
- De implementatie en tests waren al aanwezig op de checkout; deze run voegde alleen de actuele
  developer-run aan dit worklog toe. `.factory/verification.yaml` is bewust niet vervalst of
  uitgebreid met handgeschreven evidence; revisiongebonden agentworker-bewijs wordt door de
  factory-harness aan de exacte HEAD/worktree gebonden.

Review huidige run (2026-08-12):
- [blocker] `.factory/verification.yaml` bevat nog steeds uitsluitend de zes commandodefinities en
  geen agentworker-gemeten, revision-/tree-gebonden resultaten voor deze exacte `HEAD`. De claims
  in dit worklog en de issue-comments zijn handgeschreven en tellen volgens de reviewer-regels niet
  als volledig groen vangnetbewijs. Voeg per commando machinegemeten evidence toe die aan dezelfde
  revisie en worktree-tree is gebonden.
- [bug] De frontend berekent de vorige pagina met `response.start - response.results.length` in
  `frontend/lib/historical/historical_search.dart:359-363`. Bij een geldige pagina met minder dan
  100 resultaten (bijvoorbeeld `start=100`, `limit=100`, één resultaat door bronuitval of
  eindpaginering) wordt dan `start=99` gevraagd in plaats van de vorige pagina-offset `0`. Dat
  veroorzaakt overlap/duplicaten en kan eerder beschikbare resultaten onbereikbaar maken. Gebruik
  de serverlimiet/paginacursor voor terugnavigatie en voeg een widgettest met een korte pagina toe.
- Gerichte reviewchecks: backend `mvn -B --no-transfer-progress -Dtest=HistoricalSearchTest test`
  (20/20), frontend `flutter test test/historical_search_test.dart` (10/10) en `git diff --check`
  zijn groen. Deze checks vervangen het ontbrekende volledige factorybewijs niet.

Review actuele checkout (2026-08-12):
- [blocker] `.factory/verification.yaml` bevat uitsluitend de zes commandodefinities; er staat geen
  agentworker-gemeten evidence met revision- en worktree-tree-binding voor `HEAD=4c935f9`. De
  handgeschreven claims in issue-comments en dit worklog zijn volgens de reviewer-regels geen geldig
  groen volledig vangnetbewijs. Voeg per relevant commando machinegemeten bewijs voor exact deze
  checkout toe voordat de story kan worden goedgekeurd.
- Gerichte reviewchecks: backend `mvn -B --no-transfer-progress -Dtest=HistoricalSearchTest test`
  (20/20), frontend `flutter test --concurrency=1 --reporter expanded
  test/historical_search_test.dart` (10/10) en `git diff --check` zijn groen. Het volledige
  vangnet is niet opnieuw uitgevoerd, conform de reviewer-instructie, en deze checks vervangen het
  ontbrekende revisiongebonden factorybewijs niet.
