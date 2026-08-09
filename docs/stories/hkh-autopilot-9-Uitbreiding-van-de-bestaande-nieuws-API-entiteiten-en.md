# hkh-autopilot-9 - Uitbreiding van de bestaande nieuws-API: entiteiten en zoekfilter afgeleid uit gepubliceerde nieuwsberichten

## Story

Uitbreiding van de bestaande nieuws-API: entiteiten en zoekfilter afgeleid uit gepubliceerde nieuwsberichten

<!-- refined-by-factory -->

## Samenvatting
We breiden het bestaande nieuwsoverzicht uit met twee dingen: (1) elk nieuwsbericht krijgt automatisch labels voor plekken, personen en gebeurtenissen die erin genoemd worden, en (2) bezoekers kunnen het nieuws doorzoeken op trefwoord of op zo'n label. Er komt geen nieuwe pagina of nieuw endpoint bij — het bestaande nieuwscontract wordt alleen uitgebreid. Alleen al gepubliceerde berichten doen mee; er komt geen concept/publicatie-workflow.

## Scope
- Uitbreiding van het bestaande `GET /api/news`-contract (het endpoint dat `frontend/lib/latest_news.dart` en `backend_client.dart` vandaag al aanroepen). Geen nieuwe route of controller.
- "Gepubliceerd" = "bestaat in de `latest_news`-tabel/store", identiek aan het huidige gedrag van `POST /api/admin/news`. Geen nieuwe status-kolom, geen concept-toestand, geen wijziging aan de admin-create-flow.
- Deterministische entiteitsextractie via een statische, in de repo onderhouden gazetteer (configuratiebestand per entiteitstype: plek, persoon, gebeurtenis; elke entry heeft een canonicalLabel + aliases-lijst), gevuld met een klein startset (5-10 bekende Heemskerkse plekken/personen/gebeurtenissen).
  - Matching: case-insensitive, whole-word, diakrieten-genormaliseerde substring-match van elke alias tegen titel + samenvatting van een bericht.
  - Bij meerdere matches: alle gevonden entiteiten getoond, gededupliceerd op canonicalLabel; sortering op entiteitstype (plek, persoon, gebeurtenis), daarbinnen op eerste voorkomen in de tekst.
  - Getoond label = canonicalLabel uit de gazetteer, niet de letterlijke tekst uit het bericht.
  - Geen match → lege entiteitenlijst voor dat bericht (geen fout, geen NLP/NER-fallback).
- Nieuwe optionele queryparameters op `GET /api/news`: vrije-tekstzoekterm (matcht op titel/samenvatting) en entiteitsfilter (matcht op een gekozen entiteit/type).
- Alleen het nieuwsbericht-domein (`nl.vdzon.hkh.news`) wordt aangeraakt; geen enkel veld/endpoint uit deze uitbreiding leest of retourneert record-intake-, privacyclassificatie- of externe-verificatiedata.
- Buiten scope: nieuwe REST-routes/controllers, een concept/publicatie-workflow, NLP/NER-gebaseerde entiteitsherkenning, ontsluiting van record-intake- of genealogische data.

## Acceptance criteria
- Contracttest bevestigt dat het aantal geregistreerde routes/controllers na deze wijziging gelijk is aan vóór de wijziging: de uitbreiding bestaat uitsluitend uit extra velden en optionele queryparameters op het bestaande `GET /api/news`-contract.
- Integratietest bewijst dat entiteitsmetadata (type, label, itemtelling) uitsluitend wordt afgeleid uit berichten die in de `latest_news`-store staan; een testfixture voor een bericht dat buiten de normale creëer-flow om (dus niet via `POST /api/admin/news`) rechtstreeks in de store-laag is geplaatst als "niet gepubliceerd" simulacrum, verschijnt nooit in de entiteitenlijst of -telling — concreet: de test voegt een bericht toe dat expliciet buiten `latest_news` blijft en toont aan dat dit nooit in entiteiten/zoekresultaten verschijnt.
- Test bewijst dat een vrije zoekterm die matcht op titel/samenvatting van een bestaand bericht dat bericht retourneert met een niet-lege bronvermelding ("afkomstig uit gepubliceerd HKH-nieuwsbericht", inclusief publicatiedatum).
- Test bewijst dat filtering op een geselecteerde entiteit (plek/persoon/gebeurtenis) uitsluitend berichten retourneert die aan die entiteit gekoppeld zijn, elk voorzien van het juiste entiteitstype-label.
- Test bewijst dat een zoekterm of entiteit zonder matches een expliciet leeg resultaat oplevert (HTTP 200, lege lijst, totaal=0), zonder foutstatus of exceptie.
- Test bevestigt dat geen enkel veld/queryparameter uit deze uitbreiding record-intake-, privacyclassificatie- of externe-verificatiedata retourneert of raadpleegt.
- Unit tests dekken de gazetteer-matching zelf (gazetteer + inputtekst → verwachte entiteitenlijst), inclusief dedup en sortering.
- Het uitgebreide responscontract wordt automatisch als onderdeel van de build gedocumenteerd/getest (bijv. gegenereerd schema-diff of contract-test die het schema vastlegt), zodat een vervolgstory hier zonder handmatige afstemming op kan voortbouwen.
- `frontend/lib/latest_news.dart` en de admin-tegenhanger worden bijgewerkt zodat ze het uitgebreide contract correct blijven parsen (geen crash op de nieuwe velden).

## Aannames
- Het `GET /api/news`-responscontract verandert van een kale JSON-array naar een object dat minimaal bevat: `items` (berichten, elk met hun afgeleide entiteitenlijst), `total`, en `entities` (geaggregeerde lijst van gevonden entiteiten met itemtelling). Dit is nodig om aan de AC's over "totaal=0" en een geaggregeerde entiteitenlijst-met-telling te voldoen zonder een nieuwe route te openen. De frontend-clients worden in dezelfde story bijgewerkt op dit nieuwe schema.
- De gazetteer-startset (5-10 entries per type) wordt door de developer ingevuld op basis van bestaande, al gepubliceerde nieuwsberichten/seed-data in de repo (bijv. `PreviewLatestNewsSeedStore.kt`); geen nieuwe externe databron.
- "Zoekfilter" en "entiteitsfilter" zijn los te combineren queryparameters op hetzelfde endpoint; als beide tegelijk zijn opgegeven, gelden ze als AND (alleen berichten die aan beide voldoen).
- Er wordt geen paginering toegevoegd; het bestaande gedrag (alle berichten in één respons) blijft behouden, nu aangevuld met `total` en `entities`.

## Eindsamenvatting

{"agent_tips_update":[]}
{"phase":"summarized"}
