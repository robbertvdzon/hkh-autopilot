# Functional Spec

## Gebruikersfrontend

De homepage (`/`) toont eerst de servicecontrole. Na een succesvolle controle blijft de bestaande
ontdekintroductie, productvisieactie en servicekaart staan, wordt daaronder het laatste nieuws
geladen en volgt daarna het homepage-ontdekblok (zie "Homepage-ontdekblok" hieronder). Routes,
navigatie en zichtbare inhoudsvolgorde worden niet door statussemantiek gewijzigd.

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

## Nieuwsentiteiten en zoekfilter (backend)

Het bestaande `GET /api/news`-contract is uitgebreid, zonder nieuwe route of pagina: elk
nieuwsbericht krijgt automatisch labels voor plekken, personen en gebeurtenissen die erin genoemd
worden, en bezoekers kunnen het nieuws doorzoeken op vrije tekst of op zo'n label. Alleen berichten
die via de bestaande admin-createflow in `latest_news` staan doen mee; er is geen concept- of
publicatiestatus.

Entiteitsherkenning gebeurt deterministisch via een statische, in de repo onderhouden gazetteer per
entiteitstype (plek, persoon, gebeurtenis), niet via NLP/NER. Matching is hoofdletterongevoelig,
diakrieten-genormaliseerd en op heel woord, tegen titel en samenvatting samen. Bij meerdere matches
worden alle gevonden entiteiten getoond, gededupliceerd op het canonieke label, gesorteerd op
entiteitstype (plek, persoon, gebeurtenis) en daarbinnen op eerste voorkomen in de tekst. Het
getoonde label is altijd het canonieke label uit de gazetteer, niet de letterlijke tekst uit het
bericht. Geen match levert een lege entiteitenlijst op voor dat bericht, nooit een fout.

