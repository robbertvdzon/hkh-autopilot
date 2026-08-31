# SF-2370 - Vind een onderbouwd antwoord over een plek, gebouw of monument in Heemskerk (Wikidata + Wikimedia Commons)

## Story

Vind een onderbouwd antwoord over een plek, gebouw of monument in Heemskerk (Wikidata + Wikimedia Commons)

<!-- refined-by-factory -->

## Scope
Voeg een tweede, deterministisch herkende vraagtak toe naast de bestaande persoonsroute: een vraag over een plek, gebouw of monument in Heemskerk (bijv. "Wat is Kasteel Assumburg?") levert een onderbouwd antwoord op uit live Wikidata-claims met genummerde bronverwijzing, plus een beeldgalerij uit Wikimedia Commons met licentie. Geen match, meerdere matches of een tijdelijk onbereikbare bron leiden nooit tot een verzonnen antwoord.

**Herkenning (frontend, client-side, `frontend/lib/personquery/person_query_interpreter.dart`):**
Breid de bestaande deterministische interpreter uit (of voeg een tweede interpretatiepad toe dat dezelfde strip-stappen hergebruikt): na het verwijderen van vraagwoorden ({wie,wat,waar,wanneer,welke,hoe}) en functiewoorden/lidwoorden ({was,is,geboren,getrouwd,overleden,gedoopt,de,het,een,van,in,op,te,uit}), verwijder ook het losstaande woord "Heemskerk". Blijft een landmark-trefwoord ({kasteel,kerk,molen,toren,gemaal,station,brug,huis,hof,plein,sluis,kapel,klooster}) direct naast minstens één hoofdletterwoord over, dan is dat de plek/gebouw-zoekterm (trefwoord + naam, bv. "Kasteel Assumburg"). Zonder landmark-trefwoord gelden dezelfde regels als bij personen (>=2 opeenvolgende hoofdletterwoorden). Herkent de tekst een landmark-kandidaat, dan gaat de vraag naar de nieuwe plek/gebouw-route in plaats van de persoonsroute. Zonder persoonsnaam én zonder plek/gebouw-kandidaat verandert er niets: geen aanroep, bestaand "Hiervoor vinden we geen betrouwbare bron"-gedrag blijft van kracht.

**Backend: nieuwe plek/gebouw-route:**
Nieuw, zelfstandig backend-package (bv. `nl.vdzon.hkh.placesearch`, analoog aan `personsearch`) met een REST-controller op bv. `/api/place-search` (naast het bestaande `/api/person-search`). Deze route hergebruikt niet de sessiegebonden achtergrondjob-infrastructuur van de persoonsroute (QUEUED/RUNNING-polling): een synchrone request/response-aanroep met een harde totale deadline van 2000ms (Wikidata + eventueel Commons) volstaat, omdat deze externe bronnen doorgaans binnen enkele honderden milliseconden reageren.

Stappen server-side voor een herkende kandidaat-zoekterm:
1. `GET https://www.wikidata.org/w/api.php?action=wbsearchentities&search=<kandidaat>&language=nl&type=item&format=json&limit=5`.
2. Voor elk teruggekomen QID: `GET https://www.wikidata.org/wiki/Special:EntityData/{QID}.json`.
3. Een kandidaat telt alleen mee bij P131 (ligt-in-gemeente-claim, eventueel één niveau doorverwezen) == Q9926, óf P625-coördinaten binnen de Heemskerkse gemeentegrens (vaste, eenvoudige bounding-box of puntlijst; documenteer de gekozen grenscoördinaten in code-commentaar als eigen geometrische aanname, geen officiële Wikidata-claim).
4. Geen SPARQL/Query Service-aanroep (bekend onbetrouwbaar/502); de route mag hier niet van afhankelijk zijn.
5. Precies 1 match binnen Heemskerk => bouw het antwoord uit de claims van dat ene item (label, description, P571 oprichtingsdatum, P149 architectuurstijl, P84 architect, P1435 erfgoedstatus indien aanwezig). Elke feitelijke zin krijgt een genummerde bronmarkering met QID, link naar `https://www.wikidata.org/wiki/{QID}` en checkedAt (analoog aan `PersonSearchSourceCitation`/`PersonSearchAnswerSentence`).
6. 0 of >1 match => geen antwoord construeren; bij >1 match de gevonden kandidaatnamen (labels) als verfijningsvoorstel meesturen, zonder resultaten van verschillende kandidaten samen te voegen.

