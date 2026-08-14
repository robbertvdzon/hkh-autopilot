# hkh-autopilot-28 - Open Archieven-resultaatkaart met volledig herleidbaar metadata-contract

## Story

Open Archieven-resultaatkaart met volledig herleidbaar metadata-contract

<!-- refined-by-factory -->

## Scope

Koppel het bestaande genormaliseerde historische zoekresultaat aan de publieke resultaatkaart voor Open Archieven.

De kaart gebruikt uitsluitend waarden uit het bestaande resultaatcontract:

- bronnaam;
- titel of, als die ontbreekt, primaire beschrijving;
- beschikbare bronhouder-, persoons-, gebeurtenis- en dateringsmetadata;
- server-side ophaaldatum en technische bronstatus;
- stabiele identifier;
- metadatarechten, object-/mediarechten en privacystatus;
- de door de bron aangeleverde permanente bron-URI.

Inhoudelijke metadata wordt alleen getoond wanneer de bestaande contractregels dat toestaan: metadatarechten zijn `ALLOWED` en privacystatus is `CLEAR`. Er worden geen waarden afgeleid uit zoektermen, titels, identifiers of URL’s.

Adapterlogica, querymapping, bronkeuze, cache, foutclassificatie, retrygedrag, statusaggregatie, opslag en database blijven buiten scope.

## Acceptance criteria

- Een volledige Open Archieven-fixture wordt via het bestaande publieke zoekresultaatcontract als resultaatkaart weergegeven.
- De kaart toont de door de bron aangeleverde bronnaam, een aanwezige titel of primaire beschrijving, beschikbare relevante metadata, de stabiele identifier en de ophaaldatum.
- Ontbrekende inhoudelijke optionele velden worden niet aangevuld met afleidingen of aannames. Als titel en beschrijving beide ontbreken, toont de kaart geen verzonnen inhoudelijke tekst.
- De kaart toont metadatarechten, object-/mediarechten en privacystatus afzonderlijk en uitsluitend op basis van expliciete contractwaarden.
- Ontbrekende, lege, niet-herkende of tegenstrijdige rechten- en privacystatussen worden als `Onbekend` weergegeven en geven geen toestemming of weigering af.
- Een resultaat zonder geldige stabiele identifier of zonder geldige absolute HTTP(S)-bron-URI wordt niet als publieke kaart weergegeven en biedt geen bronlink.
- Voor Open Archieven worden de verplichte contractvelden `source_name`, `uuid` en `original_source_url` correct weergegeven als respectievelijk bronnaam, `stable_identifier` en permanente bronlink. De bronlink wordt niet lokaal samengesteld.
- De permanente bronlink heeft een expliciet zichtbaar label dat aankondigt dat de externe bron in een nieuw tabblad opent, is met het toetsenbord bedienbaar en opent veilig zonder `window.opener` bloot te stellen.
- De link gebruikt uitsluitend de bron-URI uit het resultaatcontract en kopieert of bewaart geen lokale broninhoud, zoekpayload of ruwe providerrespons.
- Bestaande kaarten, bronstatussen, foutcategorieën, gedeeltelijke resultaten, nulresultaten en retrycontext blijven inhoudelijk ongewijzigd. Bronuitval wordt niet als inhoudelijk nulresultaat gepresenteerd.
- Geautomatiseerde tests dekken minimaal:
  - één volledige geldige Open Archieven-fixture;
  - een fixture met ontbrekende optionele rechten- of privacystatussen;
  - fixtures zonder verplichte identifier of permanente bron-URI;
  - het zichtbare linklabel, de veilige externe-linkactie en toetsenbordbediening;
  - behoud van bestaande gedeeltelijke-resultaat-, fout- en retrysemantiek.

## Aannames

- Het bestaande `GET /api/historical-search`-contract en de bestaande genormaliseerde resultaatmodellen zijn de bron van waarheid; er komt geen nieuwe route of contractvariant.
- Een geldig Open Archieven-resultaat vereist een niet-lege `source_name`, een geldige `uuid` en een absolute HTTP(S)-`original_source_url`.
- De bestaande fail-closed-regels voor metadatarechten en privacy blijven leidend; onbekende waarden worden nooit als toestemming geïnterpreteerd.
- Relevante metadata betekent uitsluitend metadata die al door het resultaatcontract wordt geleverd, waaronder bronhouder, persoon, gebeurtenis en datering.
- De kaart toont geen ruwe providerpayload en maakt geen nieuwe externe bronaanvraag.

## Eindsamenvatting

Samenvatting voor de PO:

- De publieke Open Archieven-resultaatkaart toont nu alleen geldige contractresultaten met bronnaam, identifier en absolute bron-URI.
- Titel, beschrijving en overige metadata worden uitsluitend expliciet en fail-closed getoond.
- Rechten en privacy worden afzonderlijk weergegeven; onbekende waarden worden als “Onbekend” getoond.
- Ongeldige resultaten krijgen geen kaart of bronlink. De externe link is zichtbaar gelabeld, toetsenbordbedienbaar en veilig geopend.
- Bestaande nulresultaat-, deelresultaat-, fout- en retrysemantiek bleef behouden.
- Gerichte verificatie: 30 tests groen. Volgens het worklog is ook het volledige factory-vangnet groen: backend 340 tests, frontend 79 tests plus webbuild en admin 35 tests.
- Preview/E2E is niet uitgevoerd omdat geen preview-URL beschikbaar is.
- Adapterlogica, bronkeuze, cache, opslag en database zijn bewust niet gewijzigd.
- De rol-instructie noemt `summary-finished`, maar het opdrachtcontract vereist `summarized`; dit besluit volgt het opdrachtcontract.

<!-- deploy-summary:start -->
Historische zoekresultaten tonen nu duidelijker waar ze vandaan komen en welke informatie betrouwbaar beschikbaar is. Ongeldige resultaten worden niet als openbare kaart of link getoond.
<!-- deploy-summary:end -->