De respons bevat per bericht een niet-lege bronvermelding ("Afkomstig uit gepubliceerd
HKH-nieuwsbericht", met publicatiedatum) en, naast de losse berichten, een geaggregeerde
entiteitenlijst met itemtelling over alle gepubliceerde berichten.

De twee nieuwe, los combineerbare (AND) queryparameters zijn een vrije zoekterm (matcht op titel of
samenvatting) en een entiteitsfilter (matcht op een gekozen entiteit). Filtering op entiteit
retourneert uitsluitend berichten die aan die entiteit gekoppeld zijn, elk met het juiste
entiteitstype-label. Een zoekterm of entiteit zonder matches levert een expliciet leeg resultaat op
(HTTP 200, lege lijst, totaal 0), zonder foutstatus of exceptie. Geen enkel veld of queryparameter
uit deze uitbreiding leest of retourneert record-intake-, privacyclassificatie- of
externe-verificatiedata.

Buiten scope: nieuwe REST-routes/controllers, een concept/publicatie-workflow en
NLP/NER-gebaseerde entiteitsherkenning.

## Homepage-ontdekblok (gebruikersfrontend)

Op de homepage staat, naast de bestaande servicestatus- en "Laatste nieuws"-secties, een
ontdekblok waarmee bezoekers uitsluitend al gepubliceerd nieuws doorzoeken: nooit gegevens uit
interne beoordelings-, privacyclassificatie- of externe-verificatieprocessen. Het blok bestaat uit
een gelabeld zoekveld en een rij klikbare entiteitchips (plek/persoon/gebeurtenis), gevuld met de
geaggregeerde entiteiten uit het bestaande `GET /api/news`-contract. Dit is de enige "primaire
ontdekactie" op de homepage; er is geen los, tweede toegankelijkheidspad ernaast.

Een chipklik of zoekopdracht toont een resultatenlijst: per resultaat een titel, samenvatting, een
badge per gekoppelde entiteit (met het entiteitstype) en de niet-lege bronregel uit de respons.
Levert de zoekopdracht of chip nul resultaten op, dan verschijnt een niet-lege lege staat met
suggestiechips — de geaggregeerde entiteitenlijst, die onafhankelijk van het actieve filter blijft
— in plaats van een lege of foutieve weergave.

Elk resultaat is aan te klikken en opent een detailweergave met de volledige berichttekst, de
publicatiedatum en de bronvermelding, plus een terugknop die naar de resultatenlijst terugkeert
zonder de eerdere zoekopdracht of chipselectie te verliezen.

Alle interactieve elementen (zoekveld, chips, resultaatkaarten, terug- en wisknop) zijn volledig
met het toetsenbord bedienbaar: bereikbaar via Tab in logische volgorde en te activeren met Enter
of spatie. Het aantal resultaten wordt na elke zoekopdracht of chipselectie hoorbaar aangekondigd
voor schermlezers via een live statusgebied, en wijzigt telkens wanneer het aantal verandert.
Badges en chips gebruiken vaste kleuren met een contrastratio van minimaal 4.5:1.

Buiten scope: elke wijziging aan `GET /api/news` zelf, en elke reconciliatie met kandidaten uit
interne beoordelings-/verificatieprocessen — dat is een orchestrator-aangelegenheid, geen
frontendwerk.

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

## Recordintake (beheer)

Een collectiebeheerder kan via het beheerformulier precies één lokaal collectierecord aanleveren als
intern concept. De intake vereist een kortlevend, geldig toegangstoken (hetzelfde gemaskeerde
tokenmechanisme als de rest van de beheerfrontend; er is geen apart invoerveld voor autorisatiebewijs)
en verwerkt per verzoek maximaal één record.

Verplichte velden zijn: lokale identifier, titel-of-beschrijving, datering, herkomst, rechtenstatus,
privacyclassificatie en een toegangs- of permalink. Ontbrekende of ongeldige velden leveren een
foutsamenvatting op; er wordt dan geen conceptrecord aangemaakt.

Privacy is een fail-closed regel los van de overige veldfouten: alleen de classificatie
`geen persoonsgegevens` is toegestaan. `mogelijk persoonsgegevens` en `persoonsgegevens` worden altijd
geweigerd, zonder opslag en zonder dat verwerkingsgrondslag, doel, rol of bewaartermijn worden
gevraagd of bewaard.

Een geldige inzending slaat het record op met status `intern_concept`. Het record wordt nooit
gepubliceerd: de respons bevat uitsluitend metadata, nooit publicatie-, download-, preview- of
objectmedia-acties of -URL's, ook niet wanneer de rechtenstatus `publicatie toegestaan` is.

Optioneel kan tegelijk een externe conceptkoppeling (status `concept`) ontstaan naar een extern
archiefrecord (naar het patroon van het Noord-Hollands Archief). Dat gebeurt alleen wanneer een
duurzame URL, een koppelmotivering en een onzekerheidswaarde (`laag`/`middel`/`hoog`) alle drie
aanwezig en geldig zijn; ontbreekt of faalt één van de drie, dan blijft het interne conceptrecord wel
bestaan, maar zonder koppeling.

Na een mislukte validatie toont het formulier een foutsamenvatting, verplaatst de toetsenbordfocus
daarheen en koppelt elke fout programmatisch aan het bijbehorende veld. Succes, fout en
privacyblokkade worden via tekst en een aria-live-statusgebied gecommuniceerd, niet uitsluitend via
kleur. Tokeninhoud, claims of headers worden nooit getoond, gelogd of opgeslagen; afwijzingen bevatten
alleen een technische foutcode (bijvoorbeeld `PRIVACY_CLASSIFICATION_BLOCKED`).

Twee nieuwe, nullable velden worden ingevuld in dezelfde bevestigingsstap als de bestaande
privacyclassificatie: `deceasedStatus` (`onbekend`/`overleden`/`levend`, fail-closed `onbekend`
zonder expliciete invoer) en `nextOfKinConfirmed`, die bevestigt of het record gegevens van een nog
levende nabestaande bevat. Het bestaande `privacyClassification`-veld en zijn fail-closed regel
blijven ongewijzigd; een curator kan een bevestigd-overleden genealogisch record legitiem als
"geen persoonsgegevens" classificeren omdat de AVG niet geldt voor overleden personen.

Bij het invullen/verlaten van het bestaande veld voor de duurzame URL, of bij klikken op de nieuwe
knop "Ophalen", herkent het systeem of de opgegeven URL het patroon
`http://opendata.archieven.nl/id/<adtid>/<guid>` volgt. Zo ja, dan bevraagt het systeem de
bestaande, keyloze `ArchivesNlClient` en toont een niet-blokkerend paneel "Brongegevens (extern,
ter controle)" met naam, geboortedatum, sterftedatum, licentie, bron-URI en een statuslabel
(Geverifieerd/Geen match/Niet bereikbaar). Volgt de URL het patroon niet, dan toont het paneel
direct "Niet bereikbaar" zonder aanroep. Opeenvolgende wijzigingen aan het veld triggeren een
gedebouncte aanroep — één netwerkaanroep na de laatste wijziging binnen de cooldown — client-side
geïmplementeerd zonder nieuwe library.

Naast het paneel staan twee acties: "Bevestig brongegevens en gebruik bij record" en "Sla op zonder
externe brongegevens". Beide laten "Opslaan record" beschikbaar en functioneel. Bij bevestigen en
opslaan bevraagt de backend de externe bron opnieuw (de eerder aan de curator getoonde preview-data
wordt hiervoor nooit vertrouwd of hergebruikt) en classificeert zowel een lokaal als een extern
samengesteld, tijdelijk en niet-persistent genealogisch record met de bestaande
`PrivacyClassifier.classify()`. Alleen wanneer zowel de lokale als de externe classificatie
`Processable` opleveren, worden naam, geboortedatum en sterftedatum daadwerkelijk opgeslagen. In
elk ander geval — lokaal `Blocked`, `deceasedStatus` nog `onbekend`, of externe classificatie
`Blocked` (inclusief "geen sterftedatum gevonden ondanks lokaal bevestigd overlijden") — weigert het
systeem persistentie van deze velden en toont een expliciete melding die de reden benoemt. Licentie,
bron-URI en ophaaldatum zijn niet-persoonsgebonden en worden altijd opgeslagen bij een succesvolle
bevraging, ongeacht de classificatie-uitkomst. Bij "Sla op zonder externe brongegevens" wordt geen
van de externe kernvelden opgeslagen. Er wordt uitsluitend gestructureerde data opgeslagen; de ruwe
JSON-LD-respons wordt nooit bewaard.

