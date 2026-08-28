# SF-2311 - Persoonsnaam herkennen en de betekenis van 'Heemskerk' ondubbelzinnig maken vóór het zoeken

## Story

Persoonsnaam herkennen en de betekenis van 'Heemskerk' ondubbelzinnig maken vóór het zoeken

<!-- refined-by-factory -->

## Scope
Implementeer in de bestaande Flutter-gebruikersfrontend (`frontend/lib`) de eerste drie schermen van de negen hoofdroute-schermen uit de epic, volledig client-side, zonder enige aanroep naar Open Archieven Records/Search of Records/Show:

1. **Startscherm** (screenKey `start`): een nieuw toegankelijk instappunt (niet de bestaande homepage, die ongewijzigd blijft) met exact één tekstinvoerveld met programmatisch label "Stel je vraag over Heemskerk", minstens één voorbeeldvraag met een volledige persoonsnaam, een dekkingsbeschrijving ("Open Archieven-genealogie voor Heemskerk, met Wikidata als aanvullende context") en een zichtbare mededeling dat een langer lopende zoekopdracht binnen de sessie kan doorlopen. Volg qua structuur en informatiehiërarchie de aangeleverde desktop- en mobile-artifacts (`hkh-sessiezoek-v19-01-start-*`).
2. **Deterministische vraaginterpretatie**, uitgevoerd bij het indienen van de vraag, vóór enige externe aanroep:
   - Normaliseer de ingevoerde vraag door achtereenvolgens (case-insensitive, op woordgrenzen) te verwijderen: (a) vraagwoorden `wie, wat, waar, wanneer, welke, hoe`; (b) functiewoorden/lidwoorden `was, is, geboren, getrouwd, overleden, gedoopt, de, het, een, van, in, op, te, uit`; (c) de vaste lijst `Heemskerk, Noord-Holland, Nederland, januari, februari, maart, april, mei, juni, juli, augustus, september, oktober, november, december`.
   - Blijven na verwijdering minimaal twee opeenvolgende woorden over die met een hoofdletter beginnen: persoonsnaam herkend (eerste woord = voornaam-kandidaat, rest = achternaam-kandidaat). Blijft precies één los hoofdletterwoord over: geen herkende naam, geen zoekopdracht.
   - Een resterend jaartal (4 cijfers)/periode-aanduiding en een resterend gebeurtenistype-woord (bijv. `geboorte`, `huwelijk`, `overlijden`, `doop`) worden als aparte, optionele zoekbeperkingen bewaard en ongewijzigd doorgegeven (opslaan in state; er is in deze story geen vervolgscherm dat ze toont).
3. **Geen herkende naam**: toon scherm `no-reliable-source` met letterlijk "Hiervoor vinden we geen betrouwbare bron", Open Archieven vermeld als "Niet uitgevoerd · persoonsnaam ontbreekt", en uitsluitend verfijningsvoorstellen die zelf een herkenbare persoonsnaam bevatten. Geen enkele Wikidata- of Open Archieven-aanroep. Volg de aangeleverde artifacts (`hkh-sessiezoek-v19-08-geen-betrouwbare-bron-*`).
4. **Voorzetsel-gebaseerde disambiguatie van "Heemskerk"**: doorzoek de oorspronkelijke (niet-genormaliseerde) vraagtekst op het letterlijke woord "Heemskerk" (case-insensitive).
   - Direct voorafgegaan door `in`, `te`, `uit` of `van`: ondubbelzinnig plaats Wikidata Q9926, geen keuzescherm.
   - Komt "Heemskerk" voor als los hoofdletterwoord náást een herkende persoonsnaam zonder zo'n direct voorafgaand voorzetsel: ambigu.
