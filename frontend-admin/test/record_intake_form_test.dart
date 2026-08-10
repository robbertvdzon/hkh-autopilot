import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_admin/auth/admin_session.dart';
import 'package:hkh_admin/recordintake/admin_record_intake.dart';
import 'package:hkh_admin/recordintake/external_archive_preview_panel.dart';
import 'package:hkh_admin/recordintake/record_intake_form.dart';

class _RecordingSource implements AdminRecordIntakeSource {
  RecordIntakeInput? received;
  Object? Function()? throwing;
  RecordIntakeCreationResult result = const RecordIntakeCreationResult(
    status: 'intern_concept',
    externalLinkCreated: false,
  );

  int previewCallCount = 0;
  List<String> previewedUrls = [];
  RecordIntakeExternalArchivePreviewResult previewResult =
      const RecordIntakeExternalArchivePreviewResult(
        status: RecordIntakeExternalArchivePreviewStatus.verified,
        name: 'Jan Jansen',
        birthDate: '1900-01-01',
        deathDate: '1980-05-05',
        license: 'CC0',
        sourceUri: 'http://opendata.archieven.nl/id/1000/verified-jan',
      );

  @override
  Future<RecordIntakeCreationResult> create({
    required AdminIdentity identity,
    required RecordIntakeInput input,
  }) async {
    received = input;
    final failure = throwing?.call();
    if (failure != null) throw failure;
    return result;
  }

  @override
  Future<RecordIntakeExternalArchivePreviewResult> previewExternalArchiveData({
    required String durableUrl,
  }) async {
    previewCallCount++;
    previewedUrls.add(durableUrl);
    return previewResult;
  }
}

bool _hasPrimaryFocusWithin(Finder finder) {
  final focusContext = FocusManager.instance.primaryFocus?.context;
  if (focusContext == null) return false;
  return find
      .descendant(of: finder, matching: find.byWidget(focusContext.widget))
      .evaluate()
      .isNotEmpty;
}

Future<void> _fillRequiredFields(
  WidgetTester tester, {
  required String localIdentifier,
}) async {
  await tester.enterText(
    find
        .ancestor(
          of: find.text('Lokale identifier'),
          matching: find.byType(TextFormField),
        )
        .first,
    localIdentifier,
  );
  await tester.enterText(
    find
        .ancestor(of: find.text('Titel'), matching: find.byType(TextFormField))
        .first,
    'Testrecord',
  );
  await tester.enterText(
    find
        .ancestor(
          of: find.text('Datering'),
          matching: find.byType(TextFormField),
        )
        .first,
    'circa 1900',
  );
  await tester.enterText(
    find
        .ancestor(
          of: find.text('Herkomst'),
          matching: find.byType(TextFormField),
        )
        .first,
    'Testherkomst',
  );
  await tester.enterText(
    find
        .ancestor(
          of: find.text('Rechtenstatus'),
          matching: find.byType(TextFormField),
        )
        .first,
    'publicatie toegestaan',
  );
  await tester.enterText(
    find
        .ancestor(
          of: find.text('Toegangs- of permalink'),
          matching: find.byType(TextFormField),
        )
        .first,
    'https://collectie.hkh-autopilot.local/records/$localIdentifier',
  );
  await tester.tap(find.byKey(const Key('privacyClassification')));
  await tester.pumpAndSettle();
  await tester.tap(find.text('geen persoonsgegevens').last);
  await tester.pumpAndSettle();
}

Future<void> _pumpForm(
  WidgetTester tester,
  AdminRecordIntakeSource source,
) async {
  await tester.binding.setSurfaceSize(const Size(800, 2400));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(
          child: RecordIntakeForm(
            identity: const AdminIdentity('admin@example.com'),
            recordIntakeSource: source,
          ),
        ),
      ),
    ),
  );
}

