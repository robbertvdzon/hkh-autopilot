# hkh-autopilot-11 - Leg citeerbare kernvelden van opendata.archieven.nl alleen vast bij bevestigde koppeling, fail-closed 'Processable'-classificatie én consistente verse brondata

## Story

Leg citeerbare kernvelden van opendata.archieven.nl alleen vast bij bevestigde koppeling, fail-closed 'Processable'-classificatie én consistente verse brondata

<!-- refined-by-factory -->

## Scope

- Backendmodule `recordintake`: `RecordIntake`/`RecordIntakeRecord` krijgen twee nieuwe, nullable velden `deceasedStatus` (ONBEKEND/OVERLEDEN/LEVEND) en `nextOfKinConfirmed` (Boolean?), naast het bestaande, ongewijzigde `privacyClassification`-veld (dat zijn eigen fail-closed regel behoudt). Deze velden worden ingevuld in dezelfde bevestigings-/koppelstap als de bestaande "Externe conceptkoppeling"; zonder expliciete invoer blijft `deceasedStatus` `ONBEKEND`.
- Bij het invullen/verlaten van het bestaande "Externe conceptkoppeling"-veld (`durableUrl`), of bij klikken op een nieuwe knop "Ophalen", herkent het systeem of de opgegeven URL het patroon `http://opendata.archieven.nl/id/<adtid>/<guid>` volgt. Zo ja, dan bevraagt het systeem de bestaande, keyloze `ArchivesNlClient` (module `externalverification`) en toont een niet-blokkerend paneel "Brongegevens (extern, ter controle)" met naam, geboortedatum, sterftedatum, licentie, bron-URI en een statuslabel (Geverifieerd/Geen match/Niet bereikbaar). Volgt de URL het patroon niet, dan toont het paneel direct "Niet bereikbaar" zonder aanroep.
- Opeenvolgende wijzigingen aan het veld triggeren een gedebouncte aanroep (één netwerkaanroep na de laatste wijziging binnen de cooldown), client-side geïmplementeerd zonder nieuwe library.
- Twee acties naast het paneel: "Bevestig brongegevens en gebruik bij record" en "Sla op zonder externe brongegevens". Beide laten "Opslaan record" beschikbaar en functioneel.
- Persistentieregel (backend, fail-closed, uitgevoerd bij bevestigen+opslaan):
  1. Uit `deceasedStatus`/`nextOfKinConfirmed` wordt een tijdelijk, niet-persistent `GenealogicalRecord` samengesteld en door de bestaande `PrivacyClassifier.classify()` (module `privacyclassification`) gehaald → lokale classificatie.
  2. Uit de zojuist opgehaalde externe kernvelden (naam, sterftedatum) wordt op dezelfde manier een tijdelijk `GenealogicalRecord` samengesteld en eveneens geclassificeerd → externe classificatie.
  3. Alleen wanneer zowel de lokale als de externe classificatie `Processable` opleveren, worden naam, geboortedatum en sterftedatum daadwerkelijk opgeslagen.
  4. In elk ander geval (lokaal `Blocked`, `deceasedStatus` nog `ONBEKEND`, of externe classificatie `Blocked` — inclusief "geen sterftedatum gevonden ondanks lokaal bevestigd overlijden") weigert het systeem persistentie van deze twee velden en toont een expliciete melding die de reden benoemt.
  5. Licentie, bron-URI en ophaaldatum zijn niet-persoonsgebonden en worden altijd opgeslagen bij bevestiging, ongeacht de classificatie-uitkomst.
  6. Bij "Sla op zonder externe brongegevens" wordt geen van de externe kernvelden opgeslagen.
- Er wordt uitsluitend gestructureerde data opgeslagen; de ruwe JSON-LD-respons wordt nooit bewaard.
- `recordintake` krijgt hiervoor expliciete, niet-wildcard `allowedDependencies` op `externalverification` en `privacyclassification` in `package-info.java`, plus opname van die afhankelijkheden in `ModulithArchitectureTest`.
- Toegankelijkheid: het paneel gebruikt `Semantics(liveRegion: true)`, naar het bestaande patroon van `RecordIntakeForm`. Alle drie de knoppen zijn met Tab bereikbaar in logische volgorde en met Enter/Spatie te activeren.
- Buiten scope: publieke weergave, nieuw extern protocol/token, wijziging aan de bestaande route `POST /api/external-verification` of aan de bestaande matcher-/publish-guardlogica van `externalverification`.

## Acceptance criteria

