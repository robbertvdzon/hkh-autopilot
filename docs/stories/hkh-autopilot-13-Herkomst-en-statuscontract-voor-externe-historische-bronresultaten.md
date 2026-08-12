# hkh-autopilot-13 - Herkomst- en statuscontract voor externe historische bronresultaten

## Story

Herkomst- en statuscontract voor externe historische bronresultaten

<!-- refined-by-factory -->

## Scope

Bouw een brononafhankelijk backend-contract voor één extern historisch zoekresultaat en een adapter voor Open Archieven/Noord-Hollands Archief.

Het contract bevat minimaal:

- een stabiele bronidentifier en resolvebare bronlink;
- bronhouder;
- titel of beschrijving;
- datering;
- bronversie óf momentopname-identificatie;
- server-side opgehaaldatum in UTC;
- afzonderlijke status voor rechten op metadata;
- afzonderlijke status voor rechten op objecten/media;
- privacystatus;
- technische beschikbaarheidsstatus;
- een afgeleide verificatiestatus en machineleesbare reden.

De adapter leest uitsluitend noodzakelijke metadata uit de bronrespons. Lokale media, volledige bronpayloads, volledige objectinhoud en gevoelige persoonsgegevens worden niet opgeslagen, geretourneerd of gelogd.

Een volledig geverifieerd resultaat is alleen toegestaan wanneer alle verplichte velden aanwezig, syntactisch geldig en onderling niet tegenstrijdig zijn, de bron beschikbaar is en de privacycontrole geen blokkade oplevert. Bij ontbrekende, onbekende of tegenstrijdige informatie geeft het contract alleen een veilige minimale uitkomst met de bekende bronlink en technische status, zonder niet-geverifieerde inhoudelijke metadata.

De adapter gebruikt een beschrijvende user-agent en begrenst uitgaande bronverzoeken tot maximaal vier verzoeken per seconde per server-uitgaand IP-adres. De bestaande individuele verificatieroute blijft functioneel ongewijzigd. Een concrete publieke zoekroute, opslagmodel en frontendweergave vallen buiten deze story; toekomstige zoekfunctionaliteit consumeert dit contract.

## Acceptance criteria

- Een geldig fixture-resultaat levert alle verplichte contractvelden, een volledige verificatiestatus en uitsluitend metadata op.
- Een ontbrekende, lege, ongeldig geformatteerde of tegenstrijdige identifier, bronhouder, titel/beschrijving, datering, bronversie/momentopname, opgehaaldatum, rechten-, privacy- of beschikbaarheidsstatus verhindert de volledige verificatiestatus.
- De minimale fail-closed-uitkomst bevat geen niet-geverifieerde titel, beschrijving, datering of persoonsinformatie; een bekende stabiele bronidentifier en bronlink mogen behouden blijven.
- Metadatarechten en object-/mediarechten zijn afzonderlijke statussen. Onbekende objectrechten geven nooit toestemming voor media en maken een metadataresultaat niet automatisch ongeldig; metadatarechten worden wel zelfstandig beoordeeld.
- Een mogelijk levende persoon of persoonsgegevens zonder aantoonbaar passende grondslag worden uit de publieke metadata-uitkomst verwijderd. Dezelfde gegevens mogen ook niet in applicatielogs, foutmeldingen of testfixtures voor publieke responses voorkomen.
- De adapter stuurt een niet-standaard, beschrijvende user-agent en overschrijdt in een geautomatiseerde timingtest nooit vier uitgaande verzoeken per seconde per server-uitgaand IP-adres.
- Geautomatiseerde tests dekken minimaal: geldige metadata, onbekende rechten, privacybeperking, tijdelijke bronuitval, lege bronrespons en een gewijzigde bronversie/momentopname.
- Bij tijdelijke bronuitval of een lege respons ontstaat geen volledig geverifieerd resultaat en wordt geen oude of gedeeltelijke inhoud als actueel gepresenteerd.
- Bij een gewijzigde bronversie/momentopname wordt de actuele versie zichtbaar vastgelegd; een eerder resultaat wordt niet stilzwijgend als actuele broninformatie hergebruikt.
- De contractdocumentatie beschrijft de statussen, fail-closed-regels en het expliciete onderscheid tussen rechten op datasetmetadata en rechten op afzonderlijke objecten of media.

## Aannames

- De story levert een herbruikbaar backend-contract en een bronadapter; een nieuwe zoek-API, database-opslag en frontend zijn geen onderdeel van deze story.
- De bestaande Open Archieven/Noord-Hollands Archief-client wordt hergebruikt of uitgebreid voor JSON-LD-metadata; ontbrekende bronvelden worden niet door aannames aangevuld.
- De bronhouder wordt door de adapter betrouwbaar vastgesteld uit bronconfiguratie of bronmetadata. Bij conflicterende waarden volgt fail-closed gedrag.
- Een bronversie wordt gelezen uit expliciete bronmetadata of een betrouwbare HTTP-momentopname-indicatie zoals ETag of Last-Modified. Ontbreekt die informatie, dan is het resultaat niet volledig geverifieerd.
- De relevante IP-beperking is het server-uitgaande IP-adres. Beperking per publieke eindgebruikers-IP valt buiten deze story.
- Zonder expliciete, controleerbare grondslag worden persoonsgegevens niet publiek teruggegeven. Een algemene publieke bronvermelding geldt niet vanzelf als grondslag.
- De stabiele bronlink mag in een minimale uitkomst blijven staan wanneer de identifier zelf bekend en veilig is; ontbreekt de identifier, dan blijft alleen de technische minimale status beschikbaar.

## Eindsamenvatting

Samenvatting voor de PO:

- Een brononafhankelijk contract en adapter voor Open Archieven/Noord-Hollands Archief zijn gebouwd.
- Ontbrekende, ongeldige of tegenstrijdige broninformatie leidt fail-closed tot een minimale veilige uitkomst. Metadatarechten en mediarechten zijn gescheiden; privacygegevens worden niet teruggegeven, opgeslagen of gelogd.
- De adapter gebruikt bronversies, UTC-ophaaldatum, beschrijvende user-agent en gedeelde rate limiting. Documentatie en tests zijn bijgewerkt.
- Getest: 22 gerichte backendtests; volledig vangnet groen met 278 backendtests, 35 frontendtests en 36 adminfrontendtests. Er was geen previewomgeving beschikbaar.
- Bewust niet gedaan: publieke zoekroute, opslagmodel, frontendweergave en wijzigingen aan de bestaande individuele verificatieroute.

<!-- deploy-summary:start -->
Historische zoekresultaten worden nu gecontroleerd voordat ze worden getoond. Bij twijfel worden inhoud en persoonsgegevens weggelaten en blijft alleen een veilige bronverwijzing over. De bestaande individuele controle blijft werken.
<!-- deploy-summary:end -->
