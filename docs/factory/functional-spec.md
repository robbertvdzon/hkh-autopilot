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

## Persoonsvraag over Heemskerk (gebruikersfrontend)

Vanaf de homepage opent een nieuwe actie "Stel je vraag over Heemskerk" een losstaand, volledig
client-side instappunt (screenKey `start`) met exact één tekstinvoerveld met programmatisch label
"Stel je vraag over Heemskerk", minstens één voorbeeldvraag met een volledige persoonsnaam, een
dekkingsbeschrijving ("Open Archieven-genealogie voor Heemskerk, met Wikidata als aanvullende
context") en een mededeling dat een langer lopende zoekopdracht binnen de sessie kan doorlopen. De
bestaande homepage, servicecontrole en nieuwssectie blijven ongewijzigd.

Bij het indienen van de vraag interpreteert het systeem de tekst volledig deterministisch en
client-side, vóór enige externe aanroep: eerst worden achtereenvolgens vraagwoorden
(`wie, wat, waar, wanneer, welke, hoe`), functiewoorden/lidwoorden (`was, is, geboren, getrouwd,
overleden, gedoopt, de, het, een, van, in, op, te, uit`) en een vaste lijst plaats-/maandnamen
(`Heemskerk, Noord-Holland, Nederland` en de maandnamen) verwijderd. Blijven daarna minstens twee
opeenvolgende hoofdletterwoorden over (Unicode-bewust, dekt Nederlandse diakrieten), dan is een
persoonsnaam herkend (eerste woord voornaam-kandidaat, rest achternaam-kandidaat); blijft precies
één los hoofdletterwoord over, dan is geen naam herkend. Een resterend jaartal en een resterend
gebeurtenistype-woord (`geboorte, huwelijk, overlijden, doop`) worden als losse, optionele
zoekbeperkingen bewaard; deze story toont er nog geen vervolgscherm voor.

Zonder herkende naam verschijnt scherm `no-reliable-source` met letterlijk "Hiervoor vinden we geen
betrouwbare bron", Open Archieven vermeld als "Niet uitgevoerd · persoonsnaam ontbreekt", en
uitsluitend verfijningsvoorstellen die zelf een herkenbare persoonsnaam bevatten. Er wordt in dat
geval geen enkele Wikidata- of Open Archieven-aanroep gedaan.

Het letterlijke woord "Heemskerk" in de oorspronkelijke (niet-genormaliseerde) vraagtekst wordt
voorzetsel-gebaseerd gedisambigueerd: direct voorafgegaan door `in`, `te`, `uit` of `van` is de
betekenis ondubbelzinnig plaats (Wikidata Q9926) en verschijnt geen keuzescherm. Komt "Heemskerk"
voor als los hoofdletterwoord náást een herkende persoonsnaam zonder zo'n direct voorafgaand
voorzetsel, dan is de betekenis ambigu en verschijnt vóór enige zoekopdracht scherm
`meaning-selection`: een radiogroep met Q9926 (plaats) en Q91564725 (achternaam) en de
oorspronkelijke vraag zichtbaar. De labels en beschrijvingen worden live opgehaald bij Wikidata
(`wbsearchentities` gevolgd door `Special:EntityData` voor beide QID's); bij een mislukte live
oproep (netwerkfout, time-out, ongeldige respons) toont het scherm de vaste fallback-labels
"Q9926 · Heemskerk (plaats)" en "Q91564725 · Heemskerk (achternaam)" met een zichtbare
storingsmelding, en blijft de keuze bruikbaar. Resultaten van de twee betekenissen worden nooit
samengevoegd. De knop "Zoek met deze betekenis" bevestigt de keuze; "Vraag aanpassen" brengt de
gebruiker terug naar het startscherm met de oorspronkelijke vraag nog ingevuld, zonder de
interpretatie opnieuw automatisch uit te voeren.

Alle drie schermen (start, meaning-selection, no-reliable-source) zijn volledig bedienbaar met
Tab/Shift+Tab (logische volgorde), Enter (indienen/bevestigen) en, op meaning-selection,
pijltjestoetsen tussen de radio-opties; focus is altijd zichtbaar gemarkeerd via de bestaande
gedeelde `ButtonStyle`-conventie met een 3px-focusrand, en status/instructies zijn als leesbare
tekst gecommuniceerd, niet uitsluitend via kleur. Elk scherm heeft exact één desktop- en één
mobile-uitwerking; bij 320 CSS-pixels breedte blijft `document.scrollWidth == document.clientWidth`
zonder horizontaal scrollen.

Buiten scope van deze eerste drie schermen: de sessie-indicator met live aantallen en het hervatten
van een job na navigatie/reload. Een succesvolle indiening (herkende naam, en bij ambiguïteit een
bevestigde keuze) leidt naar de live zoek- en antwoordroute hieronder.

## Live persoonszoekopdracht en antwoord (gebruikersfrontend + backend)

Een succesvolle indiening op `start`/`meaning-selection` dient de vraag in bij
`POST /api/person-search`. De backend geeft eenmalig een route-gebonden, niet-raadbare
sessiecookie uit (`hkh_person_search_session`, geen login, los van het bestaande admin/Google-
authenticatiemechanisme) en maakt daarmee precies één job aan met een cryptografisch random
job-id. De idempotentiesleutel is sessie-id + genormaliseerde vraagtekst + gekozen
Heemskerk-betekenis: een herhaalde indiening met dezelfde sleutel terwijl de job nog niet terminaal
is, levert dezelfde job-id op zonder nieuwe bronraadpleging.

Direct na jobcreatie start de live Records/Search-aanroep (en de eventueel benodigde Wikidata-
contextaanroep) en wacht het webrequest binnen hetzelfde verzoek maximaal 2000 ms op een terminale,
volledig gevalideerde uitkomst. Is de uitkomst binnen dat budget terminaal, dan toont de gebruiker
direct het passende scherm (`supported-answer`, de no-reliable-source-variant bij nul resultaten, of
`source-outage`), zonder een verplichte tussenstap. Is de job na 2 seconden niet terminaal, dan
retourneert het webrequest met een status die aangeeft dat de opdracht nog loopt; de job zelf blijft
onafhankelijk van dit request doorlopen. Statuspolling, hervatten na navigatie/reload en een
sessie-indicator met aantallen zijn buiten scope van deze route en volgen in de vervolgstory.

Open Archieven Records/Search (`GET https://api.openarchieven.nl/1.1/records/search.json`,
`archive_code=nha`, `eventplace=Heemskerk`, `lang=nl`, `number_show=100`, URL-gecodeerde `name`,
`start` voor paginering) wordt bevraagd met een beschrijvende User-Agent, gzip, maximaal 4
requests/seconde, korte timeouts en een begrensde eindige back-off. Een respons telt alleen als
geslaagd bij HTTP 2xx, geldige JSON, aanwezige verplichte velden (`number_found`, `results`) en een
leeg `error_code`; elke afwijking (ook een gevuld `error_code` bij HTTP 200) is een mislukte
bronraadpleging. Resultaten worden gededupliceerd op `archive_code` + `identifier`. Levert
Records/Search `number_found > 100` op, dan claimt geen enkele route een volledige uitkomst: de job
eindigt met status `PARTIAL`, de gebruiker krijgt een verfijningsverzoek (naam aanvullen, periode of
gebeurtenistype opgeven) en er volgt geen Records/Show-aanroep.

Voor ieder daadwerkelijk getoond kandidaatrecord (na deduplicatie, binnen `number_show=100`) wordt
Open Archieven Records/Show (`GET .../records/show.json`, `archive=nha`, `identifier=<id>`,
`lang=nl`) live bevraagd, met dezelfde validatieregels als Search. Alleen `Person`-, `Event`-,
`RelationEP`- en `Source`-gegevens uit een gevalideerd Show-record mogen een feitelijke
antwoordzin dragen; zonder geldig, live opgehaald Show-record verschijnt geen archiefbewering voor
dat record. Iedere feitelijke zin krijgt direct erachter een genummerde bronmarkering met
beherende instelling, brontype, archief-, register-, akte-/documentnummer en recordnummer/
identifier, plus links naar Open Archieven (`https://www.openarchieven.nl/{archive_code}:
{identifier}`) en, indien aanwezig, `SourceDigitalOriginal`; elk record toont `checkedAt`.

Gecontroleerd voorbeeld: voor 'Wie was Nicolaas Jacobus Sinnige, geboren in Heemskerk in 1878?'
(geen meaning-selection, want het voorzetsel 'in' staat direct vóór 'Heemskerk') levert
Records/Search exact één match op (`archive_code=nha`,
`identifier=002ED0F3-F08C-4223-A5EA-BA385D04336E`); Records/Show toont een geboorte op 25 juli 1878
in Heemskerk met Pieter Sinnige als Vader en Anna Geertruida Eenhuis als Moeder. Het antwoord
vermeldt expliciet en zichtbaar dat deze ene geboorteakte geen volledig levensverhaal is en geen
overzicht van alle gebeurtenissen in Heemskerk in 1878.

Vanuit `supported-answer` kan de bezoeker een rol/persoon uit hetzelfde gevalideerde Show-record
volgen (`followed-connection`, bijvoorbeeld 'Vader' Pieter Sinnige): dit opent een detailweergave die
de oorspronkelijke vraag en het gekozen vervolgspoor zichtbaar houdt en expliciet vermeldt dat een
bronrol geen volledig levensverhaal van die persoon is. Er zijn maximaal twee vervolgsporen per
antwoord — de rollen met een gekoppelde persoonsnaam in `RelationEP` van hetzelfde Show-record, in
recordvolgorde — zonder extra externe aanroep.

Op `supported-answer` en `source-outage` verschijnt optionele Wikidata-informatie uitsluitend onder
een sectie die letterlijk 'Context' heet; deze sectie draagt nooit zelfstandig een geboorte-,
huwelijks-, overlijdens-, doop- of bevolkingsregistratiebewering. Faalt de voor een antwoord
vereiste Records/Search- of Records/Show-aanroep volgens de validatieregels, dan verschijnt
`source-outage`: Open Archieven wordt exact aangeduid als 'tijdelijk niet geraadpleegd', er
verschijnt geen enkele archiefbewering, ook niet wanneer Wikidata wel bereikbaar was (dan uitsluitend
onder 'Context').

`live-search`, `supported-answer`, `followed-connection` en `source-outage` zijn bedienbaar met
Tab/Shift+Tab/Enter, met zichtbare focus; live-/gereed-/Context-/uitvalstatus is zonder kleur
begrijpelijk. Voor elk van deze vier schermen bestaat exact één desktop- en één mobile-uitwerking;
bij 320 CSS-pixels blijft `document.scrollWidth == document.clientWidth` zonder horizontaal
scrollen.

Buiten scope van deze route: statuspolling-API, hervatten na navigatie/reload/terugkeer, een
sessie-indicator met live aantallen, versleutelde opslag met retentie/opschoning (60 min
inactiviteit/24 uur hard), CANCELLED/EXPIRED-afhandeling en Agent Runtime als uitvoeringsadapter.
Dit volgt in de vervolgstory 'Achtergrondopdracht laten doorlopen, sessiestatus tonen, hervatten en
na afloop opschonen'.

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

Buiten scope: de publicatie-workflow zelf, objectmedia-opslag, verwerkingsgrondslag/doel/
bewaartermijn-registratie en koppeling met andere externe archieven dan het
Noord-Hollands Archief-patroon.

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

## Verificatie

De persoonsvraag-interpretatie is gedekt met Dart unit-tests op `PersonQueryInterpreter`
(startscherm-voorbeeldvraag, de epicvraag "Wie was Nicolaas Jacobus Sinnige, geboren in Heemskerk
in 1878?", "Cornelis Heemskerk" als ambigu voorbeeld, een vraag zonder herkenbare naam en een vraag
met precies één overblijvend hoofdletterwoord) en met Flutter-widgettests voor de drie schermen
(labels/teksten, radiogroep-gedrag inclusief de Wikidata-fallback bij een mislukte live oproep,
toetsenbordnavigatie met Tab/Shift+Tab/Enter/pijltjestoetsen en kleuronafhankelijke
statusweergave). Geen enkele test of implementatiecode in `personquery` roept een echte Open
Archieven- of Wikidata-endpoint, of Open Archieven Records/Search/Show, aan.

De live persoonszoekopdracht (`personsearch`) is gedekt met backend unit-, service- en
controllertests (`PersonSearchAnswerBuilderTest`, `PersonSearchJobStoreTest`,
`PersonSearchServiceTest`, `PersonSearchSessionResolverTest`, `PersonSearchRateLimiterTest`,
`RestClientArchivesOpenSearchClientTest`, `PersonSearchControllerTest`), waaronder het verplichte
Nicolaas Jacobus Sinnige-voorbeeld end-to-end (`PersonSearchNicolaasSinnigeExampleTest`) tegen een
fixture die het échte, geneste Open Archieven-schema simuleert (`response.number_found`/
`response.docs` voor Search; hoofdlettergevoelige, geneste `Person`/`Event`/`RelationEP`/`Source`
voor Show) — dit schema is live met `curl` tegen de publieke API geverifieerd, nadat een eerdere
testronde faalde op een zelfbedacht plat schema (zie het SF-2318-worklog). Flutter-widgettests
(`test/personsearch/`) dekken de vier schermen (`live-search`, `supported-answer`,
`followed-connection`, `source-outage`), inclusief bronmarkeringen, Context-sectie, vervolgsporen,
toetsenbordnavigatie en kleuronafhankelijke statusweergave, en `person_query_page_test.dart` dekt de
doorschakeling vanaf een succesvolle indiening.

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

De per-record licentiecontrole is gedekt met twee JSON-LD-fixtures (met en zonder zichtbare
licentie) die respectievelijk licentiestatus "licentie bekend" (met waarde en controledatum) en
`License unknown` opleveren; een gerichte test bevestigt dat de publish-guard publicatie weigert
zodra de licentiestatus `License unknown` is, ook wanneer de verificatiestatus `Verified` is; en een
integratietest met twee records uit dezelfde archiefcollectie bevestigt dat beide records hun eigen,
onafhankelijke licentie-uitkomst behouden (geen afleiding van het ene record naar het andere). Een
Flutter-widget-/semantiektest bevestigt tekstlabel én icoon van de licentiestatusbadge, en een
gerichte kleur-/contrasttest berekent de contrastratio van de gebruikte kleurwaarden (≥4.5:1)
volgens de WCAG 2.1-formule, als vervanging van axe-core conform de bestaande repo-conventie.

Testergoedkeuring vereist daarnaast volledig groen, revisiongebonden bewijs voor iedere opdracht in
`.factory/verification.yaml`. Ontbrekend bewijs, een onbekende configversie, toolfout, timeout,
non-zero exitcode of revisionmismatch is nooit groen.
