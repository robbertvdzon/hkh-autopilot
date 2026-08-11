# hkh-autopilot-12 - Publieke recorddetailpagina: toegankelijke sectie 'Externe bronverificatie' met jaarprecisie en zelfherstellend gedrag bij herclassificatie

## Story

Publieke recorddetailpagina: toegankelijke sectie 'Externe bronverificatie' met jaarprecisie en zelfherstellend gedrag bij herclassificatie

<!-- refined-by-factory -->

## Scope

Deze story bouwt, zonder bestaand precedent, het volledige fundament voor een publiek zichtbare
sectie "Externe bronverificatie" op de (nog te bouwen) publieke recorddetailpagina van de
gebruikersfrontend (`frontend`). Niets van het onderstaande bestaat al in de repository:

- **Nieuwe publieke backendroute** (bijv. `GET /api/records/{localIdentifier}`, analoog aan de
  bestaande route-conventie zoals `/api/news`) die per record uitsluitend niet-privacygevoelige,
  publicatiewaardige velden teruggeeft. De ruwe `RecordIntakeRecord`-data (o.a. status,
  `deceasedStatus`, `nextOfKinConfirmed`) wordt nooit rechtstreeks geëxposeerd.
- **Nieuw afgeleid statusmodel**, server-side bij elk verzoek berekend (niet los opgeslagen):
  - `NO_INTAKE`: er bestaat geen `RecordIntakeRecord` voor deze `localIdentifier`.
  - `SAVED_WITHOUT_SOURCE`: er bestaat een `RecordIntakeRecord`, maar de `archive*`-velden zijn
    `null` (curator koos destijds "Sla op zonder externe brongegevens").
  - `CONFIRMED`: er bestaat een `RecordIntakeRecord` met gevulde `archive*`-velden, én een
    beheerder heeft deze expliciet bevestigd (zie hieronder), én de bij dit verzoek opnieuw
    uitgevoerde `PrivacyClassifier.classify()` levert `Processable` op voor het record
    (samengesteld uit de actuele `deceasedStatus`/`nextOfKinConfirmed`-velden, naar het patroon
    van de bestaande dubbele classificatie in de recordintake-bevestigingsflow).
  - Levert de live herclassificatie `Blocked` op voor een eerder bevestigd record, dan degradeert
    de publieke weergave voor dat verzoek naar de neutrale melding (zonder `confirmedBy`/
    `confirmedAt` te wissen); wordt het record later weer `Processable`, dan verschijnt de
    `CONFIRMED`-weergave automatisch weer op basis van de bewaarde bevestiging — dit is het
    "zelfherstellende gedrag".
- **Nieuwe, persistente beheerdersbevestigingsactie**: `RecordIntakeRecord` krijgt twee nieuwe
  nullable velden, `confirmedBy` en `confirmedAt`, gevuld via een nieuwe actie in het bestaande
  admin-only pad (uitbreiding op, niet vervanging van, de bestaande recordintake-flow). Het
  vullen van de `archive*`-velden alleen is niet voldoende voor `CONFIRMED`: de expliciete
  beheerdersbevestiging is een aparte, bewuste stap.
- **Publieke sectie "Externe bronverificatie"** (h2) op de recorddetailpagina:
  - Bij `CONFIRMED`: statustekst, naam, geboortejaar en (indien aanwezig) sterftejaar — beide
    afgeleid uit `archiveBirthDate`/`archiveDeathDate` maar uitsluitend als jaartal getoond, nooit
    als dag-nauwkeurige datum; licentie (`archiveLicense`); een klikbare, met zichtbare tekst
    gelabelde link naar `archiveSourceUri` die in een nieuw tabblad opent (`rel="noopener"`, met
    een programmatisch gekoppeld label dat aankondigt dat het een externe link is, naar het
    bestaande patroon in `frontend-admin`); en de tekst "Bevestigd door beheerder op [datum]"
    (gebaseerd op `confirmedAt`).
  - Bij `SAVED_WITHOUT_SOURCE` of `NO_INTAKE` (en, ongewijzigd voor de bezoeker, ook bij een live
    herclassificatie naar `Blocked`): dezelfde sectie toont één neutrale, niet-technische melding
    zonder velden en zonder link. Er is bewust geen apart "ingetrokken"-bericht: alle drie de
    gevallen tonen exact dezelfde melding, om geen metadata over een eventuele eerdere publicatie
    te lekken.
  - De sectie is in-/uitklapbaar via een toegankelijke `<button>` met `aria-expanded`/
    `aria-controls`.
  - Status wordt zowel tekstueel als via icoon onderscheiden (nooit uitsluitend kleur), met een
    contrastratio van minimaal 4,5:1, naar het bestaande patroon
    (`PrivacyClassificationStatusView` in `frontend-admin`).
- Bestaande lokale recordvelden, paginanavigatie en de bestaande "Ontdek"-zoekfunctie
  (`GET /api/news`, kandidaat gazetteer-matching) blijven functioneel ongewijzigd; deze nieuwe
  sectie wordt niet aan die zoekfunctie gekoppeld en levert er geen resultaten aan.

