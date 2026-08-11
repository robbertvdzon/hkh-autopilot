import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/records/record_detail_page.dart';
import 'package:hkh_app/records/record_public_view.dart';

Widget _wrap(Widget child) => MaterialApp(
  home: Scaffold(body: SingleChildScrollView(child: child)),
);

const _confirmed = RecordPublicView(
  localIdentifier: 'rec-1',
  status: RecordPublicStatus.confirmed,
  name: 'Jan Klaassen',
  birthYear: '1900',
  deathYear: '1980',
  license: 'CC-BY-4.0',
  sourceUri: 'http://opendata.archieven.nl/id/1234/abcd-ef01',
);
final _confirmedWithConfirmation = RecordPublicView(
  localIdentifier: _confirmed.localIdentifier,
  status: _confirmed.status,
  name: _confirmed.name,
  birthYear: _confirmed.birthYear,
  deathYear: _confirmed.deathYear,
  license: _confirmed.license,
  sourceUri: _confirmed.sourceUri,
  confirmedAt: DateTime.utc(2026, 3, 5, 10),
);

const _savedWithoutSource = RecordPublicView(
  localIdentifier: 'rec-2',
  status: RecordPublicStatus.savedWithoutSource,
);

const _noIntake = RecordPublicView(
  localIdentifier: 'rec-3',
  status: RecordPublicStatus.noIntake,
);

bool _hasPrimaryFocusWithin(Finder finder) {
  final focusContext = FocusManager.instance.primaryFocus?.context;
  if (focusContext == null) return false;
  return find
      .descendant(of: finder, matching: find.byWidget(focusContext.widget))
      .evaluate()
      .isNotEmpty;
}

