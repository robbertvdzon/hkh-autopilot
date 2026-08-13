# hkh-autopilot-17 - Worklog

Story-context bij eerste pickup:
Contract, bron- en lokale dekkingssamenvatting

Werk backendcontract, service, controller, Flutter-weergave en tests uit voor brongebonden paginatellingen, de genormaliseerde Heemskerk-indicatie, statusmeldingen, metadata-behoud en privacygrenzen.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Issuecontext, developer-instructies, development.md, technical-spec.md en de bestaande
  historische zoekroute gelezen; er zijn geen merge-conflictmarkers aangetroffen.
- Backendcontract uitgebreid met nullable `resultCount` en `heemskerkCount` per geselecteerde bron.
  Beschikbare bronnen tellen uitsluitend veilig genormaliseerde resultaten op de huidige zichtbare
  pagina; niet-beschikbare bronnen houden beide tellingen `null`.
- Heemskerk-indicatie toegevoegd met expliciete `placeStatus=AVAILABLE` en NFKC-, whitespace- en
  hoofdletterongevoelige exacte normalisatie. Oorspronkelijke resultaatmetadata en bron-URI's zijn
  niet gewijzigd.
- Fluttercontract en historische zoekweergave tonen per bron status, paginatelling en een expliciet
  gelabelde lokale Heemskerk-indicatie als plaatsmetadata-indicatie, niet als historisch bewijs.
- De live statussemantiek bevat bij responses met tellingen ook de per-bron samenvatting, zodat de
  dekking niet alleen visueel maar ook programmatisch uitleesbaar is.
- Tests toegevoegd voor volledige/lege/gedeeltelijke/falende bronbeschikbaarheid, per-brontellingen,
  zekere normalisatie, ontbrekende/onzekere/andere plaatsen en het frontendresponscontract.
- Gerichte tests groen: backend 23 en frontend 15. Volledig vangnet groen: backend 301 tests,
  frontend 53 tests, frontend analyze/build web en frontend-admin analyze/35 tests.
