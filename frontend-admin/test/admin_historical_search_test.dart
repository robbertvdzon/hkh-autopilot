import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_admin/auth/admin_session.dart';
import 'package:hkh_admin/historical/admin_historical_search.dart';
import 'package:hkh_admin/historical/admin_historical_search_view.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class _HistoricalSource implements AdminHistoricalSearchSource {
  String? query;
  AdminIdentity? identity;

  @override
  Future<AdminHistoricalSearchResultPage> search({
    required AdminIdentity identity,
    required String query,
  }) async {
    this.identity = identity;
    this.query = query;
    return AdminHistoricalSearchResultPage(
      results: [
        AdminHistoricalSearchResult(
          source: 'OPEN_ARCHIEVEN',
          sourceName: 'Synthetisch Archief',
          stableIdentifier: 'hee:record-1',
          originalSourceUrl: 'https://source.example/record-1',
          technicalStatus: 'AVAILABLE',
          sourceVerification: const AdminHistoricalStatus(
            status: 'CONFIRMED',
            reason: 'Bronidentiteit is bevestigd.',
          ),
          metadataRights: const AdminHistoricalStatus(
            status: 'UNKNOWN',
            reason: 'Rechteninformatie ontbreekt.',
          ),
          privacy: const AdminHistoricalStatus(
            status: 'REJECTED',
            reason: 'Privacy is geblokkeerd.',
          ),
          publicRelease: const AdminHistoricalStatus(
            status: 'NOT_APPLICABLE',
            reason: 'Niet van toepassing.',
          ),
          objectMediaRights: const AdminHistoricalStatus(
            status: 'UNKNOWN',
            reason: 'Objectrechten zijn onbekend.',
          ),
        ),
      ],
      total: 1,
      start: 0,
      limit: 100,
      state: 'RESULTS',
    );
  }
}

void main() {
  test(
    'client sends the authenticated admin request and parses safe fields',
    () async {
      final client = AdminHistoricalSearchClient(
        'https://example.test',
        client: MockClient((request) async {
          expect(
            request.url.toString(),
            'https://example.test/api/admin/historical-search?q=kerk',
          );
          expect(request.headers['Authorization'], 'Bearer token');
          return http.Response(
            jsonEncode({
              'results': [
                {
                  'source': 'OPEN_ARCHIEVEN',
                  'source_name': 'Archief',
                  'stable_identifier': 'hee:1',
                  'original_source_url': 'https://source.example/1',
                  'technicalStatus': 'AVAILABLE',
                  'sourceVerificationStatus': 'CONFIRMED',
                  'sourceVerificationReason': 'Bevestigd.',
                  'metadataRightsStatus': 'UNKNOWN',
                  'metadataRightsReason': 'Onbekend.',
                  'privacyStatus': 'CONFIRMED',
                  'privacyReason': 'CLEAR.',
                  'publicReleaseStatus': 'UNKNOWN',
                  'publicReleaseReason': 'Niet bevestigd.',
                  'objectMediaRightsStatus': 'REJECTED',
                  'objectMediaRightsReason': 'Beperkt.',
                },
              ],
              'total': 1,
              'start': 0,
              'limit': 100,
              'state': 'RESULTS',
            }),
            200,
          );
        }),
      );

      final page = await client.search(
        identity: const AdminIdentity(
          'admin@example.com',
          requestHeaders: {'Authorization': 'Bearer token'},
        ),
        query: 'kerk',
      );

      expect(page.results.single.sourceName, 'Archief');
      expect(page.results.single.metadataRights.status, 'UNKNOWN');
      expect(page.results.single.objectMediaRights.status, 'REJECTED');
    },
  );

  testWidgets(
    'shows source identity, textual statuses and reasons accessibly',
    (tester) async {
      final source = _HistoricalSource();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: AdminHistoricalSearchView(
                identity: const AdminIdentity('admin@example.com'),
                source: source,
              ),
            ),
          ),
        ),
      );

      await tester.enterText(find.byType(TextField), 'Heemskerk');
      await tester.tap(find.text('Zoeken'));
      await tester.pumpAndSettle();

      expect(source.query, 'Heemskerk');
      expect(source.identity?.email, 'admin@example.com');
      expect(find.textContaining('Bron: Synthetisch Archief'), findsOneWidget);
      expect(find.text('Stabiele identifier: hee:record-1'), findsOneWidget);
      expect(
        find.text('Permanente bronlink: https://source.example/record-1'),
        findsOneWidget,
      );
      expect(
        find.textContaining('Bronverificatie: Bevestigd.'),
        findsOneWidget,
      );
      expect(find.textContaining('Metadatarechten: Onbekend.'), findsOneWidget);
      expect(find.textContaining('Privacy: Afgewezen.'), findsOneWidget);
      expect(
        find.textContaining('Publieke vrijgave: Niet van toepassing.'),
        findsOneWidget,
      );
      expect(
        find.bySemanticsLabel('Privacy: Afgewezen. Privacy is geblokkeerd.'),
        findsOneWidget,
      );
    },
  );

  test('status foreground colors meet the normal-text contrast target', () {
    for (final color in [
      AdminHistoricalStatusColors.confirmed,
      AdminHistoricalStatusColors.unknown,
      AdminHistoricalStatusColors.rejected,
      AdminHistoricalStatusColors.notApplicable,
    ]) {
      final foreground = color.computeLuminance() + 0.05;
      final background =
          AdminHistoricalStatusColors.background.computeLuminance() + 0.05;
      final contrast = foreground > background
          ? foreground / background
          : background / foreground;
      expect(contrast, greaterThanOrEqualTo(4.5));
    }
  });
}
