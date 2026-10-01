import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/theme/app_theme.dart';

import 'wcag_contrast.dart';

/// De achtergrondkleur zoals die vóór hkh-298 gold. Deze waarde was niet
/// expliciet opgeschreven: `ThemeData` leidde hem af uit
/// `ColorScheme.fromSeed(seedColor: #315B52, brightness: light).surface`.
/// Deze test legt hem vast als verwachtingswaarde, zodat de rekenregel van
/// AC1 aantoonbaar op de werkelijke oude kleur is toegepast.
const _oldBackgroundColor = Color(0xFFF5FBF7);

/// De nieuwe, centrale achtergrondkleur na hkh-298.
const _newBackgroundColor = Color(0xFFD8EFE0);

ColorScheme get _scheme => AppTheme.light.colorScheme;

void main() {
  group('AC1 — de achtergrondkleur volgt exact de vastgelegde rekenregel', () {
    test('het oude thema leverde #F5FBF7 als schermachtergrond op', () {
      // Reconstructie van de situatie vóór hkh-298: geen expliciete
      // scaffoldBackgroundColor, dus de afgeleide surface-kleur.
      final before = ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: AppTheme.seedColor,
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      );

      expect(before.scaffoldBackgroundColor, _oldBackgroundColor);
    });

    test('het token is #D8EFE0 en is de enige achtergronddefinitie', () {
      expect(AppTheme.backgroundColor, _newBackgroundColor);
      expect(AppTheme.light.scaffoldBackgroundColor, AppTheme.backgroundColor);
    });

    test('#F5FBF7 met 8 procentpunt minder HSL-lichtheid is #D8EFE0', () {
      expect(
        AppTheme.twoShadesDarker(_oldBackgroundColor),
        _newBackgroundColor,
      );
      expect(
        AppTheme.backgroundColor,
        AppTheme.twoShadesDarker(_oldBackgroundColor),
      );
    });

    test('de lichtheid daalt met precies 8 procentpunt (97,25% -> 89,25%)', () {
      final oldHsl = HSLColor.fromColor(_oldBackgroundColor);
      final newHsl = HSLColor.fromColor(AppTheme.backgroundColor);

      expect(oldHsl.lightness, closeTo(0.9725, 0.0001));
      expect(newHsl.lightness, closeTo(0.8922, 0.0001));
      expect(oldHsl.lightness - newHsl.lightness, closeTo(0.08, 0.0005));
    });

    test('tint, verzadiging en alfa blijven ongewijzigd', () {
      final oldHsl = HSLColor.fromColor(_oldBackgroundColor);
      final newHsl = HSLColor.fromColor(AppTheme.backgroundColor);

      // Tint en verzadiging worden ongewijzigd doorgegeven; de afwijking die
      // overblijft komt uitsluitend door de afronding naar 8-bits RGB.
      expect(newHsl.hue, closeTo(oldHsl.hue, 1.0));
      expect(newHsl.saturation, closeTo(oldHsl.saturation, 0.015));
      expect(newHsl.alpha, oldHsl.alpha);
      expect(AppTheme.backgroundColor.a, _oldBackgroundColor.a);
    });

    test('de regel is twee stappen van 4 procentpunt en kapt af op 0%', () {
      expect(AppTheme.shadeStep, 0.04);
      // Referentievoorbeeld uit de story: L=100% wordt L=92%.
      expect(
        AppTheme.twoShadesDarker(const Color(0xFFFFFFFF)),
        const Color(0xFFEBEBEB),
      );
      // Afkapping: zwart blijft zwart in plaats van een negatieve lichtheid.
      expect(
        AppTheme.twoShadesDarker(const Color(0xFF000000)),
        const Color(0xFF000000),
      );
      // Alfa blijft behouden.
      expect(
        AppTheme.twoShadesDarker(const Color(0x80FFFFFF)).a,
        const Color(0x80FFFFFF).a,
      );
    });
  });

  group('AC3 — geen enkele andere themakleur verandert', () {
    test('elke ColorScheme-kleur houdt exact zijn huidige waarde', () {
      final cs = _scheme;

      expect(cs.brightness, Brightness.light);
      expect(cs.primary, const Color(0xFF046B5C));
      expect(cs.onPrimary, const Color(0xFFFFFFFF));
      expect(cs.primaryContainer, const Color(0xFFA0F2DF));
      expect(cs.onPrimaryContainer, const Color(0xFF005045));
      expect(cs.secondary, const Color(0xFF4A635D));
      expect(cs.onSecondary, const Color(0xFFFFFFFF));
      expect(cs.secondaryContainer, const Color(0xFFCDE8E0));
      expect(cs.onSecondaryContainer, const Color(0xFF334B46));
      expect(cs.tertiary, const Color(0xFF436278));
      expect(cs.onTertiary, const Color(0xFFFFFFFF));
      expect(cs.tertiaryContainer, const Color(0xFFC9E6FF));
      expect(cs.onTertiaryContainer, const Color(0xFF2B4A5F));
      expect(cs.error, const Color(0xFFBA1A1A));
      expect(cs.onError, const Color(0xFFFFFFFF));
      expect(cs.errorContainer, const Color(0xFFFFDAD6));
      expect(cs.onErrorContainer, const Color(0xFF93000A));
      expect(cs.surface, const Color(0xFFF5FBF7));
      expect(cs.onSurface, const Color(0xFF171D1B));
      expect(cs.onSurfaceVariant, const Color(0xFF3F4946));
      expect(cs.surfaceContainerLowest, const Color(0xFFFFFFFF));
      expect(cs.surfaceContainerLow, const Color(0xFFEFF5F2));
      expect(cs.surfaceContainer, const Color(0xFFE9EFEC));
      expect(cs.surfaceContainerHigh, const Color(0xFFE3EAE6));
      expect(cs.surfaceContainerHighest, const Color(0xFFDEE4E1));
      expect(cs.surfaceDim, const Color(0xFFD5DBD8));
      expect(cs.surfaceBright, const Color(0xFFF5FBF7));
      expect(cs.outline, const Color(0xFF6F7976));
      expect(cs.outlineVariant, const Color(0xFFBEC9C5));
      expect(cs.inverseSurface, const Color(0xFF2B3230));
      expect(cs.onInverseSurface, const Color(0xFFECF2EF));
      expect(cs.inversePrimary, const Color(0xFF84D6C3));
      expect(cs.shadow, const Color(0xFF000000));
      expect(cs.scrim, const Color(0xFF000000));
      expect(cs.surfaceTint, const Color(0xFF046B5C));
    });

    test('het kleurenschema is identiek aan dat van vóór hkh-298', () {
      final before = ColorScheme.fromSeed(
        seedColor: const Color(0xFF315B52),
        brightness: Brightness.light,
      );

      expect(_scheme, before);
    });

    test('de seedkleur en Material 3-instelling zijn ongewijzigd', () {
      expect(AppTheme.seedColor, const Color(0xFF315B52));
      expect(AppTheme.light.useMaterial3, isTrue);
    });
  });

  group('AC4 — leesbaarheid op de nieuwe achtergrond', () {
    // Normale tekst: minimaal 4.5:1.
    test('onSurface op de achtergrond haalt 14.12:1 (>= 4.5:1)', () {
      final ratio = contrastRatio(_scheme.onSurface, AppTheme.backgroundColor);
      expect(ratio, closeTo(14.12, 0.01));
      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('onSurfaceVariant op de achtergrond haalt 7.70:1 (>= 4.5:1)', () {
      final ratio = contrastRatio(
        _scheme.onSurfaceVariant,
        AppTheme.backgroundColor,
      );
      expect(ratio, closeTo(7.70, 0.01));
      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('primary op de achtergrond haalt 5.32:1 (>= 4.5:1)', () {
      final ratio = contrastRatio(_scheme.primary, AppTheme.backgroundColor);
      expect(ratio, closeTo(5.32, 0.01));
      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('secondary op de achtergrond haalt 5.36:1 (>= 4.5:1)', () {
      final ratio = contrastRatio(_scheme.secondary, AppTheme.backgroundColor);
      expect(ratio, closeTo(5.36, 0.01));
      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('tertiary op de achtergrond haalt 5.32:1 (>= 4.5:1)', () {
      final ratio = contrastRatio(_scheme.tertiary, AppTheme.backgroundColor);
      expect(ratio, closeTo(5.32, 0.01));
      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('error op de achtergrond haalt 5.34:1 (>= 4.5:1)', () {
      final ratio = contrastRatio(_scheme.error, AppTheme.backgroundColor);
      expect(ratio, closeTo(5.34, 0.01));
      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('onPrimaryContainer op de achtergrond haalt 7.77:1 (>= 4.5:1)', () {
      final ratio = contrastRatio(
        _scheme.onPrimaryContainer,
        AppTheme.backgroundColor,
      );
      expect(ratio, closeTo(7.77, 0.01));
      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('onSecondaryContainer op de achtergrond haalt 7.76:1 (>= 4.5:1)', () {
      final ratio = contrastRatio(
        _scheme.onSecondaryContainer,
        AppTheme.backgroundColor,
      );
      expect(ratio, closeTo(7.76, 0.01));
      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('onErrorContainer op de achtergrond haalt 7.73:1 (>= 4.5:1)', () {
      final ratio = contrastRatio(
        _scheme.onErrorContainer,
        AppTheme.backgroundColor,
      );
      expect(ratio, closeTo(7.73, 0.01));
      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('elke voorgrondkleur voor normale tekst haalt minimaal 4.5:1', () {
      final cs = _scheme;
      final foregrounds = <String, Color>{
        'onSurface': cs.onSurface,
        'onSurfaceVariant': cs.onSurfaceVariant,
        'primary': cs.primary,
        'secondary': cs.secondary,
        'tertiary': cs.tertiary,
        'error': cs.error,
        'onPrimaryContainer': cs.onPrimaryContainer,
        'onSecondaryContainer': cs.onSecondaryContainer,
        'onErrorContainer': cs.onErrorContainer,
        'inverseSurface': cs.inverseSurface,
      };

      for (final entry in foregrounds.entries) {
        expect(
          contrastRatio(entry.value, AppTheme.backgroundColor),
          greaterThanOrEqualTo(4.5),
          reason: '${entry.key} op #D8EFE0 haalt de 4.5:1-grens niet',
        );
      }
    });

    // Grote tekst en betekenisvolle UI-randen/iconen: minimaal 3:1.
    test('outline (betekenisvolle rand/icoon) haalt 3.71:1 (>= 3:1)', () {
      final ratio = contrastRatio(_scheme.outline, AppTheme.backgroundColor);
      expect(ratio, closeTo(3.71, 0.01));
      expect(ratio, greaterThanOrEqualTo(3.0));
    });

    test('elke voorgrondkleur haalt ook de 3:1-grens voor grote tekst', () {
      final cs = _scheme;
      final foregrounds = <String, Color>{
        'onSurface': cs.onSurface,
        'onSurfaceVariant': cs.onSurfaceVariant,
        'primary': cs.primary,
        'secondary': cs.secondary,
        'tertiary': cs.tertiary,
        'error': cs.error,
        'outline': cs.outline,
      };

      for (final entry in foregrounds.entries) {
        expect(
          contrastRatio(entry.value, AppTheme.backgroundColor),
          greaterThanOrEqualTo(3.0),
          reason: '${entry.key} op #D8EFE0 haalt de 3:1-grens niet',
        );
      }
    });

    test(
      'gemeld: outlineVariant haalt 3:1 niet, vóór noch ná de wijziging',
      () {
        // outlineVariant is in Material 3 uitsluitend bedoeld voor decoratieve
        // scheidingslijnen en wordt in frontend/lib nergens gebruikt (geen
        // Divider, geen expliciete verwijzing). De kleur haalde de 3:1-grens al
        // niet op de oude achtergrond; dat is dus geen gevolg van hkh-298 en
        // wordt conform AC4 gemeld in plaats van verholpen door de achtergrond
        // minder donker te maken.
        final before = contrastRatio(
          _scheme.outlineVariant,
          _oldBackgroundColor,
        );
        final after = contrastRatio(
          _scheme.outlineVariant,
          AppTheme.backgroundColor,
        );

        expect(before, closeTo(1.62, 0.01));
        expect(after, closeTo(1.40, 0.01));
        expect(before, lessThan(3.0));
        expect(after, lessThan(3.0));
      },
    );

    test('de contrastberekening klopt op bekende referentiewaarden', () {
      expect(
        contrastRatio(const Color(0xFF000000), const Color(0xFFFFFFFF)),
        closeTo(21.0, 0.001),
      );
      expect(
        contrastRatio(const Color(0xFFFFFFFF), const Color(0xFFFFFFFF)),
        closeTo(1.0, 0.001),
      );
    });
  });

  group('AC5 — donker thema', () {
    test('de publieke applicatie heeft geen donker thema', () {
      // Vastlegging van de constatering uit stap 0: er is uitsluitend een
      // licht thema. Zie ook de MaterialApp-test in screen_background_test.
      expect(AppTheme.light.brightness, Brightness.light);
      expect(_scheme.brightness, Brightness.light);
    });
  });
}
