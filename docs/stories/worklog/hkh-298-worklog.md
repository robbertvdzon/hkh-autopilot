# hkh-298 - Donkerdere achtergrond kleur

## hkh-299 - Development

Story-context bij pickup: de achtergrond van de publieke Flutter-applicatie moet twee stappen
donkerder worden volgens een vastgelegde rekenregel (8 procentpunt minder HSL-lichtheid), de kleur
moet op precies één centrale plek in de themadefinitie staan, en de ontwikkelaar levert de
geautomatiseerde tests voor AC1 t/m AC5 mee.

### Stap 0 - geverifieerde uitkomsten

De refinement had geen checkout; dit is wat de werkelijke code laat zien.

- **Waar stond de achtergrondkleur?** Nergens expliciet. `frontend/lib/main.dart` had alleen
  `ThemeData(colorScheme: ColorScheme.fromSeed(seedColor: Color(0xFF315B52), brightness: light),
  useMaterial3: true)`. Flutter leidde `scaffoldBackgroundColor` daaruit af als
  `colorScheme.surface`. De feitelijke oude achtergrondkleur was daarmee **#F5FBF7**
  (HSL 140,0° / 42,86% / 97,25%, alfa 1,0), vastgesteld met een tijdelijke probe-test.
- **Hardgecodeerde achtergrondliteralen op schermniveau?** Geen. De enige kleurliteral in
  `frontend/lib/` was de seedkleur; de overige `Theme.of(context).colorScheme.surfaceContainerHighest`
  -verwijzingen zijn kaart-/paneelvlakken (buiten scope) en blijven ongewijzigd.
- **Donker thema?** Nee. `MaterialApp` zet alleen `theme:`, geen `darkTheme`,
  `highContrastTheme` of `highContrastDarkTheme`. AC5 is dus niet van toepassing; dit is
  vastgelegd in twee passerende tests in plaats van alleen in tekst.
- **Deelt de beheerdersapplicatie het themabestand?** Nee. `frontend-admin/lib/main.dart` heeft een
  eigen, losstaande `ThemeData` en importeert niets uit `frontend/`. De beheerdersapplicatie blijft
  dus ongewijzigd.
- **Draait `flutter test`?** Ja. Flutter 3.44.6 / Dart 3.12.2 is beschikbaar; de bestaande suite
  draait en er zijn unit- en widgettests aan toegevoegd. Geen infrastructuurbevinding.

### Gekozen interpretatie van "2 tinten donkerder"

De standaardregel uit de story is toegepast: ongewijzigde tint en verzadiging, HSL-lichtheid
8 procentpunten lager (twee stappen van elk 4 procentpunt), afgekapt op minimaal 0%, alfa gelijk.
De alternatieve Material-swatchvariant is niet gebruikt, want de oude achtergrond kwam niet uit een
benoemde swatchtint maar uit een uit de seed afgeleide Material 3-`surface`.

**Oud: #F5FBF7 (L = 97,25%) -> nieuw: #D8EFE0 (L = 89,25%).**

De resterende afwijking in tint (140,00° -> 140,87°) en verzadiging (42,86% -> 41,82%) komt
uitsluitend door de afronding naar 8-bits RGB; de rekenregel laat beide ongemoeid.

### Gedaan

- `frontend/lib/theme/app_theme.dart` toegevoegd als centrale themadefinitie met het ene
  achtergrondtoken `AppTheme.backgroundColor` (`Color(0xFFD8EFE0)`), de gedocumenteerde oude waarde
  `AppTheme.previousBackgroundColor`, de rekenregel `AppTheme.twoShadesDarker` met
  `AppTheme.shadeStep = 0.04`, en `AppTheme.light` (de `ThemeData` die voorheen inline in
  `main.dart` stond, nu met expliciete `scaffoldBackgroundColor: backgroundColor`).
- `frontend/lib/main.dart` gebruikt `theme: AppTheme.light`; het inline `ThemeData`-blok is weg.
  Dit is de enige productiewijziging buiten het nieuwe themabestand.
- Het `ColorScheme` is bewust letterlijk ongewijzigd gelaten (zelfde seed, zelfde brightness, geen
  overrides), zodat AC3 hard aantoonbaar is. De achtergrond wordt uitsluitend via
  `scaffoldBackgroundColor` gezet.
- Tests toegevoegd:
  - `frontend/test/theme/wcag_contrast.dart` - herbruikbare, zuiver rekenkundige WCAG 2.x
    luminantie-/contrastberekening (geen netwerk, geen fixtures).
  - `frontend/test/theme/app_theme_test.dart` - 24 tests voor AC1 (oude waarde #F5FBF7, nieuwe
    waarde #D8EFE0, rekenregel, referentievoorbeeld #FFFFFF -> #EBEBEB, afkapping op 0%, behoud van
    alfa), AC3 (alle 34 `ColorScheme`-kleuren tegen expliciet opgeschreven verwachte waarden plus
    gelijkheid met het schema van vóór hkh-298), AC4 (contrastverhoudingen per voorgrondkleur, met
    de verhouding in de testnaam én in de assertion) en AC5 (alleen een licht thema).
  - `frontend/test/theme/screen_background_test.dart` - 6 widgettests voor AC2: de drie app-niveau
    schermen (`HomePage`, `ProductVisionPage`, `PersonQueryPage`) worden opgebouwd en de
    *daadwerkelijk toegepaste* achtergrondkleur (de kleur van het `Material` dat `Scaffold` zelf om
    zijn inhoud zet) wordt vergeleken met het token; plus een test dat geen enkel `Scaffold` een
    eigen `backgroundColor` zet, een navigatietest naar een vervolgscherm, en de AC5-controle dat
    `MaterialApp` geen `darkTheme`/`highContrastTheme` heeft. `PersonQueryPage` krijgt een stille
    in-repo fake voor `PersonSearchSource` (de sessie-indicator in de AppBar bevraagt zijn bron al
    in `initState`); er is geen nieuwe externe mockinfrastructuur gebouwd.
