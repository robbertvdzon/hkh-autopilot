# hkh-autopilot-7 - Persoonsniveau levend-detectie via de FamilySearch 110/95-jaarregel voor AVG-classificatie

## Story

Persoonsniveau levend-detectie via de FamilySearch 110/95-jaarregel voor AVG-classificatie

<!-- refined-by-factory -->

## Samenvatting
De privacyclassificatie van genealogische records (zoals bidprentjes) wordt uitgebreid met een concrete regel om te bepalen of een genoemde persoon (de hoofdpersoon of een familielid) vermoedelijk nog leeft. Deze regel is gebaseerd op een bekende, extern gedocumenteerde vuistregel (de FamilySearch 110/95-jaarregel): iemand wordt als "vermoedelijk levend" gezien als er geen overlijdens- of begrafenisdatum bekend is én de persoon jonger dan 110 jaar zou zijn, of minder dan 95 jaar geleden trouwde of een kind kreeg. Zodra één genoemd persoon zo wordt aangemerkt, blijft het hele record geblokkeerd voor verdere verwerking. Ontbrekende of onleesbare datums leiden altijd tot blokkade, nooit tot automatische vrijgave. Dit maakt de bestaande, al goedgekeurde privacyclassificatie concreter en beter testbaar, zonder dat er handmatige controle nodig is.

## Scope
- Uitbreiding van de bestaande, fail-closed AVG-classificatie in backendmodule `nl.vdzon.hkh.privacyclassification` (zie `PrivacyClassifier`, `GenealogicalRecord`) met een nieuw, zelfstandig testbaar detectiemechanisme voor "vermoedelijk levende" personen, gebaseerd op de FamilySearch 110/95-jaarregel.
- Het domeinmodel wordt uitgebreid met een lijst van genoemde personen per record (hoofdpersoon + familieleden). Elke genoemde persoon heeft optionele datumvelden: geboortedatum, huwelijksdatum, datum geboorte kind, overlijdensdatum en begrafenisdatum (los tekstveld, net als bestaande datumvelden elders in de repo, bijv. `ExternalVerificationRequest.birthDate`).
- Nieuwe, pure functie/klasse die per genoemde persoon een machineleesbare uitkomst teruggeeft: `likely_living`, `deceased` of `unknown_failclosed`.
- Regel per persoon:
  - Heeft de persoon een geldige, parsbare overlijdens- of begrafenisdatum → `deceased`.
  - Geen overlijdens-/begrafenisdatum, en een geldige geboortedatum die ≤110 jaar geleden valt → `likely_living`.
  - Geen overlijdens-/begrafenisdatum, geboortedatum ontbreekt of is >110 jaar geleden, maar een geldige huwelijks- of kindgeboortedatum ligt ≤95 jaar geleden → `likely_living`.
  - Geen enkel bruikbaar datumveld aanwezig, of een aanwezig datumveld is niet als geldige datum te parsen → `unknown_failclosed`.
  - Geen overlijdens-/begrafenisdatum, maar wél een geldige geboortedatum >110 jaar geleden én (indien aanwezig) een geldige huwelijks-/kindgeboortedatum >95 jaar geleden → `deceased` (leeftijdsgebaseerde vermoeden conform de bronregel; geen expliciete overlijdensdatum nodig).
- Integratie: het totaaloordeel van het record blijft `Blocked` zodra minstens één genoemde persoon (hoofdpersoon of familielid) `likely_living` of `unknown_failclosed` oplevert. Dit detectiemechanisme werkt aanvullend naast de bestaande controles in `PrivacyClassifier` (bestaande `DeceasedStatus`- en `nextOfKin`-controles blijven ongewijzigd bestaan); het record is alleen `Processable` als geen van de bestaande of nieuwe controles blokkeert.
- "Referentiemoment" voor de leeftijdsberekening is het moment waarop de classificatie draait (systeemklok), niet een vaste kalenderdatum.
- Buiten scope: wijzigen van de bestaande `DeceasedStatus`/`nextOfKin`-velden of -logica, opslag/REST-endpoint voor de nieuwe persoonsgegevens, UI-weergave van de per-persoon uitkomst (de bestaande record-brede statusweergave in `frontend-admin` volstaat), en het vastleggen van event-granulariteit (bijv. dag- vs. jaarprecisie) verder dan wat hieronder als aanname is vastgelegd.