5. **Meaning-selection scherm** (screenKey `meaning-selection`, uitsluitend bij ambigu geval): radiogroep met Q9926 (plaats) en Q91564725 (achternaam), oorspronkelijke vraag zichtbaar. Haal bij tonen live op: Wikidata `wbsearchentities` (search=Heemskerk, language=nl, type=item, format=json) en vervolgens `Special:EntityData/Q9926.json` en `Special:EntityData/Q91564725.json` voor het actuele NL-label en de beschrijving per optie. Bij een mislukte live oproep (netwerkfout, time-out, ongeldige respons): toon de radiogroep met vaste fallback-labels "Q9926 · Heemskerk (plaats)" en "Q91564725 · Heemskerk (achternaam)" plus een zichtbare storingsmelding; de keuze blijft bruikbaar. Resultaten van de twee betekenissen worden nooit samengevoegd; de gekozen betekenis is bedoeld als vaste zoekbeperking voor een latere vervolgroute. Volg de aangeleverde artifacts (`hkh-sessiezoek-v23-02-betekenis-kiezen-*`), inclusief de knoppen "Zoek met deze betekenis" en "Vraag aanpassen" (laatste brengt de gebruiker terug naar het startscherm met de oorspronkelijke vraag nog ingevuld).
6. **Toegankelijkheid** op alle drie schermen: Tab/Shift+Tab in logische volgorde, Enter dient in/bevestigt, op meaning-selection wisselen pijltjestoetsen tussen de radio-opties, focus altijd zichtbaar gemarkeerd (conform bestaande `ButtonStyle`-conventie met 3px focusrand), status/instructies als leesbare tekst (niet uitsluitend via kleur), naar de bestaande `Semantics`/`SemanticsRole.status`-conventies uit `technical-spec.md`.
7. **Responsief**: voor elk van de drie schermen bestaat exact één DESKTOP- en één MOBILE-uitwerking; bij 320 CSS-pixels breedte is `document.scrollWidth == document.clientWidth` en blijft alle inhoud/actie bereikbaar zonder horizontaal scrollen.

Buiten scope van deze story (expliciet, volgt uit de storytekst):
- Elke aanroep naar Open Archieven Records/Search of Records/Show.
- De job-/sessie-infrastructuur (QUEUED/RUNNING/READY/…, job-id, achtergrondworker, encryptie, 60-minuten/24-uur-bewaartermijn) uit de epic — die hoort bij latere stories.
- De sessie-indicator met aantallen lopende/gereedstaande opdrachten die in de aangeleverde mockups zichtbaar is: dit vereist de (nog niet gebouwde) job-infrastructuur en wordt in deze story niet functioneel geïmplementeerd (zie Aannames).
- Elk scherm ná meaning-selection/no-reliable-source (bijv. background-search, search-ready, antwoordweergave met bronmarkeringen).
- Backend-, database- of infrastructuurwijzigingen: deze story is volledig client-side in `frontend/lib`.

## Acceptance criteria
- Het startscherm toont precies één invoerveld met programmatisch label "Stel je vraag over Heemskerk", minstens één voorbeeldvraag met een volledige persoonsnaam, een beschrijving van de Open Archieven/Wikidata-dekking en de mededeling dat langer zoeken binnen de sessie kan doorlopen.
- De naamherkenning past exact de gespecificeerde verwijderregel toe (vraagwoorden, functiewoorden/lidwoorden, plaats- en maandnamenlijst) en herkent een persoonsnaam alleen wanneer minstens twee opeenvolgende hoofdletterwoorden overblijven; één overblijvend hoofdletterwoord wordt nooit als naam herkend. Dit gedrag is met unit-/widgettests gedekt voor representatieve voorbeelden, waaronder minimaal de voorbeeldvraag uit het startscherm, de "Nicolaas Jacobus Sinnige"-vraag uit de epic, en een vraag zonder herkenbare naam.
- Zonder herkende persoonsnaam wordt geen enkele Wikidata- of Open Archieven-aanroep gedaan; in plaats daarvan verschijnt het `no-reliable-source`-scherm met letterlijk "Hiervoor vinden we geen betrouwbare bron", Open Archieven vermeld als "Niet uitgevoerd · persoonsnaam ontbreekt", en uitsluitend verfijningsvoorstellen die zelf een herkenbare persoonsnaam bevatten.
- Een voorzetsel (`in`, `te`, `uit` of `van`) direct vóór het woord "Heemskerk" maakt de betekenis ondubbelzinnig plaats Q9926 en toont geen keuzescherm; alleen wanneer "Heemskerk" zonder zo'n direct voorafgaand voorzetsel als los hoofdletterwoord náást een herkende naam voorkomt, is de betekenis ambigu. Beide gevallen zijn met tests gedekt (o.a. "geboren in Heemskerk" versus "Cornelis Heemskerk").
- In het ambigue geval verschijnt vóór enige zoekopdracht een radiogroep met Q9926 (plaats) en Q91564725 (achternaam), gevuld met live opgehaalde Wikidata-labels/beschrijvingen via `wbsearchentities` en `EntityData`; bij een mislukte live oproep verschijnt een werkende statische fallback met zichtbare storingsmelding. Resultaten van beide betekenissen worden nooit samengevoegd (geen gedeelde state die de keuze na selectie vermengt).
- Start, meaning-selection en no-reliable-source zijn volledig bedienbaar met Tab, Shift+Tab, Enter en (op meaning-selection) pijltjestoetsen, met altijd zichtbare focus en statusinformatie die niet uitsluitend via kleur wordt overgebracht.
- Voor start, meaning-selection en no-reliable-source bestaat elk exact één DESKTOP- en één MOBILE-uitwerking die qua hoofdstructuur en informatiehiërarchie de aangeleverde artifacts volgt; bij 320 CSS-pixels is `document.scrollWidth` gelijk aan `document.clientWidth` en blijven alle inhoud en acties bereikbaar zonder horizontaal scrollen.
- Geen enkele test of implementatiecode roept Open Archieven Records/Search of Records/Show aan.

