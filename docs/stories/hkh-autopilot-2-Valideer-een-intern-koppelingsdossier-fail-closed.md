# hkh-autopilot-2 - Valideer een intern koppelingsdossier fail-closed

## Story

Valideer een intern koppelingsdossier fail-closed

<!-- refined-by-factory -->

## Samenvatting
Het systeem controleert een intern dossier dat één record van HKH aan één extern record koppelt. Alleen wanneer de herkomst, beschrijving, rechten, privacy en onderbouwing voldoende duidelijk zijn, mag de relatie als metadata-link worden gepubliceerd. Bij twijfel of ontbrekende informatie blokkeert het systeem de publicatie en maakt het duidelijk welke gegevens aandacht nodig hebben. Objectmedia krijgt een afzonderlijke, strengere vrijgave.

## Scope
- Realiseer in de backend een interne, deterministische validator voor één aangeleverd koppelingsdossier. Opslag, een REST-endpoint, beheerinterface en daadwerkelijke publicatie vallen buiten deze story.
- Een dossier bevat exact twee afzonderlijke records in vaste invoervolgorde en één relatie tussen die records.
- Ieder record bevat:
  - bronhouder;
  - minimaal een permanente URL of identifier;
  - minimaal een titel of beschrijving;
  - een datering met afzonderlijk vastgelegde onzekerheid;
  - metadatarechten;
  - objectrechten;
  - privacyclassificatie.
- Rechten gebruiken de gecontroleerde waarden `toegestaan`, `niet toegestaan` en `onduidelijk`. Privacy gebruikt `openbaar`, `beperkt` en `onduidelijk`. Dateringsonzekerheid gebruikt `zeker`, `geschat` en `onbekend`.
- De relatie bevat een niet-lege relatietype-aanduiding, een niet-lege verbindingsgrond, minimaal één bewijslink en de bevestigingsstatus `bevestigd` of `hypothese`.
- Het validatieresultaat bevat:
  - de dossierstatus `publiceerbaar als metadata-link` of `geblokkeerd`;
  - een dossierbrede aanduiding of objectmedia mag worden uitgevoerd;
  - unieke, stabiel gesorteerde machineleesbare veldpaden voor blokkades van de metadata-link;
  - unieke, stabiel gesorteerde machineleesbare veldpaden voor blokkades van objectmedia.
- Veldpaden gebruiken de invoerindex voor records, bijvoorbeeld `records[0].sourceHolder`, `records[1].objectRights` en `relation.confirmationStatus`.

## Acceptance criteria
- Een dossier met een ander aantal dan exact twee records krijgt status `geblokkeerd`, met `records` als blokkerend veldpad.
- De twee records moeten via hun opgegeven permanente URL of identifier van elkaar verschillen. Dezelfde stabiele referentie voor beide records blokkeert het dossier en vermeldt de referentievelden van beide records.
- Verplichte tekstwaarden die ontbreken of na het verwijderen van omringende witruimte leeg zijn, gelden als ontbrekend.
- Voor de alternatieven permanente URL/identifier en titel/beschrijving is minimaal één waarde vereist. Als beide alternatieven ontbreken, worden beide betreffende veldpaden gemeld.
- Iedere opgegeven permanente URL en bewijslink is een absolute HTTP- of HTTPS-URL. Een malformed of andersoortige URL blokkeert het bijbehorende veld, ook wanneer een alternatief identificatieveld wel geldig is.
- Een recorddatering bevat zowel een waarde als expliciete dateringsonzekerheid. Een ontbrekende of onbekende onzekerheidswaarde blokkeert `records[n].dating.uncertainty`.
- Ontbrekende of niet-herkende waarden voor metadatarechten, objectrechten, privacyclassificatie of bevestigingsstatus blokkeren hun eigen veldpad.
- Een relatie geldt als toetsbaar wanneer zowel een niet-lege verbindingsgrond als minimaal één geldige bewijslink aanwezig is.
- Status `publiceerbaar als metadata-link` wordt uitsluitend toegekend wanneer:
  - het dossier exact twee geldige, afzonderlijke records bevat;
  - alle verplichte record- en relatievelden geldig zijn;
  - metadatarechten voor beide records expliciet `toegestaan` zijn;
  - de privacyclassificatie van beide records `openbaar` is;
  - de relatie `bevestigd` is.