Het paneel gebruikt `Semantics(liveRegion: true)`, naar het bestaande patroon van
`RecordIntakeForm`. Alle drie de knoppen ("Ophalen", "Bevestig brongegevens en gebruik bij record",
"Sla op zonder externe brongegevens") zijn met Tab bereikbaar in logische volgorde en met
Enter/Spatie te activeren.

Buiten scope: de publicatie-workflow zelf, objectmedia-opslag, verwerkingsgrondslag/doel/
bewaartermijn-registratie, koppeling met andere externe archieven dan het
Noord-Hollands Archief-patroon, publieke weergave van de externe brongegevens, een nieuw extern
protocol/token, en wijziging aan de bestaande route `POST /api/external-verification` of aan de
bestaande matcher-/publish-guardlogica van `externalverification`.

## Privacyclassificatie (genealogische records)

De AVG geldt niet voor overleden personen. Een genealogisch record (bijvoorbeeld een bidprentje)
wordt daarom automatisch als `Processable` geclassificeerd zodra vaststaat dat de persoon overleden
is én er geen gegevens van een nog levende nabestaande in het record staan. In elk ander geval
(onbekende of levende overlijdensstatus, of wel een gedetecteerd nabestaande-veld) is het record
`Blocked` met een verplichte, leesbare tekstuele reden — nooit alleen een interne code. Ontbreekt of
is de overlijdensstatus niet herkend, dan is de uitkomst standaard fail-closed `Blocked`.

Een `Blocked`-record kan nooit gepubliceerd worden: een publish-guard weigert publicatie voor elk
geblokkeerd record en staat publicatie toe voor elk `Processable`-record. Er is nog geen bestaande
publicatieworkflow in de repository om deze guard op aan te sluiten; een latere publicatiefeature kan
hem hergebruiken.

Aanvullend hierop wordt per in het record genoemde persoon (de hoofdpersoon of een familielid)
beoordeeld of deze vermoedelijk nog leeft, volgens de FamilySearch 110/95-jaarregel: een extern
gedocumenteerde, niet-wettelijke vuistregel uit de genealogiepraktijk (FamilySearch), geen
wettelijke AVG-norm. Een persoon is vermoedelijk levend wanneer er geen overlijdens- of
begrafenisdatum bekend is, én de persoon jonger dan 110 jaar zou zijn, of minder dan 95 jaar
geleden trouwde of een kind kreeg (grenzen inclusief). Ontbrekende of onleesbare datumvelden leiden
altijd tot een fail-closed blokkade, nooit tot automatische vrijgave. Zodra één genoemde persoon zo
wordt aangemerkt, blijft het hele record `Blocked`, aanvullend op de bestaande
overlijdensstatus-/nabestaande-controles. Bekende beperking: datums worden verwacht met volledige
dag-precisie (ISO-8601); een datum met alleen een jaartal wordt ook verwerkt (met 1 januari als
impliciete dag), maar fijnere granulariteit dan dag of jaar wordt niet ondersteund.

Als aanvullend, onafhankelijk veiligheidsnet wordt ook gekeken naar de industriestandaard GEDCOM
7.0 RESN-markering (vertrouwelijk/afgesloten/privacy), via een nieuw optioneel veld met ruwe
GEDCOM-brontekst op het record. Bevat die brontekst een RESN-waarde `CONFIDENTIAL`, `LOCKED` of
`PRIVACY` — op recordniveau of dieper, binnen een gebeurtenis/feit — dan is het record altijd
`Blocked`, ongeacht de uitkomst van de leeftijdsregel of de overige controles. Is er geen
GEDCOM-brondata beschikbaar (bijvoorbeeld omdat de bron alleen JSON of RDF is), dan heeft dit
signaal geen invloed en blijft de bestaande classificatie leidend. Syntactisch ongeldige, niet-lege
GEDCOM-brontekst wordt fail-closed als blokkerend behandeld. Er is nog geen echte GEDCOM-koppeling
met een externe bron gebouwd; het veld wordt vooralsnog alleen met synthetische testfixtures
gevuld. Bekende beperking: alleen GEDCOM 7.0 RESN-syntax wordt ondersteund.

In de beheerfrontend (`frontend-admin`) wordt de classificatiestatus altijd getoond met zowel een
tekstlabel als een icoon, nooit uitsluitend via kleur, met een contrastratio van minimaal 4.5:1
tussen voorgrond- en achtergrondkleur.

Buiten scope: de daadwerkelijke publicatieworkflow zelf, opslag/REST-endpoint voor genealogische
records en koppeling met externe archieven.

## Externe verificatie (archieven.nl)

Het systeem kan zelf controleren of een lokaal genealogisch record (bijvoorbeeld een bidprentje)
overeenkomt met het publieke, vrij toegankelijke archief van archieven.nl/Noord-Hollands Archief. Er
is geen inlogtoken nodig om deze bron te bevragen. Per verzoek wordt precies één lokaal record
(lokale identifier, naam, geboortedatum, overlijdensdatum en de archieven.nl-koppeling `adtid`/
`guid`) vergeleken met wat het archief teruggeeft via de resolvebare URI
`http://opendata.archieven.nl/id/<adtid>/<guid>`, bevraagd met header `Accept: application/ld+json`.

