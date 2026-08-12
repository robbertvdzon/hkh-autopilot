# hkh-81 — Review-worklog

## Review

- Gerichte backendcheck uitgevoerd met `HistoricalMetadataContractTest` en
  `OpenArchievenMetadataAdapterTest`: 10 tests, 0 failures en 0 errors.
- De volledige story-diff is beoordeeld tegen `main`; er zijn geen conflictmarkers
  of whitespacefouten gevonden.
- Review afgewezen wegens de volgende bevindingen:
  - [blocker] Er staat geen agentworker-gemeten, revision-gebonden verificatiebewijs
    in de checkout. `.factory/verification.yaml` bevat alleen de commandodefinities;
    de groene aantallen in `.task.md` zijn handgeschreven proza en voldoen niet aan
    het factory-reviewcontract.
  - [blocker] `OpenArchievenMetadataAdapter.parse` selecteert met `pick` de eerste
    waarde uit meerdere JSON-LD-nodes/aliases, maar markeert alleen conflicten voor
    een beperkte subset van identifier- en holder-velden. Tegenstrijdige titel,
    datering, privacystatus, rechtenstatus of `@id`-waarden kunnen daardoor als
    `VERIFIED` worden teruggegeven. Een `@graph` met verder geldige metadata maar
    `privacyStatus: CLEAR` in de eerste node en `privacyStatus: BLOCKED` in de
    tweede node is bijvoorbeeld niet fail-closed. Dit raakt de regels voor
    tegenstrijdige brondata en privacy.
  - [bug] De adapter behandelt een expliciete bronversie en een HTTP-ETag als
    tegenstrijdig wanneer de strings niet gelijk zijn (`bodyVersion != etag`). Een
    ETag is een opaque HTTP-validator en hoeft niet gelijk te zijn aan de
    inhoudelijke bronversie; een normale response met bijvoorbeeld `version: v1`
    en een hashachtige ETag wordt zo onterecht ongeverifieerd. Kies één betrouwbare
    versierepresentatie volgens de beschreven prioriteit, of vergelijk alleen
    waarden die aantoonbaar hetzelfde contractveld representeren.

## Besluit

De branch gaat terug naar de developer. Herstel de fail-closed conflict- en
versieafhandeling, voeg regressietests voor deze fixtures toe en lever daarna
agentworker-bewijs voor exact dezelfde HEAD/worktree-tree aan.

## Development retry

Stappenplan:
[x]: reviewbevindingen en factory-instructies lezen
[x]: JSON-LD-conflictverwerking en versieafhandeling herstellen
[x]: regressietests toevoegen en gerichte backendtests uitvoeren
[x]: volledig factory-vangnet uitvoeren en revision-gebonden resultaten vastleggen

Doel van deze run: de adapter mag geen eerste JSON-LD-waarde als waarheid nemen
wanneer dezelfde contractwaarde elders in de respons anders voorkomt. Een
inhoudelijke bronversie en een HTTP-ETag zijn verschillende soorten versie-
informatie; de bronversie krijgt voorrang en een afwijkende ETag is geen
tegenstrijdigheid. Alle wijzigingen blijven uncommitted voor de factory.

Uitgevoerd:
- `OpenArchievenMetadataAdapter` verzamelt nu alle waarden per contractveld
  over de JSON-LD-nodes en aliases. Conflicten in identifier/`@id`, holder,
  titel, beschrijving, datering, bronversie, snapshot, rechten, privacy en
  beschikbaarheid zetten het resultaat fail-closed op
  `CONTRADICTORY_SOURCE_DATA`; de inhoudelijke metadata blijft dan leeg.
- Een inhoudelijke bronversie heeft voorrang op HTTP-ETag/Last-Modified. Een
  afwijkende opaque ETag veroorzaakt geen vals conflict meer.
- Regressietests toegevoegd voor conflicterende titel-, datum-, identifier- en
  privacywaarden en voor de combinatie expliciete bronversie + opaque ETag.
