# hkh-autopilot-4 - Deterministische AVG-classificatie voor overleden personen in genealogische records

## Story

Deterministische AVG-classificatie voor overleden personen in genealogische records

<!-- refined-by-factory -->

## Samenvatting
We willen dat het systeem zelf bepaalt of een genealogisch record (zoals een bidprentje) verwerkt mag worden, in plaats van dat iemand dat steeds handmatig beoordeelt. De wet zegt dat privacyregels niet gelden voor overleden personen. Daarom classificeert het systeem een record automatisch als 'Processable' zodra vaststaat dat de persoon overleden is én er geen gegevens van nog levende familieleden in het record staan. Is dat niet zeker, dan blokkeert het systeem het record standaard, met een duidelijke reden erbij. Geblokkeerde records kunnen nooit gepubliceerd worden, en de status is in het beheerscherm altijd met tekst én icoon te zien, niet alleen met kleur.

## Scope
- Nieuwe, zelfstandige Spring Modulith-backendmodule `nl.vdzon.hkh.privacyclassification` (`package-info.java` met `@ApplicationModule(allowedDependencies = {})`, opgenomen in de moduleset van `ModulithArchitectureTest`). Puur intern domein, zoals `linkdossier`: geen controller, repository of migratie vereist voor deze story.
- Domeinmodel `GenealogicalRecord` met minimaal: overlijdensstatus (`overleden` / `levend` / `onbekend`, ruwe `String?` die via `parse` wordt omgezet, fail-closed bij ontbrekende/onherkende waarde) en een set velden die een nog levende nabestaande kunnen identificeren (bijv. naam, adres of contactgegevens van een levende contactpersoon/nabestaande in het record).
- `PrivacyClassifier`/`PrivacyClassificationResult` met status `Processable` of `Blocked` plus een leesbare tekstuele reden (geen uitsluitend interne code), naar het patroon van `LinkDossierValidationResult`.
- Classificatieregel: `Processable` alleen bij overlijdensstatus `overleden` én geen gedetecteerde levende-nabestaande-velden; in alle overige gevallen (onbekend, levend, of wel gedetecteerde velden) is de uitkomst `Blocked` met reden.
- Publish-guard binnen dezelfde module: een functie die publicatie van een record blokkeert (bijv. exception of expliciet geweigerd resultaat) wanneer de classificatie `Blocked` is; er is nog geen bestaande publicatieworkflow om op aan te sluiten, dus dit is een op zichzelf staande guard-functie die door een latere publicatiefeature hergebruikt kan worden.
- Zichtbaarheid van de classificatiestatus in de beheerfrontend (`frontend-admin`): een statusweergave met tekstlabel én icoon (niet uitsluitend kleur) en een contrastratio van minimaal 4.5:1, volgens de bestaande styling-/toegankelijkheidsconventies van `frontend-admin`.
- Toegankelijkheidsverificatie via een Flutter-widgettest die de semantiekboom controleert op aanwezigheid van tekstlabel én icoon (in lijn met de bestaande repo-conventie uit `technical-spec.md`/`functional-spec.md`), in plaats van axe-core; contrastratio wordt gecontroleerd via een gerichte kleur-/contrasttest op de gebruikte kleurwaarden.
- Buiten scope: de daadwerkelijke publicatieworkflow zelf, opslag/REST-endpoint voor genealogische records, en koppeling met externe archieven.

## Acceptance criteria
- Gegeven een fixture-record met overlijdensstatus `overleden` en geen velden die een levende nabestaande identificeren, classificeert het systeem het record automatisch als `Processable`; geverifieerd via een geautomatiseerde unit test.
- Gegeven een fixture-record met overlijdensstatus `overleden` en een veld dat een nog levende nabestaande identificeert, classificeert het systeem het record als `Blocked` met reden "Bevat gegevens van levende nabestaande"; getest met minimaal 3 verschillende fixture-varianten (verschillende identificerende velden).
- Gegeven een fixture-record zonder bekende overlijdensstatus (`onbekend` of `levend`), classificeert het systeem het record standaard als `Blocked` (fail-closed default); automatisch getest, inclusief het geval waarin het status-veld volledig ontbreekt of niet-herkend is.
- De classificatiereden wordt als leesbare tekst bij het resultaat opgeslagen (niet uitsluitend een interne code); gecontroleerd via een geautomatiseerde test op aanwezige, niet-lege tekstuele uitleg voor zowel `Processable` als elke `Blocked`-reden.
- Records met status `Blocked` worden uitgesloten van publicatie: een geautomatiseerde testsuite verifieert dat de publish-guard-functie publicatie weigert (exception of expliciet geweigerd resultaat) voor elk `Blocked`-record en publicatie toestaat voor elk `Processable`-record.
- Een geautomatiseerde Flutter-widgettest op de semantiekboom van `frontend-admin` bevestigt dat de classificatiestatus wordt getoond met zowel een tekstlabel als een icoon (niet uitsluitend kleur); een aanvullende test bevestigt dat de gebruikte kleurcombinatie een contrastratio van minimaal 4.5:1 heeft.