Wanneer naam en geboorte-/overlijdensdatum overeenkomen met de opgehaalde archiefkernvelden krijgt
het record status `Verified`. Zonder match — bijvoorbeeld bij een niet-bestaande of ongeldige guid —
krijgt het record status `Unverified`. Een `Unverified`-record kan nooit gepubliceerd worden: een
publish-guard weigert publicatie voor elk `Unverified`-record en staat publicatie toe voor elk
`Verified`-record. Er is nog geen bestaande publicatieworkflow in de repository om deze guard op
aan te sluiten; een latere publicatiefeature kan hem hergebruiken.

Naast de naam-/datumverificatie controleert het systeem ook, per record en als onderdeel van
dezelfde bevraging, of dat specifieke archiefrecord een hergebruikslicentie vermeldt (bijvoorbeeld
`CC0`), gelezen uit het `license`-veld van de opgehaalde JSON-LD-respons. Dit is een nieuw, los
statusbegrip — de licentiestatus — dat niet wordt samengevoegd met de bestaande verificatiestatus
`Verified`/`Unverified`. Vermeldt het record een licentie, dan krijgt het licentiestatus "licentie
bekend" met de vastgestelde licentiewaarde en controledatum. Ontbreekt de licentie-informatie, dan
krijgt het record licentiestatus `License unknown`. Dit gebeurt uitsluitend op basis van het
antwoord van dát ene record: er wordt nooit een licentiewaarde van een ander record uit dezelfde
archiefcollectie hergebruikt of als aanname overgenomen, ook niet wanneer andere records uit
dezelfde collectie wel een bekende licentie hebben. De publish-guard weigert publicatie ook
wanneer de licentiestatus `License unknown` is, onafhankelijk van de verificatiestatus — een
`Verified`-record met `License unknown` wordt dus alsnog niet gepubliceerd.

Er wordt nooit de volledige externe brondata opgeslagen: alleen de externe URI, welke velden
gematcht zijn, het controletijdstip, de resulterende status en de licentiestatus (met, indien
bekend, licentiewaarde en controledatum).

Alleen wanneer het archiefendpoint zelf expliciet een toegangstoken eist (vandaag niet het geval),
toont het systeem één invoerveld hiervoor. Dat token wordt versleuteld opgeslagen en verschijnt
nooit in leesbare vorm in een UI-respons of in logoutput.

In de beheerfrontend (`frontend-admin`) wordt de externe archiefbron getoond als een link met een
programmatisch gekoppeld aria-label/semantisch label dat aankondigt dat de link een externe bron in
een nieuw tabblad opent, naar de bestaande toegankelijkheidsconventies van `frontend-admin`. De
licentiestatus krijgt een eigen statusbadge, naast de bestaande verificatie- en privacystatusbadges,
met zowel een tekstlabel als een icoon en een contrastratio van minimaal 4.5:1, naar het patroon van
`PrivacyClassificationStatusView`.

Buiten scope: de daadwerkelijke publicatieworkflow zelf, koppeling met andere externe archieven dan
het archieven.nl/Noord-Hollands Archief-patroon, het daadwerkelijk bouwen van een tokenprotocol
voor een endpoint dat vandaag geen autorisatie vereist (alleen het invoerveld en de versleutelde
opslag ervoor zijn voorbereid), en wijzigingen aan de bestaande naam-/datumverificatielogica.

## Brononafhankelijk historisch metadata-contract (backend)

Het herbruikbare backendcontract voor individuele historische metadata levert één brononafhankelijk
voor een extern zoekresultaat. Het resultaat bevat, wanneer het volledig geverifieerd is, uitsluitend
een stabiele bronidentifier, een resolvebare bronlink, bronhouder, titel en/of beschrijving, datering,
bronversie of momentopname-identificatie, de server-side opgehaalde UTC-tijd, afzonderlijke statussen
voor metadatarechten en object-/mediarechten, een privacystatus, een technische beschikbaarheidsstatus
en een afgeleide verificatiestatus met machineleesbare reden.

`VERIFIED` wordt alleen teruggegeven wanneer alle verplichte waarden aanwezig, syntactisch geldig en
onderling consistent zijn, de bron beschikbaar is, metadatarechten `ALLOWED` zijn en de privacycontrole
geen blokkade oplevert. Ontbrekende, ongeldige of tegenstrijdige brondata, onbekende of beperkte
metadatarechten, onbekende of geblokkeerde privacy, een lege/ongeldige respons of tijdelijke bronuitval
levert `UNVERIFIED` op. De minimale uitkomst mag alleen een bekende veilige bronidentifier/-link en de
technische status behouden; niet-geverifieerde titel, beschrijving, datering en persoonsgegevens
worden niet teruggegeven. Onbekende object-/mediarechten maken metadata niet automatisch ongeldig,
maar geven nooit toestemming voor media.

De `OpenArchievenMetadataAdapter` leest uitsluitend noodzakelijke velden uit JSON-LD van Open
Archieven/Noord-Hollands Archief. Iedere bevraging haalt de actuele bron opnieuw op: een expliciete
bronversie heeft voorrang op een HTTP-ETag of `Last-Modified`-momentopname-indicatie en een ouder
resultaat wordt niet gecachet als actuele broninformatie. De adapter stuurt de beschrijvende
user-agent `HKH-Autopilot-HistoricalMetadata/1.0` en gebruikt één procesbrede limiet met minimaal
251 ms tussen verzoeken. Daarmee worden maximaal vier verzoeken per seconde vanuit de server-egress
toegelaten, zonder een afzonderlijke bucket per doelhost. Volledige bronpayloads, media, gevoelige
persoonsgegevens en niet-noodzakelijke broninhoud worden niet opgeslagen, geretourneerd of gelogd.