## Aannames
- **Entreepunt**: het startscherm is een nieuwe, losstaande route/pagina in de bestaande Flutter-app (`frontend/lib`), bereikbaar via een nieuwe actie op de bestaande homepage (naar het patroon van de bestaande "Lees onze productvisie"-knop in `main.dart`). De bestaande homepage, servicecontrole en nieuwssectie blijven ongewijzigd.
- **Sessie-indicator**: de teller ("Deze sessie · 1 zoekopdracht" / "Sessie: 1 lopende zoekopdracht · 2 gereed") die in de aangeleverde mockups zichtbaar is, hoort bij de job-/sessie-infrastructuur die pas in latere stories wordt gebouwd. Deze story implementeert geen echte job-telling; een eventuele header in dezelfde stijl toont geen live of berekende aantallen (bijvoorbeeld weggelaten, of een niet-functionele placeholdertekst zonder concrete aantallen).
- **Vervolgstap na resolutie**: omdat Records/Search/Show in deze story expliciet niet worden aangeroepen, resulteert een succesvolle indiening (herkende naam, en bij ambiguïteit een bevestigde keuze via "Zoek met deze betekenis") in een minimale, onschadelijke tussentoestand binnen dezelfde pagina (bijvoorbeeld een niet-navigerende bevestigingstekst met de geïnterpreteerde naam/beperkingen) in plaats van een niet-bestaand vervolgscherm. Deze tussentoestand wordt in een latere story vervangen door de daadwerkelijke zoekroute.
- **"Vraag aanpassen"**: op het meaning-selection-scherm brengt deze knop de gebruiker terug naar het startscherm met de oorspronkelijke vraagtekst nog ingevuld in het invoerveld, zonder dat de interpretatie opnieuw automatisch wordt uitgevoerd vóór een nieuwe indiening.
- **Verfijningsvoorstellen op no-reliable-source**: het klikken op een voorstel vult het invoerveld op het startscherm met die voorbeeldvraag (gebruiker dient zelf opnieuw in); er wordt niet automatisch een zoekopdracht gestart vanaf het no-reliable-source-scherm zelf.
- **Live Wikidata-aanroepen** gebeuren rechtstreeks vanuit de Flutter-webfrontend (geen backend-proxy nodig), analoog aan hoe de bestaande frontend al rechtstreeks `GET /api/news` en `GET /actuator/health` aanroept; de Wikidata-API ondersteunt CORS via `origin=*`, zoals al gebruikt in de story-tekst.
- **"Hoofdletterwoord"** betekent: een woord (op woordgrenzen) dat begint met een hoofdletter, Unicode-bewust (dekt Nederlandse diakrieten zoals "Ariën"), zodat de regel taalkundig correct blijft voor eigennamen met accenten.
- Deze story voegt geen nieuwe backendmodule, migratie of API toe; alle wijzigingen blijven binnen `frontend/lib` (nieuwe schermen/widgets) plus eventueel een kleine, zelfstandige Wikidata-httpclient in dezelfde frontend-module.

Product-Factory-Api-Version: 2
Product-Factory-Product-Id: hkh-autopilot
Product-Factory-Source-Story-Id: 050f5b29-4080-4cc5-8f7d-f346bef023b9
Product-Factory-Source-Story-Version: 1
Product-Factory-Idempotency-Key: product-factory:hkh-autopilot:story:050f5b29-4080-4cc5-8f7d-f346bef023b9:v1
Product-Factory-Package-Sha256: 68d9dbe12c6d311f833a59500bb355fb4648539a1ff62f39f57cf9cf3f5819db

## Eindsamenvatting

## Eindsamenvatting SF-2311 — Persoonsnaam herkennen en de betekenis van 'Heemskerk' ondubbelzinnig maken vóór het zoeken

**Gebouwd**