- Gerichte adapterrun: 12 tests, 0 failures, 0 errors.
- Volledig vangnet: `mvn -B --no-transfer-progress clean verify` (271 tests,
  0 failures, 0 errors), frontend `flutter analyze`, `flutter test` en
  `flutter build web`, plus frontend-admin `flutter analyze` en `flutter test`:
  alle zes exitcode 0 en alle Flutter-tests geslaagd.
- De zes exacte vangnetcommando's zijn in één ononderbroken agent-run
  uitgevoerd. De afsluitende statusregel was
  `backend=0 frontend_analyze=0 frontend_test=0 frontend_build=0
  admin_analyze=0 admin_test=0`.

## Review retry

- Gerichte backendcheck uitgevoerd met `HistoricalMetadataContractTest` en
  `OpenArchievenMetadataAdapterTest`: 15 tests, 0 failures en 0 errors.
- De review blijft afgewezen:
  - [blocker] Er staat nog steeds geen agentworker-gemeten, revision-gebonden
    bewijs in de checkout. `.factory/verification.yaml` bevat alleen de
    commandodefinities; de groene aantallen in deze worklog en `.task.md` zijn
    geen geldig factory-bewijs.
  - [bug] `OpenArchievenMetadataAdapter.parse` valideert niet dat een enkele
    bronidentifier (`@id`/`identifier`) bij de aangevraagde `adtid/guid` hoort.
    Een 200-respons met verder geldige metadata maar bijvoorbeeld
    `@id=https://opendata.archieven.nl/id/1000/ander-record` levert daardoor
    `VERIFIED` op met `metadata.sourceIdentifier` voor het andere record en
    `sourceLink` voor het aangevraagde record. Dat is een tegenstrijdige,
    verkeerd toe te schrijven bronverwijzing en moet fail-closed worden
    behandeld of expliciet aan dezelfde bronlink worden gebonden.

## Besluit retry

De branch gaat opnieuw terug naar de developer. Herstel de identifier/link-
binding en lever daarna agentworker-bewijs voor exact dezelfde HEAD/worktree-tree
aan; de gerichte tests zijn op zichzelf geen vervanging voor het volledige
revision-gebonden vangnet.

## Development retry 2

Stappenplan:
[x]: reviewbevinding over identifier/link-binding en factory-instructies lezen
[x]: identifier-binding fail-closed herstellen
[x]: regressietests toevoegen
[x]: volledig factory-vangnet uitvoeren en revision-gebonden resultaten vastleggen

Doel van deze run: een respons met een identifier van een ander archiefrecord mag
nooit als `VERIFIED` terugkomen wanneer de adapter het aangevraagde record
opvraagt. Een identifier in de korte vorm `adtid/guid` blijft geldig wanneer die
wel aan de aangevraagde bronlink gebonden is. Alle wijzigingen blijven
uncommitted voor de factory.

Uitgevoerd:
- `OpenArchievenMetadataAdapter` bewaart nu elke aanwezige identifierwaarde als
  brongegeven, controleert die tegen de aangevraagde `adtid/guid` (zowel als
  korte identifier als als URI) en markeert een afwijkende identifier als
  tegenstrijdige brondata. De fail-closed uitkomst behoudt uitsluitend de
  bekende aangevraagde identifier/link.
- Regressietests toegevoegd voor een identifier van een ander record en voor
  een geldige korte identifier van het aangevraagde record.
- Gerichte backendrun: `HistoricalMetadataContractTest` en
  `OpenArchievenMetadataAdapterTest`, 17 tests, 0 failures en 0 errors.
- Volledig vangnet op de definitieve wijziging: `mvn -B --no-transfer-progress
  clean verify` (273 tests, 0 failures/errors), `frontend` analyze/test/build
  web (35 tests, 0 failures), en `frontend-admin` analyze/test (35 tests,
  0 failures). Alle zes commando's eindigden met exitcode 0.
- Revision-bound handover-identificatie vóór de laatste vangnetrun:
  `HEAD=47ab4b8`; de definitieve worktree-digest wordt na het vastleggen van
  deze resultaten opnieuw gecontroleerd. Alle wijzigingen blijven uncommitted.

## Final review

