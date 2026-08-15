# hkh-186 - Worklog

Story-context:
Werk documentatie bij voor hkh-autopilot-30.

Stappenplan:
[x]: actuele story, factory-documentatie en implementatiediff gelezen
[x]: relevante README's, ontwikkelhandleidingen en factory-specificaties bijgewerkt
[x]: API-contract, bronsemantiek en neutrale frontendweergave vastgelegd
[x]: diff gecontroleerd op uitsluitend documentatiewijzigingen

Done / rationale:
- De documentatie beschrijft nu `querySemantics` per bron als nullable lijst van daadwerkelijk
  gebruikte semantische Open Archieven-providerparameters.
- Technische parameters en afleidingen uit zoekterm, resultaatmetadata, titel of URL zijn expliciet
  uitgesloten van de zichtbare interpretatie.
- De Nederlandse labels voor naam- en plaatssemantiek en de vaste neutrale melding bij ontbrekende
  of onbetrouwbare semantiek zijn vastgelegd.
- Bijgewerkt: root-README, `frontend/README.md`, `docs/development.md`,
  `docs/factory/development.md`, `docs/factory/functional-spec.md` en
  `docs/factory/technical-spec.md`.
- Deployment-, secrets- en runtime-documentatie hoefden niet te wijzigen; de story voegt geen
  configuratie, opslag of deploymentgedrag toe.