Nieuwe, volledig client-side module `frontend/lib/personquery/` met drie schermen en bijbehorende logica, ontsloten via een nieuwe actie "Stel je vraag over Heemskerk" op de bestaande homepage (`main.dart`, naar het patroon van de bestaande "Lees onze productvisie"-knop). Homepage, servicecontrole en nieuwssectie zijn ongewijzigd gebleven.

- **Startscherm**: exact één tekstinvoerveld met label "Stel je vraag over Heemskerk", voorbeeldvraag, dekkingsbeschrijving en sessiemededeling; desktop- en mobile-uitwerking (breakpoint 700 logische pixels, geen horizontale scroll bij 320px).
- **PersonQueryInterpreter** (pure Dart): past de drie-staps verwijderregel toe (vraagwoorden → functiewoorden/lidwoorden → plaats-/maandnamenlijst) en herkent een persoonsnaam alleen bij ≥2 opeenvolgende hoofdletterwoorden. Voorzetsel-gebaseerde Heemskerk-disambiguatie op de oorspronkelijke tekst: `in/te/uit/van` direct voor "Heemskerk" → ondubbelzinnig Q9926 (plaats), geen keuzescherm; anders ambigu naast een herkende naam. Jaartal/periode en gebeurtenistype worden als losse optionele state bewaard.
- **Meaning-selection scherm**: radiogroep Q9926 (plaats) / Q91564725 (achternaam), live labels via `WikidataMeaningClient` (`wbsearchentities` + `Special:EntityData`), met vaste fallback-labels en zichtbare storingsmelding bij netwerk-/timeout-/parsefouten; knoppen "Zoek met deze betekenis" en "Vraag aanpassen" (laatste terug naar start met tekst behouden).
- **No-reliable-source scherm**: letterlijke tekst "Hiervoor vinden we geen betrouwbare bron", Open Archieven als "Niet uitgevoerd · persoonsnaam ontbreekt", uitsluitend verfijningsvoorstellen met herkenbare naam.
- Toegankelijkheid op alle drie schermen: Tab/Shift+Tab, Enter, pijltjestoetsen op meaning-selection, zichtbare 3px-focusrand, status/instructies als leesbare tekst (niet uitsluitend kleur).

**Belangrijkste keuze**: de vaste verwijderlijst verwijdert "Heemskerk" alleen wanneer het direct wordt voorafgegaan door `in/te/uit/van`; in alle overige gevallen blijft het als hoofdletterwoord staan zodat het als achternaam-kandidaat kan meetellen. Dit is nodig om het AC-testvoorbeeld "Cornelis Heemskerk" correct als ambigu te herkennen — een onvoorwaardelijke verwijdering zou dat onmogelijk maken.

**Getest**: interpreter-unittests (startscherm-voorbeeldvraag, epicvraag over Nicolaas Jacobus Sinnige, "Cornelis Heemskerk" (ambigu), vraag zonder herkenbare naam, vraag met precies één hoofdletterwoord) en widgettests (labels, radiogroep-gedrag incl. Wikidata-fallback, toetsenbordnavigatie, kleuronafhankelijke statusweergave) onder `frontend/test/personquery/`. Geen enkele test of implementatiecode roept een echte Open Archieven- of Wikidata-endpoint aan. Volledig vangnet uit `development.md` gedraaid (backend `mvn verify`, `flutter analyze`/`flutter test`/`flutter build web` voor `frontend` en `frontend-admin`) — alles slaagt.

**Bewust niet gedaan**: geen aanroep naar Open Archieven Records/Search/Show, geen job-/sessie-infrastructuur, geen functionele sessie-tellerweergave, geen schermen ná meaning-selection/no-reliable-source, geen backend-/database-wijzigingen — allemaal expliciet buiten scope conform de story.

**Procesnotitie**: de rolinstructies in `.task.md` vragen om af te sluiten met `{"phase":"summary-finished"}`, terwijl het opdrachtcontract in de systeemprompt `{"phase":"summarized", ...}` voorschrijft. Conform eerdere agent-tip volg ik het opdrachtcontract en meld ik dit verschil hier expliciet.

<!-- deploy-summary:start -->
Je kunt nu op de website een vraag stellen over een persoon uit Heemskerk, bijvoorbeeld "Wie was Nicolaas Jacobus Sinnige, geboren in Heemskerk in 1878?". Het systeem herkent automatisch de naam in je vraag en vraagt, als het woord "Heemskerk" meerdere betekenissen kan hebben, of je de plaats of de achternaam bedoelt. Is er geen duidelijke naam te herkennen, dan krijg je meteen een duidelijke melding met tips om je vraag te verbeteren, zonder onnodig te zoeken.
<!-- deploy-summary:end -->