## Acceptance criteria
1. Gegeven een genoemd familielid met een geldige geboortedatum ≤110 jaar geleden en zonder overlijdens-/begrafenisdatum, wanneer de classificatiefunctie draait, dan is de leeftijdsregel-uitkomst voor die persoon `likely_living` en het totaaloordeel van het record `Blocked`.
2. Gegeven een genoemd familielid waarvan alleen een huwelijk of de geboorte van een kind ≤95 jaar geleden is geregistreerd (geen geboorte- of overlijdensdatum, of een geboortedatum >110 jaar geleden) en geen overlijdens-/begrafenisdatum aanwezig is, dan is de uitkomst voor die persoon `likely_living` en het record `Blocked`.
3. Gegeven een record waarin alle genoemde personen (hoofdpersoon en familieleden) een geldige, ingevulde overlijdens- of begrafenisdatum hebben, dan is de leeftijdsregel-uitkomst voor elk van hen `deceased`, en is het record — bij afwezigheid van andere blokkerende signalen (bestaande `DeceasedStatus`/`nextOfKin`-controles) — `Processable`.
4. Gegeven een record waarin voor een genoemde persoon geen enkel bruikbaar datumveld aanwezig is, of een aanwezig geboorte-, huwelijks-, kindgeboorte-, overlijdens- of begrafenisdatumveld niet als geldige datum te parsen is, dan is de leeftijdsregel-uitkomst voor die persoon `unknown_failclosed` en het totaaloordeel van het record `Blocked`.
5. Gegeven een record waarin een genoemde persoon een geldige geboortedatum >110 jaar geleden heeft, geen overlijdens-/begrafenisdatum, en geen (of geen geldige) huwelijks-/kindgeboortedatum ≤95 jaar geleden, dan is de leeftijdsregel-uitkomst voor die persoon `deceased`.
6. De classificatiefunctie retourneert voor elke genoemde persoon een machineleesbaar resultaat (enum-waarde `likely_living` | `deceased` | `unknown_failclosed`), uitleesbaar door een geautomatiseerde test zonder visuele inspectie.
7. Een geautomatiseerde backend unit-testsuite (naast de bestaande `PrivacyClassifierTest`) dekt minimaal: net onder de 110-jaargrens (bijv. 109 jaar) en net erboven (bijv. 111 jaar, zonder overlijdensdatum en zonder huwelijks-/kindsignaal); net onder en net boven de 95-jaargrens voor huwelijk/kind; een persoon met overlijdensdatum aanwezig; een persoon met een ontbrekend of onleesbaar datumveld; en een record met meerdere genoemde personen waarvan er precies één `likely_living` is (record moet `Blocked` zijn).
8. De brongrond (FamilySearch 110/95-jaarregel als externe praktijkregel, geen wettelijke norm) en de bekende beperking rond ontbrekende event-granulariteit (bijv. alleen jaartal in plaats van volledige datum) worden expliciet gedocumenteerd in code-commentaar en/of `docs/factory/technical-spec.md` en `functional-spec.md`.

## Aannames
- Het domeinmodel wordt uitgebreid met een lijst genoemde personen (hoofdpersoon + familieleden) met per persoon optionele stringvelden voor geboorte-, huwelijks-, kindgeboorte-, overlijdens- en begrafenisdatum, analoog aan het patroon van bestaande losse datumvelden (`ExternalVerificationRequest.birthDate`/`deathDate`) — geen nieuwe datumtype-afhankelijkheid nodig, parsing gebeurt binnen het nieuwe mechanisme.
- Verwacht datumformaat is ISO-8601 (`yyyy-MM-dd`); een datum met alleen jaartal (`yyyy`) wordt ook als geldig geparsed (met 1 januari als impliciete dag) tenzij de developer een preciezere aanpak kiest — dit is een bekende beperking die conform AC 8 gedocumenteerd moet worden.
- Grenswaarden zijn inclusief (`≤110` en `≤95` jaar); "jaren geleden" wordt berekend als volledige kalenderjaren tussen de datum en het moment van classificeren.
- De nieuwe leeftijdsregel is aanvullend op (niet ter vervanging van) de bestaande `DeceasedStatus`- en `nextOfKin`-controles in `PrivacyClassifier`; alle bestaande tests en gedrag van `PrivacyClassifier`/`PrivacyPublishGuard` blijven ongewijzigd werken voor records zonder de nieuwe persoonsgegevens.
- Voor het geval "geldige geboortedatum >110 jaar geleden, geen overlijdensdatum, geen (geldig) huwelijks-/kindsignaal" (niet expliciet benoemd in de oorspronkelijke acceptatiecriteria) wordt `deceased` aangehouden, conform de FamilySearch-brondefinitie waarin het overschrijden van beide leeftijdsgrenzen zonder tegensignaal als vermoedelijk overleden geldt.
- Er is geen wettelijke autoriteit achter de 110/95-jaarregel; het is een branchepraktijk van een externe partij (FamilySearch), wat als kanttekening in de documentatie wordt opgenomen zoals door Product Factory gevraagd.

## Eindsamenvatting

