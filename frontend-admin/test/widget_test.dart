import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_admin/auth/admin_session.dart';
import 'package:hkh_admin/main.dart';
import 'package:hkh_admin/news/admin_latest_news.dart';
import 'package:hkh_admin/recordintake/admin_record_intake.dart';

class _NewsSource implements AdminLatestNewsSource {
  String? title;
  String? message;

  @override
  Future<void> create({
    required AdminIdentity identity,
    required String title,
    required String message,
  }) async {
    this.title = title;
    this.message = message;
  }
}

class _RecordIntakeSource implements AdminRecordIntakeSource {
  @override
  Future<RecordIntakeCreationResult> create({
    required AdminIdentity identity,
    required RecordIntakeInput input,
  }) async => const RecordIntakeCreationResult(
    status: 'intern_concept',
    externalLinkCreated: false,
  );

  @override
  Future<RecordIntakeExternalArchivePreviewResult> previewExternalArchiveData({
    required String durableUrl,
  }) async => const RecordIntakeExternalArchivePreviewResult(
    status: RecordIntakeExternalArchivePreviewStatus.unreachable,
  );
}

class _AuthenticatedSession implements AdminSessionSource {
  @override
  bool get configured => true;
  @override
  Stream<AdminIdentity> get identities => const Stream.empty();
  @override
  Future<AdminIdentity?> bootstrap() async =>
      const AdminIdentity('admin@example.com');
  @override
  Future<AdminIdentity?> signIn() async =>
      const AdminIdentity('admin@example.com');
  @override
  Future<void> signOut() async {}
  @override
  void dispose() {}
}

void main() {
  testWidgets('shows the verified administrator', (tester) async {
    final newsSource = _NewsSource();
    await tester.pumpWidget(
      HkhAdminApp(
        sessionSource: _AuthenticatedSession(),
        newsSource: newsSource,
        recordIntakeSource: _RecordIntakeSource(),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Beheerder geverifieerd'), findsOneWidget);
    expect(find.text('admin@example.com'), findsOneWidget);
    expect(find.text('Nieuw bericht'), findsOneWidget);

    final newsFields = find.byType(TextFormField);
    await tester.enterText(newsFields.at(0), 'Dorpsnieuws');
    await tester.enterText(newsFields.at(1), 'Een historisch bericht.');
    await tester.ensureVisible(find.text('Publiceren'));
    await tester.tap(find.text('Publiceren'));
    await tester.pumpAndSettle();

    expect(newsSource.title, 'Dorpsnieuws');
    expect(newsSource.message, 'Een historisch bericht.');
    expect(find.text('Het nieuwsbericht is gepubliceerd.'), findsOneWidget);
  });

  testWidgets('explains when Google login is not configured', (tester) async {
    await tester.pumpWidget(
      HkhAdminApp(
        sessionSource: const DisabledAdminSessionSource(),
        newsSource: _NewsSource(),
        recordIntakeSource: _RecordIntakeSource(),
        googleButtonBuilder: () => const SizedBox.shrink(),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Google-login is nog niet geconfigureerd.'),
      findsOneWidget,
    );
  });
}