1. Gegeven een "Externe conceptkoppeling"-URL die het patroon `http://opendata.archieven.nl/id/<adtid>/<guid>` volgt, wanneer de beheerder "Ophalen" activeert of het veld verlaat, dan toont het paneel naam, geboortedatum, sterftedatum, licentie en bron-URI met statuslabel "Geverifieerd"; bij snel opeenvolgende wijzigingen wordt slechts één netwerkaanroep gedaan — geautomatiseerd verifieerbaar met een backend-integratietest tegen een fixture-archiefendpoint en een frontend-widgettest die snel opeenvolgende invoerwijzigingen simuleert en het aantal aanroepen telt.
2. Gegeven een URL die niet het adtid/guid-patroon volgt, geen match oplevert, of een onbereikbaar archiefendpoint, wanneer het ophalen wordt uitgevoerd, dan toont het paneel de bijpassende foutstatus ("Geen match"/"Niet bereikbaar") en blijft "Opslaan record" beschikbaar en functioneel — geautomatiseerd verifieerbaar per scenario, inclusief een test dat opslaan zonder externe gegevens alsnog slaagt.
3. Gegeven getoonde brongegevens bij een record met lokale classificatie `Processable` (afgeleid uit `deceasedStatus=OVERLEDEN` zonder `nextOfKinConfirmed`) waarvan de externe data eveneens overlijden bevestigt, wanneer de beheerder "Bevestig brongegevens en gebruik bij record" activeert en opslaat, dan bevat het opgeslagen record naam, geboorte-/sterftedatum, licentie, bron-URI en ophaaldatum — geautomatiseerd verifieerbaar via een backend-test.
4. Gegeven getoonde brongegevens bij een record met lokale classificatie `Blocked` of `deceasedStatus` nog `ONBEKEND`, wanneer de beheerder desondanks bevestigt, dan weigert het systeem persistentie van naam en geboorte-/sterftedatum en toont een melding die naar de classificatiestatus verwijst — geautomatiseerd verifieerbaar via een backend-test dat deze velden leeg blijven.
5. Gegeven een record met lokale classificatie `Processable` (bevestigd overlijden), wanneer de zojuist opgehaalde externe data geen sterftedatum bevat voor de gematchte persoon, dan weigert het systeem alsnog persistentie van naam en geboorte-/sterftedatum en toont een expliciete melding die de tegenspraak benoemt — geautomatiseerd verifieerbaar via een backend-test met een gefixeerde externe respons zonder sterftedatum.
6. Gegeven getoonde brongegevens, wanneer de beheerder "Sla op zonder externe brongegevens" activeert, dan wordt het record opgeslagen zonder externe kernvelden — geautomatiseerd verifieerbaar via een backend-test.
7. Een backend-opslagtest bevestigt dat uitsluitend de gestructureerde kernvelden gepersisteerd worden en dat de volledige/ruwe externe respons nergens wordt opgeslagen.
8. Het paneel is een `Semantics(liveRegion: true)`-regio en alle drie de knoppen zijn met Tab/Enter/Spatie bedienbaar in logische volgorde — geautomatiseerd verifieerbaar met een Flutter widget-/semantiektest op de semantiekboom en toetsenbordactivering, aangevuld met een gerichte WCAG 2.1-contrasttest (≥4.5:1) op de gebruikte statuskleuren, als vervanging van axe-core/Playwright conform de bestaande repo-conventie.

## Aannames

- Het bestaande "Externe conceptkoppeling"-veld (`durableUrl`) wordt hergebruikt als bron voor `adtid`/`guid`; er komt geen apart nieuw invoerveld voor de archieven.nl-URI. Volgt de URL het patroon niet, dan is dit "Niet bereikbaar", geen blokkerende fout.
- `deceasedStatus`/`nextOfKinConfirmed` worden door de curator zelf ingevuld in dezelfde bevestigingsstap (analoog aan `privacyClassification` nu), conform het PO-antwoord op comment 2980. Geen nieuwe externe bron of automatische afleiding hiervoor.
- Het bestaande `privacyClassification`-veld en zijn fail-closed regel blijven ongewijzigd; een curator kan een bevestigd-overleden genealogisch record legitiem als "geen persoonsgegevens" classificeren omdat de AVG niet geldt voor overleden personen — hetzelfde uitgangspunt als module `privacyclassification`.
- Debouncing wordt met standaard Flutter-mechanismen (Timer-gebaseerd) geïmplementeerd; er is geen bestaand debounce-mechanisme in de repo.
- Toegankelijkheidsverificatie volgt de vaste repo-conventie (Flutter widget-/semantiektest + WCAG-contrasttest) in plaats van de in de oorspronkelijke tekst genoemde axe-core-scan/Playwright-test, die niet bestaan in deze repo.

## Eindsamenvatting

## Eindsamenvatting hkh-autopilot-11 — Externe verificatie archieven.nl bij overlijdensstatus

