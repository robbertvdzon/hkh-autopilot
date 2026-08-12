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
