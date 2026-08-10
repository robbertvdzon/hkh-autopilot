import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_admin/recordintake/admin_record_intake.dart';
import 'package:hkh_admin/recordintake/external_archive_preview_panel.dart';

/// Relatieve luminantie volgens WCAG 2.1 (https://www.w3.org/TR/WCAG21/#dfn-relative-luminance).
double _relativeLuminance(Color color) {
  double channel(double value) => value <= 0.03928
      ? value / 12.92
      : math.pow((value + 0.055) / 1.055, 2.4).toDouble();

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
      'shows a live region with the verified status and core fields',
      (tester) async {
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: ExternalArchivePreviewPanel(
                loading: false,
                result: const RecordIntakeExternalArchivePreviewResult(
                  status: RecordIntakeExternalArchivePreviewStatus.verified,
                  name: 'Jan Jansen',
                  birthDate: '1900-01-01',
                  deathDate: '1980-05-05',
                  license: 'CC0',
                  sourceUri:
                      'http://opendata.archieven.nl/id/1000/verified-jan',
                ),
                confirmed: null,
                onConfirm: () {},
                onDecline: () {},
              ),
            ),
          ),
        );

        final panelSemantics = tester.widget<Semantics>(
          find
              .ancestor(
                of: find.text('Brongegevens (extern, ter controle)'),
                matching: find.byType(Semantics),
              )
              .first,
        );
        expect(panelSemantics.properties.liveRegion, isTrue);
        expect(
          panelSemantics.properties.label,
          'Brongegevens (extern, ter controle)',
        );

        expect(find.text('Geverifieerd'), findsOneWidget);
        expect(find.bySemanticsLabel('Icoon Geverifieerd'), findsOneWidget);
        expect(find.text('Naam: Jan Jansen'), findsOneWidget);
        expect(find.text('Geboortedatum: 1900-01-01'), findsOneWidget);
        expect(find.text('Sterftedatum: 1980-05-05'), findsOneWidget);
        expect(find.text('Licentie: CC0'), findsOneWidget);
        expect(
          find.text(
            'Bron-URI: http://opendata.archieven.nl/id/1000/verified-jan',
          ),
          findsOneWidget,
        );
      },
    );

    testWidgets('shows the no match status without core fields', (
      tester,
    ) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: ExternalArchivePreviewPanel(
              loading: false,
              result: RecordIntakeExternalArchivePreviewResult(
                status: RecordIntakeExternalArchivePreviewStatus.noMatch,
              ),
              confirmed: null,
              onConfirm: _noop,
              onDecline: _noop,
            ),
          ),
        ),
      );

      expect(find.text('Geen match'), findsOneWidget);
      expect(find.bySemanticsLabel('Icoon Geen match'), findsOneWidget);
      expect(find.textContaining('Naam:'), findsNothing);
    });

    testWidgets('shows the unreachable status without core fields', (
      tester,
    ) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: ExternalArchivePreviewPanel(
              loading: false,
              result: RecordIntakeExternalArchivePreviewResult(
                status: RecordIntakeExternalArchivePreviewStatus.unreachable,
              ),
              confirmed: null,
              onConfirm: _noop,
              onDecline: _noop,
            ),
          ),
        ),
      );

      expect(find.text('Niet bereikbaar'), findsOneWidget);
      expect(find.bySemanticsLabel('Icoon Niet bereikbaar'), findsOneWidget);
    });

    testWidgets('both actions call their callback', (tester) async {
      var confirmed = false;
      var declined = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ExternalArchivePreviewPanel(
              loading: false,
              result: const RecordIntakeExternalArchivePreviewResult(
                status: RecordIntakeExternalArchivePreviewStatus.verified,
              ),
              confirmed: null,
              onConfirm: () => confirmed = true,
              onDecline: () => declined = true,
            ),
          ),
        ),
      );

      await tester.tap(
        find.text('Bevestig brongegevens en gebruik bij record'),
      );
      await tester.tap(find.text('Sla op zonder externe brongegevens'));
      await tester.pumpAndSettle();

      expect(confirmed, isTrue);
      expect(declined, isTrue);
    });
  });

  group('color contrast', () {
    test('verified foreground meets the 4.5:1 AA contrast minimum', () {
      final ratio = _contrastRatio(
        ExternalArchivePreviewStatusColors.verifiedForeground,
        ExternalArchivePreviewStatusColors.background,
      );

      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('no match foreground meets the 4.5:1 AA contrast minimum', () {
      final ratio = _contrastRatio(
        ExternalArchivePreviewStatusColors.noMatchForeground,
        ExternalArchivePreviewStatusColors.background,
      );

      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('unreachable foreground meets the 4.5:1 AA contrast minimum', () {
      final ratio = _contrastRatio(
        ExternalArchivePreviewStatusColors.unreachableForeground,
        ExternalArchivePreviewStatusColors.background,
      );

      expect(ratio, greaterThanOrEqualTo(4.5));
    });
  });
}

void _noop() {}
