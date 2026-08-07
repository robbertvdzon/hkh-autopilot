# hkh-autopilot-1 - Worklog

Story-context bij eerste pickup:
Flutter-webstatussemantiek en testdekking realiseren

Implementeer de unieke live-statussen en het vereiste retry- en focusgedrag in de gebruikersfrontend, voeg alle geautomatiseerde widgettests toe, leg vooraf de handmatige scenario's vast, concretiseer de factory-documentatie en het verificatievangnet, en voer een zelfreview uit.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Taakcontext en factory-instructies gelezen; er zijn geen aanvullende PO-comments of agent-tips.
- Alle service- en nieuwsstatussen gebruiken nu exact één `SemanticsRole.status` met het
  voorgeschreven label. Zichtbare kopieën en decoratieve indicatoren veroorzaken geen dubbele
  statusnode; nieuwsinhoud en gewone uitleg blijven afzonderlijk leesbaar.
- Beide retryknoppen hebben een expliciete focusrand van drie pixels en behouden natuurlijke
  lees-/Tab-volgorde en Material-activering met Enter en spatie. De retrycallbacks zijn block-bodied,
  zodat `setState` in debugmodus geen toegewezen `Future` retourneert.
- Widgettests toegevoegd voor alle statusvarianten en transities, unieke statusnodes, labels,
  focusacties, semantische volgorde, Tab-focus, focusweergave en beide activeringstoetsen. Bestaande
  homepage-inhoud en productvisieroute blijven afgedekt.
- Factorydocs zijn ingevuld met de echte structuur, flows, commando's, targettoolchain,
  deployment- en secretcontext. De Flutter CI-toolchain en lockfiles zijn op stable 3.44.7 / Dart
  3.12.2 uitgelijnd. Het machinevangnet bouwt nu ook de gebruikerswebfrontend.
- Zelfreview: geen route-, navigatie- of zichtbare inhoudsvolgordewijziging gevonden; geen secrets,
  persoonsgegevens, tracking, conflictmarkers of handmatige focusverplaatsing toegevoegd.

## Geautomatiseerd verificatiebewijs developer

Volledig vangnet uit `.factory/verification.yaml`, lokaal uitgevoerd op 2026-08-07:

- `backend`: `mvn -B --no-transfer-progress clean verify` — exit 0; 23 tests, 0 failures,
  0 errors, 0 skipped; build success.
- `frontend`: `flutter analyze` — exit 0; geen issues.
- `frontend`: `flutter test` — exit 0; 11 tests, alle geslaagd.
- `frontend`: `flutter build web` — exit 0; webbuild aangemaakt in `frontend/build/web`.
- `frontend-admin`: `flutter analyze` — exit 0; geen issues.
- `frontend-admin`: `flutter test` — exit 0; 4 tests, alle geslaagd.

De echte browser-/schermlezertest is bewust niet door de developer gesimuleerd; de testerrol voert
onderstaande vooraf vastgelegde scenario's uit op de gebouwde Flutter-webapp en vult de werkelijke
omgeving en waarnemingen aan.

## Herhaalbare handmatige testscenario's (vooraf vastgelegd)

Gemeenschappelijke voorbereiding: bouw de gebruikersfrontend vanuit `frontend/` met
`flutter build web --dart-define=API_BASE_URL=<test-backend>` en open `/` in een desktopbrowser
met Flutter-websemantiek ingeschakeld en een schermlezer actief. Gebruik browser-DevTools of een
gecontroleerde test-backend om de genoemde requests te vertragen, te laten falen of een vast
antwoord te geven. Noteer per scenario browser, besturingssysteem en schermlezer inclusief versies,
de geteste Git-revisie/build, verwachte en werkelijk gehoorde tekst, aantal aankondigingen,
focusgedrag, toetsenbordresultaat en afwijkingen. Gebruik uitsluitend synthetische nieuwsdata.

1. **Service laden naar beschikbaar.** Vertraag `GET /actuator/health` en `GET /api/version`,
   open `/`, en antwoord daarna met HTTP 200, healthstatus `UP` en synthetische versievelden.
   Verwacht eerst eenmaal ‘De historische omgeving wordt voorbereid.’ en daarna eenmaal
   ‘Service beschikbaar.’; de status krijgt geen focus en bestaande focus wordt niet verplaatst.
