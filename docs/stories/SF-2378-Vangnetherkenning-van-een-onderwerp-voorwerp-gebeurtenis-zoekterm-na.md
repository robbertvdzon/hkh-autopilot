# SF-2378 - Vangnetherkenning van een onderwerp/voorwerp/gebeurtenis-zoekterm (na persoon en plek/gebouw)

## Story

Vangnetherkenning van een onderwerp/voorwerp/gebeurtenis-zoekterm (na persoon en plek/gebouw)

<!-- refined-by-factory -->

## Scope
Breid `frontend/lib/personquery/person_query_interpreter.dart` uit met een derde, laatste herkenningstak (vangnet) voor onderwerpen, voorwerpen en gebeurtenissen. Deze tak wordt uitsluitend uitgevoerd nadat is vastgesteld dat noch de bestaande persoonsnaam-regel noch de bestaande plek/gebouw-regel een kandidaat oplevert. Er wordt geen enkele externe aanroep (Europeana, Wikidata) gedaan en er wordt geen nieuw scherm gebouwd; de uitkomst is uitsluitend een nieuw, expliciet veld `topicSearchTerm: String?` op het bestaande `PersonQueryInterpretation`-resultaatobject, bedoeld als invoer voor de latere vervolgstory ("topic-europeana-bronraadpleging").

Normalisatie voor de onderwerp-tak:
1. Hergebruik de bestaande `_stripWords`-verwijdering met de bestaande `_questionWords`, `_functionWords` en `_fixedContextWords`-lijsten, exact zoals de persoons- en plek/gebouw-regels die al gebruiken.
2. Verwijder daarnaast het losstaande woord "Heemskerk" (ongeacht hoofdlettergebruik) uit de tekst.
3. Bepaal of er, na deze normalisatie, minimaal één overgebleven woord van drie letters of langer bestaat dat niet voorkomt in de bestaande landmark-trefwoordenlijst (`_landmarkWords`). Is dat zo, dan is er een kandidaat; zo niet, dan niet.
4. Is er een kandidaat: stel `topicSearchTerm` samen als de aaneengesloten tekstspanne uit de OORSPRONKELIJKE vraag (originele spelling, hoofdlettergebruik en spatiëring) die loopt van het eerste tot en met het laatste overgebleven (niet-verwijderde) woord. Eventuele woorden die tussen die twee ankerwoorden in staan en zelf wél verwijderd zouden zijn (zoals een voorzetsel), blijven binnen die spanne behouden — er wordt dus niet simpelweg alleen de overgebleven woorden met een spatie aan elkaar geplakt.
5. Is er geen kandidaat: `topicSearchTerm` blijft `null`, en er verandert niets aan het bestaande gedrag van de aanroepende pagina (`person_query_page.dart` hoeft niet gewijzigd te worden: die leest `topicSearchTerm` in deze story nog niet uit, dus de bestaande `!hasRecognizedName` → "geen betrouwbare bron"-afhandeling blijft vanzelf intact).

Geen wijziging aan de bestaande persoons- of plek/gebouw-testresultaten, geen netwerkaanroepen, geen caching, geen UI-wijziging.

## Acceptance criteria
- De onderwerp-herkenning wordt alleen uitgevoerd nadat is vastgesteld dat zowel de bestaande persoonsnaam-regel als de bestaande plek/gebouw-regel geen kandidaat opleveren voor de ingevoerde vraag, en nooit vóór of naast die twee regels.
- De vraagtekst wordt genormaliseerd met de daadwerkelijke, bestaande `_stripWords`-functie en de bestaande `_questionWords`/`_functionWords`/`_fixedContextWords`-lijsten uit `person_query_interpreter.dart`, aangevuld met één nieuwe stap die het losstaande woord "Heemskerk" verwijdert.
- Blijft na deze normalisatie minimaal één woord van drie letters of langer over dat geen bestaand landmark-trefwoord (`_landmarkWords`) is, dan wordt `topicSearchTerm` gevuld met de tekstspanne uit de oorspronkelijke vraag (originele spelling/hoofdlettergebruik/spatiëring) die loopt van het eerste tot het laatste overgebleven woord, inclusief eventuele tussenliggende verwijderde woorden (zoals een voorzetsel) binnen die spanne.
- Blijft er niets bruikbaars over (geen woord van drie letters of langer, of uitsluitend landmark-trefwoorden), dan blijft `topicSearchTerm` `null`; er wordt geen enkele Europeana- of Wikidata-aanroep gedaan en het bestaande "Hiervoor vinden we geen betrouwbare bron"-gedrag op het startscherm blijft ongewijzigd.
- Voor de voorbeeldvraag "Wat weten we over de watersnood van 1916 in Heemskerk?" levert de herkenning exact de onderwerp-zoekterm "watersnood van 1916" op (zie Aannames voor de precieze, deterministische regel waarmee dit voorbeeld met de bestaande code haalbaar is).
- Bestaande tests voor de persoonsnaam- en plek/gebouw-regels (`person_query_interpreter_test.dart`) blijven ongewijzigd slagen.
- De volledige herkenningslogica van deze story bevat geen enkele netwerkaanroep en is volledig geïsoleerd unit-testbaar op basis van invoertekst en verwachte `topicSearchTerm` (of de afwezigheid daarvan), met nieuwe tests toegevoegd aan `person_query_interpreter_test.dart`.