void main() {
  group('CONFIRMED', () {
    testWidgets(
      'shows the section heading, status text, name, years, license and source '
      'link in the initial semantics tree without interaction',
      (tester) async {
        await tester.pumpWidget(
          _wrap(
            ExternalSourceVerificationSection(view: _confirmedWithConfirmation),
          ),
        );

        expect(
          find.bySemanticsLabel('Externe bronverificatie'),
          findsOneWidget,
        );
        expect(
          find.bySemanticsLabel(confirmedExternalVerificationStatusLabel),
          findsOneWidget,
        );
        expect(
          find.bySemanticsLabel(
            'Icoon $confirmedExternalVerificationStatusLabel',
          ),
          findsOneWidget,
        );
        expect(find.text('Jan Klaassen'), findsOneWidget);
        expect(find.text('Geboortejaar: 1900'), findsOneWidget);
        expect(find.text('Sterftejaar: 1980'), findsOneWidget);
        expect(find.text('Licentie: CC-BY-4.0'), findsOneWidget);
        expect(
          find.bySemanticsLabel(
            'Bekijk bron (opent externe bron in nieuw tabblad)',
          ),
          findsOneWidget,
        );
        expect(
          find.textContaining('Bevestigd door beheerder op'),
          findsOneWidget,
        );
      },
    );

    testWidgets(
      'never renders a day or month number in the displayed birth/death year text',
      (tester) async {
        await tester.pumpWidget(
          _wrap(ExternalSourceVerificationSection(view: _confirmed)),
        );

        final birthText = tester
            .widget<Text>(find.text('Geboortejaar: 1900'))
            .data!;
        final deathText = tester
            .widget<Text>(find.text('Sterftejaar: 1980'))
            .data!;
        // Only a single, bare 4-digit year may appear: no day/month digits alongside it.
        expect(RegExp(r'^Geboortejaar: \d{4}$').hasMatch(birthText), isTrue);
        expect(RegExp(r'^Sterftejaar: \d{4}$').hasMatch(deathText), isTrue);
      },
    );

    testWidgets('omits the death year when absent', (tester) async {
      const noDeathYear = RecordPublicView(
        localIdentifier: 'rec-4',
        status: RecordPublicStatus.confirmed,
        name: 'Jan Klaassen',
        birthYear: '1900',
        license: 'CC-BY-4.0',
        sourceUri: 'http://opendata.archieven.nl/id/1234/abcd-ef01',
      );
      await tester.pumpWidget(
        _wrap(ExternalSourceVerificationSection(view: noDeathYear)),
      );

      expect(find.textContaining('Sterftejaar'), findsNothing);
    });

    testWidgets(
      'the source link is reachable and activatable via Tab + Enter only',
      (tester) async {
        String? openedUri;
        await tester.pumpWidget(
          _wrap(
            ExternalSourceVerificationSection(
              view: _confirmed,
              openLink: (uri) => openedUri = uri,
            ),
          ),
        );

        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        expect(
          _hasPrimaryFocusWithin(
            find.byKey(const Key('external-source-verification-toggle')),
          ),
          isTrue,
        );

        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        expect(
          _hasPrimaryFocusWithin(find.byKey(const Key('external-source-link'))),
          isTrue,
        );

        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await tester.pump();

        expect(openedUri, _confirmed.sourceUri);
      },
    );
  });

  group('neutral status', () {
    testWidgets(
      'SAVED_WITHOUT_SOURCE shows the neutral message without a link or fields',
      (tester) async {
        await tester.pumpWidget(
          _wrap(ExternalSourceVerificationSection(view: _savedWithoutSource)),
        );

        expect(
          find.bySemanticsLabel(neutralExternalVerificationMessage),
          findsOneWidget,
        );
        expect(find.byKey(const Key('external-source-link')), findsNothing);
        expect(
          find.bySemanticsLabel(confirmedExternalVerificationStatusLabel),
          findsNothing,
        );
      },
    );

    testWidgets(
      'NO_INTAKE shows the same neutral message without a link or fields',
      (tester) async {
        await tester.pumpWidget(
          _wrap(ExternalSourceVerificationSection(view: _noIntake)),
        );

        expect(
          find.bySemanticsLabel(neutralExternalVerificationMessage),
          findsOneWidget,
        );
        expect(find.byKey(const Key('external-source-link')), findsNothing);
      },
    );
  });

  group('self-healing degradation', () {
    testWidgets(
      'a page reload after a live reclassification to the neutral status shows the '
      'neutral message again, and reverting shows CONFIRMED again automatically',
      (tester) async {
        await tester.pumpWidget(
          _wrap(
            ExternalSourceVerificationSection(view: _confirmedWithConfirmation),
          ),
        );
        expect(
          find.bySemanticsLabel(confirmedExternalVerificationStatusLabel),
          findsOneWidget,
        );

        // Second page load after the backend degraded a live reclassification to
        // Blocked: same localIdentifier, now reported as the neutral status.
        await tester.pumpWidget(
          _wrap(const ExternalSourceVerificationSection(view: _noIntake)),
        );
        expect(
          find.bySemanticsLabel(neutralExternalVerificationMessage),
          findsOneWidget,
        );
        expect(
          find.bySemanticsLabel(confirmedExternalVerificationStatusLabel),
          findsNothing,
        );

        // A later reload with the record processable again shows CONFIRMED again,
        // based on the previously stored confirmation - no further action needed.
        await tester.pumpWidget(
          _wrap(
            ExternalSourceVerificationSection(view: _confirmedWithConfirmation),
          ),
        );
        expect(
          find.bySemanticsLabel(confirmedExternalVerificationStatusLabel),
          findsOneWidget,
        );
      },
    );
  });

  group('toggle', () {
    testWidgets(
      'reports aria-expanded/aria-controls equivalents that flip on toggle',
      (tester) async {
        await tester.pumpWidget(
          _wrap(
            ExternalSourceVerificationSection(view: _confirmedWithConfirmation),
          ),
        );

        final toggleFinder = find.byKey(
          const Key('external-source-verification-toggle'),
        );
        final before = tester.getSemantics(toggleFinder);
        expect(
          before,
          matchesSemantics(
            isButton: true,
            hasExpandedState: true,
            isExpanded: true,
          ),
        );
        expect(
          before.controlsNodes,
          contains('external-source-verification-content'),
        );
        expect(
          find.bySemanticsLabel(confirmedExternalVerificationStatusLabel),
          findsOneWidget,
        );

        await tester.tap(toggleFinder);
        await tester.pumpAndSettle();

        final after = tester.getSemantics(toggleFinder);
        expect(
          after,
          matchesSemantics(
            isButton: true,
            hasExpandedState: true,
            isExpanded: false,
          ),
        );
        expect(
          find.bySemanticsLabel(confirmedExternalVerificationStatusLabel),
          findsNothing,
        );
      },
    );
  });

  group('color contrast', () {
    double relativeLuminance(Color color) {
      double channel(double value) => value <= 0.03928
          ? value / 12.92
          : math.pow((value + 0.055) / 1.055, 2.4).toDouble();

      final r = channel(color.r);
      final g = channel(color.g);
      final b = channel(color.b);
      return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    double contrastRatio(Color a, Color b) {
      final lighter =
          math.max(relativeLuminance(a), relativeLuminance(b)) + 0.05;
      final darker =
          math.min(relativeLuminance(a), relativeLuminance(b)) + 0.05;
      return lighter / darker;
    }

    test('confirmed foreground meets the 4.5:1 AA contrast minimum', () {
      final ratio = contrastRatio(
        ExternalSourceVerificationColors.confirmedForeground,
        ExternalSourceVerificationColors.background,
      );
      expect(ratio, greaterThanOrEqualTo(4.5));
    });

    test('neutral foreground meets the 4.5:1 AA contrast minimum', () {
      final ratio = contrastRatio(
        ExternalSourceVerificationColors.neutralForeground,
        ExternalSourceVerificationColors.background,
      );
      expect(ratio, greaterThanOrEqualTo(4.5));
    });
  });

  group('record detail page regression', () {
    testWidgets(
      'loads the record via the injected source and is not wired to news search',
      (tester) async {
        final source = _FakeRecordSource({'rec-1': _confirmedWithConfirmation});

        await tester.pumpWidget(
          MaterialApp(
            home: RecordDetailPage(localIdentifier: 'rec-1', source: source),
          ),
        );
        await tester.pumpAndSettle();

        expect(source.requestedIdentifiers, ['rec-1']);
        expect(
          find.bySemanticsLabel(confirmedExternalVerificationStatusLabel),
          findsOneWidget,
        );
      },
    );
  });
}

class _FakeRecordSource implements RecordPublicSource {
  _FakeRecordSource(this.responses);

  final Map<String, RecordPublicView> responses;
  final requestedIdentifiers = <String>[];

  @override
  Future<RecordPublicView> loadRecord(String localIdentifier) async {
    requestedIdentifiers.add(localIdentifier);
    final response = responses[localIdentifier];
    if (response == null) throw StateError('unknown localIdentifier');
    return response;
  }
}