Volg voor de HTTP-client hetzelfde patroon als `PersonSearchWikidataContextClient.kt` (fail-closed: elke fout/timeout levert geen resultaat op) en hergebruik zo mogelijk de bestaande RestClient-configuratiebenadering (`PersonSearchClientConfiguration.kt`) met een eigen base-URL-bean voor `commons.wikimedia.org`.

**Beeldmateriaal (Wikimedia Commons):**
Voor het gevonden QID: gebruik P373 (Commons-categorienaam) indien aanwezig; anders P18 (afbeeldingsbestand) rechtstreeks van het item. Bevraag live `https://commons.wikimedia.org/w/api.php` met `action=query&generator=categorymembers&gcmtitle=Category:<P373-waarde>&gcmtype=file&gcmlimit=12&prop=imageinfo&iiprop=url|extmetadata&format=json`. Toon maximaal 6 gededupliceerde afbeeldingen (dedupliceer op bestandsnaam), elk met de directe URL uit `imageinfo.url`, licentie uit `imageinfo.extmetadata.LicenseShortName.value` en link naar de Commons-bestandspagina. Geen categorie/P18 of nul resultaten => leeg beeldblok, nooit een placeholder- of verzonnen afbeelding.

**Foutafhandeling en budget:**
Bij een niet-2xx-status, time-out, ongeldige JSON of ontbrekend verplicht veld voor Wikidata: toon "Wikidata is tijdelijk niet geraadpleegd" zonder enige claim te tonen. Faalt alleen Commons (Wikidata was wel geldig): toon "Wikimedia Commons · niet uitgevoerd · afhankelijk van Wikidata" voor het beeldblok, terwijl de rest van het Wikidata-antwoord gewoon getoond wordt. Nooit een antwoord construeren bij onvoldoende bewijs (fail-closed).

**Caching:**
Cache elk opgehaald Wikidata-record en elke Commons-imageinfo-respons uitsluitend kortstondig (in-memory met TTL, geen structurele database-opslag) met zichtbare checkedAt op het scherm. Wikidata/Commons blijven altijd de bron van waarheid, met QID-link resp. bestandspagina-link.

**Frontend:**
Hergebruik het bestaande startscherm met bijgewerkte voorbeeldvraag en een extra dekkingsbadge ("Wikidata + Wikimedia Commons — plekken, gebouwen en monumenten") naast de bestaande Open Archieven-badge. Voeg drie nieuwe schermtoestanden toe, volgens de acht meegeleverde UX-artifacts (richtinggevend, geen pixel-perfecte kopie vereist):
- **place-answer**: vraagtitel, checkedAt, onderbouwd antwoord met genummerde bronmarkeringen, bronbox, beeldgalerij met per-afbeelding licentiebadge, apart gelabeld "Context"-blok voor de gemeentekoppeling.
- **place-empty**: exact "Hiervoor vinden we geen betrouwbare bron" + bronnenstatus + eventuele verfijningsvoorstellen bij meerdere kandidaten.
- **place-outage**: "Wikidata is tijdelijk niet geraadpleegd" + afhankelijke Commons-status + retry-actie.

Alle schermen: Tab/Shift+Tab/Enter-bedienbaar, zichtbare focusrand, statusbadges niet uitsluitend kleurafhankelijk, werkend op 320 CSS-pixels breedte zonder horizontaal scrollen (desktop- en mobile-variant per toestand).

