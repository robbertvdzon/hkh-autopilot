# hkh-159 - Worklog

Story-context bij pickup:
Open Archieven-budget, cache en deduplicatie implementeren

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: address review findings
[x]: run full factory safety net
[x]: update story-log with results

Done / rationale:
- `.task.md`, de developer-instructies, `development.md`, `technical-spec.md` en de bestaande
  historicalsearch-code/tests zijn gelezen; er waren geen conflictmarkers of andere lokale
  wijzigingen.
- De Open Archieven-route gebruikt een proceslokaal per-IP budget met burst 10 en maximaal 60
  aanvragen per rolling minuut. Het IP komt alleen uit `X-Forwarded-For` bij een geconfigureerde
  vertrouwde directe proxy; anders blijft het directe connection-IP leidend.
- Geldige genormaliseerde Open Archieven-pagina's worden tijdelijk en begrensd gecachet op een
  SHA-256-cachekey met bron, volledige genormaliseerde context, offset, limiet en vaste taal `nl`.
  Gelijktijdige misses delen één externe aanvraag via single-flight. Cachekeys bewaren geen vrije
  zoekwaarden.
- Upstream HTTP 429 is toegevoegd als veilige bronstatus `RATE_LIMITED`; maximaal één retry gebruikt
  alleen een bruikbare `Retry-After` van ten hoogste twee seconden. Iedere daadwerkelijke poging houdt
  de bestaande allowlisted logging aan; een lokale budgetoverschrijding geeft vaste HTTP 429 terug.
- `OpenArchievenProtectionTest` dekt concurrency, TTL/negatieve cache, keyvariaties, budget/IP-
  isolatie, proxyvertrouwen, retry en veilige statusmapping.
- Verificatie groen: `mvn -B --no-transfer-progress clean verify` (330 tests), `flutter analyze`,
  `flutter test` (72 tests), `flutter build web`, `frontend-admin/flutter analyze` en
  `frontend-admin/flutter test` (36 tests); alle commando's exitcode 0 zonder failures/errors.

Reviewopvolging:
- Cachecontextvelden worden nu injectief, lengtegeprefixt en nullable geserialiseerd vóór SHA-256;
  een regressietest controleert expliciet dat scheidingstekens in zoekvelden geen key-collision geven.
- Eerste pagina's én vervolgpagina's van de historische service vangen onverwachte adapterexceptions
  veilig als `TEMPORARILY_UNAVAILABLE`; alleen budgetoverschrijding blijft expliciet propagëren naar
  HTTP 429. Beide paden hebben regressietests.
- De proxytrust ondersteunt expliciete CIDR-ranges en de productie-manifests configureren de
  OpenShift-pod-CIDR `10.128.0.0/14` via een versioned ConfigMap. De forwarded header blijft voor
  peers buiten die range genegeerd.

Review:
- [opgelost] `OpenArchievenSearchAdapter.cacheKeyFor` bouwde de digest-input met ongescapete
  `|`-scheidingstekens. Daardoor kunnen verschillende geldige zoekcontexten dezelfde cachekey
  krijgen, bijvoorbeeld `text="x|place=y|person=<null>"` met lege `place` versus `text="x"` en
  `place="y|person=<null>|place=<null>"`. De provider-aanvragen verschillen dan wel, maar de
  tweede kan de genormaliseerde uitkomst van de eerste terugkrijgen; serialiseer veldwaarden
  injectief vóór het hashen en voeg een regressietest toe.
- [opgelost] `HistoricalSearchService` had de bestaande `runCatching` rond zowel de eerste als
  vervolgpagina verwijderd en vangt nu alleen `HistoricalSearchRequestBudgetExceededException`.
  Een andere adapter-/transportexception ontsnapt daardoor als HTTP 500 in plaats van als veilige
  bronuitval/partiële beschikbaarheid. Behoud de expliciete propagatie van budgetoverschrijding,
  maar herstel de bestaande veilige fallback voor overige exceptions op beide paden.
- [opgelost] De productie-deployment configureerde geen `HKH_HISTORICAL_TRUSTED_PROXY_ADDRESSES`:
  de backend krijgt alleen het bestaande `hkh-runtime`-secret, de sleutel staat niet in
  `deploy/secrets-cluster.env.example`/het sealed secret, terwijl de frontend-nginx wel
  `X-Forwarded-For` naar de backend doorstuurt. In de huidige route wordt dus zonder expliciete
  trustcontext het proxy/connection-IP als gedeelde bucket gebruikt in plaats van het gebruikers-IP.
  Voeg een deployment-veilige trustconfiguratie (passend bij de werkelijke proxy-adressen/-ranges)
  en de bijbehorende documentatie toe, zonder forwarded headers algemeen te vertrouwen.
- Gerichte controle na herstel: `mvn -B --no-transfer-progress -Dtest=HistoricalSearchTest,OpenArchievenProtectionTest test`
  slaagde met 52 tests, 0 failures en 0 errors. Het volledige vangnet slaagde daarna met 330
  backendtests, 72 frontendtests en 36 frontend-admin-tests, naast de drie analyse/buildstappen,
  allemaal exitcode 0.

Vervolgreview:
- [blocker] De nieuwe `HKH_HISTORICAL_TRUSTED_PROXY_ADDRESSES=10.128.0.0/14` vertrouwt het volledige
  OpenShift-podnetwerk, niet uitsluitend de frontend-proxy. De publieke `backend`-Route blijft
  bestaan en OpenShift-router-/andere pod-peers vallen daardoor binnen dezelfde trust-range; een
  aangeleverde `X-Forwarded-For` kan de budgetbucket alsnog naar een gekozen IP verplaatsen.
  Beperk de trustcontext tot de werkelijke proxy-peers of sluit de directe backend-Route af en
  zorg dat de proxy de forwarded-header overschrijft/sanitiseert.
- De eerdere cachekey- en exceptionbevindingen zijn gecontroleerd als opgelost. Het gerichte backend-
  controlevangnet slaagde opnieuw met 52 tests; `git diff --check` en de OpenShift-kustomize-render
  slaagden eveneens.

Huidige developer-run:
- Het backend-Route-manifest is uit de gedeployde base verwijderd; de webclients gebruiken
  same-origin proxying zodat verkeer niet om de frontend/adminproxy heen kan.
- `backend-ingress` beperkt backendverkeer tot de frontend- en adminproxy-pods. Beide nginx-configs
  zetten de door de OpenShift-router aangeleverde laatste forwarded-hop om naar één waarde voordat
  die naar de backend gaat.
- Documentatie en de productie-buildconfiguratie zijn hiermee in overeenstemming gebracht.
- Eindcontrole groen: volledig vangnet met Maven 333 tests, frontend 72 tests en frontend-admin 36
  tests, plus beide analyzes en de frontend-webbuild; alle commando's exitcode 0 zonder failures of
  errors. De OpenShift-overlay is gerenderd en `git diff --check` is groen.
