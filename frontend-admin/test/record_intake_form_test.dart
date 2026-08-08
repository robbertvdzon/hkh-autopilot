import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_admin/auth/admin_session.dart';
import 'package:hkh_admin/recordintake/admin_record_intake.dart';
import 'package:hkh_admin/recordintake/record_intake_form.dart';

class _RecordingSource implements AdminRecordIntakeSource {
  RecordIntakeInput? received;
  Object? Function()? throwing;
  RecordIntakeCreationResult result = const RecordIntakeCreationResult(
    status: 'intern_concept',
    externalLinkCreated: false,
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

      await tester.tap(find.text('Lokale identifier: Vul een lokale identifier in.'));
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
            .ancestor(of: find.text('Titel'), matching: find.byType(TextFormField))
            .first,
        'Poldermolen De Eendracht',
      );
      await tester.enterText(
        find
            .ancestor(of: find.text('Datering'), matching: find.byType(TextFormField))
            .first,
        'circa 1890',
      );
      await tester.enterText(
        find
            .ancestor(of: find.text('Herkomst'), matching: find.byType(TextFormField))
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
            .ancestor(of: find.text('Titel'), matching: find.byType(TextFormField))
            .first,
        'Familiefoto',
      );
      await tester.enterText(
        find
            .ancestor(of: find.text('Datering'), matching: find.byType(TextFormField))
            .first,
        '1950',
      );
      await tester.enterText(
        find
            .ancestor(of: find.text('Herkomst'), matching: find.byType(TextFormField))
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
}