## Aannames
- **Contradictie in het autoritatieve voorbeeld, opgelost als volgt.** Het letterlijk toepassen van de ONGEWIJZIGDE bestaande `_stripWords`-lijsten (`_questionWords`, `_functionWords`, `_fixedContextWords`) plus alleen de Heemskerk-verwijdering op "Wat weten we over de watersnood van 1916 in Heemskerk?" laat "weten we over watersnood 1916" over: "van" wordt namelijk al door de bestaande `_functionWords`-lijst verwijderd (het staat er al in, voor de persoons-/plek-regels), terwijl "weten", "we" en "over" in géén van de bestaande lijsten voorkomen. Het voorbeeld eist echter exact "watersnood van 1916" — dus mét "van" en zónder "weten we over". Met een pure woord-voor-woord filter (verwijderde woorden weglaten, overgebleven woorden aaneenrijgen) is dit voorbeeld met de huidige lijsten wiskundig niet haalbaar zonder óf de bestaande functiewoordenlijst te wijzigen (verboden: mag persoons-/plek-regels niet raken) óf extra woorden toe te voegen.
  - **Gekozen, niet-blokkerende aanname:** (a) de developer voegt de generieke, niet-hoofdletter-gevoelige connectiewoorden "we", "weten" en "over" toe aan de bestaande `_functionWords`-set (uitbreiden, niet herschrijven — dit past bij de expliciete tekst dat de opsomming in de epic "illustratief, niet uitputtend" is); en (b) `topicSearchTerm` wordt niet samengesteld door de overgebleven tokens simpelweg met een spatie aaneen te rijgen, maar door de aaneengesloten tekstspanne uit de OORSPRONKELIJKE vraag te nemen die van het eerste tot het laatste overgebleven woord loopt, inclusief eventuele daartussen gelegen woorden die zelf wél verwijderd zouden zijn (zoals "van"). Dit is een letterlijke lezing van de eis "de resterende woordreeks... met behoud van de originele spaties tussen de overgebleven woorden": het woord "van" zit tussen de twee overgebleven ankerwoorden "watersnood" en "1916" in en blijft daardoor behouden, terwijl "de" (vóór het eerste ankerwoord) en "in"/"Heemskerk" (ná het laatste ankerwoord) buiten de spanne vallen.
  - Met deze twee aanvullingen produceert de bestaande normalisatie plus de twee nieuwe stappen exact "watersnood van 1916" voor het autoritatieve voorbeeld, zonder de bestaande persoons- of plek/gebouw-testresultaten te raken (geen van de drie toegevoegde woorden is een hoofdletterwoord, landmark-trefwoord of anderszins relevant voor die regels).
  - Mocht de PO bij review een andere, eenvoudiger regel bedoeld hebben (bijvoorbeeld: negeer het exacte voorbeeld en accepteer een andere zoekterm), dan is dat een gerichte correctie op deze ene aanname, niet op de rest van de story.
- `person_query_page.dart` en overige aanroeppunten worden in deze story niet gewijzigd: niets leest `topicSearchTerm` nog uit, dus het bestaande "geen betrouwbare bron"-gedrag blijft automatisch intact zonder extra werk.
- De landmark-uitsluiting in stap 3 gebruikt dezelfde `_landmarkWords`-set als de bestaande plek/gebouw-regel, ongewijzigd.