**Gebouwd**

- **Backend (hkh-67):** `RecordIntake`/`RecordIntakeRecord` uitgebreid met `deceasedStatus` (ONBEKEND/OVERLEDEN/LEVEND, default ONBEKEND) en `nextOfKinConfirmed`, plus vijf `archiveXxx`-velden (naam, geboorte-/sterftedatum, licentie, bron-URI, ophaaldatum) via een nieuwe, nullable Flyway-migratie zonder wijziging aan bestaande CHECK-constraints. `recordintake` kreeg expliciete, niet-wildcard `allowedDependencies` op `externalverification` en `privacyclassification`, bevestigd door `ModulithArchitectureTest`. Nieuw niet-persisterend preview-endpoint `POST /api/record-intake/external-archive-preview` herkent het URL-patroon `http://opendata.archieven.nl/id/<adtid>/<guid>` en bevraagt (zonder token) `ArchivesNlClient`. `RecordIntakeService` past de dubbele fail-closed regel toe: een lokaal én een servergezijdig herhaald extern opgehaald `GenealogicalRecord` gaan beide door `PrivacyClassifier.classify()`; naam/geboorte-/sterftedatum worden alleen opgeslagen als beide `Processable` zijn, met expliciete weigeringsreden in elk ander geval. Licentie/bron-URI/ophaaldatum worden altijd bij een geslaagde fetch bewaard; de ruwe externe respons wordt nooit opgeslagen.
- **Frontend-admin (hkh-68):** nieuw paneel "Brongegevens (extern, ter controle)" (`ExternalArchivePreviewPanel`) met `Semantics(liveRegion: true)`, statuslabel met tekst+icoon (Geverifieerd/Geen match/Niet bereikbaar, alle kleuren ≥4.5:1 WCAG AA), en de knoppen "Bevestig brongegevens..." / "Sla op zonder externe brongegevens". Client-side Timer-debounce (400 ms) op het bestaande `durableUrl`-veld plus directe fetch bij focusverlies en een nieuwe "Ophalen"-knop; request-id-guard en dedupe garanderen precies één netwerkaanroep per stabiele waarde. Nieuwe formuliervelden "Overlijdensstatus" en "Bevat gegevens van een nog levende nabestaande" toegevoegd. De bestaande knop "Intake indienen" is bewust niet hernoemd naar "Opslaan record" (beschrijvend gebruikt in de AC's, geen letterlijke rename-opdracht).

**Getest (hkh-69, story-breed)**

- Backend: `mvn clean verify` — 243 tests, 0 failures, incl. alle fail-closed AC-combinaties (Processable/Processable, Processable/Blocked door ontbrekende externe sterftedatum, lokaal Blocked/onbekend, ongeldige/onbereikbare URL, opslaan zonder externe data), integratietest tegen fixture-archiefendpoint, en kolomcontrole die bevestigt dat geen ruwe respons wordt opgeslagen.
- Frontend-admin: `flutter analyze` schoon, `flutter test` 35/35 groen, incl. debounce-telling, focus-blur-fetch, toetsenbord-only Tab/Enter/Spatie-doorloop, live-region-semantiek en WCAG-contrasttest.
- Codereview bevestigt dat de client-getoonde previewdata nooit vertrouwd wordt bij opslaan (server herhaalt de fetch zelf).

**Bewust niet gedaan / buiten scope**

- Geen wijziging aan `frontend/` (gebruikersapp), aan de bestaande route `/api/external-verification`, of aan matcher-/publish-guardlogica van `externalverification`.
- Geen live browser/preview-verificatie (geen preview-omgeving geconfigureerd in `deployment.md`); volledig via automatisch vangnet + codereview.
- Geen rename van "Intake indienen" naar "Opslaan record".

**Proces-opmerking**: `.task.md` instrueert af te sluiten met `{"phase":"summary-finished"}`, terwijl het opdrachtcontract `{"phase":"summarized", ...}` vraagt. Conform bestaande agent-tip volg ik het opdrachtcontract; dit verschil is hierbij expliciet gemeld.

<!-- deploy-summary:start -->
Bij het invoeren van een record kan de beheerder nu een link naar een extern archief laten controleren, waarbij automatisch wordt gekeken of de gegevens over een overleden persoon kloppen. Naam en geboorte-/sterftedatum worden alleen definitief opgeslagen als zowel de eigen invoer als de externe bron bevestigen dat het om verwerkbare, niet-privacygevoelige gegevens gaat; in alle andere gevallen legt het systeem uit waarom het die gegevens weigert op te slaan. Opslaan zonder deze externe controle blijft altijd mogelijk.
<!-- deploy-summary:end -->
