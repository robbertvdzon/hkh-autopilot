# hkh-189 - Worklog

Story-context bij pickup:
Contracttestmatrix voor publieke historische zoekstatussen

Stappenplan:
[x]: issue-context, factory-instructies, development.md en technical-spec.md gelezen
[x]: bestaande backend- en Flutter-contracttests en verificatieconfiguratie inspecteren
[x]: contracttestmatrix uitbreiden met reproduceerbare fixtures/mocks
[x]: relevante tests en het volledige vangnet uitvoeren
[x]: zelfreview uitvoeren en resultaten vastleggen

Done / rationale:
- Dit worklog is aan het begin van de developer-run aangemaakt volgens de factory-instructies.
- De backendmatrix gebruikt lokale JDK-HTTP-fixtures voor geldig, leeg, partieel, ongeldig JSON,
  ontbrekende/tegenstrijdige velden, timeout, HTTP 5xx en ontbrekende rechten/privacy.
- De frontendmatrix gebruikt synthetische responses en controleert bronidentiteit, exacte link,
  tellingen, kaartzichtbaarheid, veilige foutteksten en de `Onbekend`-mapping.
- Gerichte backendmatrix: 3 tests groen; frontendmatrix: 3 tests groen.
- Volledig vangnet groen: backend Maven 359 tests, frontend 86 tests plus analyze en webbuild,
  frontend-admin 39 tests plus analyze.
- Zelfreview afgerond: geen conflictmarkers, geen secrets/providerpayloads in publieke asserts,
  fixtures stoppen in `finally` en alle wijzigingen blijven uncommitted.

## [REVIEWER]

- Factory-verificatie gecontroleerd: aanwezig en groen voor de actuele HEAD/tree; gerichte backend-
  en Flutter-contracttests zijn eveneens groen.
- [blocker] De nieuwe Flutter-matrix (`frontend/test/hkh189_historical_search_contract_test.dart`,
  regels 88-296) zet `state`, bronstatus en tellingen in de synthetische responses, maar de
  assertions controleren die waarden niet. De diagnostiek bevat alleen de scenarionaam en
  kaartzichtbaarheid; bij een regressie in de weergegeven toestand of telling blijven de tests
  groen. Voeg per case expliciete verwachte state, bronstatus en totale/per-bron telling toe en
  assert die aan de publieke UI-/contractgrens, inclusief de null-tellingen bij bronfouten.

## Developer rerun

- De reviewer-blokkade is opnieuw opgepakt: de Flutter-matrix krijgt expliciete verwachte state-,
  status- en telwaarden per scenario, inclusief beide bronwaarden bij gedeeltelijke beschikbaarheid
  en nullable tellingen bij bronfouten.
- De assertions controleren nu ook de zichtbare totale-resultaattekst, lege/foutstatus en per-bron
  status/telling. De foutmatrix verwacht expliciet `SOURCE_FAILURE`, totaal `0` en `null` voor beide
  broncountvelden.
- Gerichte tests: backend `Hkh189HistoricalSearchContractTest` 3 groen; Flutter hkh-189-contracttest
  3 groen.
- Volledig vangnet opnieuw uitgevoerd: backend Maven 359 tests groen; frontend analyze, 86 tests en
  webbuild groen; frontend-admin analyze en 39 tests groen.
- Zelfreview afgerond: geen conflictmarkers, geen nieuwe secrets of providerpayloads, fixtures blijven
  lokaal/synthetisch en de wijzigingen zijn uncommitted gelaten voor de factory.
