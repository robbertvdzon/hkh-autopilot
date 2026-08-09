# hkh-autopilot-10 - Worklog

Story-context bij eerste pickup:
Homepage-ontdekblok: zoekveld, entiteitchips, resultaten, lege staat en detailweergave

Breid LatestNewsSource/BackendClient.loadLatestNews uit met optionele q/entity-parameters en het volledige {items,total,entities}-contract. Bouw op de homepage (main.dart, naast de bestaande service- en "Laatste nieuws"-secties) een ontdekblok: gelabeld zoekveld, entiteitchips (PLEK/PERSOON/GEBEURTENIS), resultatenlijst (titel, samenvatting, entiteitstype-badge, bronregel), niet-lege lege-staat met suggestiechips (de geaggregeerde entities), en een detailweergave (volledige tekst, publicatiedatum, bron, terug-actie). Toegankelijkheid volgens bestaande repo-conventie: Semantics(liveRegion: true) voor de resultatentelling (naar het RecordIntakeForm-patroon), volledige Tab-/Enter/Spatie-bediening met focusrand, en vaste badge-/chipkleuren met contrastratio >=4.5:1 naar het PrivacyClassificationStatusColors-patroon. Schrijf alle bijbehorende Flutter widget-/semantiek-/contrasttests en de backend_client_test-uitbreiding voor q/entity/total/entities. Zorg dat uitsluitend LatestNewsItem/NewsEntity-velden gebruikt worden, nooit record-intake-, privacyclassificatie- of externe-verificatiedata.

Stappenplan:
[x]: read issue and target docs (.task.md, development.md, technical-spec.md, functional-spec.md)
[x]: LatestNewsSource/BackendClient.loadLatestNews uitbreiden met q/entity + {items,total,entities}
[x]: DiscoverSection widget (zoekveld, entiteitchips, resultaten, lege staat, detailweergave)
[x]: DiscoverSection in main.dart homepage opnemen naast service-/nieuwsstatus
[x]: bestaande widget_test.dart fakes aanpassen aan nieuw contract
[x]: nieuwe widget-/semantiek-/contrasttests voor het ontdekblok
[x]: technical-spec.md / functional-spec.md bijwerken
[x]: volledig vangnet uit development.md draaien (backend mvn verify, frontend/frontend-admin analyze+test+build)

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- `LatestNewsSource.loadLatestNews` retourneert nu `NewsSearchResult` ({items,total,entities}) i.p.v.
  een kale itemlijst en accepteert optionele `q`/`entity`; dit is een breaking change voor alle
  bestaande call sites (`_LatestNewsSection`, tests), bewust conform de story-scope.
- Nieuw bestand `frontend/lib/news/discover_section.dart` bevat het volledige ontdekblok
  (zoekveld, chips, resultaten, lege staat, detailpagina, badge-/chipkleuren) om `main.dart`
  behapbaar te houden, naar het patroon van de bestaande scheiding tussen `main.dart` en
  `lib/news/latest_news.dart`.
- Detailweergave gebruikt `Navigator.push`/`AppBar`-terugknop (zelfde patroon als
  `ProductVisionPage`) in plaats van een los "terug"-widget binnen dezelfde pagina: de standaard
  AppBar-backbutton is al volledig toetsenbordbedienbaar en behoudt de resultatenlijst-state.
- Badge-/chipkleuren (`NewsEntityBadgeColors`) zijn nieuwe vaste voorgrondkleuren tegen wit,
  gecontroleerd op >=4.5:1 (PLEK 7.87:1, PERSOON 8.63:1, GEBEURTENIS 6.57:1), naar het patroon van
  `PrivacyClassificationStatusColors`.
- `DiscoverSection` staat in `main.dart` ná `_LatestNewsSection` (niet ervoor): beide secties
  bevragen dezelfde `LatestNewsSource` onafhankelijk bij `initState`, en met `DiscoverSection`
  eerst in de boom zou de bestaande Tab-volgordetest ("Lees onze productvisie" → "Opnieuw
  proberen") breken doordat het zoekveld/de wisknop ertussen zouden komen. Door `DiscoverSection`
  na `_LatestNewsSection` te plaatsen, blijft die bestaande volgorde ongewijzigd; wel is het aantal
  `loadLatestNews`-aanroepen op de gedeelde bron in `widget_test.dart`'s retry-scenario van 2 naar
  3 gegaan (initiële aanroep van beide secties + de retryklik) — bijgewerkt met een toelichtende
  comment op de call-volgorde-aanname.
- Het zoekveld gebruikt `onEditingComplete` in plaats van `onSubmitted`: Flutter's
  standaardafhandeling van `onSubmitted` unfocust het veld eerst (zie `EditableText._finalizeEditing`),
  wat de Tab-volgorde na een zoekopdracht zou terugzetten naar het begin van de pagina in plaats van
  door te gaan naar de eerstvolgende chip. `onEditingComplete` slaat dat standaardgedrag over.
- Vangnet-resultaten: backend `mvn -B --no-transfer-progress clean verify` → 216 tests, 0
  failures/errors, BUILD SUCCESS; `frontend`: `flutter analyze` schoon, `flutter test`
  (concurrency=1) 22/22 groen (incl. nieuwe `discover_section_test.dart`, 9 tests), `flutter build
  web` succesvol; `frontend-admin`: `flutter analyze` schoon, `flutter test` 22/22 groen
  (ongewijzigd, ter controle opnieuw gedraaid).
- `.factory/verification.yaml` behoefde geen wijziging: geen nieuwe commando's, working
  directories of pathPrefixes nodig voor deze story.