Dit herbruikbare contract blijft afzonderlijk van de publieke zoekroute hieronder; de bestaande
individuele verificatieroute blijft ongewijzigd. De contract- en adaptertests dekken onder meer
geldige metadata, onbekende rechten, privacyblokkade, bronuitval, lege/ongeldige respons,
tegenstrijdige bronwaarden, gewijzigde bronversies en de user-agent/rate-limitregels.

## Publieke historische zoekroute

De homepage bevat naast `Laatste nieuws` een zelfstandige ingang `Historisch zoeken`. Deze route
raadpleegt alleen openbare externe bronnen en gebruikt geen nieuwsresultaten, record-intakegegevens,
adminfunctionaliteit of lokale privacyclassificaties. Zoekopdrachten, bronpayloads, scans, foto's en
ruwe externe persoonsgegevens worden niet opgeslagen.

De backendroute `GET /api/historical-search` gebruikt een eigen zoekcontract met de optionele
parameters `q`, `place`, `person`, `event`, `fromYear`, `toYear` en `source`, plus `start` (standaard
0) en `limit` (standaard 100, maximaal 100). Lege waarden worden genegeerd. `fromYear` en `toYear`
moeten samen twee viercijferige jaren vormen en het vanafjaar mag niet na het eindjaar liggen. De
bronkeuze is `EUROPEANA` of `OPEN_ARCHIEVEN`; zonder keuze worden beide bronnen bevraagd en worden de
resultaten cursorgewijs samengevoegd tot maximaal de gevraagde paginagrootte.

De adapters leveren één genormaliseerd resultaatmodel met `source`, `sourceRecordId`, `stableUrl`,
`title`, `description`, `person`, `event`, `dateStart`, `dateEnd`, `institution`, `rights`,
`privacy`, `retrievedAt`, `technicalStatus`, `metadataRights`, `objectMediaRights` en
`privacyStatus`. Alleen een geldige, door de bron geleverde HTTP(S)-recordlink en bronidentifier
worden gebruikt; links worden niet lokaal geconstrueerd. Ontbrekende, ongeldige of tegenstrijdige
metadata wordt niet inhoudelijk ingevuld. Titel, beschrijving, persoon, gebeurtenis, datering,
bronhouder en bronrechten worden alleen getoond wanneer metadatarechten expliciet `ALLOWED` en
privacy expliciet `CLEAR` zijn. Object-/mediarechten blijven een afzonderlijke status en verlenen
nooit automatisch medierechten.

Europeana gebruikt `query`, herhaalde `qf`, `rows` en `start`; persoon wordt `who:<persoon>`, plek
`where:<plek>` en een volledig ingevulde periode een inclusieve `YEAR:[<van> TO <tot>]`. Zonder
`HKH_EUROPEANA_WSKEY` is alleen Europeana uitgeschakeld. Open Archieven gebruikt `name`, optioneel
`eventplace`, `number_show` en `start`; een gebeurtenis wordt als laagzekere naamterm gemarkeerd en
de periode volgt de ondersteunde jaarzoeksyntaxis. Open Archieven is beperkt tot maximaal vier
serververzoeken per seconde met minimaal 251 ms tussenruimte en een beschrijvende User-Agent.

De route geeft per bron een technische status (`AVAILABLE`, `DISABLED`,
`TEMPORARILY_UNAVAILABLE` of `INVALID_RESPONSE`) met een veilige, korte melding en de
geaggregeerde zoektoestand `RESULTS`, `NO_RESULTS`, `PARTIAL_AVAILABILITY` of `SOURCE_FAILURE`.
Alleen bronnen die uiteindelijk `AVAILABLE` zijn dragen bij aan `results` en `total`. Als minstens
één bron beschikbaar is, blijven diens resultaten zichtbaar wanneer een andere bron uitvalt; de
frontend toont per falende bron de tekst "niet geconfigureerd", "tijdelijk niet beschikbaar" of
"ongeldige bronrespons". Een volledig beschikbare maar lege zoekopdracht is `NO_RESULTS` en is dus
geen bronfout.

Een foutieve zoekopdracht geeft HTTP 400 met een leesbare validatiefout. Volledige bronuitval geeft
`SOURCE_FAILURE`, geen resultaatcount en een programmatisch uitleesbare bronprobleemstatus met de
toetsenbordbedienbare actie `Opnieuw proberen`. Transportfouten blijven een afzonderlijke
retrybare technische status. Bij uitval tijdens paginering wordt de bronstatus bijgewerkt, telt de
bron niet meer mee en wordt de effectieve offset aangepast zodat beschikbare resultaten bereikbaar
blijven. De gebruikerspagina communiceert laden, resultaten, nul resultaten, gedeeltelijke
beschikbaarheid, bronuitval, validatie en opnieuw proberen via één status/live-regio.
Resultaatkaarten tonen de bronidentifier, ophaaldatum, afzonderlijke technische/rechten/
privacystatussen en een tekstueel herkenbare externe link die uitsluitend naar de door de bron
geleverde URL verwijst.