## Acceptance criteria
- De vraaginterpretatie herkent een plek/gebouw-kandidaat wanneer, na het verwijderen van vraagwoorden, functiewoorden/lidwoorden en het losstaande woord "Heemskerk", een van de landmark-trefwoorden (kasteel, kerk, molen, toren, gemaal, station, brug, huis, hof, plein, sluis, kapel, klooster) direct naast minstens één hoofdletterwoord overblijft; ontbreekt een landmark-trefwoord, dan gelden dezelfde regels als bij personen (minimaal twee opeenvolgende hoofdletterwoorden).
- Wanneer noch een persoonsnaam noch een plek/gebouw-kandidaat wordt herkend, blijft het bestaande gedrag van de persoonsroute ongewijzigd: geen Wikidata- of Commons-aanroep, en "Hiervoor vinden we geen betrouwbare bron".
- Voor een herkende plek/gebouw-kandidaat doet het backend live een Wikidata wbsearchentities-aanroep (language=nl, type=item, limit 5) en haalt voor elk kandidaat-QID live Special:EntityData op; een kandidaat telt alleen mee bij P131 (eventueel één niveau doorverwezen) naar Q9926 of P625-coördinaten binnen de Heemskerkse gemeentegrens; er wordt geen SPARQL/Query Service-aanroep gedaan.
- Bij precies één binnen-Heemskerk-match toont de app direct het antwoord; bij nul of meer dan één match toont de app "Hiervoor vinden we geen betrouwbare bron", en bij meer dan één match worden de gevonden kandidaatnamen als verfijningsvoorstel getoond zonder resultaten van verschillende kandidaten samen te voegen.
- Elke feitelijke antwoordzin is direct herleidbaar tot een live opgehaalde Wikidata-claim van het gevonden record en krijgt een genummerde bronverwijzing met QID, link naar wikidata.org/wiki/{QID} en checkedAt-tijdstip; het scherm vermeldt zichtbaar dat het een actuele beschrijving van dit ene object is, geen volledige geschiedschrijving.
- Beeldmateriaal wordt opgehaald via P373 (Commons-categorie) indien aanwezig, anders via P18, door live de Wikimedia Commons Action-API te bevragen (generator=categorymembers, gcmtype=file, gcmlimit maximaal 12, prop=imageinfo, iiprop=url|extmetadata); er worden maximaal 6 gededupliceerde afbeeldingen getoond met bestands-URL, licentie en link naar de Commons-bestandspagina, en een lege set toont een leeg beeldblok zonder verzonnen afbeelding.
- De synchrone afhandeling wacht maximaal 2 seconden in totaal op Wikidata en, indien van toepassing, Wikimedia Commons; bij een niet-2xx-status, time-out, ongeldige JSON of ontbrekend verplicht veld toont de app respectievelijk "Wikidata is tijdelijk niet geraadpleegd" of "Wikimedia Commons · niet uitgevoerd · afhankelijk van Wikidata", zonder een archiefbewering te construeren.
- Elk getoond Wikidata-record en elke getoonde afbeelding wordt uitsluitend kortstondig gecachet met zichtbare checkedAt, nooit zelf als bron gepresenteerd, en er ontstaat geen structurele lokale kopie van Wikidata- of Commons-gegevens.
- Voor de voorbeeldvraag "Wat is Kasteel Assumburg?" herkent de interpretatie de zoekterm "Kasteel Assumburg", levert Wikidata precies één match binnen Heemskerk op, en toont het antwoord minimaal de gemeentekoppeling (P131) en het rijksmonumentstatus-gegeven (P1435), elk met eigen genummerde bronverwijzing.
- Alle vier schermtoestanden (place-start, place-answer, place-empty, place-outage) zijn volledig bedienbaar met Tab, Shift+Tab en Enter, tonen zichtbare toetsenbordfocus en statusinformatie die niet uitsluitend op kleur steunt, en zijn op 320 CSS-pixels breedte (desktop- én mobile-variant per toestand) volledig bruikbaar zonder horizontaal scrollen.
- Testdekking omvat minimaal: interpreter-unittests voor landmark-herkenning (inclusief "Wat is Kasteel Assumburg?" en negatieve gevallen); backend-tests met gemockte Wikidata/Commons-responses voor 0/1/>1 matches, P131-doorverwijzing, P625-coördinatenfilter, ontbrekende P373 (fallback naar P18), en de 2s-timeout/foutpaden; een end-to-end/widget-test voor alle vier schermtoestanden inclusief toetsenbordbediening en 320px-layout.

## Aannames
- De exacte bounding-box/puntlijst voor de Heemskerkse gemeentegrens (P625-fallback) is een implementatiedetail dat de developer zelf vaststelt en als code-commentaar documenteert; er is geen officiële Wikidata-geometrie vereist.
- Wanneer de interpreter zowel een persoonsnaam-patroon als een landmark-kandidaat zou kunnen opleveren, heeft de landmark/plek-herkenning voorrang (de vraag gaat dan naar de plek/gebouw-route, niet naar de persoonsroute), conform de in de story beschreven volgorde van regels.
- De acht meegeleverde UX-artifacts (4 schermtoestanden × desktop/mobile) zijn richtinggevend voor hoofdstructuur en informatiehiërarchie; een pixel-perfecte kopie is niet vereist.
- Er is geen nieuwe sessie- of achtergrondjob-infrastructuur nodig voor deze route; puur synchrone request/response volstaat binnen de 2s-deadline.

Product-Factory-Api-Version: 2
Product-Factory-Product-Id: hkh-autopilot
Product-Factory-Source-Story-Id: 0cf37c81-3555-42e1-8d4c-4ec7a378cb8f
Product-Factory-Source-Story-Version: 1
Product-Factory-Idempotency-Key: product-factory:hkh-autopilot:story:0cf37c81-3555-42e1-8d4c-4ec7a378cb8f:v1
Product-Factory-Package-Sha256: fcdeb15027412d95fef0f89bb33ce2458f98664a9dfe869cdbace7f52487f77e

