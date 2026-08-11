# hkh-75 - Worklog

Story-context bij eerste pickup:
Frontend: publieke recorddetailpagina met sectie 'Externe bronverificatie'

Nieuwe recorddetailpagina in frontend/lib die de nieuwe `GET /api/records/{localIdentifier}`-route
(hkh-74) aanroept; in-/uitklapbare, toegankelijke sectie met tekst+icoon-statusweergave (nooit
uitsluitend kleur, contrast >=4,5:1), CONFIRMED-weergave (naam, geboortejaar, sterftejaar,
licentie, bevestigingsdatum, externe link met aria-aankondiging) en één neutrale melding voor
SAVED_WITHOUT_SOURCE/NO_INTAKE/gedegradeerd CONFIRMED. Geen koppeling aan DiscoverSection/
GET /api/news.

Stappenplan:
[x]: read issue and target docs (.task.md, development.md, technical-spec.md, hkh-74-worklog)
[x]: implement RecordPublicView-model + RecordPublicSource op BackendClient
[x]: implement ExternalSourceVerificationSection + RecordDetailPage
[x]: implement toegankelijke externe-bronlink (nieuw tabblad, rel=noopener-equivalent)
[x]: schrijf Flutter widget-/semantiektests voor alle ACs
[x]: run volledig vangnet (backend mvn verify, frontend analyze/test/build web, admin analyze/test)
[x]: update story-log met resultaten

Done / rationale:
- Nieuw model `frontend/lib/records/record_public_view.dart`: `RecordPublicStatus`-enum
  (`noIntake`/`savedWithoutSource`/`confirmed`), `RecordPublicView` (`fromJson`, velden matchen
  exact de backendrespons uit hkh-74: `localIdentifier`/`status`/`name`/`birthYear`/`deathYear`/
  `license`/`sourceUri`/`confirmedAt`) en de `RecordPublicSource`-interface, naar het bestaande
  patroon van `BackendStatusSource`/`LatestNewsSource`.
- `BackendClient` implementeert nu ook `RecordPublicSource` (`loadRecord`) via
  `GET /api/records/{localIdentifier}`, met dezelfde timeout-/foutafhandelingsconventie als
  `loadLatestNews`.
- Nieuwe module `frontend/lib/records/record_detail_page.dart`:
  - `ExternalSourceVerificationSection` (h2 "Externe bronverificatie", altijd in de semantiekboom;
    standaard uitgeklapt zodat de AC "zonder gebruikersinteractie zichtbaar" voor `CONFIRMED`
    voldaan is) met een toegankelijke in-/uitklapknop die `Semantics(expanded:, controlsNodes:)`
    gebruikt (het `aria-expanded`/`aria-controls`-equivalent op Flutter web; `controlsNodes`
    verwijst naar het `identifier` van de sectie-inhoud).
  - `CONFIRMED`: statuslabel "Extern geverifieerd" met zowel tekst als een los-gelabeld icoon
    (`Icoon Extern geverifieerd`, nooit uitsluitend kleur), naam, `Geboortejaar: <jaar>` en
    (indien aanwezig) `Sterftejaar: <jaar>` — beide al jaartal-only via de backend (hkh-74), hier
    puur getoond zonder verdere bewerking; licentie; de externe link; en "Bevestigd door
    beheerder op <dd-mm-jjjj>" op basis van `confirmedAt`.
  - Elk ander geval (`SAVED_WITHOUT_SOURCE`, `NO_INTAKE`, en impliciet een gedegradeerd
    `CONFIRMED` omdat de backend dat al als `SAVED_WITHOUT_SOURCE`-achtige neutrale status
    teruggeeft) toont exact dezelfde neutrale, niet-technische melding zonder velden en zonder
    link (`neutralExternalVerificationMessage`), om geen metadata over een eventuele eerdere
    publicatie te lekken.
  - `_ExternalSourceLink`: klikbare, zichtbaar gelabelde link ("Bekijk bron") met
    `Semantics(link:true, label:'... (opent externe bron in nieuw tabblad)')`, naar het patroon
    van `ExternalVerificationLinkView` in `frontend-admin`. Werkelijke navigatie (nieuw tabblad,
    zonder `window.opener` aan de nieuwe pagina bloot te stellen — het `rel="noopener"`-equivalent
    voor `window.open`) via een nieuwe, platformafhankelijke `openExternalLink`-helper
    (`external_link_launcher.dart`, met conditionele export naar een web-implementatie op
    `package:web`/`dart.library.html` en een no-op stub voor de Dart VM waarop `flutter test`
    standaard draait), zodat noch `flutter test` noch `flutter build web` breekt. `openLink` is
    injecteerbaar op `ExternalSourceVerificationSection` voor deterministische widgettests.
  - `RecordDetailPage`: laadt via `RecordPublicSource.loadRecord(localIdentifier)` en toont de
    sectie; niet gekoppeld aan `DiscoverSection`/`GET /api/news` (geen wijziging aan
    `main.dart`/`discover_section.dart`).