Buiten scope: wijzigingen aan de bestaande route `POST /api/external-verification` en de
bijbehorende matcher-/publish-guardlogica, wijziging aan de bestaande admin-bevestigingsflow van
de recordintake zelf (dit is een aparte, aanvullende actie), en elke koppeling met de
"Ontdek"-nieuwszoekfunctie.

**Correctie op de oorspronkelijke issuetekst:** de verwijzingen naar een "bestaande publieke
recorddetailpagina", een "herzien" leespad en een "reeds bestaande beheerdersbevestiging in
kandidaat 46" zijn onjuist — niets hiervan bestaat in de repository; deze story bouwt dit
fundament vanaf nul (bevestigd door de PO in issuecomment 3016). Ook de in de acceptatiecriteria
genoemde Playwright-/axe-core-tooling bestaat niet in deze repo (geen Node-toolchain) en wordt
vervangen door de bestaande, herhaaldelijk toegepaste repo-conventie: Flutter widget-/
semantiektests op de accessibility-/semantiekboom, plus een gerichte WCAG 2.1-contrasttest
(≥4,5:1), zoals eerder toegepast bij o.a. `PrivacyClassificationStatusView` en het
homepage-ontdekblok.

## Acceptance criteria

- Bij het laden van de recorddetailpagina voor een `CONFIRMED`-record is, zonder voorafgaande
  gebruikersinteractie, in de initiële semantiekboom een h2 "Externe bronverificatie" aanwezig met
  statustekst, naam, geboortejaar, sterftejaar (indien aanwezig), licentie en een link met
  zichtbare linktekst naar de bron-URI — geverifieerd via een Flutter widget-/semantiektest; een
  expliciete assertie bevestigt dat er geen dag- of maandgetal in de weergegeven datumtekst
  voorkomt.
- Bij het laden van de recorddetailpagina voor `SAVED_WITHOUT_SOURCE` en, apart getest, voor
  `NO_INTAKE`, toont dezelfde sectie een duidelijke, niet-technische neutrale melding zonder
  bronlink en zonder record-/datumvelden, voor beide statussen apart geverifieerd via een Flutter
  widget-/semantiektest.
- Zelfherstellend gedrag: een backend-integratietest bevestigt dat voor eenzelfde `localIdentifier`
  een eerste opvraging na een beheerdersbevestiging `CONFIRMED` oplevert, en een tweede opvraging
  ná een wijziging van `deceasedStatus`/`nextOfKinConfirmed` die de classificatie naar `Blocked`
  verandert, zonder verdere actie de neutrale statuswaarde oplevert (zonder dat `confirmedBy`/
  `confirmedAt` gewist zijn); een Flutter widget-/integratietest bevestigt hetzelfde gedrag op
  UI-niveau (twee opeenvolgende paginaladingen met een classificatiewijziging ertussenin).
- De externe bronlink opent in een nieuw tabblad met `rel="noopener"`, heeft een programmatisch
  gekoppeld label dat aankondigt dat het een externe link is, en is volledig met alleen het
  toetsenbord (Tab + Enter) bereikbaar en activeerbaar — geverifieerd via een Flutter
  widget-/semantiektest die de Tab-volgorde en toetsactivatie simuleert (`tester.sendKeyEvent`,
  geen tap/muis).
- Een gerichte WCAG 2.1-contrasttest berekent de contrastratio (≥4,5:1) van de gebruikte
  statuskleuren, en een semantiekboomtest bevestigt dat elke statusbadge zowel een tekstlabel als
  een icoon heeft naast de kleur — beide geautomatiseerd in de bestaande Flutter-testsuite.
- De in-/uitklapknop rapporteert correct `aria-expanded` (`true`/`false`) en is via
  `aria-controls` programmatisch gekoppeld aan de sectie-inhoud, geverifieerd via een
  semantiekboom-snapshotvergelijking vóór en na de toggle.
- Bestaande lokale recordvelden, paginanavigatie en de bestaande "Ontdek"-zoekfunctie blijven
  functioneel ongewijzigd en tonen geen resultaten uit deze nieuwe sectie — geverifieerd via een
  regressie-widgettest die deze elementen/routes na de wijziging opnieuw controleert.
- De nieuwe module(s)/uitbreiding volgen de bestaande Modulith-conventie: elke gewijzigde/nieuwe
  module heeft een `package-info.java` met expliciete `allowedDependencies` (geen wildcard) en
  staat in de moduleset van `ModulithArchitectureTest`.

## Aannames

- Er bestaat geen eerdere, niet-aangeleverde story die dit fundament al opleverde; blijkt die
  later alsnog te bestaan, dan wordt dit werk hergebruikt/samengevoegd in plaats van dubbel
  gebouwd (expliciet zo aangenomen door de PO in issuecomment 3016).
