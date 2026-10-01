import 'package:flutter/material.dart';

/// Centrale themadefinitie van de publieke (bezoekers)applicatie.
///
/// De achtergrondkleur van elk app-niveau scherm staat hier op precies één
/// plek: [AppTheme.backgroundColor]. Schermen zetten zelf geen
/// achtergrondkleurliteraal; zij erven de kleur via
/// [ThemeData.scaffoldBackgroundColor].
abstract final class AppTheme {
  /// Basiskleur waaruit het Material 3-kleurenschema wordt afgeleid.
  /// Ongewijzigd ten opzichte van de situatie vóór hkh-298.
  static const Color seedColor = Color(0xFF315B52);

  /// De achtergrondkleur zoals die vóór hkh-298 gold: de impliciete
  /// `ColorScheme.fromSeed(seedColor: seedColor, brightness: light).surface`
  /// die Flutter als `scaffoldBackgroundColor` gebruikte.
  ///
  /// HSL: tint 140,0°, verzadiging 42,86%, lichtheid 97,25%.
  static const Color previousBackgroundColor = Color(0xFFF5FBF7);

  /// Het centrale achtergrondtoken van de publieke applicatie (hkh-298 AC1).
  ///
  /// Dit is [previousBackgroundColor] met ongewijzigde tint en verzadiging en
  /// een HSL-lichtheid die 8 procentpunten lager ligt (twee stappen van elk
  /// 4 procentpunten), afgekapt op minimaal 0%; de alfawaarde blijft gelijk.
  /// Oftewel: #F5FBF7 (L = 97,25%) wordt #D8EFE0 (L = 89,25%).
  ///
  /// De literal hieronder is exact het resultaat van
  /// `twoShadesDarker(previousBackgroundColor)`; `AppThemeTest` bewaakt dat.
  static const Color backgroundColor = Color(0xFFD8EFE0);

  /// De vastgelegde rekenregel voor "twee tinten donkerder": verlaag de
  /// HSL-lichtheid met [shadeStep] × 2 procentpunt, met ongewijzigde tint,
  /// verzadiging en alfa, afgekapt op minimaal 0%.
  static Color twoShadesDarker(Color source) {
    final hsl = HSLColor.fromColor(source);
    return hsl
        .withLightness((hsl.lightness - 2 * shadeStep).clamp(0.0, 1.0))
        .toColor();
  }

  /// Eén "tint" in de rekenregel van hkh-298: 4 procentpunten HSL-lichtheid.
  static const double shadeStep = 0.04;

  /// Het lichte (en enige) thema van de publieke applicatie.
  ///
  /// Er is bewust geen donker thema: `MaterialApp` krijgt geen `darkTheme`,
  /// zodat dit thema in elke platformhelderheid geldt.
  static final ThemeData light = ThemeData(
    colorScheme: ColorScheme.fromSeed(
      seedColor: seedColor,
      brightness: Brightness.light,
    ),
    scaffoldBackgroundColor: backgroundColor,
    useMaterial3: true,
  );
}
