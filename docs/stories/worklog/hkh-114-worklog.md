# hkh-114 — Documentatie-worklog

## Stappenplan

[x]: `.task.md`, factory-documentatie, story-worklog en story-diff gelezen
[x]: relevante documentatie op volledigheid en feitelijke juistheid gecontroleerd
[x]: README's, frontend-handleiding, developmentdocs en factory-specs bijgewerkt
[x]: story-samenvatting en documentatiediff gecontroleerd

## Uitgevoerd

- De algemene [README](../../../README.md) en [frontend-handleiding](../../../frontend/README.md) beschrijven
  nu de nieuwe vervolgzoekingang voor zekere plaats-, persoons-, gebeurtenis- en periodemetadata,
  inclusief exacte waarden, standaardbronkeuze, waarschuwing, terugnavigatie en opslaggrenzen.
- `docs/development.md` en `docs/factory/development.md` zijn aangevuld met de implementatiegrenzen
  van `HistoricalFollowUpAction`, de fail-closed gating en het automatische hergebruik van de
  bestaande `HistoricalSearchSource`.
- `docs/factory/functional-spec.md` en `docs/factory/technical-spec.md` beschrijven de functionele
  contracten, semantische buttons, geldige jaarperiode, statusmapping, waarschuwing en testdekking.
- De storydocumentatie is bijgewerkt zodat zij niet meer meldt dat documentatie nog ontbreekt.
- Er zijn geen productiecode-, test-, infra- of PR-bestanden gewijzigd en er zijn geen secrets verwerkt.

## Controle

- `git diff --check` is schoon.
- De wijzigingsscope bestaat uitsluitend uit documentatiebestanden.
- De story-worklog bevat het bestaande groene factory-vangnet; voor deze documentatie-only taak is
  geen nieuwe build- of testrun nodig.
