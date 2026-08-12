# hkh-autopilot-14 - Publieke historische zoekroute met herleidbare bronmetadata

## Story

Publieke historische zoekroute met herleidbare bronmetadata

<!-- refined-by-factory -->

## Scope

Voeg een zelfstandige publieke historische zoekroute toe naast ‘Laatste nieuws’. De route gebruikt geen nieuws-, record-intake- of lokale privacygegevens.

De backend levert één genormaliseerd zoekcontract voor twee versieerbare bronadapters:

- Europeana via `GET https://api.europeana.eu/record/v2/search.json`.
- Open Archieven via `GET https://api.openarchieven.nl/1.1/records/search.json`, met de optionele detailopvraag via `records/show.json` wanneer aanvullende recordmetadata nodig is.

De route ondersteunt vrije tekst, plek, persoon, gebeurtenis, vanafjaar, eindjaar en bronkeuze. Paginering gebruikt een startpositie en een maximum van 100 resultaten per verzoek. De bronkeuze kan één bron beperken; zonder bronkeuze worden beide bronnen bevraagd. Ontbreekt de Europeana-wskey, dan wordt alleen Europeana uitgeschakeld en blijft Open Archieven beschikbaar.

Beide adapters normaliseren resultaten naar:

`source`, `sourceRecordId`, `stableUrl`, `title`, `description`, `person`, `event`, `dateStart`, `dateEnd`, `institution`, `rights`, `privacy` en `retrievedAt`.

Een stabiele externe URL wordt uitsluitend overgenomen uit het bronantwoord. Er worden geen URL’s voor bronrecords geconstrueerd. Alleen resultaten met een geldige externe recordlink worden als resultaatkaart aan bezoekers getoond.

De route bewaart geen zoekopdrachten, scans, foto’s, ruwe API-antwoorden of ruwe externe persoonsgegevens. Externe persoonsinformatie wordt alleen als minimaal genormaliseerd veld verwerkt wanneer de bron dit expliciet en veilig beschikbaar stelt; zij wordt niet als ruwe bronpayload doorgegeven.

## Acceptance criteria

- De homepage bevat een zelfstandige, gelabelde ingang ‘Historisch zoeken’ naast ‘Laatste nieuws’.
- De historische zoekroute gebruikt een eigen backendcontract en clientbron en roept geen nieuwsroute of adminfunctionaliteit aan.
- Het formulier ondersteunt vrije tekst en optionele filters voor plek, persoon, gebeurtenis, vanafjaar, eindjaar en bron.
- Een periode wordt gevalideerd als twee viercijferige jaren waarbij het vanafjaar niet na het eindjaar ligt. Lege filters worden niet naar de bron gestuurd.
- Europeana gebruikt `query`, herhaalde `qf`, `rows` en `start`; persoon wordt als `qf=who:<persoon>` verwerkt, plek als plaatsfilter, gebeurtenis als vrije zoekterm en de periode als inclusieve `qf=YEAR:[<van> TO <tot>]`.
- Open Archieven gebruikt `name`, eventueel `eventplace`, `number_show` en `start`; persoon wordt via `name` gezocht, een plek via `eventplace`, een gebeurtenis via `name` met expliciete lage zoekzekerheid en een periode via de ondersteunde jaarzoeksyntaxis.
- Open Archieven gebruikt maximaal 100 resultaten per verzoek en maximaal vier requests per seconde, met een beschrijvende User-Agent.
- Elk getoond resultaat bevat, wanneer door de bron geleverd, bronhouder, titel of beschrijving, datering, stabiele bronidentifier, ophaaldatum en een externe recordlink.
- Elke getoonde externe link verwijst naar de door de bron geleverde record-URL en is tekstueel herkenbaar als externe link.
- Elk resultaat toont afzonderlijke tekstuele statussen voor technische beschikbaarheid, metadatarechten, object- of mediarechten en privacy.
- Ontbrekende, ongeldige of tegenstrijdige waarden worden fail-closed weergegeven als ‘Onbekend’ of ‘Niet vastgesteld’; er wordt geen licentie, recht of privacystatus afgeleid zonder expliciete broninformatie.
- De route toont afzonderlijke laad-, succes-, lege-, fout- en retry-statussen. Deze statussen worden semantisch aangekondigd en de retryactie is volledig toetsenbordbedienbaar.
- Paginering werkt zonder lokale opslag of cache van bronpayloads, media, zoektermen of persoonsgegevens.
- Contracttests controleren de mapping, paginering, validatie, bronisolatie, fail-closed statussen, URL-herkomst, User-Agent en rate limiting.
- UI- en toegankelijkheidstests controleren de zelfstandige route, alle statusovergangen, toetsenbordbediening en externe-linklabels.
- Tests bevestigen dat nieuwsresultaten, record-intakegegevens en lokale privacyclassificaties niet via deze zoekroute worden ontsloten.

## Aannames

- De publieke backendroute krijgt één eigen zoekendpoint met genormaliseerde queryparameters en een zero-based startpositie.
- Vrije tekst is de primaire zoekterm; wanneer een provider een verplichte zoekparameter heeft, wordt die gevuld met de best passende ingevulde zoekterm. Een niet-ondersteunde combinatie levert een duidelijke fout- of lege status op en geen brede, ongerichte zoekopdracht.
- De bronwaarden Europeana en Open Archieven zijn stabiele, versieerbare bronkeuzes.
- De Europeana-wskey staat uitsluitend server-side in configuratie en verschijnt nooit in frontendcode, responses of logs.
- `retrievedAt` wordt server-side in UTC vastgesteld.
- Ontbrekende bronrechten of privacyinformatie blokkeren het tonen van onbetrouwbare inhoudelijke metadata, maar geven wel de afzonderlijke onbekende status weer.

## Eindsamenvatting

De zelfstandige route “Historisch zoeken” is gebouwd met Europeana en Open Archieven als bronnen. De route ondersteunt tekst-, plaats-, persoons-, gebeurtenis- en jaarfilters, bronkeuze, paginering, bronstatussen en retrybare foutmeldingen. Resultaten tonen alleen door de bron geleverde links; ontbrekende of onveilige metadata wordt afgeschermd. Nieuws-, record-intake-, admin- en lokale privacygegevens blijven buiten scope en worden niet opgeslagen.

De gerichte tests zijn groen: 16 backendtests en 8 frontendtests. Getest zijn onder meer validatie, bronmapping, URL-herkomst, privacy- en rechtenafscherming, paginering, rate limiting, foutstatussen, toetsenbordbediening en de homepage-ingang. De previewomgeving was niet beschikbaar. Een formeel revisiongebonden volledig factory-vangnetbewijs ontbreekt nog in deze checkout, ondanks eerder beschreven volledige runs.

<!-- deploy-summary:start -->
Je kunt nu zelfstandig zoeken in openbare historische bronnen via “Historisch zoeken”. Je kunt zoeken op onder meer naam, plaats, gebeurtenis en periode, en resultaten bevatten duidelijke bron- en foutinformatie.
<!-- deploy-summary:end -->
