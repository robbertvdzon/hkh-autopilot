# hkh-90 - Worklog

Story-context bij eerste pickup:
Werk documentatie bij voor de publieke historische zoekroute.

Stappenplan:
[x]: story, factory-docs, diff en bestaande worklogs gelezen
[x]: relevante README's, development-, functional-, technical-, UX- en configuratiedocumentatie bijgewerkt
[x]: parent-worklog en dit document bijgewerkt
[x]: documentatiediff gecontroleerd; geen productiecode, tests of infra gewijzigd

Done / rationale:
- `README.md`, `docs/development.md`, `docs/deployment.md`, `docs/factory/development.md`,
  `docs/factory/functional-spec.md`, `docs/factory/technical-spec.md` en
  `docs/factory/secrets-local.md` beschrijven nu de gerealiseerde `historicalsearch`-module,
  `GET /api/historical-search`, de twee bronadapters, validatie, cursorpaginering, fail-closed
  metadata/statusmapping, rate limiting en de Europeana/Open Archieven-runtimeconfiguratie.
- `frontend/README.md` documenteert de homepage-ingang, zoekfilters, bronstatussen en
  toegankelijkheids-/privacygrenzen van `HistoricalSearchPage`.
- De documentatie vermeldt expliciet dat `HKH_EUROPEANA_WSKEY` server-side blijft en dat ontbrekende
  configuratie alleen Europeana uitschakelt; Open Archieven blijft onafhankelijk beschikbaar.
- Alleen Markdown-documentatie is gewijzigd. Er is geen productiecode, testbestand of deployment-
  implementatie aangepast.
