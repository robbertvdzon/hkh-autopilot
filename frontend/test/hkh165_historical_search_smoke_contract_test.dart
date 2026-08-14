import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/backend/backend_client.dart';
import 'package:hkh_app/historical/historical_search.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _hkh165ValidRouteResponse = '''
{
  "results": [{
    "source": "OPEN_ARCHIEVEN",
    "sourceRecordId": "hee:synthetic-1",
    "stableUrl": "https://synthetic.example/items/record-1",
    "source_name": "Synthetisch Archief",
    "stable_identifier": "hee:synthetic-1",
    "original_source_url": "https://synthetic.example/items/record-1",
    "title": "Synthetisch Heemskerk-resultaat",
    "description": "Minimale testfixture",
    "place": "Heemskerk",
    "dateStart": "1900",
    "retrievedAt": "2026-08-14T00:00:00Z",
    "technicalStatus": "AVAILABLE",
    "metadataRights": "ALLOWED",
    "objectMediaRights": "RESTRICTED",
    "privacyStatus": "CLEAR",
    "placeStatus": "AVAILABLE"
  }],
  "total": 1,
  "start": 0,
  "limit": 100,
  "state": "RESULTS",
  "sources": [
    {"source": "EUROPEANA", "status": "DISABLED", "message": "Bron is niet geconfigureerd."},
    {"source": "OPEN_ARCHIEVEN", "status": "AVAILABLE", "message": null, "resultCount": 1, "heemskerkCount": 1}
  ]
}
''';

class _Hkh165SmokeSource implements HistoricalSearchSource {
  _Hkh165SmokeSource(this.response);

  final HistoricalSearchResponse response;
  int calls = 0;

  @override
  Future<HistoricalSearchResponse> loadHistoricalSearch({
    String? text,
    String? place,
    String? person,
    String? event,
    String? fromYear,
    String? toYear,
    HistoricalSourceChoice? source,
    int start = 0,
    int limit = 100,
  }) async {
    calls++;
    return response;
  }
}

HistoricalSearchResponse _hkh165ResponseFrom(String json) =>
    HistoricalSearchResponse.fromJson(jsonDecode(json) as Map<String, dynamic>);

void main() {
  test(
    'BackendClient parses the synthetic public route contract unchanged',
    () async {
      final client = BackendClient(
        'https://synthetic.example',
        client: MockClient((request) async {
          expect(request.url.path, '/api/historical-search');
          expect(request.url.queryParameters['q'], 'Heemskerk');
          expect(request.url.queryParameters['start'], '0');
          expect(request.url.queryParameters['limit'], '100');
          return http.Response(_hkh165ValidRouteResponse, 200);
        }),
      );

      final response = await client.loadHistoricalSearch(text: ' Heemskerk ');
      final result = response.results[0];
      expect(response.state, 'RESULTS');
      expect(response.sources[0].status, 'DISABLED');
      expect(response.sources[1].status, 'AVAILABLE');
      expect(result.normalizedSourceName, 'Synthetisch Archief');
      expect(result.normalizedStableIdentifier, 'hee:synthetic-1');
      expect(
        result.normalizedOriginalSourceUrl,
        'https://synthetic.example/items/record-1',
      );
    },
  );

  testWidgets(
    'successful Heemskerk result is visible in the existing search page',
    (tester) async {
      final source = _Hkh165SmokeSource(
        _hkh165ResponseFrom(_hkh165ValidRouteResponse),
      );
      await tester.pumpWidget(
        MaterialApp(home: HistoricalSearchPage(source: source)),
      );

      await tester.enterText(find.bySemanticsLabel('Vrije tekst'), 'Heemskerk');
      await tester.ensureVisible(
        find.byKey(const Key('historical-search-submit')),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pumpAndSettle();

      expect(source.calls, 1);
      await tester.ensureVisible(find.text('Synthetisch Heemskerk-resultaat'));
      expect(find.text('Synthetisch Heemskerk-resultaat'), findsOneWidget);
      expect(find.text('Bronnaam: Synthetisch Archief'), findsOneWidget);
      expect(find.text('Bronidentifier: hee:synthetic-1'), findsOneWidget);
      expect(
        find.text('Technische beschikbaarheid: Toegestaan'),
        findsOneWidget,
      );
      expect(find.text('Europeana: niet geconfigureerd.'), findsOneWidget);
    },
  );

  testWidgets(
    'zero, partial and complete failure responses keep distinct UI states',
    (tester) async {
      final empty = HistoricalSearchResponse(
        results: const [],
        total: 0,
        start: 0,
        limit: 100,
        state: 'NO_RESULTS',
        sources: const [
          HistoricalSourceStatus(source: 'EUROPEANA', status: 'DISABLED'),
          HistoricalSourceStatus(
            source: 'OPEN_ARCHIEVEN',
            status: 'AVAILABLE',
            resultCount: 0,
            heemskerkCount: 0,
          ),
        ],
      );
      await tester.pumpWidget(
        MaterialApp(
          home: HistoricalSearchPage(source: _Hkh165SmokeSource(empty)),
        ),
      );
      await tester.ensureVisible(
        find.byKey(const Key('historical-search-submit')),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pumpAndSettle();
      expect(
        find.text('Geen historische resultaten gevonden.'),
        findsOneWidget,
      );
      expect(
        find.text('Geen historische bronnen konden worden geraadpleegd.'),
        findsNothing,
      );
      expect(
        find.text(
          'Open Archieven: beschikbaar, 0 resultaten. Lokale Heemskerk-indicatie op basis van plaatsmetadata: 0 (geen historisch bewijs).',
        ),
        findsOneWidget,
      );

      final partial = HistoricalSearchResponse(
        results: [
          HistoricalSearchResult(
            source: 'OPEN_ARCHIEVEN',
            sourceRecordId: 'hee:synthetic-1',
            stableUrl: 'https://synthetic.example/items/record-1',
            sourceName: 'Synthetisch Archief',
            stableIdentifier: 'hee:synthetic-1',
            originalSourceUrl: 'https://synthetic.example/items/record-1',
            title: 'Beschikbaar deelresultaat',
            retrievedAt: DateTime.utc(2026, 8, 14),
            metadataRights: 'ALLOWED',
            privacyStatus: 'CLEAR',
          ),
        ],
        total: 1,
        start: 0,
        limit: 100,
        state: 'PARTIAL_AVAILABILITY',
        sources: const [
          HistoricalSourceStatus(
            source: 'EUROPEANA',
            status: 'TEMPORARILY_UNAVAILABLE',
          ),
          HistoricalSourceStatus(
            source: 'OPEN_ARCHIEVEN',
            status: 'AVAILABLE',
            resultCount: 1,
            heemskerkCount: 1,
          ),
        ],
      );
      await tester.pumpWidget(
        MaterialApp(
          home: HistoricalSearchPage(source: _Hkh165SmokeSource(partial)),
        ),
      );
      await tester.ensureVisible(
        find.byKey(const Key('historical-search-submit')),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pumpAndSettle();
      expect(find.text('Beschikbaar deelresultaat'), findsOneWidget);
      expect(
        find.text('Europeana: tijdelijk niet beschikbaar.'),
        findsOneWidget,
      );
      expect(
        find.textContaining('Open Archieven: beschikbaar, 1 resultaten.'),
        findsOneWidget,
      );

      final failure = HistoricalSearchResponse(
        results: const [],
        total: 0,
        start: 0,
        limit: 100,
        state: 'SOURCE_FAILURE',
        sources: const [
          HistoricalSourceStatus(
            source: 'EUROPEANA',
            status: 'TEMPORARILY_UNAVAILABLE',
          ),
          HistoricalSourceStatus(
            source: 'OPEN_ARCHIEVEN',
            status: 'HTTP_ERROR',
          ),
        ],
      );
      await tester.pumpWidget(
        MaterialApp(
          home: HistoricalSearchPage(source: _Hkh165SmokeSource(failure)),
        ),
      );
      await tester.ensureVisible(
        find.byKey(const Key('historical-search-submit')),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pumpAndSettle();
      expect(
        find.text('Geen historische bronnen konden worden geraadpleegd.'),
        findsOneWidget,
      );
      expect(find.byKey(const Key('historical-search-retry')), findsOneWidget);
      expect(find.text('0 historische resultaten'), findsNothing);
      expect(
        find.textContaining('0 historische resultaten geladen.'),
        findsNothing,
      );
    },
  );
}