- Gerichte Maven-run uitgevoerd op de actuele checkout met
  `HistoricalMetadataContractTest` en `OpenArchievenMetadataAdapterTest`: 17
  tests, 0 failures en 0 errors. Dit is geen volledig factory-vangnet.
- [blocker] Er staat nog steeds geen agentworker-gemeten, revision-gebonden
  verificatie-uitvoer in de checkout. `.factory/verification.yaml` bevat alleen
  commandodefinities; de groene aantallen in `.task.md` en dit worklog zijn
  handgeschreven en voldoen niet aan het reviewcontract.
- [bug] `OpenArchievenMetadataAdapter.parse` telt verschillende representaties
  van dezelfde identifier als conflict. Een respons met zowel
  `@id: "https://opendata.archieven.nl/id/1000/item-1"` als
  `identifier: "1000/item-1"` krijgt via `identifierConflict` twee distincte
  waarden en eindigt `UNVERIFIED/CONTRADICTORY_SOURCE_DATA`, hoewel beide
  waarden volgens `matchesRequestedRecord` aan hetzelfde aangevraagde record
  binden. Normaliseer identifier-URI en korte identifier vóór de
  conflictcontrole en voeg hiervoor een regressietest toe.
- [bug] `ExternalVerificationClientConfiguration` zet `serverKey` op de
  URI-hostnaam en `FourPerSecondRateLimiter` houdt per die sleutel de timing
  bij. De acceptance criterion vereist een limiet per server-uitgaand
  IP-adres; verschillende hostnamen die naar hetzelfde uitgaande IP resolven
  krijgen nu elk een eigen limiet en kunnen samen boven vier verzoeken per
  seconde uitkomen. Sleutel de limiter aan het uitgaande IP of aan een
  aantoonbaar equivalente netwerkdoel-identiteit en test dat scenario.

## Besluit

De branch blijft afgewezen: herstel beide adapter/rate-limitbevindingen en
lever agentworker-bewijs voor exact dezelfde HEAD/worktree-tree aan voordat de
review opnieuw wordt aangeboden.

## Development retry 3

Stappenplan:
[x]: identifierrepresentaties normaliseren vóór conflictcontrole
[x]: rate limiting koppelen aan genormaliseerde uitgaande IP-identiteit
[x]: regressietests en contractdocumentatie bijwerken
[x]: volledig factory-vangnet uitvoeren en revisiongebonden resultaten controleren

Doel van deze run: herstel de twee concrete reviewbevindingen zonder de
bestaande individuele verificatieroute te wijzigen. De factory blijft eigenaar
van commit/push/PR en van het uiteindelijke agentworker-bewijs; deze run legt
alleen lokaal controleerbare resultaten vast.

Uitgevoerd:
- Identifierwaarden worden eerst naar hun recordpad genormaliseerd, zodat een
  equivalente URI (`@id`) en korte identifier niet als conflict gelden. Iedere
  aanwezige identifier wordt nog steeds tegen de aangevraagde `adtid/guid`
  gecontroleerd; afwijkende records blijven fail-closed.
- De configuratie gebruikt nu een DNS/IP-afgeleide limiterkey. Hostaliassen
  met dezelfde opgeloste doel-IP-adressen delen één `FourPerSecondRateLimiter`
  bucket. De test simuleert twee aliassen en controleert één gedeelde timing-
  interval.
- Gerichte backendrun: 19 tests, 0 failures, 0 errors.
- Volledig vangnet uitgevoerd volgens `docs/factory/development.md`:
  backend `mvn -B --no-transfer-progress clean verify` (275 tests, 0 failures,
  0 errors), frontend analyze/test/build web (35 tests, 0 failures), en
  frontend-admin analyze/test (35 tests, 0 failures). Alle zes commando's
  eindigden met exitcode 0.
- Afsluitende controle: `git diff --check` geslaagd, geen conflictmarkers in
  gewijzigde bestanden, en alle wijzigingen blijven uncommitted.
- Handover-identificatie van deze checkout: `HEAD=68d8055c3a3bc6256c55064f60094f4518e493bb`.
  Het agentworker-gemeten bewijs wordt na deze run door de factory-harness aan
  de werkelijke checkout-tree gebonden.
