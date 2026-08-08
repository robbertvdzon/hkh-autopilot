import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_admin/externalverification/license_status_view.dart';

/// Relatieve luminantie volgens WCAG 2.1 (https://www.w3.org/TR/WCAG21/#dfn-relative-luminance).
double _relativeLuminance(Color color) {
  double channel(double value) =>
      value <= 0.03928 ? value / 12.92 : math.pow((value + 0.055) / 1.055, 2.4).toDouble();

  final r = channel(color.r);
  final g = channel(color.g);
  final b = channel(color.b);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/// Contrastratio volgens WCAG 2.1 (https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio).
double _contrastRatio(Color a, Color b) {
  final lighter = math.max(_relativeLuminance(a), _relativeLuminance(b)) + 0.05;
  final darker = math.min(_relativeLuminance(a), _relativeLuminance(b)) + 0.05;
  return lighter / darker;
}

void main() {
  group('semantics tree', () {
    testWidgets(
      'shows a text label and an icon plus the license value for a known license',
      (tester) async {
        await tester.pumpWidget(
          const MaterialApp(
            home: Scaffold(
              body: LicenseStatusView(status: LicenseStatus.known, licenseValue: 'CC0'),
            ),
          ),
        );

        expect(find.bySemanticsLabel('Licentie bekend'), findsOneWidget);
        expect(find.bySemanticsLabel('Icoon Licentie bekend'), findsOneWidget);
        expect(find.byIcon(Icons.verified), findsOneWidget);
        expect(find.text('CC0'), findsOneWidget);
      },
    );

    testWidgets('shows the literal "License unknown" label and an icon when no license is found', (
      tester,
    ) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(body: LicenseStatusView(status: LicenseStatus.unknown)),
        ),
      );

      expect(find.bySemanticsLabel('License unknown'), findsOneWidget);
      expect(find.bySemanticsLabel('Icoon License unknown'), findsOneWidget);
      expect(find.byIcon(Icons.help_outline), findsOneWidget);
      expect(
        find.text('Geen hergebruikslicentie gevonden voor dit record.'),
        findsOneWidget,
      );
    });
  });

  group('color contrast', () {
    test('known-license foreground meets the 4.5:1 AA contrast minimum', () {
      final ratio = _contrastRatio(
        LicenseStatusColors.knownForeground,
        LicenseStatusColors.background,
      );

      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('unknown-license foreground meets the 4.5:1 AA contrast minimum', () {
      final ratio = _contrastRatio(
        LicenseStatusColors.unknownForeground,
        LicenseStatusColors.background,
      );

      expect(ratio, greaterThanOrEqualTo(4.5));
    });
  });
}