- Een relatie met bevestigingsstatus `hypothese` krijgt altijd status `geblokkeerd`; de metadata-blokkadelijst bevat dan `relation.confirmationStatus`.
- Objectmedia is uitsluitend toegestaan wanneer het dossier twee records bevat en objectrechten voor beide records expliciet `toegestaan` zijn. Ontbrekende, niet-toegestane of onduidelijke objectrechten maken objectmedia dossierbreed niet toegestaan en leveren de betreffende `records[n].objectRights`-paden op.
- Metadata-linkstatus en objectmedia-uitvoer worden afzonderlijk beoordeeld: expliciet niet-toegestane of onduidelijke objectrechten blokkeren objectmedia, maar blokkeren op zichzelf geen metadata-link.
- Bij meerdere fouten bevat iedere blokkadelijst elk betrokken veldpad precies eenmaal en altijd in dezelfde lexicografische volgorde, onafhankelijk van uitvoeringsvolgorde.
- Geautomatiseerde backendtests dekken minimaal:
  - een volledig dossier met bevestigde relatie dat als metadata-link publiceerbaar is en objectmedia toestaat;
  - ieder verplicht recordveld via parametrische of afzonderlijke ontbrekend-veldscenario’s;
  - een relatie met status `hypothese`;
  - ontbrekende en onduidelijke objectrechten;
  - een verkeerd aantal records;
  - een ongeldige URL;
  - twee records met dezelfde stabiele referentie;
  - meerdere gelijktijdige fouten met deterministische veldpadvolgorde.

## Aannames
- “Intern koppelingsdossier” betekent in deze story een backend-domeinobject en validator; koppeling aan transport, opslag, schermen en publicatieprocessen volgt in latere stories.
- De validator controleert vorm, volledigheid, gecontroleerde waarden en interne samenhang. Hij raadpleegt geen externe bronnen en bewijst niet dat een URL bereikbaar is of dat de inhoudelijke claim historisch juist is.
- Een expliciete objectrechtenclassificatie is verplicht voor een volledig dossier, maar alleen de waarde `toegestaan` geeft objectmedia vrij.
- Metadatarechten en privacy worden fail-closed toegepast: alleen expliciete toestemming en de classificatie `openbaar` geven een metadata-link vrij.
- Niet-lege identifiers, titels, beschrijvingen, bronhouders, relatietypen en verbindingsgronden krijgen binnen deze story geen externe inhoudelijke verificatie.

## Eindsamenvatting

# Eindsamenvatting story `hkh-autopilot-2` — Valideer een intern koppelingsdossier fail-closed

## Wat is gebouwd
Een nieuwe, zelfstandige backend-module `nl.vdzon.hkh.linkdossier` (Spring Modulith) met:

- **Domeinmodel** — een koppelingsdossier met exact twee geordende records en één relatie ertussen. Elk record bevat bronhouder, permanente URL/identifier, titel/beschrijving, datering met aparte onzekerheid, metadatarechten, objectrechten en privacyclassificatie.
- **Deterministische validator** — verzamelt álle overtredingen (stopt nooit bij de eerste) en levert een resultaat met: dossierstatus (`publiceerbaar als metadata-link` of `geblokkeerd`), een dossierbrede objectmedia-indicatie, en twee gescheiden lijsten machineleesbare veldpaden (metadata-blokkades en objectmedia-blokkades), elk ontdubbeld en lexicografisch gesorteerd.
- **Vaste veldpaden** zoals afgesproken, o.a. `records[0].sourceHolder`, `records[1].objectRights`, `relation.confirmationStatus`.

