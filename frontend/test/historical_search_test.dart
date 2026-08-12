import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/backend/backend_client.dart';
import 'package:hkh_app/backend/backend_status.dart';
import 'package:hkh_app/historical/historical_search.dart';
import 'package:hkh_app/main.dart';
import 'package:hkh_app/news/latest_news.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _responseJson = '''
{"results":[{"source":"OPEN_ARCHIEVEN","sourceRecordId":"a-1",
"stableUrl":"https://example.test/record/a-1","title":"Kasteel","description":"Beschrijving",
"person":"Jan","event":"Huwelijk","dateStart":"1900","dateEnd":null,
"institution":"Historisch Archief","rights":null,"privacy":null,
"retrievedAt":"2026-08-12T00:00:00Z","technicalStatus":"AVAILABLE",
"metadataRights":"UNKNOWN","objectMediaRights":"UNKNOWN","privacyStatus":"UNKNOWN"}],
"total":1,"start":0,"limit":100,
"sources":[{"source":"OPEN_ARCHIEVEN","status":"AVAILABLE","message":null}]}
''';

class _HistoricalSource implements HistoricalSearchSource {
  _HistoricalSource(this.result);

  final Future<HistoricalSearchResponse> result;
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
  }) {
    calls++;
    return result;
  }
}

void main() {
  test('sends normalized historical filters and pagination', () async {
    final client = BackendClient(
      'https://example.test',
      client: MockClient((request) async {
        expect(request.url.path, '/api/historical-search');
        expect(request.url.queryParameters['q'], 'kasteel');
        expect(request.url.queryParameters['place'], 'Heemskerk');
        expect(request.url.queryParameters['person'], 'Jan');
        expect(request.url.queryParameters['event'], 'Huwelijk');
        expect(request.url.queryParameters['fromYear'], '1800');
        expect(request.url.queryParameters['toYear'], '1900');
        expect(request.url.queryParameters['source'], 'OPEN_ARCHIEVEN');
        expect(request.url.queryParameters['start'], '100');
        expect(request.url.queryParameters['limit'], '100');
        return http.Response(_responseJson, 200);
      }),
    );

    final response = await client.loadHistoricalSearch(
      text: 'kasteel',
      place: 'Heemskerk',
      person: 'Jan',
      event: 'Huwelijk',
      fromYear: '1800',
      toYear: '1900',
      source: HistoricalSourceChoice.openArchieven,
      start: 100,
    );

    expect(
      response.results.single.stableUrl,
      'https://example.test/record/a-1',
    );
    expect(response.results.single.metadataRights, 'UNKNOWN');
  });

  testWidgets(
    'shows loading, success, fail-closed statuses and external action',
    (tester) async {
      final completer = Completer<HistoricalSearchResponse>();
      final source = _HistoricalSource(completer.future);
      await tester.pumpWidget(
        MaterialApp(home: HistoricalSearchPage(source: source)),
      );

      await tester.enterText(find.bySemanticsLabel('Vrije tekst'), 'kasteel');
      await tester.ensureVisible(
        find.byKey(const Key('historical-search-submit')),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pump();
      await tester.pump();
      expect(
        find.bySemanticsLabel('Historische zoekresultaten worden geladen.'),
        findsOneWidget,
      );

      completer.complete(
        HistoricalSearchResponse.fromJson(const <String, dynamic>{
          'results': [
            {
              'source': 'OPEN_ARCHIEVEN',
              'sourceRecordId': 'a-1',
              'stableUrl': 'https://example.test/record/a-1',
              'title': 'Kasteel',
              'description': 'Beschrijving',
              'person': 'Jan',
              'event': 'Huwelijk',
              'dateStart': '1900',
              'dateEnd': null,
              'institution': 'Historisch Archief',
              'rights': null,
              'privacy': null,
              'retrievedAt': '2026-08-12T00:00:00Z',
              'technicalStatus': 'AVAILABLE',
              'metadataRights': 'UNKNOWN',
              'objectMediaRights': 'UNKNOWN',
              'privacyStatus': 'UNKNOWN',
            },
          ],
          'total': 1,
          'start': 0,
          'limit': 100,
          'sources': [
            {
              'source': 'OPEN_ARCHIEVEN',
              'status': 'AVAILABLE',
              'message': null,
            },
          ],
        }),
      );
      await tester.pumpAndSettle();

      expect(find.text('Kasteel'), findsNothing);
      expect(find.text('Beschrijving'), findsNothing);
      expect(find.text('Persoon: Jan'), findsNothing);
      expect(find.text('Gebeurtenis: Huwelijk'), findsNothing);
      expect(find.text('Datering: 1900'), findsNothing);
      expect(find.text('Metadatarechten: Onbekend'), findsOneWidget);
      expect(find.text('Object-/mediarechten: Onbekend'), findsOneWidget);
      expect(find.text('Privacy: Onbekend'), findsOneWidget);
      expect(find.text('Externe bron openen in nieuw tabblad'), findsOneWidget);
    },
  );

  testWidgets(
    'shows content metadata only when rights and privacy are explicit',
    (tester) async {
    final response = HistoricalSearchResponse(
        results: [
          HistoricalSearchResult(
            source: 'EUROPEANA',
            sourceRecordId: 'safe-1',
            stableUrl: 'https://example.test/safe-1',
            title: 'Veilige titel',
            description: 'Veilige beschrijving',
            person: 'Jan',
            event: 'Huwelijk',
            dateStart: '1900',
            institution: 'Archief',
            retrievedAt: DateTime.utc(2026, 8, 12),
            metadataRights: 'ALLOWED',
            privacyStatus: 'CLEAR',
          ),
        ],
        total: 1,
        start: 0,
        limit: 100,
        sources: [
          HistoricalSourceStatus(source: 'EUROPEANA', status: 'AVAILABLE'),
        ],
      );
      await tester.pumpWidget(
        MaterialApp(
          home: HistoricalSearchPage(
            source: _HistoricalSource(Future.value(response)),
          ),
        ),
      );
      await tester.ensureVisible(
        find.byKey(const Key('historical-search-submit')),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Veilige titel').first);

      expect(find.text('Veilige titel'), findsOneWidget);
      expect(find.text('Veilige beschrijving'), findsOneWidget);
      expect(find.text('Persoon: Jan'), findsOneWidget);
      expect(find.text('Datering: 1900'), findsOneWidget);
    },
  );

  testWidgets('shows empty and retryable error states', (tester) async {
    final error = Completer<HistoricalSearchResponse>();
    final source = _HistoricalSource(error.future);
    await tester.pumpWidget(
      MaterialApp(home: HistoricalSearchPage(source: source)),
    );
    await tester.ensureVisible(
      find.byKey(const Key('historical-search-submit')),
    );
    await tester.tap(find.byKey(const Key('historical-search-submit')));
    await tester.pump();
    error.completeError(StateError('offline'));
    await tester.pumpAndSettle();
    expect(
      find.text('Historisch zoeken is tijdelijk niet beschikbaar.'),
      findsOneWidget,
    );
    expect(find.byKey(const Key('historical-search-retry')), findsOneWidget);

    final empty = _HistoricalSearchResponseFactory.empty();
    await tester.pumpWidget(
      MaterialApp(
        home: HistoricalSearchPage(
          source: _HistoricalSource(Future.value(empty)),
        ),
      ),
    );
    await tester.ensureVisible(
      find.byKey(const Key('historical-search-submit')),
    );
    await tester.tap(find.byKey(const Key('historical-search-submit')));
    await tester.pumpAndSettle();
    expect(find.text('Geen historische resultaten gevonden.'), findsOneWidget);
  });

  testWidgets('homepage exposes the independent historical search entry', (
    tester,
  ) async {
    final source = _HistoricalSource(
      Future.value(_HistoricalSearchResponseFactory.empty()),
    );
    await tester.pumpWidget(
      HkhApp(
        statusSource: _StatusSource(),
        newsSource: _NewsSource(),
        historicalSource: source,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Laatste nieuws'), findsOneWidget);
    expect(find.text('Historisch zoeken'), findsOneWidget);
  });
}

class _HistoricalSearchResponseFactory {
  static HistoricalSearchResponse empty() => const HistoricalSearchResponse(
    results: [],
    total: 0,
    start: 0,
    limit: 100,
    sources: [
      HistoricalSourceStatus(source: 'OPEN_ARCHIEVEN', status: 'AVAILABLE'),
    ],
  );
}

class _StatusSource implements BackendStatusSource {
  @override
  Future<BackendStatus> load() async =>
      const BackendStatus(application: 'hkh', version: 'test', commit: 'test');
}

class _NewsSource implements LatestNewsSource {
  @override
  Future<NewsSearchResult> loadLatestNews({String? q, String? entity}) async =>
      const NewsSearchResult(items: [], total: 0, entities: []);
}