- Kleuren `ExternalSourceVerificationColors` (`confirmedForeground` 7,87:1,
  `neutralForeground` 10,05:1 tegen wit), beide boven de WCAG 2.1 AA-minimumdrempel van 4,5:1,
  getest via dezelfde contrastberekening als `PrivacyClassificationStatusColors`.
- Tests (`frontend/test/record_detail_page_test.dart`): initiële semantiekboom voor `CONFIRMED`
  (h2, statuslabel+icoon, naam, jaartallen, licentie, link, bevestigingsdatum) zonder voorafgaande
  interactie; expliciete regex-assertie dat de jaartaltekst geen dag-/maandgetal bevat; aparte
  tests voor `SAVED_WITHOUT_SOURCE` en `NO_INTAKE` die beide dezelfde neutrale melding tonen
  zonder link/velden; een zelfherstellend-gedragtest die drie opeenvolgende widget-pumps simuleert
  (CONFIRMED -> neutraal na degradatie -> automatisch weer CONFIRMED, zonder verdere actie);
  een toetsenbord-only test (`sendKeyEvent`, geen tap) die Tab-volgorde en Enter-activatie op de
  externe link bevestigt; een toggle-test die de semantiekboom-snapshot voor/na de klap-actie
  vergelijkt (`matchesSemantics(isButton:, hasExpandedState:, isExpanded:)` plus `controlsNodes`);
  twee contrasttests; en een regressietest die bevestigt dat `RecordDetailPage` uitsluitend via de
  geïnjecteerde `RecordPublicSource` laadt (geen koppeling aan nieuwszoekfunctie). Bestaande
  `discover_section_test.dart`/`widget_test.dart` blijven ongewijzigd en slagen nog steeds (dekt de
  AC "bestaande navigatie/zoekfunctie ongewijzigd").
- `frontend/test/backend_client_test.dart` uitgebreid met twee tests voor `BackendClient.loadRecord`
  (succesvolle parse, backend-foutafhandeling).
- `pubspec.yaml`: `web`-pakket (al transitieve afhankelijkheid via de Flutter-webengine) als
  directe afhankelijkheid toegevoegd, uitsluitend gebruikt in de web-specifieke
  `external_link_launcher_web.dart` (conditioneel geëxporteerd, dus niet meegecompileerd op de
  Dart VM waarop `flutter test`/`flutter analyze` draaien).
- De publieke recorddetailpagina/sectie is een nieuwe, op zichzelf staande module; er is bewust
  geen route vanuit `HomePage`/`DiscoverSection` toegevoegd (buiten scope, story-tekst sluit
  koppeling aan de "Ontdek"-zoekfunctie expliciet uit).
- `.factory/verification.yaml` ongewijzigd: geen nieuwe verificatiecommando's nodig, de bestaande
  zes commando's (backend `mvn verify`, frontend `analyze`/`test`/`build web`,
  frontend-admin `analyze`/`test`) dekken deze wijziging al.
- Volledig vangnet uitgevoerd en groen: `mvn -B --no-transfer-progress clean verify` (backend, 256
  tests, 0 failures/errors), `flutter analyze` (frontend, geen meldingen), `flutter test` (frontend,
  35 tests, alle geslaagd, ook met `--concurrency=1 --reporter expanded` gecontroleerd op stille
  crashes), `flutter build web` (frontend, slaagt inclusief de web-specifieke link-launcher),
  `flutter analyze`/`flutter test` (frontend-admin, ongewijzigd, blijft groen).

Niet gedaan / aangepast:
- Geen wijziging aan de backend (hkh-74 is al `review-approved`; deze subtaak was uitsluitend
  frontend).
- Geen route/link vanuit de bestaande homepage/`DiscoverSection` naar `RecordDetailPage`: expliciet
  buiten scope van deze story (geen koppeling aan de "Ontdek"-nieuwszoekfunctie).
- `docs/factory/technical-spec.md`/`development.md` niet bijgewerkt: dat is expliciet toegewezen
  aan de documentatie-subtaak `hkh-78`.