void main() {
  testWidgets(
    'submitting an empty form shows an error summary, moves focus to it, and links each error to its field',
    (tester) async {
      final source = _RecordingSource();
      await _pumpForm(tester, source);

      await tester.tap(find.text('Intake indienen'));
      await tester.pumpAndSettle();

      expect(find.text('Controleer de volgende gegevens'), findsOneWidget);
      expect(find.bySemanticsLabel('Foutsamenvatting'), findsOneWidget);

      final summaryFocus = tester
          .widget<Focus>(
            find
                .ancestor(
                  of: find.text('Controleer de volgende gegevens'),
                  matching: find.byType(Focus),
                )
                .first,
          )
          .focusNode;
      expect(summaryFocus?.hasFocus, isTrue);

      expect(find.text('Vul een lokale identifier in.'), findsOneWidget);
      expect(source.received, isNull);

      final localIdentifierTextField = tester.widget<TextField>(
        find
            .ancestor(
              of: find.text('Lokale identifier'),
              matching: find.byType(TextField),
            )
            .first,
      );

      await tester.tap(
        find.text('Lokale identifier: Vul een lokale identifier in.'),
      );
      await tester.pumpAndSettle();
      expect(localIdentifierTextField.focusNode?.hasFocus, isTrue);
    },
  );

  testWidgets(
    'a complete valid submission clears the form and announces success through the live status region',
    (tester) async {
      final source = _RecordingSource();
      await _pumpForm(tester, source);

      await tester.enterText(
        find
            .ancestor(
              of: find.text('Lokale identifier'),
              matching: find.byType(TextFormField),
            )
            .first,
        'HKH-2026-0001',
      );
      await tester.enterText(
        find
            .ancestor(
              of: find.text('Titel'),
              matching: find.byType(TextFormField),
            )
            .first,
        'Poldermolen De Eendracht',
      );
      await tester.enterText(
        find
            .ancestor(
              of: find.text('Datering'),
              matching: find.byType(TextFormField),
            )
            .first,
        'circa 1890',
      );
      await tester.enterText(
        find
            .ancestor(
              of: find.text('Herkomst'),
              matching: find.byType(TextFormField),
            )
            .first,
        'Streekarchief Waterland',
      );
      await tester.enterText(
        find
            .ancestor(
              of: find.text('Rechtenstatus'),
              matching: find.byType(TextFormField),
            )
            .first,
        'publicatie toegestaan',
      );
      await tester.enterText(
        find
            .ancestor(
              of: find.text('Toegangs- of permalink'),
              matching: find.byType(TextFormField),
            )
            .first,
        'https://collectie.hkh-autopilot.local/records/hkh-2026-0001',
      );
      await tester.tap(find.byKey(const Key('privacyClassification')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('geen persoonsgegevens').last);
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Intake indienen'));
      await tester.tap(find.text('Intake indienen'));
      await tester.pumpAndSettle();

      expect(source.received?.localIdentifier, 'HKH-2026-0001');
      expect(
        find.text('Het record is opgeslagen als intern concept.'),
        findsOneWidget,
      );
      final statusSemanticsWidget = tester.widget<Semantics>(
        find
            .ancestor(
              of: find.text('Het record is opgeslagen als intern concept.'),
              matching: find.byType(Semantics),
            )
            .first,
      );
      expect(statusSemanticsWidget.properties.liveRegion, isTrue);
    },
  );

  testWidgets(
    'a fail closed privacy rejection is reported through the summary and the live status region',
    (tester) async {
      final source = _RecordingSource()
        ..throwing = () => const RecordIntakePrivacyBlocked();
      await _pumpForm(tester, source);

      await tester.enterText(
        find
            .ancestor(
              of: find.text('Lokale identifier'),
              matching: find.byType(TextFormField),
            )
            .first,
        'HKH-2026-0002',
      );
      await tester.enterText(
        find
            .ancestor(
              of: find.text('Titel'),
              matching: find.byType(TextFormField),
            )
            .first,
        'Familiefoto',
      );
      await tester.enterText(
        find
            .ancestor(
              of: find.text('Datering'),
              matching: find.byType(TextFormField),
            )
            .first,
        '1950',
      );
      await tester.enterText(
        find
            .ancestor(
              of: find.text('Herkomst'),
              matching: find.byType(TextFormField),
            )
            .first,
        'Particuliere collectie',
      );
      await tester.enterText(
        find
            .ancestor(
              of: find.text('Rechtenstatus'),
              matching: find.byType(TextFormField),
            )
            .first,
        'publicatie toegestaan',
      );
      await tester.enterText(
        find
            .ancestor(
              of: find.text('Toegangs- of permalink'),
              matching: find.byType(TextFormField),
            )
            .first,
        'https://collectie.hkh-autopilot.local/records/hkh-2026-0002',
      );
      await tester.tap(find.byKey(const Key('privacyClassification')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('mogelijk persoonsgegevens').last);
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Intake indienen'));
      await tester.tap(find.text('Intake indienen'));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('PRIVACY_CLASSIFICATION_BLOCKED'),
        findsWidgets,
      );
    },
  );

  testWidgets(
    'rapid successive durable url edits trigger only one debounced preview call',
    (tester) async {
      final source = _RecordingSource();
      await _pumpForm(tester, source);

      final durableUrlFinder = find
          .ancestor(
            of: find.text('Duurzame URL'),
            matching: find.byType(TextFormField),
          )
          .first;

      await tester.enterText(
        durableUrlFinder,
        'http://opendata.archieven.nl/id/1000/a',
      );
      await tester.pump(const Duration(milliseconds: 100));
      await tester.enterText(
        durableUrlFinder,
        'http://opendata.archieven.nl/id/1000/ab',
      );
      await tester.pump(const Duration(milliseconds: 100));
      await tester.enterText(
        durableUrlFinder,
        'http://opendata.archieven.nl/id/1000/verified-jan',
      );
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();

      expect(source.previewCallCount, 1);
      expect(source.previewedUrls, [
        'http://opendata.archieven.nl/id/1000/verified-jan',
      ]);
      expect(find.text('Geverifieerd'), findsOneWidget);
      expect(find.text('Naam: Jan Jansen'), findsOneWidget);
    },
  );

  testWidgets(
    'leaving the durable url field triggers an immediate preview fetch without waiting for the debounce',
    (tester) async {
      final source = _RecordingSource();
      await _pumpForm(tester, source);

      final durableUrlFinder = find
          .ancestor(
            of: find.text('Duurzame URL'),
            matching: find.byType(TextFormField),
          )
          .first;
      await tester.enterText(
        durableUrlFinder,
        'http://opendata.archieven.nl/id/1000/verified-jan',
      );

      await tester.tap(
        find
            .ancestor(
              of: find.text('Koppelmotivering'),
              matching: find.byType(TextFormField),
            )
            .first,
      );
      await tester.pump();

      expect(source.previewCallCount, 1);
    },
  );

  testWidgets(
    'a url that does not follow the archieven.nl pattern still allows saving without external data',
    (tester) async {
      final source = _RecordingSource()
        ..previewResult = const RecordIntakeExternalArchivePreviewResult(
          status: RecordIntakeExternalArchivePreviewStatus.unreachable,
        );
      await _pumpForm(tester, source);
      await _fillRequiredFields(tester, localIdentifier: 'HKH-2026-0010');

      final durableUrlFinder = find
          .ancestor(
            of: find.text('Duurzame URL'),
            matching: find.byType(TextFormField),
          )
          .first;
      await tester.enterText(
        durableUrlFinder,
        'https://noord-hollandsarchief.nl/record/1',
      );
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();

      expect(find.text('Niet bereikbaar'), findsOneWidget);

      await tester.ensureVisible(find.text('Intake indienen'));
      await tester.tap(find.text('Intake indienen'));
      await tester.pumpAndSettle();

      expect(source.received?.confirmExternalArchiveData, isNull);
      expect(
        find.text('Het record is opgeslagen als intern concept.'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'declining a geen match preview still allows saving without external data',
    (tester) async {
      final source = _RecordingSource()
        ..previewResult = const RecordIntakeExternalArchivePreviewResult(
          status: RecordIntakeExternalArchivePreviewStatus.noMatch,
        );
      await _pumpForm(tester, source);
      await _fillRequiredFields(tester, localIdentifier: 'HKH-2026-0011');

      final durableUrlFinder = find
          .ancestor(
            of: find.text('Duurzame URL'),
            matching: find.byType(TextFormField),
          )
          .first;
      await tester.enterText(
        durableUrlFinder,
        'http://opendata.archieven.nl/id/1000/does-not-exist',
      );
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();

      expect(find.text('Geen match'), findsOneWidget);

      await tester.ensureVisible(
        find.text('Sla op zonder externe brongegevens'),
      );
      await tester.tap(find.text('Sla op zonder externe brongegevens'));
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Intake indienen'));
      await tester.tap(find.text('Intake indienen'));
      await tester.pumpAndSettle();

      expect(source.received?.confirmExternalArchiveData, isFalse);
      expect(
        find.text('Het record is opgeslagen als intern concept.'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'confirming external archive data sends deceasedStatus, nextOfKinConfirmed and confirmExternalArchiveData',
    (tester) async {
      final source = _RecordingSource();
      await _pumpForm(tester, source);
      await _fillRequiredFields(tester, localIdentifier: 'HKH-2026-0012');

      final durableUrlFinder = find
          .ancestor(
            of: find.text('Duurzame URL'),
            matching: find.byType(TextFormField),
          )
          .first;
      await tester.enterText(
        durableUrlFinder,
        'http://opendata.archieven.nl/id/1000/verified-jan',
      );
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.byKey(const Key('deceasedStatus')));
      await tester.tap(find.byKey(const Key('deceasedStatus')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('overleden').last);
      await tester.pumpAndSettle();

      await tester.ensureVisible(
        find.text('Bevestig brongegevens en gebruik bij record'),
      );
      await tester.tap(
        find.text('Bevestig brongegevens en gebruik bij record'),
      );
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Intake indienen'));
      await tester.tap(find.text('Intake indienen'));
      await tester.pumpAndSettle();

      expect(source.received?.deceasedStatus, 'overleden');
      expect(source.received?.nextOfKinConfirmed, isFalse);
      expect(source.received?.confirmExternalArchiveData, isTrue);
    },
  );

  testWidgets(
    'the fetch, confirm and decline buttons are reachable via Tab in logical order and activate with Enter/Space',
    (tester) async {
      final source = _RecordingSource();
      await _pumpForm(tester, source);

      final durableUrlFinder = find
          .ancestor(
            of: find.text('Duurzame URL'),
            matching: find.byType(TextFormField),
          )
          .first;
      await tester.enterText(
        durableUrlFinder,
        'http://opendata.archieven.nl/id/1000/verified-jan',
      );
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();
      final callsAfterDebounce = source.previewCallCount;

      await tester.tap(durableUrlFinder);
      await tester.pumpAndSettle();

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(
        _hasPrimaryFocusWithin(
          find.byKey(const Key('archivePreviewFetchButton')),
        ),
        isTrue,
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(source.previewCallCount, callsAfterDebounce + 1);

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(
        _hasPrimaryFocusWithin(
          find.byKey(const Key('confirmExternalArchiveData')),
        ),
        isTrue,
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pumpAndSettle();

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(
        _hasPrimaryFocusWithin(
          find.byKey(const Key('declineExternalArchiveData')),
        ),
        isTrue,
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pumpAndSettle();

      final statusText = tester
          .widgetList<Text>(
            find.descendant(
              of: find.byType(ExternalArchivePreviewPanel),
              matching: find.text(
                'Externe brongegevens worden niet gebruikt bij opslaan.',
              ),
            ),
          )
          .toList();
      expect(statusText, isNotEmpty);
    },
  );
}
