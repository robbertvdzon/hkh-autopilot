# Functional Spec

## Gebruikersfrontend

De homepage (`/`) toont eerst de servicecontrole. Na een succesvolle controle blijft de bestaande
ontdekintroductie, productvisieactie en servicekaart staan en wordt daaronder het laatste nieuws
geladen. Routes, navigatie en zichtbare inhoudsvolgorde worden niet door statussemantiek gewijzigd.

De beleefde statusmeldingen zijn:

- service: ‘De historische omgeving wordt voorbereid.’, ‘De HKH-service is niet bereikbaar.’ en
  ‘Service beschikbaar.’;
- nieuws: ‘Laatste nieuws wordt geladen.’, ‘Het laatste nieuws kon niet worden geladen.’,
  ‘Laatste nieuws geladen.’ bij berichten en ‘Er zijn nog geen nieuwsberichten.’ bij een lege lijst.

Elke statusovergang levert exact één statusnode en verplaatst toetsenbord- of
toegankelijkheidsfocus niet. De zichtbare laadindicatoren, iconen en tekstkopieën zijn geen extra
statusnodes. Een nieuwsresultaat zonder items is een succesvolle, afzonderlijke uitkomst.

De actie ‘Opnieuw proberen’ volgt de bijbehorende foutmelding in lees- en focusvolgorde, is met Tab
bereikbaar, toont bij focus een contrasterende rand van drie pixels en werkt met Enter en spatie.
Een retry toont eerst opnieuw de passende laadstatus en daarna één uitkomst.

## Koppelingsdossier (backend)

Een koppelingsdossier legt vast dat één HKH-record bij één extern record hoort. Het bestaat uit exact
twee records in vaste invoervolgorde plus één relatie daartussen. De backend beoordeelt zo'n dossier
intern en fail-closed: alleen wanneer herkomst, beschrijving, datering, rechten, privacy en
onderbouwing volledig duidelijk zijn, is de relatie publiceerbaar als metadata-link. Bij twijfel of
ontbrekende gegevens blokkeert het dossier en benoemt het resultaat welke velden aandacht nodig
hebben.

Ieder record bevat een bronhouder, minimaal een permanente URL of identifier, minimaal een titel of
beschrijving, een datering met afzonderlijk vastgelegde onzekerheid, metadatarechten, objectrechten en
een privacyclassificatie. De relatie bevat een relatietype, een verbindingsgrond, minimaal één
bewijslink en een bevestigingsstatus. Gecontroleerde waarden zijn: rechten `toegestaan`,
`niet toegestaan` en `onduidelijk`; privacy `openbaar`, `beperkt` en `onduidelijk`;
dateringsonzekerheid `zeker`, `geschat` en `onbekend`; bevestigingsstatus `bevestigd` en `hypothese`.

Beoordelingsregels:

- tekstwaarden die ontbreken of na het weglaten van omringende witruimte leeg zijn, gelden als
  ontbrekend;
- van de alternatievenparen permanente URL/identifier en titel/beschrijving is minimaal één waarde
  vereist; ontbreken beide, dan worden beide veldpaden gemeld;
- iedere opgegeven permanente URL en bewijslink moet een absolute http- of https-URL zijn, ook wanneer
  het alternatieve veld wel geldig is;
- de twee records moeten van elkaar verschillen via hun stabiele referentie: de permanente URL, en
  anders de identifier. Dezelfde referentie blokkeert en meldt het feitelijk gebruikte referentieveld
  van beide records;
- datering en dateringsonzekerheid worden apart beoordeeld; `onbekend` is een geldige onzekerheid en
  blokkeert niet, een ontbrekende of niet-herkende waarde wel;
- ontbrekende of niet-herkende gecontroleerde waarden blokkeren hun eigen veldpad.

De status `publiceerbaar als metadata-link` wordt uitsluitend toegekend bij twee geldige, van elkaar
verschillende records, geldige verplichte record- en relatievelden, voor beide records metadatarechten
`toegestaan` en privacy `openbaar`, en een relatie met status `bevestigd`. Een `hypothese` blokkeert
altijd.

Objectmedia wordt volledig los beoordeeld en is alleen dossierbreed toegestaan bij twee records met
voor beide expliciet `toegestaan` objectrechten. Ontbrekende, niet-toegestane of onduidelijke
objectrechten blokkeren objectmedia, maar nooit de metadata-link; omgekeerd mag een om andere redenen
geblokkeerd dossier objectmedia wel toestaan.

Het resultaat bevat de dossierstatus, de dossierbrede objectmedia-indicatie en twee afzonderlijke
lijsten met machineleesbare veldpaden — één voor metadata-blokkades en één voor
objectmedia-blokkades. Beide lijsten bevatten elk veldpad precies eenmaal en staan altijd in dezelfde
lexicografische volgorde, onafhankelijk van de uitvoeringsvolgorde.

Buiten scope van de huidige realisatie: opslag, een REST-endpoint, een beheerinterface en de
daadwerkelijke publicatie. De validator raadpleegt geen externe bronnen; hij bewijst niet dat een URL
bereikbaar is of dat de inhoudelijke claim historisch juist is.

## Verificatie

Widgettests dekken alle statusvarianten, aantallen en labels van statusnodes, afwezigheid van
focusacties, lees- en Tab-volgorde, focusweergave en activatie met beide toetsen. Een tester voert de
scenario's uit het story-worklog aanvullend uit op een echte Flutter-webbuild met één gangbare
desktopbrowser en schermlezer en legt omgeving, revisie, gehoorde tekst, aantallen en focusgedrag
vast.

De koppelingsdossiervalidator is gedekt met backendgedragstests: een volledig geldig dossier met
bevestigde relatie, parametrische scenario's per verplicht recordveld, beide alternatievenparen, een
hypothese-relatie, ontbrekende, niet-toegestane en onduidelijke objectrechten, een verkeerd aantal
records, een ongeldige permanente URL en bewijslink, twee records met dezelfde stabiele referentie,
een geblokkeerd dossier dat objectmedia wel toestaat, en meerdere gelijktijdige fouten met exacte
deterministische veldpadvolgorde.

Testergoedkeuring vereist daarnaast volledig groen, revisiongebonden bewijs voor iedere opdracht in
`.factory/verification.yaml`. Ontbrekend bewijs, een onbekende configversie, toolfout, timeout,
non-zero exitcode of revisionmismatch is nooit groen.