## Eindsamenvatting

## Eindsamenvatting SF-2370 — Plek/gebouw-route (Wikidata + Wikimedia Commons)

**Gebouwd**
Naast de bestaande persoonsvraag-route herkent de app nu ook vragen over een plek, gebouw of monument in Heemskerk (bv. "Wat is Kasteel Assumburg?"). De frontend-interpreter herkent landmark-trefwoorden (kasteel, kerk, molen, toren, gemaal, station, brug, huis, hof, plein, sluis, kapel, klooster) naast een hoofdletterwoord en geeft die herkenning voorrang boven de persoonsroute; zonder landmark-trefwoord blijft het bestaande persoonsgedrag ongewijzigd.

Backend: een nieuwe, zelfstandige Spring Modulith-module `nl.vdzon.hkh.placesearch` met synchrone `POST /api/place-search` binnen een harde 2000ms-budget. De pijplijn doet `wbsearchentities` → per QID `Special:EntityData`, filtert op P131 (gemeentekoppeling, evt. één niveau doorverwezen) of P625 binnen een zelf gedocumenteerde bounding box — bewust geen SPARQL. Bij precies 1 match wordt het antwoord opgebouwd uit label/description/P571/P149/P84/P1435, elk met genummerde bronverwijzing (QID, link, checkedAt). Bij 0 of >1 match volgt fail-closed "geen betrouwbare bron", met kandidaatlabels als verfijningsvoorstel bij meerdere treffers. Afbeeldingen komen live van Wikimedia Commons via P373 (categorie) of P18-fallback, gededupliceerd tot max. 6, met licentie en bestandspaginalink. Fouten/timeouts op Wikidata tonen "Wikidata is tijdelijk niet geraadpleegd"; een geïsoleerde Commons-fout laat het Wikidata-antwoord staan met een aparte statusmelding. Alles kortstondig in-memory gecached (TTL), nooit structureel opgeslagen.

Frontend kreeg drie nieuwe schermtoestanden (place-answer, place-empty, place-outage) plus een bijgewerkt startscherm met extra dekkingsbadge, allemaal toetsenbord-bedienbaar met zichtbare focus, kleuronafhankelijke statusbadges en bruikbaar op 320px breedte.

**Belangrijke keuze tijdens de bouw**
Een tweede `ExecutorService`-bean botste met de bestaande persoonsroute-bean (`NoUniqueBeanDefinitionException`); opgelost met een expliciete `@Qualifier`, wat een kleine, noodzakelijke wijziging in de bestaande `PersonSearchService.kt` opleverde.

**Getest**
Backend: 292/292 tests groen (embedded HTTP-fixtures voor Wikidata/Commons, alle match-scenario's, P131-doorverwijzing, P625-filter, P373→P18-fallback, timeout/foutpaden). Frontend: 104/104 tests groen (interpreter, schermtests inclusief 320px en toetsenbordbediening, routeringstests). `frontend-admin` ongewijzigd (22/22, terecht geskipt). Reviewer keurde goed zonder blockers. Tester verifieerde live tegen de preview-omgeving: het canonieke voorbeeld "Kasteel Assumburg" gaf exact het verwachte antwoord met bronverwijzingen en 6 afbeeldingen; niet-matchende termen gaven terecht fail-closed gedrag; brede generieke termen gaven soms `OUTAGE` door het 2s-budget bij meerdere sequentiële Wikidata-round-trips — verwacht en geaccepteerd gedrag, geen bug.

**Bewust niet gedaan**
Geen SPARQL/Query Service-aanroep (expliciet uitgesloten). Geen wijziging aan `frontend-admin` (buiten scope). Geen widget-test met een gevulde beeldgalerij in de frontend (kleine testhiaat, niet AC-blokkerend, backend-kant van de beeldpijplijn wel grondig getest). Geen structurele opslag van Wikidata/Commons-data.

**Opmerking over deze rol**
De rolinstructies in `.task.md` en het opdrachtcontract van deze summarizer-taak verschillen in het verwachte slot-JSON (`summary-finished` vs. `summarized`); conform eerdere agent-tip volg ik het opdrachtcontract hieronder.

<!-- deploy-summary:start -->
Je kunt nu ook vragen stellen over gebouwen, plekken en monumenten in Heemskerk, zoals "Wat is Kasteel Assumburg?". De app geeft daarbij een onderbouwd antwoord met bronvermelding en een fotogalerij, of laat eerlijk weten als er geen betrouwbaar antwoord gevonden kon worden.
<!-- deploy-summary:end -->