Conform scope: géén REST-endpoint, opslag, migratie, beheerinterface of frontendwijziging.

## Belangrijkste keuzes
- **Fail-closed**: publiceerbaar alleen bij twee geldige, van elkaar verschillende records, alle verplichte velden geldig, beide metadatarechten expliciet `toegestaan`, beide privacyclassificaties `openbaar` en relatie `bevestigd`. `hypothese` blokkeert altijd.
- **Objectmedia volledig apart beoordeeld**: alleen twee records plus beide objectrechten `toegestaan`. Onduidelijke of niet-toegestane objectrechten blokkeren nooit de metadata-link — en omgekeerd mag een om andere redenen geblokkeerd dossier objectmedia wel toestaan.
- **Gecontroleerde waarden als tekst gemodelleerd** met een parse-stap, zodat ontbrekende én niet-herkende waarden netjes worden afgekeurd in plaats van dat inlezen/constructie stukloopt.
- **Nooit een uitzondering naar buiten**: een onverwachte fout levert een geblokkeerd, objectmedia-verboden resultaat.
- **Dateringsonzekerheid `onbekend`** geldt als geldige waarde en blokkeert niet; alleen ontbrekend of niet-herkend blokkeert. Bewust gekozen en door reviewer akkoord bevonden.
- Bij een afwijkend aantal records worden de aangeleverde records en relatie nog steeds inhoudelijk beoordeeld; `records` komt in beide blokkadelijsten.

## Wat is getest
- 41 gedragstests op de validator; volledige backendsuite groen: **64 tests, 0 failures, 0 errors** (`mvn clean verify`, exitcode 0).
- Gedekt: volledig geldig dossier, parametrische ontbrekend-veldscenario's per verplicht veld, beide alternatievenparen, hypothese-relatie, ontbrekende/niet-toegestane/onduidelijke objectrechten, verkeerd aantal records, ongeldige permanente URL en bewijslink, gelijke stabiele referentie (via URL én via identifier), geblokkeerd-maar-objectmedia-toegestaan, en meerdere gelijktijdige fouten met exacte deterministische veldpadvolgorde.
- Regressievangnet frontend en frontend-admin: analyze zonder issues, alle tests groen, webbuild geslaagd. Geen flakes waargenomen.
- Aparte review- en testronde uitgevoerd; **geen blockers**.

## Bewust niet gedaan
- Geen REST-endpoint, opslag/migratie, beheerscherm of daadwerkelijke publicatie — dat volgt in latere stories.
- Geen externe verificatie: de validator controleert vorm, volledigheid en samenhang, maar bereikt geen URL's en toetst niet of de inhoudelijke claim historisch juist is.
- Geen browser-/E2E-verificatie, omdat deze story geen zichtbaar oppervlak oplevert.

## Aandachtspunten voor later (niet blokkerend)
- De distinctness-check vergelijkt de feitelijk gebruikte referentie: heeft record 0 een permanente URL en record 1 een identifier met exact dezelfde tekst, dan gelden ze als gelijk (fail-closed, dus de veilige kant).
- Losse tests voor ontbrekende `relation.relationType` en `relation.connectionGround` (nu alleen via het meervoudige-foutenscenario gedekt).
- Overweging: gecontroleerde waarden hoofdletterongevoelig maken; de story schrijft nu alleen kleine letters voor.

<!-- deploy-summary:start -->
Koppelingen tussen twee records worden nu automatisch gecontroleerd voordat ze gedeeld mogen worden. Alleen als herkomst, rechten en privacy helemaal duidelijk zijn, komt een koppeling erdoor; bij twijfel wordt hij tegengehouden en zie je precies welke gegevens aandacht nodig hebben. Het tonen van afbeeldingen en ander materiaal wordt daarbij apart en strenger beoordeeld.
<!-- deploy-summary:end -->

_Opmerking: de rolinstructies in `.task.md` noemen `{"phase":"summary-finished"}`, de opdrachtcontract `{"phase":"summarized"}`. Ik volg het contract uit de opdracht._