## Publieke recorddetailpagina en externe bronverificatie

Naast de bestaande beheerfrontend heeft de gebruikersfrontend nu een publieke recorddetailpagina
(`RecordDetailPage`, module `records`) met een in-/uitklapbare sectie "Externe bronverificatie".
De pagina bevraagt hiervoor een nieuwe, publieke en ongeauthenticeerde backendroute,
`GET /api/records/{localIdentifier}`, die per record uitsluitend afgeleide, niet-privacygevoelige,
publicatiewaardige velden teruggeeft — nooit de ruwe `RecordIntakeRecord`-data (status,
`deceasedStatus`, `nextOfKinConfirmed`).

De backend berekent bij elk verzoek opnieuw, server-side, een van drie statussen (nooit los
opgeslagen):

- `NO_INTAKE`: er bestaat geen record-intake voor deze `localIdentifier`;
- `SAVED_WITHOUT_SOURCE`: er bestaat een record-intake, maar de externe brongegevens
  (`archive*`-velden) ontbreken, of een beheerder heeft het record nog niet expliciet bevestigd;
- `CONFIRMED`: de externe brongegevens zijn aanwezig, een beheerder heeft het record expliciet
  bevestigd, én een bij dit verzoek opnieuw uitgevoerde privacyclassificatie levert `Processable`
  op.

De beheerdersbevestiging is een nieuwe, aparte actie (`POST
/api/admin/record-intake/{localIdentifier}/confirm`) in het bestaande admin-only pad, met dezelfde
tokenverificatie als de rest van de beheerfrontend. Alleen het vullen van de externe brongegevens
is onvoldoende voor `CONFIRMED`: de bevestiging is een bewuste, losse stap die `confirmedBy` en
`confirmedAt` vastlegt.

Levert een latere, live herclassificatie voor een eerder bevestigd record `Blocked` op (bijvoorbeeld
na een wijziging van de overlijdensstatus), dan toont de publieke pagina voor dat verzoek dezelfde
neutrale melding als `SAVED_WITHOUT_SOURCE`, zonder dat de bewaarde bevestiging (`confirmedBy`/
`confirmedAt`) gewist wordt. Wordt het record later weer `Processable`, dan verschijnt de
`CONFIRMED`-weergave automatisch weer, zonder verdere actie — dit zelfherstellende gedrag is
functioneel gedekt door zowel een backend- als een Flutter-integratietest.

Bij `CONFIRMED` toont de sectie: statuslabel "Extern geverifieerd" (tekst én icoon, nooit
uitsluitend kleur), naam, geboortejaar en — indien aanwezig — sterftejaar (beide afgeleid uit de
bestaande dag-precieze `archiveBirthDate`/`archiveDeathDate`-velden, maar uitsluitend als jaartal
getoond), licentie, een klikbare link met zichtbare tekst ("Bekijk bron") naar de externe bron die
in een nieuw tabblad opent zonder `window.opener` bloot te stellen en met een programmatisch
gekoppeld label dat aankondigt dat het een externe bron is (naar het bestaande patroon in
`frontend-admin`), en de tekst "Bevestigd door beheerder op [datum]".

Bij `SAVED_WITHOUT_SOURCE`, `NO_INTAKE` en een gedegradeerd `CONFIRMED`-record toont de sectie
bewust exact dezelfde neutrale, niet-technische melding, zonder velden en zonder link — er is geen
apart "ingetrokken"-bericht, om geen metadata over een eventuele eerdere publicatie te lekken.

De sectie is in-/uitklapbaar via een toegankelijke knop met een expliciete `expanded`-status en een
programmatische koppeling naar de sectie-inhoud (het Flutter-equivalent van
`aria-expanded`/`aria-controls`), en volledig met het toetsenbord (Tab + Enter) bereikbaar en
bedienbaar.

Bestaande lokale recordvelden, paginanavigatie en de bestaande "Ontdek"-zoekfunctie
(`GET /api/news`) blijven functioneel ongewijzigd; deze nieuwe sectie is er niet aan gekoppeld en
levert er geen resultaten aan.

Buiten scope: wijzigingen aan de bestaande route `POST /api/external-verification` en de
bijbehorende matcher-/publish-guardlogica, wijziging aan de bestaande admin-bevestigingsflow van de
recordintake zelf, en elke koppeling met de "Ontdek"-nieuwszoekfunctie.

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

