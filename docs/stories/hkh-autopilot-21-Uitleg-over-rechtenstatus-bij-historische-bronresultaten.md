# hkh-autopilot-21 - Uitleg over rechtenstatus bij historische bronresultaten

## Story

Uitleg over rechtenstatus bij historische bronresultaten

<!-- refined-by-factory -->

## Scope

Breid de bestaande publieke historische zoekroute uit met een duidelijke uitleg van de rechtenstatus per historisch zoekresultaat.

- Toon afzonderlijk:
  - metadatarechten;
  - rechten op gekoppelde digitale objecten of media.
- Gebruik uitsluitend expliciete, per resultaat aangeleverde rechtenmetadata van de externe bron.
- Gebruik de bestaande statussen:
  - `ALLOWED` als `Toegestaan`;
  - `RESTRICTED` als `Beperkt`;
  - `UNKNOWN` als `Onbekend`.
- Behandel ontbrekende, lege, niet-herkende of tegenstrijdige rechtenmetadata als `Onbekend`.
- Een eventueel vrij tekstveld met bronrechten blijft broninformatie en mag niet zelfstandig naar een rechtenstatus worden vertaald.
- Toon bij de rechtenstatus een toetsenbordbedienbare uitleg die duidelijk maakt dat metadatarechten niet automatisch gelden voor gekoppelde digitale objecten of media. `Onbekend` betekent dat de bron geen expliciete, verifieerbare status levert; het betekent niet dat rechten zijn toegestaan of geweigerd.
- Pas dit consistent toe op de resultaatkaart en de bestaande detailweergave.
- Wijzig geen bronkeuze, zoekfilters, paginering, privacyregels, bronlinks, opslag of medierechten.

## Acceptance criteria

- Elk historisch zoekresultaat toont tekstueel zowel de metadatarechten als de rechten op gekoppelde digitale objecten/media.
- De weergegeven status komt uitsluitend uit de expliciete rechtenmetadata van het betreffende bronresultaat.
- Ontbrekende, lege, niet-herkende of tegenstrijdige rechteninformatie wordt als `Onbekend` getoond.
- Een bronlink, technische beschikbaarheidsstatus, algemene API-status, bronnaam, bronhouder, herkomstlabel, zoekterm of andere niet-rechtenmetadata kan nooit zelfstandig een rechtenstatus opleveren.
- De interface legt tekstueel uit dat metadatarechten en rechten op gekoppelde digitale objecten/media afzonderlijk worden beoordeeld en dat de ene status de andere niet impliceert.
- De uitleg is bereikbaar via Tab, herkenbaar als semantisch interactief element en te activeren met Enter en spatie.
- De uitleg en rechtenstatus zijn niet uitsluitend via kleur, een icoon of kleurverschil overgebracht.
- Backendcontract- en adaptertests dekken minimaal expliciete toegestane rechten, beperkte rechten, ontbrekende rechten, niet-herkende rechten en tegenstrijdige rechten.
- Frontendwidget- en semantiektests controleren de tekstuele statussen, de uitleg, de toetsenbordbediening en het ontbreken van afleiding vanuit bronlink, technische status of herkomstlabel.
- Bestaande fail-closed privacy- en metadataregels, externe bronlinks, contextweergave en paginering blijven werken.

## Aannames

- De bestaande `metadataRights`- en `objectMediaRights`-statusvelden blijven het leidende rechtencontract.
- `RESTRICTED` wordt voor gebruikers weergegeven als `Beperkt`; alle overige ontbrekende of onzekere gevallen als `Onbekend`.
- Een vrij tekstuele bronwaarde zoals `rights` blijft alleen aanvullende broninformatie en bepaalt niet zelfstandig een gecontroleerde status.
- Er worden geen nieuwe bronnen, routes, opslagmodellen, media-acties of rechtenclaims toegevoegd.

## Eindsamenvatting

PO-samenvatting:

- Alleen expliciete `ALLOWED`/`RESTRICTED`-rechten bepalen de status; ontbrekende, onbekende of tegenstrijdige waarden worden `UNKNOWN`.
- Metadatarechten en object-/mediarechten worden afzonderlijk getoond op kaart en detailpagina.
- De interactieve uitleg is toetsenbordbedienbaar en legt beide richtingen van de onafhankelijkheid uit.
- Backend- en frontendtests zijn groen. De volledige factory-verificatie is volgens het worklog succesvol afgerond.
- Bronkeuze, filters, paginering, privacy, links, opslag en media-acties zijn bewust niet gewijzigd.

<!-- deploy-summary:start -->
Historische zoekresultaten tonen nu apart welke rechten gelden voor de broninformatie en voor gekoppelde foto’s of bestanden. Ontbrekende of onduidelijke informatie wordt als “Onbekend” weergegeven.
<!-- deploy-summary:end -->
