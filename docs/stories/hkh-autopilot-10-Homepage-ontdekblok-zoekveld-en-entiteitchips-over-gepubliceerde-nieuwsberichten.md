# hkh-autopilot-10 - Homepage-ontdekblok: zoekveld en entiteitchips over gepubliceerde nieuwsberichten, met toegankelijke resultaten

## Story

Homepage-ontdekblok: zoekveld en entiteitchips over gepubliceerde nieuwsberichten, met toegankelijke resultaten

<!-- refined-by-factory -->

## Samenvatting
We voegen een zoek- en ontdekblok toe aan de HKH-homepage: bezoekers kunnen nieuwsberichten doorzoeken op vrije tekst of aanklikbare labels (plek/persoon/gebeurtenis). Een klik of zoekopdracht toont een resultatenlijst met titel, samenvatting en herkomst; bij geen resultaten zien bezoekers duidelijke suggesties in plaats van een lege pagina. Elk resultaat is volledig uit te lezen in een detailweergave met een terugknop. Het geheel is volledig te bedienen met alleen het toetsenbord en meldt het aantal resultaten hoorbaar voor schermlezers. Er wordt uitsluitend al gepubliceerd nieuws gebruikt — nooit gegevens uit interne beoordelings- of verificatieprocessen.

## Scope
- Nieuw ontdekblok op de bestaande Flutter-gebruikersfrontend-homepage (`frontend/lib`, route `/`), naast de bestaande servicestatus- en "Laatste nieuws"-secties: een gelabeld zoekveld plus een rij klikbare entiteitchips (types `PLEK`/`PERSOON`/`GEBEURTENIS`).
- Databron is uitsluitend het bestaande, door hkh-autopilot-9 uitgebreide `GET /api/news`-contract (`{items, total, entities}`, queryparameters `q` en `entity`) via `backend_client.dart#loadLatestNews`. Er komt geen nieuwe backendroute of -contractwijziging; dit is een pure frontendstory.
- Chipklik of zoekopdracht toont een resultatenlijst: per item titel, samenvatting, entiteitstype-badge en de bestaande, niet-lege bronregel ("Afkomstig uit gepubliceerd HKH-nieuwsbericht, gepubliceerd op ...") uit het API-veld `source`.
- Nul resultaten tonen een niet-lege lege-staat met suggestiechips (bijv. de geaggregeerde `entities`-lijst uit de respons), nooit een lege of foutieve weergave.
- Elk resultaat opent een detailweergave met de volledige berichttekst (`message`), publicatiedatum en bronvermelding, plus een werkende "terug naar resultaten"-link/knop.
- Alle interactieve elementen (zoekveld, chips, resultaatkaarten, terug-/wisknoppen) zijn volledig met het toetsenbord bedienbaar (Tab-volgorde, activeren via Enter/Spatie) en vormen de enige "primaire ontdekactie" op de homepage — er komt geen los, tweede toegankelijkheidspad naast dit blok.
- Toegankelijkheid wordt gebouwd en getest volgens de bestaande, herhaaldelijk toegepaste repo-conventie (zie `technical-spec.md`/`functional-spec.md` en eerdere stories hkh-autopilot-4/5/6): Flutter widget-/semantiektests op de semantiekboom (inclusief `Semantics(liveRegion: true)` voor de resultaattelling, naar het bestaande patroon uit de recordintake-form) plus een gerichte kleur-/contrasttest (WCAG-formule, ≥4,5:1). Er is geen Playwright- of axe-core-tooling in deze Kotlin/Flutter-repo; die vervanging is in dit domein al meermaals toegepast en wordt hier op dezelfde manier gedaan.
- Buiten scope: elke reconciliatie/intrekking van INTERNAL kandidaat 13 — dat is een orchestrator-aangelegenheid, geen implementatiewerk in deze story. Ook buiten scope: wijzigingen aan `GET /api/news`, record-intake-, privacyclassificatie- of externe-verificatiedata, en enige backendwijziging.