De privacyclassificatie is gedekt met backend unit-tests (`PrivacyClassifierTest`,
`PrivacyPublishGuardTest`): overleden zonder nabestaande-velden → `Processable`; minimaal drie
fixture-varianten met een gedetecteerd nabestaande-veld → `Blocked` met de exacte reden "Bevat
gegevens van levende nabestaande"; ontbrekende, niet-herkende, `onbekend`- en `levend`-status →
fail-closed `Blocked`; niet-lege tekstuele reden voor zowel `Processable` als elke `Blocked`-variant;
en de publish-guard die publicatie weigert voor elk `Blocked`-record en toestaat voor elk
`Processable`-record. De GEDCOM RESN-regel is gedekt met `GedcomResnRuleTest` (geen brontekst,
geldige brontekst zonder RESN, RESN op record- en feitniveau, niet-blokkerende RESN-waarden en
syntactisch ongeldige brontekst) en met aanvullende `PrivacyClassifierTest`-scenario's die
bevestigen dat een blokkerend RESN-signaal de totaaluitkomst `Blocked` maakt ongeacht de
leeftijdsregel, en dat een `NONE`/`NOT_APPLICABLE`-signaal de bestaande classificatie ongewijzigd
laat. Een Flutter-widgettest op de semantiekboom van `frontend-admin` bevestigt
tekstlabel én icoon voor beide statussen, en een gerichte kleur-/contrasttest berekent de
contrastratio van de gebruikte kleurwaarden (≥4.5:1) volgens de WCAG 2.1-formule, als vervanging van
axe-core conform de bestaande repo-conventie.

De recordintake is gedekt met backend unit-, integratie- en contracttests (tokenverificatie en
secret-redactie, de enkel-recordlimiet, verplichte-veldenvalidatie, fail-closed blokkade van beide
persoonsgegevensclassificaties, opslag uitsluitend als `intern_concept`, de optionele
conceptkoppeling en afwezigheid van media-/publicatievelden) en met frontend-admin widget- en
toegankelijkheidstests voor de foutsamenvatting, focusverplaatsing, per-veld foutkoppeling en het
aria-live-statusgedrag van `RecordIntakeForm`.

De externe-archiefpreview en de dubbele fail-closed persistentieregel zijn gedekt met een
backend-integratietest tegen een fixture-archiefendpoint (patroonherkenning, Geverifieerd/Geen
match/Niet bereikbaar), een backend-test dat opslaan zonder externe gegevens alsnog slaagt, een
opslagtest die bevestigt dat naam/geboorte-/sterftedatum/licentie/bron-URI/ophaaldatum alleen bij
een dubbel `Processable`-resultaat opgeslagen worden (en leeg blijven bij lokaal `Blocked`,
`deceasedStatus` nog `onbekend`, of externe `Blocked` inclusief ontbrekende sterftedatum), en een
reflectie-/opslagtest die bevestigt dat de volledige/ruwe externe respons nergens bewaard wordt. Een
frontend-widgettest simuleert snel opeenvolgende invoerwijzigingen van de duurzame URL en telt het
aantal netwerkaanroepen (precies één na de debounce-cooldown), en een Flutter widget-/
semantiektest bevestigt de `Semantics(liveRegion: true)`-regio van het paneel en de Tab/Enter/
Spatie-bedienbaarheid van alle drie de knoppen, aangevuld met een gerichte WCAG 2.1-contrasttest
(≥4.5:1) op de gebruikte statuskleuren.

De externe verificatie is gedekt met backend unit-, client- en integratietests: een geautomatiseerde
integratietest tegen een fixture-/mock-archiefendpoint bevestigt de `Accept: application/ld+json`-
header zonder autorisatietoken; minimaal twee verschillende matching-fixtures leveren `Verified` op;
een fixture met een ongeldige guid levert `Unverified` op en een gerichte test bevestigt dat de
publish-guard publicatie voor dat record weigert; een reflectie-/schema-assertie op de migratie/
entiteit bevestigt dat uitsluitend de minimale verificatievelden worden opgeslagen; en een test op
logoutput en API-respons bevestigt de afwezigheid van de tokenwaarde wanneer het mock-endpoint een
toegangstoken vereist. Een Flutter-widgettest op de semantiekboom van `frontend-admin` bevestigt dat
de link naar archieven.nl een aria-label/semantisch label heeft dat aankondigt dat een externe bron
in een nieuw tabblad opent.

Het brononafhankelijke historische metadata-contract is gedekt met `HistoricalMetadataContractTest`
en `OpenArchievenMetadataAdapterTest`: de tests bevestigen de volledige `VERIFIED`-uitkomst voor een
geldige fixture, de veilige minimale `UNVERIFIED`-uitkomst bij ontbrekende of tegenstrijdige data,
het onderscheid tussen metadata- en object-/mediarechten, privacyredactie, tijdelijke bronuitval,
lege respons, actuele bronversie zonder cache, de beschrijvende user-agent en de gedeelde
server-egresslimiet.

De per-record licentiecontrole is gedekt met twee JSON-LD-fixtures (met en zonder zichtbare
licentie) die respectievelijk licentiestatus "licentie bekend" (met waarde en controledatum) en
`License unknown` opleveren; een gerichte test bevestigt dat de publish-guard publicatie weigert
zodra de licentiestatus `License unknown` is, ook wanneer de verificatiestatus `Verified` is; en een
integratietest met twee records uit dezelfde archiefcollectie bevestigt dat beide records hun eigen,
onafhankelijke licentie-uitkomst behouden (geen afleiding van het ene record naar het andere). Een
Flutter-widget-/semantiektest bevestigt tekstlabel én icoon van de licentiestatusbadge, en een
gerichte kleur-/contrasttest berekent de contrastratio van de gebruikte kleurwaarden (≥4.5:1)
volgens de WCAG 2.1-formule, als vervanging van axe-core conform de bestaande repo-conventie.

