# hkh-84 — Documentatie-worklog

## Stappenplan

[x]: `.task.md`, factory-documentatie, worklog en story-diff gelezen
[x]: relevante documentatie op volledigheid en feitelijke juistheid gecontroleerd
[x]: functionele spec, factory-developmentdocs en documenter-worklog bijgewerkt
[x]: documentatiediff en wijzigingsscope gecontroleerd

## Uitgevoerd

- De functionele spec beschrijft nu het brononafhankelijke historische metadata-contract,
  inclusief verplichte velden, afgeleide statussen, fail-closed minimale uitkomst, privacyredactie,
  gescheiden metadata-/objectrechten, versiegedrag, rate limiting en de expliciete scopegrenzen.
- `docs/factory/development.md` beschrijft de nieuwe contract- en adaptercapaciteit naast de bestaande
  `externalverification`-module. De Engelse developmenthandleiding is aangescherpt zodat de
  procesbrede limiter en de minimale tussenruimte overeenkomen met de implementatie.
- `docs/factory/technical-spec.md` en de bestaande story-/developer-worklogs bevatten de concrete
  technische details al en zijn daarom niet dubbel aangepast.
- Er zijn geen productiecode-, test-, infra- of PR-bestanden gewijzigd. Er zijn geen secrets verwerkt.

## Controle

- `git diff --check` is schoon.
- De gewijzigde paden zijn uitsluitend documentatie onder `docs/`.
- Een volledige build/test-run was voor deze documentatie-only subtaak niet nodig; de story-worklogs
  bevatten het bestaande groene factory-vangnet voor de implementatie.