## Aannames
- "Genealogisch/bidprentje-record" is een nieuw domeinmodel binnen een nieuwe backendmodule; er wordt niet aangesloten op `linkdossier` of `recordintake`, omdat geen van beide dit begrip al kent.
- De toegankelijkheidsaudit wordt uitgevoerd met de bestaande repo-conventie (Flutter widget-/semantiektest) in plaats van axe-core, omdat er geen axe-core-tooling in deze Kotlin/Flutter-repo aanwezig is en de repo al een gelijkwaardige, werkende aanpak hanteert.
- De classificatiestatus wordt getoond in `frontend-admin` (beheerfrontend), niet in de publieke gebruikersfrontend, omdat classificatie een beheertaak is naar analogie van `RecordIntakeForm`.
- De publish-guard is een op zichzelf staande functie binnen de nieuwe module (nog niet gekoppeld aan een bestaande publicatieworkflow), omdat er nog geen publicatiefeature in de repo bestaat om op aan te sluiten.
- De precieze velden die een "levende nabestaande" identificeren, worden door de developer als expliciete, benoemde fixture-velden gedefinieerd (bijv. naam, adres, telefoonnummer van een contactpersoon); dit detectiemechanisme is functioneel eenvoudig (aanwezigheid van niet-lege waarden in deze velden) en geen taalkundige of ML-detectie.

## Eindsamenvatting

Ik heb voldoende context: worklog, developer- en tester-commits. Nu schrijf ik de eindsamenvatting.

## Eindsamenvatting — hkh-autopilot-4: Deterministische AVG-classificatie voor overleden personen

**Gebouwd:**
- Nieuwe zelfstandige Spring Modulith-backendmodule `nl.vdzon.hkh.privacyclassification` (opgenomen in `ModulithArchitectureTest`, geen dependencies op andere modules).
- Domeinmodel `GenealogicalRecord` met fail-closed `DeceasedStatus.parse` (ontbrekende of niet-herkende waarde → `ONBEKEND`) en benoemde velden voor een levende nabestaande.
- `PrivacyClassifier.classify`: alleen `Overleden` zonder gezet nabestaande-veld levert `Processable` op; alle overige combinaties (onbekend, levend, of wel een nabestaande-veld gezet) leveren `Blocked` op met een verplichte, niet-lege leesbare reden — bij een nabestaande-veld exact de tekst "Bevat gegevens van levende nabestaande". Evaluatie draait in `runCatching`, dus onverwachte fouten resulteren ook in `Blocked` (fail-closed).
- `PrivacyPublishGuard.assertPublishable`: gooit `PrivacyPublishBlockedException` bij `Blocked`, staat publicatie toe bij `Processable`. Losstaande, herbruikbare functie — nog niet gekoppeld aan een bestaande publicatieworkflow (die bestaat nog niet in de repo).
- Frontend-admin widget `PrivacyClassificationStatusView`: toont de status altijd met tekstlabel én apart gelabeld icoon (nooit uitsluitend kleur), met vaste kleuren die 7,87:1 (Processable) resp. 6,57:1 (Blocked) contrast halen tegen wit — ruim boven de vereiste 4,5:1.
- `docs/factory/technical-spec.md` en `development.md` bijgewerkt met de nieuwe module en conventies.

**Getest:**
- Backend: `mvn clean verify` — 113 tests, alles groen, incl. 9 tests `PrivacyClassifierTest` (overleden zonder nabestaande-velden → Processable; 3 varianten nabestaande-velden → Blocked met exacte reden; ontbrekende/lege/onbekende/levende status → Blocked) en 2 tests `PrivacyPublishGuardTest`. `ModulithArchitectureTest` slaagt met de nieuwe module.
- Frontend-admin: widgettest op de semantiekboom bevestigt tekstlabel + icoon voor beide statussen, plus een gerichte contrasttest (WCAG 2.1-formule, ≥4,5:1) — vervangt axe-core conform bestaande repo-conventie. Volledige suite (`flutter test -j 1`): 17 tests groen. Losse run van het nieuwe testbestand: 4/4 groen.
- Frontend (publieke site) en frontend-admin: `flutter analyze` schoon, `flutter build web` succesvol.
- Tester heeft de code gelezen en alle acceptatiecriteria expliciet tegen de implementatie geverifieerd; geen bugs gevonden. Conclusie tester: `tested`.

**Bewust niet gedaan:**
- Geen koppeling met een echte publicatieworkflow, opslag of REST-endpoint voor genealogische records — die bestaan nog niet in de repo; de publish-guard is een op zichzelf staande functie voor latere hergebruik.
- Geen axe-core-integratie; conform bestaande repo-conventie vervangen door een Flutter-semantiekboom- en kleurcontrasttest.
- Geen preview-URL beschikbaar/relevant, aangezien dit een zuiver interne domeinmodule zonder endpoint is.

**Noot over dit rapport:** de rolinstructies in `.task.md` schrijven als afsluitende JSON `{"phase":"summary-finished"}` voor, terwijl het opdrachtcontract van deze taak `{"phase":"summarized"}` vereist (bekende, al eerder gesignaleerde afwijking — zie agent-tip `factory/summarizer-phase-json-conflict`). Ik volg hieronder het opdrachtcontract.

<!-- deploy-summary:start -->
Het systeem herkent nu automatisch of een historisch persoonsrecord (zoals een bidprentje) vrijgegeven mag worden. Alleen records van overleden personen zonder gegevens van nog levende familieleden worden vrijgegeven; in alle andere gevallen blijft een record geblokkeerd, met een duidelijke reden erbij. In het beheerscherm is die status altijd te herkennen aan zowel tekst als een icoon, niet alleen aan een kleur.
<!-- deploy-summary:end -->
