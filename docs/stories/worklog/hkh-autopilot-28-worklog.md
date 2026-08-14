# hkh-autopilot-28 - Worklog

Story-context bij eerste pickup:
Publieke Open Archieven-resultaatkaart koppelen aan het contract

Pas de historische Flutter-weergave en contractmapping aan rond frontend/lib/historical/historical_search.dart, met external_link_launcher_web.dart als veiligheidsgrens. Valideer voor Open Archieven source_name, stabiele identifier en absolute HTTP(S)-original_source_url; ongeldige resultaten krijgen geen kaart of link. Toon bron-, inhouds-, rechten-, privacy- en ophaalmetadata uitsluitend volgens het bestaande contract, met Onbekend voor ontbrekende of niet-herkende statussen en zonder afgeleide inhoud. Behoud bestaande bronstatussen, gedeeltelijke resultaten, nulresultaten en retrycontext. Voeg alle benodigde fixtures en Flutter-contract-/widgettests toe, inclusief toetsenbordbediening, zichtbaar linklabel en veilig openen. Voer aansluitend een self-review uit tegen de refined story en factory-conventies.

Stappenplan:
[ ]: read issue and target docs
[ ]: implement requested changes
[ ]: run relevant tests
[ ]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

Tester-verificatie:
- Gerichte Flutter-run `flutter test test/hkh171_historical_result_card_test.dart test/hkh165_historical_search_smoke_contract_test.dart test/historical_search_test.dart` uitgevoerd: 30 tests, 0 failures/errors.
- Geldige en ongeldige Open Archieven-kaarten, fail-closed metadata/statusweergave, linklabel/semantiek en behoud van nul-, deel-, fout- en retrysemantiek zijn groen.
- Preview/E2E niet uitgevoerd: `.factory/verification.yaml` en `deployment.md` leveren geen preview-URL.

Documenter-verificatie:
- De kaartregels zijn aangevuld in `README.md`, `frontend/README.md`, `docs/development.md`,
  `docs/factory/development.md`, `docs/factory/functional-spec.md` en
  `docs/factory/technical-spec.md`.
- Vastgelegd zijn de verplichte Open Archieven-identiteit voor publieke weergave, het weglaten van
  ongeldige kaarten/links, de titel-naar-beschrijving-fallback zonder verzonnen inhoud, fail-closed
  `Onbekend`-statussen en het zichtbare nieuw-tabblad-label.
- Deployment-, secrets- en overige runbookdocumentatie zijn niet geraakt: deze story wijzigt alleen
  de bestaande gebruikersfrontend en voegt geen runtimeconfiguratie of API-route toe.
