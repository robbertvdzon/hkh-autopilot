import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_admin/externalverification/external_verification_link_view.dart';

void main() {
  testWidgets(
    'exposes an aria-label announcing an external source opening in a new tab',
    (tester) async {
      var tapped = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ExternalVerificationLinkView(
              externalUri: 'http://opendata.archieven.nl/id/1234/abcd-ef01',
              onTap: () => tapped = true,
            ),
          ),
        ),
      );

      expect(
        find.bySemanticsLabel(ExternalVerificationLinkView.linkSemanticLabel),
        findsOneWidget,
      );

      await tester.tap(find.text(ExternalVerificationLinkView.linkLabel));
      expect(tapped, isTrue);
    },
  );

  testWidgets('the semantic label mentions both an external source and a new tab', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: ExternalVerificationLinkView(
            externalUri: 'http://opendata.archieven.nl/id/1234/abcd-ef01',
          ),
        ),
      ),
    );

    final semantics = tester.getSemantics(
      find.bySemanticsLabel(ExternalVerificationLinkView.linkSemanticLabel),
    );
    expect(semantics.label, contains('archieven.nl'));
    expect(semantics.label, contains('nieuw tabblad'));
  });
}