2. **Service laden naar fout, retry en beschikbaar.** Laat een van `GET /actuator/health` en
   `GET /api/version` falen (HTTP 500 of offline). Verwacht na de laadmelding eenmaal
   ‘De HKH-service is niet bereikbaar.’. Tab naar de direct daaropvolgende actie ‘Opnieuw proberen’
   en controleer de zichtbare focusindicator. Activeer eenmaal met Enter en herhaal het scenario
   eenmaal met spatie. Laat de retry vertraagd slagen. Verwacht per retry eerst eenmaal de
   laadmelding en daarna eenmaal ‘Service beschikbaar.’, zonder automatische focusverplaatsing.
3. **Laatste nieuws laden naar berichten.** Laat de service slagen, vertraag `GET /api/news` en
   antwoord met één of meer synthetische berichten. Verwacht eenmaal ‘Laatste nieuws wordt geladen.’
   en daarna eenmaal ‘Laatste nieuws geladen.’; geen status krijgt focus.
4. **Laatste nieuws laden naar leeg resultaat.** Laat de service slagen, vertraag `GET /api/news`
   en antwoord met HTTP 200 en `[]`. Verwacht eenmaal ‘Laatste nieuws wordt geladen.’ en daarna
   eenmaal ‘Er zijn nog geen nieuwsberichten.’, zonder focusverplaatsing.
5. **Laatste nieuws laden naar fout, retry en succes.** Laat de service slagen en laat
   `GET /api/news` falen (HTTP 500 of offline). Verwacht na de laadmelding eenmaal
   ‘Het laatste nieuws kon niet worden geladen.’. Tab naar de direct daaropvolgende actie
   ‘Opnieuw proberen’, controleer de focusindicator en activeer afzonderlijke runs met Enter en
   spatie. Laat de retry vertraagd eindigen met berichten of `[]`. Verwacht eerst eenmaal
   ‘Laatste nieuws wordt geladen.’ en daarna precies één passende succesmelding, zonder
   automatische focusverplaatsing.

## Testerresultaat hkh-2

Getest op revisie `7a9eba0c4232cbaa969f30166df760166d285c98` met de bestaande release-webbuild,
Chromium 149.0.7827.0 in headless-modus op Ubuntu 24.04. De API-verzoeken naar
`GET /actuator/health`, `GET /api/version` en `GET /api/news` zijn browserlokaal onderschept en
vertraagd of beantwoord met fouten, lege resultaten en uitsluitend synthetische nieuwsdata.

- Gerichte widgettest `flutter test test/widget_test.dart`: exit 0, 9 tests geslaagd.
- Browsergedrag groen voor service laden naar beschikbaar, servicefout en retry via zowel Enter
  als spatie, nieuws laden naar berichten, nieuws laden naar leeg, en nieuwsfout en retry via zowel
  Enter als spatie.
- In iedere gemeten overgang bevatte de Flutter-websemantiek exact de verwachte
  `role="status"`-node per actieve statusstroom, zonder `tabindex` of browserfocus. De zichtbare
  kopieën en indicatoren leverden geen tweede statusnode op.
- De retryknoppen volgden de foutstatus in de semantische DOM-volgorde. Via Tab was eerst de
  productvisieactie en daarna de nieuws-retry bereikbaar; de service-retry was de eerstvolgende
  knop. Beide retryknoppen hadden de zichtbare focusrand en activeerden met Enter en spatie.
- Screenshots: `/work/screenshots/hkh-2-service-and-news-success.png`,
  `/work/screenshots/hkh-2-service-retry-focused.png` en
  `/work/screenshots/hkh-2-news-retry-focused.png`.

Beperking: in de testcontainer is geen schermlezer aanwezig en er is geen preview-URL
geconfigureerd. Daardoor kon de vereiste werkelijk gehoorde tekst en het werkelijke aantal
schermlezeraankondigingen niet worden vastgesteld. De Chromium DOM-/ARIA-inspectie is hiervoor
geen vervanging. Het volledige revisiongebonden vangnet wordt na deze tester-run door de
factory-harness uitgevoerd en is niet dubbel gedraaid.