- De publieke identifier in de nieuwe route is `RecordIntakeRecord.localIdentifier` (dezelfde
  identifier als in de bestaande recordintake-flow); er wordt geen apart, nieuw publiek-ID-schema
  geïntroduceerd.
- De nieuwe beheerdersbevestigingsactie is een uitbreiding van het bestaande admin-only pad
  (dezelfde tokenverificatie-conventie als de rest van de beheerfrontend); er wordt geen nieuw
  autenticatiemechanisme gebouwd.
- Jaarprecisie wordt afgeleid door client- of serverzijdig alleen het jaartal uit de bestaande
  `archiveBirthDate`/`archiveDeathDate`-tekstvelden te tonen; het onderliggende veld zelf blijft
  ongewijzigd (dag-precisie), alleen de publieke weergave wordt beperkt tot het jaartal.
- Playwright/axe-core-tooling wordt niet geïntroduceerd; alle in de oorspronkelijke issuetekst
  genoemde Playwright-/axe-core-verificaties worden vervangen door de bestaande Flutter
  widget-/semantiektest- en WCAG-contrasttestconventie, zoals ook bij eerdere stories in deze
  repo is toegepast.

## Eindsamenvatting

## Eindsamenvatting — hkh-autopilot-12: Publieke recorddetailpagina met sectie "Externe bronverificatie"

**Wat is gebouwd**

- **Backend (hkh-74, review-approved):** nieuw statusmodel `RecordPublicStatus` (`NO_INTAKE`/`SAVED_WITHOUT_SOURCE`/`CONFIRMED`), server-side bij elk verzoek herberekend inclusief een live `PrivacyClassifier`-herclassificatie. Nieuwe publieke, ongeauthenticeerde route `GET /api/records/{localIdentifier}` die altijd HTTP 200 teruggeeft en uitsluitend afgeleide, niet-privacygevoelige velden blootgeeft (naam, jaartal-only geboorte-/sterftedatum, licentie, bron-URI, bevestigingsdatum) — de ruwe `RecordIntakeRecord` (status, `deceasedStatus`, `nextOfKinConfirmed`) blijft verborgen. Nieuwe admin-only bevestigingsactie (`POST /api/admin/record-intake/{localIdentifier}/confirm`) zet de nieuwe velden `confirmedBy`/`confirmedAt` via het bestaande auth-pad. Flyway-migratie V9 toegevoegd. Zelfherstellend gedrag geïmplementeerd: degraderen naar `Blocked` wist de bevestiging niet, en een latere herclassificatie naar `Processable` herstelt `CONFIRMED` automatisch.
- **Frontend (hkh-75, review-approved):** nieuwe, op zichzelf staande module `record_detail_page.dart` met in-/uitklapbare sectie "Externe bronverificatie" (h2), status altijd via tekst + icoon (nooit uitsluitend kleur, contrast 7,87:1 resp. 10,05:1, ruim boven de AA-eis van 4,5:1). Bij `CONFIRMED` volledige weergave met een toegankelijke externe link (nieuw tabblad, `rel="noopener"`-equivalent, programmatisch label, volledig toetsenbord-bereikbaar). Bij alle overige gevallen exact dezelfde neutrale melding, zodat geen metadata over een eerdere publicatie lekt. Pagina is nog niet gerouteerd vanuit `main.dart`/`HomePage` — bewust, want story-tekst sluit alleen koppeling met de bestaande "Ontdek"-zoekfunctie uit, en er is nog geen algemene routinglaag in de app.
- **Test (hkh-76, test-approved):** geen aparte code-wijzigingen bovenop hkh-74/hkh-75; de story-brede testdekking (backend unit-/Testcontainers-integratietests, Flutter widget-/semantiektests, contrasttests) is al tijdens de twee ontwikkel-subtaken gebouwd en door de tester akkoord bevonden.

**Getest:** volledig vangnet groen op alle onderdelen — backend `mvn clean verify` (256+ tests), frontend `flutter analyze`/`test`/`build web`, frontend-admin `flutter analyze`/`test`. Expliciete tests voor alle statuscombinaties, het zelfherstellende gedrag (zowel backend-integratie als Flutter-widgetniveau), toetsenbordbereikbaarheid van de externe link, WCAG-contrastratio's en de aria-expanded/aria-controls-toggle.

**Bewust niet gedaan:** geen navigatie/route vanuit de bestaande homepage naar de nieuwe pagina (buiten scope); geen wijziging aan `POST /api/external-verification` of de bestaande admin-bevestigingsflow van recordintake zelf; geen koppeling met de "Ontdek"-nieuwszoekfunctie; documentatie-update (`technical-spec.md`/`development.md`) is expliciet doorgeschoven naar subtaak hkh-78.

**Opmerking over rolinstructies:** zoals eerder gesignaleerd in de agent-tips, verschilt de phase-waarde tussen de summarizer-instructies in `.task.md` (`summary-finished`, met deploy-summary-markers) en het opdrachtcontract van deze taak (`summarized`, met `descriptionSummary`/`shortDescriptionSummary`). Ik volg het opdrachtcontract voor de output hieronder.