Product-Factory-Api-Version: 2
Product-Factory-Product-Id: hkh-autopilot
Product-Factory-Source-Story-Id: f7dd9320-9c5f-41b6-a45d-5a240482504d
Product-Factory-Source-Story-Version: 1
Product-Factory-Idempotency-Key: product-factory:hkh-autopilot:story:f7dd9320-9c5f-41b6-a45d-5a240482504d:v1
Product-Factory-Package-Sha256: 166f130fa057aa884cf9095b8e47044cdf65dbf8b1f65e906145ffea024c9b99

## Eindsamenvatting

Ik heb voldoende context. Nu de eindsamenvatting samenstellen.

## Eindsamenvatting SF-2378

**Wat is gebouwd**
`frontend/lib/personquery/person_query_interpreter.dart` is uitgebreid met een derde, laatste herkenningstak voor onderwerpen/voorwerpen/gebeurtenissen ("vangnet"), die alleen draait als noch de persoonsnaam-regel noch de plek/gebouw-regel een kandidaat oplevert. Het resultaatobject `PersonQueryInterpretation` heeft een nieuw veld `topicSearchTerm: String?`. Er wordt geen enkele externe aanroep (Europeana/Wikidata) gedaan en geen UI gewijzigd; `person_query_page.dart` blijft ongemoeid, dus het bestaande "geen betrouwbare bron"-gedrag blijft intact.

**Belangrijkste keuzes**
- De bestaande `_functionWords`-set is uitgebreid met "we", "weten", "over" — nodig om het autoritatieve voorbeeld ("Wat weten we over de watersnood van 1916 in Heemskerk?" → "watersnood van 1916") haalbaar te maken zonder de bestaande persoons-/plek-regels te raken. Deze aanname stond al expliciet, niet-blokkerend, in de story.
- Omdat de bestaande `_stripWords`-helper geen tekstposities behoudt, is een aparte, tokenpositie-bewuste helper (`_findTopicSearchTerm`) gebouwd die per whitespace-token bepaalt of het verwijderd zou worden (zelfde woordenlijsten + onvoorwaardelijke "Heemskerk"-verwijdering), en vervolgens de spanne in de ORIGINELE tekst reconstrueert van het eerste tot het laatste overgebleven ankerwoord — inclusief tussenliggende verwijderde woorden zoals "van".

**Wat is getest**
- Unit tests toegevoegd aan `person_query_interpreter_test.dart`: het autoritatieve voorbeeld, geen herkenning bij bestaande naam-kandidaat, geen herkenning bij bestaande plek/gebouw-kandidaat, en een geen-kandidaat-geval (alleen landmark-woord over).
- Volledig vangnet uit `development.md` gedraaid en groen: backend `mvn clean verify` (292 tests), frontend `flutter analyze`/`flutter test` (108 tests, incl. nieuwe topicSearchTerm-tests)/`flutter build web`, frontend-admin `flutter analyze`/`flutter test` (22 tests).
- Tester heeft daarnaast met tijdelijke, weer verwijderde handmatige checks extra randgevallen geverifieerd (o.a. tussenliggende verwijderde woorden, alleen-Heemskerk-invoer, te-kort-woord-invoer) — geen bugs gevonden.

**Bewust niet gedaan**
Geen wijziging aan `person_query_page.dart` of andere aanroeppunten (uitlezen van `topicSearchTerm` is voor een vervolgstory), geen netwerkaanroepen, geen caching, geen wijziging aan bestaande persoons-/plek-testresultaten.

**Noot over procesafwijking**: de rolinstructies in `.task.md` vragen om af te sluiten met `{"phase":"summary-finished"}`, terwijl het opdrachtcontract `{"phase":"summarized", ...}` voorschrijft. Conform bekende agent-tip volg ik het opdrachtcontract.

<!-- deploy-summary:start -->
We hebben een nieuwe, slimme laatste-redmiddel-herkenning toegevoegd voor vragen over gebeurtenissen of onderwerpen (zoals een ramp of gebeurtenis), naast de bestaande herkenning van personen en plekken/gebouwen. Dit is een technische voorbereiding: gebruikers zien op dit moment nog geen verschil in de app, want het resultaat wordt pas in een volgende stap gebruikt.
<!-- deploy-summary:end -->