## Acceptance criteria
- Een Flutter-widgettest laadt de homepage (met een gemockte `LatestNewsSource`/backend-respons), controleert dat het zoekveld en minstens één entiteitchip renderen op basis van de `entities`-data uit de API-respons, simuleert een tik/klik op een chip en controleert dat een resultatenlijst met minimaal 1 item en een niet-lege bronregel verschijnt.
- Een Flutter-widgettest voert een onbekende/onzinnige zoekterm in (via het zoekveld) en controleert dat de lege-staat-component met suggestiechips verschijnt in plaats van een lege of foutieve weergave.
- Een Flutter-widgettest tikt/klikt een resultaatkaart aan en controleert dat de detailweergave de volledige berichttekst, publicatiedatum en bronvermelding toont, plus een werkende "terug naar resultaten"-widget die terugkeert naar de resultatenlijst.
- Een Flutter-widgettest doorloopt zoekveld, chips, resultaatkaarten en terug-/wisknoppen via `tester.sendKeyEvent`/focus-traversal in logische volgorde en activeert elk element uitsluitend via Enter/Spatie (geen tap/muissimulatie), als enige "primaire ontdekactie" op de homepage.
- Een Flutter-widgettest op de semantiekboom controleert dat elk interactief element (zoekveld, chips, resultaatkaarten, terug-/wisknop) een correct semantisch label/rol heeft, en een gerichte contrasttest berekent de contrastratio van de gebruikte kleurwaarden (badges, chips, tekst) volgens de WCAG-formule (≥4,5:1) — dit vervangt axe-core conform de bestaande repo-conventie.
- Een Flutter-widgettest bevestigt dat de resultatentelling wordt blootgesteld via een `Semantics(liveRegion: true)`-widget en dat de aangekondigde tekst na elke nieuwe zoekactie of chipselectie wijzigt.
- Een Flutter-widget-/model-test bevestigt dat de rendering uitsluitend velden uit `LatestNewsItem`/`NewsEntity` (afkomstig van `GET /api/news`) gebruikt, en dat er geen enkel veld uit record-intake-, privacyclassificatie- of externe-verificatiemodellen wordt gebruikt of getoond.
- `docs/factory/technical-spec.md` en `functional-spec.md` worden bijgewerkt met het nieuwe homepage-ontdekblok (componenten, databron, toegankelijkheidsaanpak), analoog aan bestaande secties.

## Aannames
- "Playwright-e2e-test" en "axe-core-scan" uit de oorspronkelijke issuetekst zijn vervangen door Flutter widget-/semantiektests plus een gerichte contrasttest, conform de expliciete, herhaaldelijk vastgelegde repo-conventie (`technical-spec.md`/`functional-spec.md`, stories hkh-autopilot-4/5/6): er is geen Playwright- of axe-core-tooling (geen Node-toolchain) in deze Kotlin/Flutter-repo. De in de issuetekst genoemde "eerder vastgelegde Flutter-web-a11y-inspectietechniek (flt-semantics-placeholder + CDP AX-boom)" en de "live-inspectiebevindingen (Playwright, 2026-08-09)" zijn niet in de repo teruggevonden (geen precedent, geen tooling); deze story implementeert toegankelijkheid dus met de wél bestaande, geverifieerde repo-aanpak in plaats van deze niet-onderbouwde referentie.
- De reconciliatie met INTERNAL kandidaat 13 (introkken/superseden) is een orchestrator-beslissing buiten deze story; deze story levert alleen het toegankelijke ontdekblok zelf op.
- "Gepubliceerd nieuwsbericht" blijft, conform hkh-autopilot-9, gelijk aan "aanwezig in `latest_news`" — er is geen apart publicatiestatusbegrip.
- Suggestiechips in de lege staat zijn de bestaande, geaggregeerde `entities`-lijst uit de API-respons (geen nieuwe aanbevelingslogica).

## Eindsamenvatting

## Eindsamenvatting — hkh-autopilot-10: Homepage-ontdekblok