De nieuwsentiteiten en het zoekfilter zijn gedekt met backend unit- en integratietests: de
gazetteer-matcher zelf (geen match, hoofdletter-/diakrietenongevoeligheid, heel-woord-matching,
dedup op canoniek label en sortering per type/eerste-voorkomen), een routecontracttest die
bevestigt dat de news-module nog steeds precies twee controllers/twee routes registreert, een
OpenAPI-schematest (`/v3/api-docs`) die het uitgebreide responscontract als build-documentatie
vastlegt, integratietests voor vrije-tekstzoek met niet-lege bronvermelding, entiteitsfilter met
correct typelabel, een expliciet leeg resultaat (HTTP 200, totaal 0) voor zowel een onvindbare
zoekterm als een onbekende entiteit, afwezigheid van record-intake-/privacyclassificatie-/
externe-verificatievelden in de respons, en een fixture die een bericht buiten `latest_news` om
rechtstreeks in de store-laag plaatst en aantoont dat dit nooit in entiteiten of zoekresultaten
verschijnt. Een Flutter-test op `backend_client_test.dart` dekt dat de gebruikersfrontend het
`{items, total, entities}`-responscontract correct parset, inclusief het meesturen van optionele
`q`/`entity`-queryparameters en het weglaten ervan wanneer beide ontbreken.

Het homepage-ontdekblok is gedekt met Flutter widget-/semantiektests
(`discover_section_test.dart`): entiteitchips die uit de API-`entities` renderen en waarvan een
klik een resultatenlijst met minimaal 1 item en een niet-lege bronregel toont; een onbekende
zoekterm die de niet-lege lege staat met suggestiechips toont; een resultaatkaart die de
detailweergave (volledige tekst, publicatiedatum, bron) opent met een werkende terugknop naar de
resultatenlijst; een volledig toetsenbord-only doorloop (`tester.sendKeyEvent`, geen tap/muis) van
zoekveld, chip, resultaatkaart en terug-/wisknop, elk uitsluitend geactiveerd met Enter of spatie;
een semantiekboomtest die het label/de rol van elk interactief element bevestigt; een gerichte
kleur-/contrasttest (WCAG-formule, ≥4.5:1) op de badge-/chipkleuren; een test die bevestigt dat de
resultatentelling via `Semantics(liveRegion: true)` loopt en na elke zoekactie/chipselectie
wijzigt; en een test die aantoont dat uitsluitend `LatestNewsItem`/`NewsEntity`/
`AggregatedNewsEntity`-velden gerenderd worden, nooit record-intake-, privacyclassificatie- of
externe-verificatiedata.

De publieke historische zoekroute is gedekt met `HistoricalSearchTest` en
`historical_search_test.dart`. De backendtests controleren het responsveld `state`, alle vier de
geaggregeerde toestanden, de vier per-bronstatussen, veilige meldingen, beschikbare-resultaten-
merging, uitsluitend beschikbare bijdragen aan `total`, uitval tijdens paginering en de effectieve
offset. De Flutter-tests controleren de zichtbaarheid van partiële resultaten, het onderscheid
tussen nul resultaten en volledige bronuitval, de bronmeldingen, één status/live-regio, de
toetsenbordbedienbare retryactie, paginering met een korte pagina en behoud van bronmetadata.

De publieke recorddetailpagina en externe bronverificatie zijn gedekt met backend unit-,
integratie- en Flutter widget-/semantiektests: `RecordPublicStatusResolverTest` dekt alle
statusovergangen (inclusief het zelfherstellende gedrag), `RecordPublicApiIntegrationTest`
(Testcontainers) bevestigt end-to-end via de publieke route en de admin-bevestigingsactie dat een
tweede opvraging na een live herclassificatie naar `Blocked` de neutrale status oplevert zonder
`confirmedBy`/`confirmedAt` te wissen, en dat de publieke route nooit ruwe `RecordIntakeRecord`-
velden lekt en altijd HTTP 200 teruggeeft (ook zonder bestaand record). `record_detail_page_test.dart`
bevestigt via de Flutter-semantiekboom: de h2 "Externe bronverificatie" met status, naam,
geboortejaar, sterftejaar, licentie en linktekst bij `CONFIRMED` (met een expliciete assertie dat er
geen dag-/maandgetal in de datumtekst voorkomt), dezelfde neutrale melding zonder velden/link voor
zowel `SAVED_WITHOUT_SOURCE` als `NO_INTAKE`, het zelfherstellende gedrag over twee opeenvolgende
paginaladingen, volledige toetsenbordbereikbaarheid/-activering van de bronlink
(`tester.sendKeyEvent`, geen tap), een gerichte WCAG 2.1-contrasttest (≥4.5:1) op de statuskleuren,
tekstlabel-plus-icoon voor de statusbadge, een `expanded`/`aria-controls`-semantiekboomvergelijking
vóór en na de toggle, en een regressietest dat bestaande recordvelden, navigatie en de
"Ontdek"-zoekfunctie ongewijzigd blijven. Een Flutter-test op `backend_client_test.dart` dekt dat
`loadRecord` het `RecordPublicView`-responscontract correct parset.

Testergoedkeuring vereist daarnaast volledig groen, revisiongebonden bewijs voor iedere opdracht in
`.factory/verification.yaml`. Ontbrekend bewijs, een onbekende configversie, toolfout, timeout,
non-zero exitcode of revisionmismatch is nooit groen.
