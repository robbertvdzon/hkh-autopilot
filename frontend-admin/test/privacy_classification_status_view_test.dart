import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_admin/privacyclassification/privacy_classification_status_view.dart';

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
      'shows a text label and an icon for a processable status',
      (tester) async {
        await tester.pumpWidget(
          const MaterialApp(
            home: Scaffold(
              body: PrivacyClassificationStatusView(
                status: PrivacyClassificationStatus.processable,
                reason:
                    'Persoon is overleden en er zijn geen gegevens van een levende nabestaande gevonden.',
              ),
            ),
          ),
        );

        expect(find.bySemanticsLabel('Verwerkbaar'), findsOneWidget);
        expect(find.bySemanticsLabel('Icoon Verwerkbaar'), findsOneWidget);
        expect(find.byIcon(Icons.check_circle), findsOneWidget);
      },
    );

    testWidgets('shows a text label and an icon for a blocked status', (
      tester,
    ) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: PrivacyClassificationStatusView(
              status: PrivacyClassificationStatus.blocked,
              reason: 'Bevat gegevens van levende nabestaande',
            ),
          ),
        ),
      );

      expect(find.bySemanticsLabel('Geblokkeerd'), findsOneWidget);
      expect(find.bySemanticsLabel('Icoon Geblokkeerd'), findsOneWidget);
      expect(find.byIcon(Icons.block), findsOneWidget);
      expect(
        find.text('Bevat gegevens van levende nabestaande'),
        findsOneWidget,
      );
    });
  });

  group('color contrast', () {
    test('processable foreground meets the 4.5:1 AA contrast minimum', () {
      final ratio = _contrastRatio(
        PrivacyClassificationStatusColors.processableForeground,
        PrivacyClassificationStatusColors.background,
      );

      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('blocked foreground meets the 4.5:1 AA contrast minimum', () {
      final ratio = _contrastRatio(
        PrivacyClassificationStatusColors.blockedForeground,
        PrivacyClassificationStatusColors.background,
      );

      expect(ratio, greaterThanOrEqualTo(4.5));
    });
  });
}
