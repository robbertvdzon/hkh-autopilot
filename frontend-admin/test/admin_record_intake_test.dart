import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_admin/auth/admin_session.dart';
import 'package:hkh_admin/recordintake/admin_record_intake.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  RecordIntakeInput validInput() => const RecordIntakeInput(
    localIdentifier: 'HKH-2026-0001',
    title: 'Poldermolen De Eendracht',
    description: null,
    dating: 'circa 1890',
    provenance: 'Streekarchief Waterland',
    rightsStatus: 'publicatie toegestaan',
    privacyClassification: 'geen persoonsgegevens',
    accessUrl: 'https://collectie.hkh-autopilot.local/records/hkh-2026-0001',
  );

  test('posts the intake with the authenticated bearer token', () async {
    final client = AdminRecordIntakeClient(
      'https://example.test',
      client: MockClient((request) async {
        expect(
          request.url.toString(),
          'https://example.test/api/record-intake',
        );
        expect(request.headers['Authorization'], 'Bearer token');
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        expect(body['localIdentifier'], 'HKH-2026-0001');
        return http.Response(
          jsonEncode({'id': 1, 'status': 'intern_concept', 'createdAt': '2026-08-08T10:00:00Z'}),
          201,
        );
      }),
    );

    final result = await client.create(
      identity: const AdminIdentity(
        'admin@example.com',
        requestHeaders: {'Authorization': 'Bearer token'},
      ),
      input: validInput(),
    );

    expect(result.status, 'intern_concept');
    expect(result.externalLinkCreated, isFalse);
  });

  test('reports the external link as created when present in the response', () async {
    final client = AdminRecordIntakeClient(
      'https://example.test',
      client: MockClient(
        (_) async => http.Response(
          jsonEncode({
            'id': 1,
            'status': 'intern_concept',
            'createdAt': '2026-08-08T10:00:00Z',
            'externalLink': {'id': 2, 'status': 'concept'},
          }),
          201,
        ),
      ),
    );

    final result = await client.create(
      identity: const AdminIdentity('admin@example.com'),
      input: validInput(),
    );

    expect(result.externalLinkCreated, isTrue);
  });

  test('throws RecordIntakeFieldErrors on a 400 response', () async {
    final client = AdminRecordIntakeClient(
      'https://example.test',
      client: MockClient(
        (_) async => http.Response(
          jsonEncode({
            'fieldErrors': ['localIdentifier', 'accessUrl'],
          }),
          400,
        ),
      ),
    );

    try {
      await client.create(
        identity: const AdminIdentity('admin@example.com'),
        input: validInput(),
      );
      fail('expected RecordIntakeFieldErrors');
    } on RecordIntakeFieldErrors catch (error) {
      expect(error.fieldErrors, ['localIdentifier', 'accessUrl']);
    }
  });

  test('throws RecordIntakePrivacyBlocked on a 422 response', () async {
    final client = AdminRecordIntakeClient(
      'https://example.test',
      client: MockClient(
        (_) async => http.Response(
          jsonEncode({'errorCode': 'PRIVACY_CLASSIFICATION_BLOCKED'}),
          422,
        ),
      ),
    );

    await expectLater(
      client.create(
        identity: const AdminIdentity('admin@example.com'),
        input: validInput(),
      ),
      throwsA(isA<RecordIntakePrivacyBlocked>()),
    );
  });

  test('reports a rejected intake for other status codes', () async {
    final client = AdminRecordIntakeClient(
      'https://example.test',
      client: MockClient((_) async => http.Response('unauthorized', 401)),
    );

    await expectLater(
      client.create(
        identity: const AdminIdentity('admin@example.com'),
        input: validInput(),
      ),
      throwsStateError,
    );
  });
}