- Tegencontrole uitgevoerd (tijdelijk, weer verwijderd): dezelfde widget onder het oude thema levert
  via dezelfde hulpfunctie #F5FBF7 op. De AC2-tests meten dus echt het resultaat van de widgetboom
  en zijn niet tautologisch.

### Gemeten contrastverhoudingen op de nieuwe achtergrond #D8EFE0

| Voorgrondkleur | Was (op #F5FBF7) | Nu (op #D8EFE0) | Norm | Oordeel |
| --- | --- | --- | --- | --- |
| `onSurface` #171D1B | 16,31:1 | **14,12:1** | 4.5:1 | voldoet |
| `onSurfaceVariant` #3F4946 | 8,89:1 | **7,70:1** | 4.5:1 | voldoet |
| `onPrimaryContainer` #005045 | 8,97:1 | **7,77:1** | 4.5:1 | voldoet |
| `onSecondaryContainer` #334B46 | 8,96:1 | **7,76:1** | 4.5:1 | voldoet |
| `onErrorContainer` #93000A | 8,92:1 | **7,73:1** | 4.5:1 | voldoet |
| `inverseSurface` #2B3230 | 12,50:1 | **10,83:1** | 4.5:1 | voldoet |
| `secondary` #4A635D | 6,19:1 | **5,36:1** | 4.5:1 | voldoet |
| `error` #BA1A1A | 6,16:1 | **5,34:1** | 4.5:1 | voldoet |
| `primary` #046B5C | 6,14:1 | **5,32:1** | 4.5:1 | voldoet |
| `tertiary` #436278 | 6,15:1 | **5,32:1** | 4.5:1 | voldoet |
| `outline` #6F7976 (randen/iconen) | 4,29:1 | **3,71:1** | 3:1 | voldoet |
| `outlineVariant` #BEC9C5 | 1,62:1 | **1,40:1** | 3:1 | **haalt de norm niet** |

### Meldplicht: `outlineVariant`

`outlineVariant` haalt de 3:1-grens niet op de nieuwe achtergrond (1,40:1) - en haalde die ook al
niet op de oude achtergrond (1,62:1). Dit is dus geen regressie van hkh-298. In Material 3 is
`outlineVariant` uitsluitend bedoeld voor decoratieve scheidingslijnen, en `frontend/lib/` gebruikt
de kleur nergens: er staat geen `Divider` en geen expliciete verwijzing in de code. Conform AC4 is
dit gemeld en **niet** stilzwijgend opgelost door de achtergrond minder donker te maken; de regel
uit AC1 blijft leidend. Een test legt beide verhoudingen expliciet vast, zodat de constatering niet
kan wegsijpelen.

### Niet gedaan / bewust zo gelaten

- Het `ColorScheme` is niet aangepast, dus `colorScheme.surface` blijft #F5FBF7. Zichtbaar gevolg:
  de `AppBar` (die in Material 3 standaard `colorScheme.surface` gebruikt) houdt de oude, lichtere
  kleur en tekent zich nu iets af tegen de donkerdere paginaachtergrond. Dat is inherent aan "alleen
  de paginaachtergrond wordt donkerder" in combinatie met AC3 ("alle overige themakleuren houden
  exact hun huidige waarde"). Punt van aandacht bij de visuele controle van AC6; als de
  productowner wil dat de AppBar meegaat, is dat een kleine vervolgaanpassing van één waarde.
- De beheerdersapplicatie (`frontend-admin/`) is ongewijzigd: eigen, niet-gedeeld thema.
- Geen golden-/screenshottests en geen previewinfrastructuur toegevoegd (expliciet buiten scope).
- AC6 blijft openstaan: visuele bevestiging op de acceptatieomgeving kan pas ná merge, omdat die
  omgeving `main` volgt. Er is geen preview-URL toegewezen.

### Reproduceerbare commando's voor de tester

```bash
cd frontend
flutter analyze
flutter test                                   # volledige suite
flutter test test/theme/app_theme_test.dart    # AC1, AC3, AC4, AC5
flutter test test/theme/screen_background_test.dart  # AC2, AC5
flutter build web
```

Geen netwerk, geen fixtures, geen login, geen externe koppeling nodig.

### Verificatie

- `(cd frontend && flutter analyze)`: geen meldingen.
- `(cd frontend && flutter test)`: volledige suite groen, inclusief de 30 nieuwe tests.
- `(cd frontend && flutter build web)`: geslaagd.
- Backend en `frontend-admin` zijn niet gewijzigd; de Runtime-worker draait het volledige
  vangnet uit `.factory/verification.yaml`.