Ik heb voldoende context. De story is volledig geïmplementeerd en getest (developer + tester akkoord, alle checks groen). Ik stel de eindsamenvatting op.

## Eindsamenvatting hkh-autopilot-7: Persoonsniveau levend-detectie via de FamilySearch 110/95-jaarregel voor AVG-classificatie

**Wat is gebouwd**
- Het domeinmodel `GenealogicalRecord` is uitgebreid met een lijst `namedPersons` (hoofdpersoon + familieleden), elk met optionele stringvelden voor geboorte-, huwelijks-, kindgeboorte-, overlijdens- en begrafenisdatum (nieuw `NamedPerson`-datatype, naar analogie van bestaande datumvelden elders in de repo).
- Nieuwe, zelfstandig testbare klasse `LivingPersonAgeRule` bepaalt per genoemde persoon de status `LIKELY_LIVING`, `DECEASED` of `UNKNOWN_FAILCLOSED` volgens de FamilySearch 110/95-jaarregel: een geldige overlijdens-/begrafenisdatum geeft `DECEASED`; zonder die datum geeft een geboortedatum ≤110 jaar geleden of een huwelijks-/kindgeboortedatum ≤95 jaar geleden `LIKELY_LIVING` (grenzen inclusief); een geboortedatum >110 jaar geleden zonder recent huwelijks-/kindsignaal geeft `DECEASED`; ontbrekende of onparsbare datumvelden geven fail-closed `UNKNOWN_FAILCLOSED`.
- `PrivacyClassifier.evaluate` is additief uitgebreid: het record blijft `BLOCKED` zodra minstens één genoemde persoon `LIKELY_LIVING` of `UNKNOWN_FAILCLOSED` oplevert, met nieuwe redenconstantes. De bestaande `DeceasedStatus`/`nextOfKin`-controles en fail-safe (`runCatching`) blijven ongewijzigd.

**Gemaakte keuzes**
- Datumparsing ondersteunt ISO-8601 (`yyyy-MM-dd`) met een `yyyy`-only fallback (1 januari als impliciete dag) — een bekende, expliciet gedocumenteerde beperking.
- Referentiemoment voor leeftijdsberekening is injecteerbaar via een `java.time.Clock` (standaard systeemklok), zodat tests deterministisch zijn.
- Grenswaarden 110 en 95 jaar zijn inclusief; het geval "geboortedatum >110 jaar geleden zonder overlijdensdatum en zonder recent huwelijks-/kindsignaal" is conform de aanname in de story als `DECEASED` behandeld.
- De externe, niet-wettelijke herkomst van de FamilySearch-regel en de beperking rond ontbrekende event-granulariteit zijn gedocumenteerd in KDoc, `docs/factory/technical-spec.md` en `functional-spec.md`.

**Getest**
- Nieuwe testsuite `LivingPersonAgeRuleTest` (17 tests): grenzen 109/110/111 jaar geboorte, 94/95/96 jaar huwelijk/kind, overlijdens-/begrafenisdatum aanwezig, ontbrekend/onleesbaar veld, `yyyy`-only fallback.
- Uitbreiding `PrivacyClassifierTest` (12 tests): meerdere genoemde personen met precies één `LIKELY_LIVING` → `Blocked`; onleesbaar datumveld → `Blocked`; alle personen `DECEASED` zonder ander blokkerend signaal → `Processable`.
- Volledig vangnet groen op alle drie modules: backend (`mvn clean verify`, 180 tests, 0 failures), `frontend` (`flutter analyze`/`test`/`build web`) en `frontend-admin` (`flutter analyze`/`test`, 22 tests). Geen bugs gevonden; tester-besluit `tested`.

**Bewust niet gedaan**
- Geen wijziging aan bestaande `DeceasedStatus`/`nextOfKin`-velden of -logica.
- Geen opslag of REST-endpoint voor de nieuwe persoonsgegevens.
- Geen UI-weergave van de per-persoon uitkomst — conform expliciete scope-afbakening in de story.

**Proceskanttekening**: er is een verschil tussen de phase-JSON in de rolinstructies van `.task.md` (`summary-finished`) en het opdrachtcontract (`summarized`). Ik volg het opdrachtcontract, zoals eerder in de agent-tips vastgelegd.

<!-- deploy-summary:start -->
We kunnen nu automatisch herkennen wanneer een genoemd familielid op een bidprentje of vergelijkbaar document vermoedelijk nog in leven is, op basis van geboorte-, huwelijks- of kinddatums. Zolang dat niet zeker uit te sluiten is, wordt het document uit voorzorg geblokkeerd voor verdere verwerking, precies zoals dat al gebeurde voor de hoofdpersoon. Er verandert verder niets aan hoe u het systeem gebruikt.
<!-- deploy-summary:end -->
