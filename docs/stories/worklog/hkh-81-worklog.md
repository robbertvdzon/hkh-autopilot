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