**Gebouwd:**
Er is een nieuw "ontdekblok" toegevoegd aan de Flutter-homepage van de gebruikersfrontend (`frontend/lib/main.dart`), direct na de bestaande "Laatste nieuws"-sectie. Het bestaat uit:
- Een gelabeld zoekveld en een rij klikbare entiteitchips (PLEK/PERSOON/GEBEURTENIS), aangedreven door het bestaande `GET /api/news`-contract (`{items, total, entities}`) met de nieuwe `q`/`entity`-parameters.
- Een resultatenlijst (titel, samenvatting, entiteitstype-badge, bronregel) en een niet-lege lege-staat met suggestiechips wanneer een zoekopdracht niets oplevert.
- Een detailweergave per resultaat met volledige berichttekst, publicatiedatum, bronvermelding en een terugknop (via `Navigator.push`/AppBar-backbutton, zelfde patroon als de bestaande productvisiepagina).
- Volledige toetsenbordbediening (Tab-volgorde, Enter/Spatie-activatie) en een `Semantics(liveRegion: true)` voor het hoorbaar aankondigen van de resultatentelling, naar het bestaande RecordIntakeForm-patroon.
- Nieuwe, contrastgecontroleerde badge-/chipkleuren (`NewsEntityBadgeColors`, alle ≥6,5:1) naar het patroon van `PrivacyClassificationStatusColors`.

**Gemaakte keuzes:**
- `LatestNewsSource.loadLatestNews` is bewust breaking gewijzigd naar een `NewsSearchResult`-object i.p.v. een kale lijst; alle bestaande call sites en tests zijn meeverhuisd.
- Logica is in een apart bestand (`frontend/lib/news/discover_section.dart`) gezet om `main.dart` behapbaar te houden.
- Het ontdekblok is ná de bestaande "Laatste nieuws"-sectie geplaatst om de bestaande Tab-volgordetest niet te breken.
- `onEditingComplete` i.p.v. `onSubmitted` gebruikt in het zoekveld, om te voorkomen dat Flutter's standaard unfocus-gedrag de Tab-volgorde na een zoekactie zou verstoren.

**Getest:**
Nieuwe Flutter widget-/semantiek-/contrasttests (`discover_section_test.dart`, 9 tests) en een uitgebreide `backend_client_test.dart` dekken: chip-klik → resultatenlijst met bronregel, onzin-zoekterm → lege-staat met suggesties, resultaatkaart → detailweergave met terug-navigatie, volledige toetsenbordbediening via `sendKeyEvent`, semantische labels/rollen, WCAG-contrastratio's, en dat uitsluitend `LatestNewsItem`/`NewsEntity`-velden gebruikt worden (geen record-intake-, privacyclassificatie- of externe-verificatiedata). Vangnet volledig groen: backend `mvn verify` (216 tests), `frontend` `flutter analyze`/`flutter test` (22/22, incl. 9 nieuwe) / `flutter build web`, `frontend-admin` ongewijzigd geverifieerd (22/22).

**Documentatie:** `technical-spec.md` en `functional-spec.md` zijn bijgewerkt met het nieuwe ontdekblok, componenten, databron en toegankelijkheidsaanpak.

**Bewust niet gedaan:** Geen wijzigingen aan `GET /api/news`, record-intake-, privacyclassificatie- of externe-verificatiedata; geen reconciliatie van INTERNAL kandidaat 13 (dat is orchestrator-werk, buiten deze story); geen Playwright/axe-core (niet aanwezig in deze Kotlin/Flutter-repo — vervangen door de bestaande, repo-conventie van Flutter widget-/semantiek-/contrasttests).

**Noot over rolinstructies:** de rolspecifieke instructies in `.task.md` vragen om af te sluiten met `{"phase":"summary-finished"}`, terwijl het opdrachtcontract `{"phase":"summarized"}` voorschrijft. Conform de bestaande agent-tip volg ik het opdrachtcontract; dit verschil is hiermee expliciet gemeld.

<!-- deploy-summary:start -->
Op de startpagina kun je nu nieuwsberichten doorzoeken: typ een zoekterm of klik op een label zoals een plaats, persoon of gebeurtenis om relevante berichten te vinden. Elk bericht kun je openen voor de volledige tekst, met een knop om weer terug te gaan naar de lijst. De hele zoekfunctie is ook volledig te bedienen met alleen het toetsenbord en werkt goed samen met schermlezers.
<!-- deploy-summary:end -->
